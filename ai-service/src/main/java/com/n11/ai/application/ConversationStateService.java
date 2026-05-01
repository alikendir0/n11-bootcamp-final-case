package com.n11.ai.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.n11.ai.domain.chat.Conversation;
import com.n11.ai.domain.chat.Message;
import com.n11.ai.domain.chat.MessageRoleEntity;
import com.n11.ai.infrastructure.persistence.ConversationRepository;
import com.n11.ai.infrastructure.persistence.MessageRepository;
import com.n11.ai.port.dto.ChatMessage;
import com.n11.ai.port.dto.MessageRole;
import com.n11.ai.port.dto.ToolCallRequest;
import com.n11.ai.port.dto.ToolCallResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class ConversationStateService {

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final GuestSessionStore guests;
    private final ObjectMapper json = new ObjectMapper();

    public ConversationStateService(ConversationRepository conversations,
                                    MessageRepository messages,
                                    GuestSessionStore guests) {
        this.conversations = conversations;
        this.messages = messages;
        this.guests = guests;
    }

    public ConversationStore open(UUID conversationId, UUID userId) {
        return userId == null
            ? new GuestStore(guests.getOrCreate(conversationId), json)
            : new AuthedStore(loadOrCreate(conversationId, userId), conversations, messages, json);
    }

    /**
     * Load existing conversation or create a new one using native INSERT ... ON CONFLICT DO NOTHING.
     * This avoids the Spring Data JPA merge() behaviour when a client-assigned UUID is set
     * before the first save, which would cause StaleObjectStateException.
     */
    Conversation loadOrCreate(UUID conversationId, UUID userId) {
        conversations.insertIfAbsent(conversationId, userId, Instant.now());
        return conversations.findById(conversationId)
            .orElseThrow(() -> new IllegalStateException("Conversation insert failed for id: " + conversationId));
    }

    // ---- AuthedStore (D-01) ---------------------------------------------

    static class AuthedStore implements ConversationStore {
        private final Conversation conv;
        private final ConversationRepository convRepo;
        private final MessageRepository msgRepo;
        private final ObjectMapper json;
        private final List<Message> persistedHistory;
        private final Set<String> seenIds;
        private int seq;

        AuthedStore(Conversation c, ConversationRepository convRepo,
                    MessageRepository msgRepo, ObjectMapper json) {
            this.conv = c;
            this.convRepo = convRepo;
            this.msgRepo = msgRepo;
            this.json = json;
            this.persistedHistory = msgRepo.findByConversationIdOrderBySequenceNoAsc(c.getId());
            this.seq = persistedHistory.isEmpty() ? 0
                    : persistedHistory.get(persistedHistory.size() - 1).getSequenceNo() + 1;
            this.seenIds = parseSeenIds(c.getSeenIdsJson(), json);
        }

        static Set<String> parseSeenIds(String raw, ObjectMapper json) {
            if (raw == null || raw.isBlank()) return newKeySet();
            try {
                Set<String> s = newKeySet();
                s.addAll(json.readValue(raw, new TypeReference<List<String>>() {}));
                return s;
            } catch (Exception e) { return newKeySet(); }
        }

        static Set<String> newKeySet() { return java.util.concurrent.ConcurrentHashMap.newKeySet(); }

        @Override public UUID conversationId() { return conv.getId(); }
        @Override public UUID userIdOrNull() { return conv.getUserId(); }

        @Override
        public List<ChatMessage> history() {
            List<ChatMessage> out = new ArrayList<>(persistedHistory.size());
            for (Message m : persistedHistory) {
                out.add(toChatMessage(m, json));
            }
            return out;
        }

        @Override public Set<String> seenIds() { return seenIds; }
        @Override public int nextSequence() { return seq++; }

        @Override
        public void appendUserMessage(String content) {
            msgRepo.save(new Message(conv.getId(), MessageRoleEntity.USER, content, null, null, nextSequence()));
            touch();
        }

        @Override
        public void appendAssistantText(String text) {
            msgRepo.save(new Message(conv.getId(), MessageRoleEntity.ASSISTANT, text, null, null, nextSequence()));
            touch();
        }

        @Override
        public void appendAssistantToolCalls(String toolCallJson) {
            msgRepo.save(new Message(conv.getId(), MessageRoleEntity.ASSISTANT, null, toolCallJson, null, nextSequence()));
            touch();
        }

        @Override
        public void appendToolResults(String toolResultJson) {
            msgRepo.save(new Message(conv.getId(), MessageRoleEntity.TOOL, null, null, toolResultJson, nextSequence()));
            String seenIdsJsonStr;
            try {
                seenIdsJsonStr = json.writeValueAsString(new ArrayList<>(seenIds));
            } catch (Exception ignored) {
                seenIdsJsonStr = "[]";
            }
            touch(seenIdsJsonStr);
        }

        private void touch() { touch(null); }

        private void touch(String seenIdsJsonStr) {
            if (seenIdsJsonStr == null) {
                try {
                    seenIdsJsonStr = json.writeValueAsString(new ArrayList<>(seenIds));
                } catch (Exception ignored) {
                    seenIdsJsonStr = "[]";
                }
            }
            convRepo.touchAndUpdateSeenIds(conv.getId(), Instant.now(), seenIdsJsonStr);
        }
    }

    // ---- GuestStore (D-03) ---------------------------------------------

    static class GuestStore implements ConversationStore {
        private final GuestSessionStore.GuestConversation g;
        private final ObjectMapper json;
        private int seq = 0;

        GuestStore(GuestSessionStore.GuestConversation g, ObjectMapper json) {
            this.g = g;
            this.json = json;
            this.seq = g.turns.size();
        }

        @Override public UUID conversationId() { return g.id; }
        @Override public UUID userIdOrNull() { return null; }

        @Override
        public List<ChatMessage> history() {
            List<ChatMessage> out = new ArrayList<>();
            for (GuestSessionStore.TurnRecord t : g.turns) {
                MessageRoleEntity role = MessageRoleEntity.valueOf(t.role());
                out.add(toChatMessageFromGuest(role, t, json));
            }
            return out;
        }

        @Override public Set<String> seenIds() { return g.seenIds; }
        @Override public int nextSequence() { return seq++; }

        @Override public void appendUserMessage(String content) {
            g.turns.add(new GuestSessionStore.TurnRecord("USER", content, null, null));
            g.touch();
        }
        @Override public void appendAssistantText(String text) {
            g.turns.add(new GuestSessionStore.TurnRecord("ASSISTANT", text, null, null));
            g.touch();
        }
        @Override public void appendAssistantToolCalls(String toolCallJson) {
            g.turns.add(new GuestSessionStore.TurnRecord("ASSISTANT", null, toolCallJson, null));
            g.touch();
        }
        @Override public void appendToolResults(String toolResultJson) {
            g.turns.add(new GuestSessionStore.TurnRecord("TOOL", null, null, toolResultJson));
            g.touch();
        }
    }

    private static ChatMessage toChatMessage(Message m, ObjectMapper json) {
        try {
            return new ChatMessage(
                MessageRole.valueOf(m.getRole().name()),
                m.getContent(),
                m.getToolCallJson() == null ? null : json.readValue(m.getToolCallJson(), new TypeReference<List<ToolCallRequest>>(){}),
                m.getToolResultJson() == null ? null : json.readValue(m.getToolResultJson(), new TypeReference<List<ToolCallResult>>(){})
            );
        } catch (Exception e) {
            return new ChatMessage(MessageRole.valueOf(m.getRole().name()), m.getContent(), null, null);
        }
    }

    private static ChatMessage toChatMessageFromGuest(MessageRoleEntity role, GuestSessionStore.TurnRecord t, ObjectMapper json) {
        try {
            return new ChatMessage(
                MessageRole.valueOf(role.name()),
                t.content(),
                t.toolCallJson() == null ? null : json.readValue(t.toolCallJson(), new TypeReference<List<ToolCallRequest>>(){}),
                t.toolResultJson() == null ? null : json.readValue(t.toolResultJson(), new TypeReference<List<ToolCallResult>>(){})
            );
        } catch (Exception e) {
            return new ChatMessage(MessageRole.valueOf(role.name()), t.content(), null, null);
        }
    }
}
