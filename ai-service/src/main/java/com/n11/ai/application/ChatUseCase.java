package com.n11.ai.application;

import com.n11.ai.domain.chat.ChatService;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.function.BiConsumer;

@Service
public class ChatUseCase {

    private final ConversationStateService stateService;
    private final ChatService chatService;

    public ChatUseCase(ConversationStateService stateService, ChatService chatService) {
        this.stateService = stateService;
        this.chatService = chatService;
    }

    public ChatReply chat(UUID conversationId, String message, UUID userId, String correlationId) {
        ConversationStore store = stateService.open(conversationId, userId);
        String text = chatService.chat(store, message, correlationId);
        return new ChatReply(conversationId, text);
    }

    public void handleStream(UUID conversationId, String message, UUID userId,
                             String correlationId, BiConsumer<String, Object> emit) {
        ConversationStore store = stateService.open(conversationId, userId);
        chatService.handleStream(store, message, correlationId, emit);
    }

    public record ChatReply(UUID conversationId, String text) {}
}
