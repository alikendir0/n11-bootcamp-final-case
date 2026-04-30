package com.n11.ai.port;

import com.n11.ai.port.dto.ChatMessage;
import com.n11.ai.port.dto.ChatResponse;
import com.n11.ai.port.dto.ToolSchema;

import java.util.List;
import java.util.function.Consumer;

/**
 * Provider-agnostic chat port (D-01 / CLAUDE.md Rule #1).
 *
 * Implementations (Phase 8): GeminiChatAdapter (production), EchoChatProvider
 * (the SOLID demonstration second adapter). Switched at runtime via
 * {@code ai.provider=gemini|echo} in ai-service config.
 *
 * NEVER add Gemini SDK types (Content, Part, FunctionCall) to this signature.
 * Mapping lives entirely in GeminiChatAdapter.
 */
public interface ChatProvider {

    /**
     * Send a conversation turn. Returns text response OR function-call requests.
     *
     * @param messages full conversation history
     * @param tools    tool schemas exposed to the LLM (may be empty)
     * @return ChatResponse with text (loop ends) or toolCalls (loop continues)
     */
    ChatResponse chat(List<ChatMessage> messages, List<ToolSchema> tools);

    /**
     * Streaming variant: emits text deltas to onDelta. ToolCall handling
     * is controlled by the orchestrating ChatService (manual loop, D-06).
     */
    void chatStream(List<ChatMessage> messages, List<ToolSchema> tools,
                    Consumer<String> onDelta,
                    Runnable onComplete,
                    Consumer<Throwable> onError);
}
