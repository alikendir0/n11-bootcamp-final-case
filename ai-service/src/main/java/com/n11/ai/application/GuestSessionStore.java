package com.n11.ai.application;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * D-03: ephemeral guest conversation state. ConcurrentHashMap with idle TTL
 * eviction every 5 minutes. No DB row. Wiped on restart.
 */
@Component
public class GuestSessionStore {

    public static final class GuestConversation {
        public final UUID id;
        public final List<TurnRecord> turns = new CopyOnWriteArrayList<>();
        public final Set<String> seenIds = ConcurrentHashMap.newKeySet();
        public volatile Instant lastActivity = Instant.now();
        GuestConversation(UUID id) { this.id = id; }
        public void touch() { this.lastActivity = Instant.now(); }
    }

    public record TurnRecord(String role, String content, String toolCallJson, String toolResultJson) {}

    private final ConcurrentMap<UUID, GuestConversation> sessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService evictor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "guest-session-evictor"); t.setDaemon(true); return t;
    });
    private final long ttlMinutes;

    public GuestSessionStore(@Value("${ai.conversation.guest-ttl-minutes:60}") long ttlMinutes) {
        this.ttlMinutes = ttlMinutes;
    }

    @PostConstruct
    public void schedule() {
        evictor.scheduleAtFixedRate(this::evict, 5, 5, TimeUnit.MINUTES);
    }

    @PreDestroy
    public void shutdown() {
        evictor.shutdownNow();
    }

    public GuestConversation getOrCreate(UUID id) {
        GuestConversation c = sessions.computeIfAbsent(id, GuestConversation::new);
        c.touch();
        return c;
    }

    public Optional<GuestConversation> peek(UUID id) {
        return Optional.ofNullable(sessions.get(id));
    }

    private void evict() {
        Instant cutoff = Instant.now().minusSeconds(ttlMinutes * 60);
        sessions.entrySet().removeIf(e -> e.getValue().lastActivity.isBefore(cutoff));
    }

    int sessionCount() { return sessions.size(); }
}
