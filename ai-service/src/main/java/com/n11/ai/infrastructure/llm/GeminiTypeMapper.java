package com.n11.ai.infrastructure.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.google.genai.types.Schema;
import com.google.genai.types.Tool;
import com.google.genai.types.Type;
import com.n11.ai.port.dto.ChatMessage;
import com.n11.ai.port.dto.ChatResponse;
import com.n11.ai.port.dto.ToolCallRequest;
import com.n11.ai.port.dto.ToolCallResult;
import com.n11.ai.port.dto.ToolSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bidirectional mapper: ai-port neutral DTOs &lt;-&gt; google-genai SDK types.
 *
 * <p>Pitfall #7: this is the ONLY class in the entire codebase (besides
 * GeminiChatAdapter and GeminiEmbeddingAdapter) where com.google.genai.*
 * imports are permitted. Verified by:
 * <pre>
 *   grep -rln 'import com.google.genai' ai-service/src/main/java
 * </pre>
 * MUST list exactly three files (this one + the two adapters).
 */
final class GeminiTypeMapper {

    private static final Logger log = LoggerFactory.getLogger(GeminiTypeMapper.class);

    private final ObjectMapper json = new ObjectMapper();

    // ---- Outbound: neutral -> Gemini -----------------------------------

    List<Content> toGeminiContents(List<ChatMessage> messages) {
        List<Content> out = new ArrayList<>();
        for (ChatMessage m : messages) {
            switch (m.role()) {
                case USER -> out.add(Content.fromParts(Part.fromText(safe(m.content()))));
                case ASSISTANT -> {
                    List<Part> parts = new ArrayList<>();
                    if (m.content() != null && !m.content().isBlank()) {
                        parts.add(Part.fromText(m.content()));
                    }
                    if (m.toolCalls() != null) {
                        for (ToolCallRequest tc : m.toolCalls()) {
                            // Use FunctionCall.builder() to include call ID for history replay.
                            // Gemini 3 Flash Preview requires function call IDs to match
                            // thought_signature expectations when replaying multi-turn tool history.
                            FunctionCall fc = FunctionCall.builder()
                                .id(tc.callId())
                                .name(tc.name())
                                .args(parseJsonAsMap(tc.argsJson()))
                                .build();
                            // Part.fromFunctionCall(FunctionCall) includes the ID field
                            // which prevents thought_signature 400 errors on history replay.
                            parts.add(Part.builder().functionCall(fc).build());
                        }
                    }
                    out.add(Content.builder().role("model").parts(parts).build());
                }
                case TOOL -> {
                    List<Part> parts = new ArrayList<>();
                    if (m.toolResults() != null) {
                        for (ToolCallResult tr : m.toolResults()) {
                            // FunctionResponse requires the function name (not call ID).
                            // Use functionName for matching; include id for Gemini call correlation.
                            String fnName = tr.functionName() != null ? tr.functionName() : tr.callId();
                            FunctionResponse fr = FunctionResponse.builder()
                                .id(tr.callId())
                                .name(fnName)
                                .response(parseJsonAsMap(tr.resultJson()))
                                .build();
                            parts.add(Part.builder().functionResponse(fr).build());
                        }
                    }
                    out.add(Content.builder().role("user").parts(parts).build());
                }
            }
        }
        return out;
    }

    List<Tool> toGeminiTools(List<ToolSchema> schemas) {
        List<FunctionDeclaration> decls = new ArrayList<>();
        for (ToolSchema s : schemas) {
            decls.add(FunctionDeclaration.builder()
                .name(s.name())
                .description(s.descriptionTr())
                .parameters(parseJsonSchema(s.parametersJsonSchema()))
                .build());
        }
        return List.of(Tool.builder().functionDeclarations(decls).build());
    }

    // ---- Inbound: Gemini -> neutral ------------------------------------

    ChatResponse fromGeminiResponse(GenerateContentResponse response) {
        if (response == null) {
            return new ChatResponse("", List.of(), "STOP");
        }

        // Use the SDK's convenience methods (verified against 1.52.0 API).
        // functionCalls() returns null (not an empty list) when the model
        // replies with plain text and no tool call — guard against the NPE.
        List<FunctionCall> sdkCalls = response.functionCalls();
        if (sdkCalls != null && !sdkCalls.isEmpty()) {
            List<ToolCallRequest> calls = new ArrayList<>();
            for (FunctionCall fc : sdkCalls) {
                String callId = fc.id().orElseGet(() -> fc.name().orElse("call"));
                String fname = fc.name().orElse("unknown");
                String argsJson = "{}";
                if (fc.args().isPresent()) {
                    try { argsJson = json.writeValueAsString(fc.args().get()); }
                    catch (Exception e) {
                        log.warn("Could not serialize Gemini function-call args for tool '{}'; "
                            + "dispatching with empty args", fname, e);
                    }
                }
                calls.add(new ToolCallRequest(callId, fname, argsJson));
            }
            return new ChatResponse(null, calls, "TOOL_CALLS");
        }

        String text = response.text();
        String finishReason = "STOP";
        try {
            var fr = response.finishReason();
            if (fr != null) finishReason = fr.toString();
        } catch (Exception e) {
            log.debug("Could not read Gemini finishReason, defaulting to STOP", e);
        }

        return new ChatResponse(text != null ? text : "", List.of(), finishReason);
    }

    // ---- Helpers --------------------------------------------------------

    private static String safe(String s) { return s == null ? "" : s; }

    @SuppressWarnings("unchecked")
    Map<String, Object> parseJsonAsMap(String jsonStr) {
        try {
            if (jsonStr == null || jsonStr.isBlank()) return Map.of();
            return json.readValue(jsonStr, LinkedHashMap.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private Schema parseJsonSchema(String schemaJson) {
        Map<String, Object> raw = parseJsonAsMap(schemaJson);
        return Schema.builder()
            .type(new Type(Type.Known.OBJECT))
            .properties(asPropertiesMap(raw.get("properties")))
            .required(asStringList(raw.get("required")))
            .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Schema> asPropertiesMap(Object node) {
        if (!(node instanceof Map<?,?> m)) return Map.of();
        LinkedHashMap<String, Schema> out = new LinkedHashMap<>();
        for (Map.Entry<?,?> e : m.entrySet()) {
            if (!(e.getValue() instanceof Map<?,?> propMapWild)) continue;
            @SuppressWarnings("unchecked")
            Map<Object, Object> propMap = (Map<Object, Object>) propMapWild;
            String typeStr = String.valueOf(propMap.getOrDefault("type", "string"));
            String desc = String.valueOf(propMap.getOrDefault("description", ""));
            Type propType = mapJsonSchemaType(typeStr);
            out.put(String.valueOf(e.getKey()),
                Schema.builder().type(propType).description(desc).build());
        }
        return out;
    }

    private Type mapJsonSchemaType(String jsonSchemaType) {
        return switch (jsonSchemaType.toLowerCase()) {
            case "integer", "int" -> new Type(Type.Known.INTEGER);
            case "number", "float", "double" -> new Type(Type.Known.NUMBER);
            case "boolean", "bool" -> new Type(Type.Known.BOOLEAN);
            case "array" -> new Type(Type.Known.ARRAY);
            case "object" -> new Type(Type.Known.OBJECT);
            default -> new Type(Type.Known.STRING);
        };
    }

    @SuppressWarnings("unchecked")
    private List<String> asStringList(Object node) {
        if (!(node instanceof List<?> l)) return List.of();
        List<String> out = new ArrayList<>();
        for (Object o : l) out.add(String.valueOf(o));
        return out;
    }
}
