package com.n11.ai.application;

import com.n11.ai.port.dto.ChatMessage;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Abstract conversation store seen by ChatService. ConversationStateService
 * picks the right backing path: ConversationRepository (authed, D-01) or
 * GuestSessionStore (guest, D-03).
 */
public interface ConversationStore {

    UUID conversationId();

    UUID userIdOrNull();           // null for guest

    List<ChatMessage> history();

    Set<String> seenIds();         // mutable: ToolDispatcher updates it after Ok results

    int nextSequence();

    void appendUserMessage(String content);

    void appendAssistantText(String text);

    void appendAssistantToolCalls(String toolCallJson);

    void appendToolResults(String toolResultJson);
}
