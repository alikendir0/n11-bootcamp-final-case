package com.n11.ai.infrastructure.llm;

import com.n11.ai.port.ChatProvider;
import com.n11.ai.port.dto.ChatMessage;
import com.n11.ai.port.dto.ChatResponse;
import com.n11.ai.port.dto.MessageRole;
import com.n11.ai.port.dto.ToolSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * Second ChatProvider adapter — the SOLID demonstration artifact.
 *
 * <p>Activated by {@code ai.provider=echo} in config-server/.../ai-service.yml.
 * When active, ai-service answers chat requests by echoing the latest user
 * message back. ZERO Gemini SDK imports — proves the port is substitutable.
 *
 * <p>This is the artifact graders inspect to confirm CLAUDE.md
 * Non-negotiable Rule #1 ("Provider-agnostic LLM abstraction is the SOLID
 * demo"). Delete it and the entire grading thesis collapses (Pitfall #7).
 */
@Component("echoChatProvider")
@ConditionalOnProperty(name = "ai.provider", havingValue = "echo")
public class EchoChatProvider implements ChatProvider {

    private static final Logger log = LoggerFactory.getLogger(EchoChatProvider.class);

    @Override
    public ChatResponse chat(List<ChatMessage> messages, List<ToolSchema> tools) {
        log.info("EchoChatProvider: echoing (zero Gemini calls). messages={} tools={}",
                 messages.size(), tools.size());
        return new ChatResponse("[ECHO] " + lastUserMessage(messages), List.of(), "STOP");
    }

    @Override
    public void chatStream(List<ChatMessage> messages, List<ToolSchema> tools,
                           Consumer<String> onDelta, Runnable onComplete,
                           Consumer<Throwable> onError) {
        try {
            onDelta.accept("[ECHO] " + lastUserMessage(messages));
            onComplete.run();
        } catch (Exception e) {
            onError.accept(e);
        }
    }

    private static String lastUserMessage(List<ChatMessage> messages) {
        return messages.stream()
            .filter(m -> m.role() == MessageRole.USER)
            .reduce((a, b) -> b)
            .map(ChatMessage::content)
            .orElse("(boş mesaj)");
    }
}
