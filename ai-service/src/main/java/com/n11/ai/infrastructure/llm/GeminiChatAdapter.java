package com.n11.ai.infrastructure.llm;

import com.google.genai.Client;
import com.google.genai.ResponseStream;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GetModelConfig;
import com.google.genai.types.ThinkingConfig;
import com.google.genai.types.Tool;
import com.n11.ai.port.ChatProvider;
import com.n11.ai.port.dto.ChatMessage;
import com.n11.ai.port.dto.ChatResponse;
import com.n11.ai.port.dto.ToolSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * Production ChatProvider — wraps google-genai 1.52.0.
 *
 * <p>Activated by {@code ai.provider=gemini} (default via {@code matchIfMissing=true}).
 * Manual function-calling loop (D-06): chat() returns ChatResponse with
 * either text (terminal) or toolCalls (ChatService dispatches and calls
 * back). All com.google.genai imports are confined to this class +
 * GeminiTypeMapper + GeminiEmbeddingAdapter (Pitfall #7).
 */
@Component("geminiChatAdapter")
@ConditionalOnProperty(name = "ai.provider", havingValue = "gemini", matchIfMissing = true)
public class GeminiChatAdapter implements ChatProvider {

    private static final Logger log = LoggerFactory.getLogger(GeminiChatAdapter.class);

    private final Client client;
    private final String fallbackModel;
    private volatile String chatModel;
    private final GeminiTypeMapper mapper = new GeminiTypeMapper();

    public GeminiChatAdapter(
            @Value("${ai.gemini.model.chat:gemini-3-flash-preview}") String chatModel,
            @Value("${ai.gemini.model.chat-fallback:gemini-2.5-flash}") String fallbackModel,
            @Value("${ai.gemini.api-key:}") String apiKey) {
        this.chatModel = chatModel;
        this.fallbackModel = fallbackModel;
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("ai-service: GEMINI_API_KEY is empty — Client built but real calls will fail.");
            this.client = Client.builder().apiKey("missing").build();
        } else {
            this.client = Client.builder().apiKey(apiKey).build();
        }
    }

    /**
     * Pitfall #1 mitigation — probe the configured chat model on startup
     * and fall back if the preview identifier returns 404.
     *
     * <p>SDK 1.52.0 requires GetModelConfig as second arg to models.get().
     */
    @EventListener(ApplicationReadyEvent.class)
    public void verifyModel() {
        String primary = chatModel;
        try {
            client.models.get(primary, GetModelConfig.builder().build());
            log.info("ai-service: resolved chat model = {} (provider = GeminiChatAdapter)", primary);
        } catch (Exception e) {
            log.warn("ai-service: primary chat model {} not available ({}); falling back to {}",
                     primary, e.getMessage(), fallbackModel);
            this.chatModel = fallbackModel;
            try {
                client.models.get(fallbackModel, GetModelConfig.builder().build());
                log.info("ai-service: resolved chat model = {} (fallback)", fallbackModel);
            } catch (Exception ee) {
                log.error("ai-service: fallback model {} also unavailable: {}",
                          fallbackModel, ee.getMessage());
            }
        }
    }

    @Override
    public ChatResponse chat(List<ChatMessage> messages, List<ToolSchema> tools) {
        List<Content> history = mapper.toGeminiContents(messages);
        GenerateContentConfig config = buildConfig(tools);
        try {
            GenerateContentResponse response = client.models.generateContent(chatModel, history, config);
            return mapper.fromGeminiResponse(response);
        } catch (Exception e) {
            log.error("ai-service: Gemini chat call failed: {}", e.getMessage());
            throw new GeminiUpstreamException("Gemini upstream error", e);
        }
    }

    @Override
    public void chatStream(List<ChatMessage> messages, List<ToolSchema> tools,
                           Consumer<String> onDelta, Runnable onComplete,
                           Consumer<Throwable> onError) {
        List<Content> history = mapper.toGeminiContents(messages);
        GenerateContentConfig config = buildConfig(tools);
        try (ResponseStream<GenerateContentResponse> stream =
                 client.models.generateContentStream(chatModel, history, config)) {
            for (GenerateContentResponse chunk : stream) {
                String text = chunk.text();
                if (text != null && !text.isEmpty()) {
                    onDelta.accept(text);
                }
            }
            onComplete.run();
        } catch (Exception e) {
            onError.accept(e);
        }
    }

    private GenerateContentConfig buildConfig(List<ToolSchema> tools) {
        GenerateContentConfig.Builder builder = GenerateContentConfig.builder();
        if (!tools.isEmpty()) {
            List<Tool> geminiTools = mapper.toGeminiTools(tools);
            builder.tools(geminiTools);
        }
        // Disable Gemini thinking mode to avoid thought_signature errors when
        // replaying tool-call history. Gemini 3 Flash Preview requires thought_signature
        // in FunctionCall parts when thinking is on; our DTO doesn't carry it.
        // Disabling thinking keeps function calling reliable through Cloudflare Tunnel.
        builder.thinkingConfig(ThinkingConfig.builder()
            .includeThoughts(false)
            .build());
        return builder.build();
    }
}
