package com.n11.ai.domain.chat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.n11.agent.AgentTool;
import com.n11.agent.ToolRegistry;
import com.n11.agent.ToolResult;
import com.n11.ai.application.ConversationStore;
import com.n11.ai.domain.tools.ToolDispatcher;
import com.n11.ai.port.ChatProvider;
import com.n11.ai.port.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.BiConsumer;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final int MAX_TOOL_LOOPS = 6;

    private final ChatProvider chatProvider;
    private final ToolDispatcher toolDispatcher;
    private final ToolRegistry toolRegistry;
    private final SystemPromptProvider systemPrompt;
    private final ObjectMapper json = new ObjectMapper();

    public ChatService(ChatProvider chatProvider, ToolDispatcher toolDispatcher,
                       ToolRegistry toolRegistry, SystemPromptProvider systemPrompt) {
        this.chatProvider = chatProvider;
        this.toolDispatcher = toolDispatcher;
        this.toolRegistry = toolRegistry;
        this.systemPrompt = systemPrompt;
    }

    // ---- Sync (POST /chat) ---------------------------------------------

    public String chat(ConversationStore store, String userMessage, String correlationId) {
        store.appendUserMessage(userMessage);
        String userId = store.userIdOrNull() == null ? null : store.userIdOrNull().toString();
        List<ChatMessage> history = withSystemPrompt(store.history());
        List<ToolSchema> tools = toolSchemas();

        for (int i = 0; i < MAX_TOOL_LOOPS; i++) {
            ChatResponse resp = chatProvider.chat(history, tools);
            if (resp.toolCalls() == null || resp.toolCalls().isEmpty()) {
                String text = resp.text() == null ? "" : resp.text();
                store.appendAssistantText(text);
                return text;
            }
            // Persist the assistant tool-call turn
            String toolCallJson = serialize(resp.toolCalls());
            store.appendAssistantToolCalls(toolCallJson);
            history.add(new ChatMessage(MessageRole.ASSISTANT, null, resp.toolCalls(), null));

            // Dispatch every tool call
            List<ToolCallResult> results = new ArrayList<>();
            for (ToolCallRequest call : resp.toolCalls()) {
                ToolResult r = toolDispatcher.dispatch(
                    call.name(), call.argsJson(), userId, correlationId, store.seenIds());
                results.add(toToolCallResult(call.callId(), call.name(), r));
            }
            String resultJson = serialize(results);
            store.appendToolResults(resultJson);
            history.add(new ChatMessage(MessageRole.TOOL, null, null, results));
        }
        String fallback = "Üzgünüm, isteğini tamamlayamadım. Tekrar dener misin?";
        store.appendAssistantText(fallback);
        return fallback;
    }

    // ---- SSE (POST /chat/stream) ---------------------------------------

    public void handleStream(ConversationStore store, String userMessage,
                             String correlationId, BiConsumer<String, Object> emit) {
        store.appendUserMessage(userMessage);
        String userId = store.userIdOrNull() == null ? null : store.userIdOrNull().toString();
        List<ChatMessage> history = withSystemPrompt(store.history());
        List<ToolSchema> tools = toolSchemas();
        StringBuilder finalText = new StringBuilder();

        for (int i = 0; i < MAX_TOOL_LOOPS; i++) {
            // Single blocking call per iteration — eliminates the idle gap that
            // killed SSE connections through Cloudflare Tunnel when using chatStream()
            // followed by a second chat() call (the double-call pattern caused a
            // 10–30 second gap with zero bytes flowing, triggering ERR_HTTP2_PROTOCOL_ERROR).
            ChatResponse resp;
            try {
                resp = chatProvider.chat(history, tools);
            } catch (Exception e) {
                log.error("LLM call failed on tool-loop iteration {}: {}", i, e.getMessage());
                String errText = "Üzgünüm, yanıt oluşturulurken bir hata oluştu. Lütfen tekrar deneyin.";
                emit.accept("delta", Map.of(
                    "text", errText,
                    "conversationId", store.conversationId().toString()));
                finalText.append(errText);
                store.appendAssistantText(errText);
                emit.accept("error", Map.of(
                    "code", "UPSTREAM_LLM_ERROR",
                    "messageTr", "Asistan yanıt veremedi: " + e.getMessage()));
                break;
            }

            if (resp.toolCalls() == null || resp.toolCalls().isEmpty()) {
                // Text response — emit as a single delta for reliable proxy delivery
                String text = resp.text() == null ? "" : resp.text();
                // If LLM returned empty text after tool calls, provide a fallback
                if (text.isEmpty() && i > 0) {
                    text = "İşlem tamamlandı. Başka bir konuda yardımcı olabilir miyim?";
                }
                if (!text.isEmpty()) {
                    emit.accept("delta", Map.of(
                        "text", text,
                        "conversationId", store.conversationId().toString()));
                }
                finalText.append(text);
                store.appendAssistantText(text);
                break;
            }

            // Tool-call branch — emit tool_call + dispatch + emit tool_result
            String toolCallJson = serialize(resp.toolCalls());
            store.appendAssistantToolCalls(toolCallJson);
            history.add(new ChatMessage(MessageRole.ASSISTANT, null, resp.toolCalls(), null));

            List<ToolCallResult> results = new ArrayList<>();
            for (ToolCallRequest call : resp.toolCalls()) {
                emit.accept("tool_call", Map.of(
                    "name", call.name(),
                    "callId", call.callId(),
                    "argsJson", call.argsJson()));
                ToolResult r = toolDispatcher.dispatch(
                    call.name(), call.argsJson(), userId, correlationId, store.seenIds());
                boolean ok = r instanceof ToolResult.Ok;
                String summary = (r instanceof ToolResult.Err err)
                    ? err.code() + ": " + err.message()
                    : ((ToolResult.Ok) r).data().toString();
                String resultType = resolveResultType(call.name(), ok);
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("callId", call.callId());
                payload.put("toolName", call.name());
                payload.put("ok", ok);
                payload.put("summary", summary.length() > 200 ? summary.substring(0, 200) + "…" : summary);
                payload.put("resultType", resultType);
                if (r instanceof ToolResult.Ok okResult) {
                    payload.put("data", buildResultData(resultType, okResult.data()));
                }
                emit.accept("tool_result", payload);
                results.add(toToolCallResult(call.callId(), call.name(), r));
            }
            String resultJson = serialize(results);
            store.appendToolResults(resultJson);
            history.add(new ChatMessage(MessageRole.TOOL, null, null, results));
        }

        emit.accept("done", Map.of(
            "conversationId", store.conversationId().toString(),
            "finalText", finalText.toString()));
    }

    // ---- Helpers --------------------------------------------------------

    private List<ChatMessage> withSystemPrompt(List<ChatMessage> existing) {
        List<ChatMessage> out = new ArrayList<>(existing.size() + 1);
        // System prompt prepended as a first user message — Gemini SDK has a separate
        // system_instruction field; for the EchoChatProvider path we just feed it as USER.
        // GeminiChatAdapter could lift this into GenerateContentConfig.systemInstruction
        // (TODO marker — Plan 04 keeps it as a USER prelude for portability across providers).
        out.add(new ChatMessage(MessageRole.USER, "Sistem yönergesi:\n" + systemPrompt.prompt(), null, null));
        out.addAll(existing);
        return out;
    }

    private List<ToolSchema> toolSchemas() {
        List<ToolSchema> out = new ArrayList<>();
        for (AgentTool t : toolRegistry.all()) {
            out.add(new ToolSchema(t.name(), t.descriptionTr(), t.parametersJsonSchema()));
        }
        return out;
    }

    private String serialize(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception e) { return "[]"; }
    }

    private String resolveResultType(String toolName, boolean ok) {
        if (!ok) return "generic";
        return switch (toolName) {
            case "search_products" -> "products";
            case "get_product" -> "product";
            case "view_cart", "add_to_cart", "update_cart_item", "remove_from_cart" -> "cart";
            case "create_order", "get_order_status" -> "order";
            case "get_payment_link" -> "payment";
            default -> "generic";
        };
    }

    private Object buildResultData(String resultType, com.fasterxml.jackson.databind.JsonNode data) {
        Object plain = json.convertValue(data, Object.class);
        return switch (resultType) {
            case "products" -> Map.of("products", plain);
            case "product" -> Map.of("product", plain);
            case "cart" -> Map.of("cart", plain);
            case "order" -> Map.of("order", plain);
            case "payment" -> {
                String url = "";
                if (data.isTextual()) {
                    url = data.asText();
                } else if (data.has("paymentPageUrl") && !data.get("paymentPageUrl").isNull()) {
                    url = data.get("paymentPageUrl").asText();
                }
                yield Map.of("paymentPageUrl", url);
            }
            default -> Map.of();
        };
    }

    private ToolCallResult toToolCallResult(String callId, String functionName, ToolResult r) {
        try {
            if (r instanceof ToolResult.Ok ok) {
                return new ToolCallResult(callId, functionName, json.writeValueAsString(Map.of("data", ok.data())), false);
            }
            ToolResult.Err err = (ToolResult.Err) r;
            return new ToolCallResult(callId, functionName,
                json.writeValueAsString(Map.of("code", err.code(), "message", err.message())), true);
        } catch (Exception e) {
            return new ToolCallResult(callId, functionName, "{\"error\":\"serialization\"}", true);
        }
    }
}
