package com.n11.ai.domain.chat;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity(name = "AiConversation")
@Table(name = "ai_conversations")
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;          // nullable: guest sessions never reach the DB (D-03)

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt = Instant.now();

    @Column(name = "seen_ids_json")
    private String seenIdsJson;   // JSON array (Pitfall #10 D-08 seenIds)

    @Column(name = "metadata_json")
    private String metadataJson;

    protected Conversation() {}

    public Conversation(UUID userId) { this.userId = userId; }

    // getters / setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant t) { this.updatedAt = t; }
    public Instant getLastActivityAt() { return lastActivityAt; }
    public void setLastActivityAt(Instant t) { this.lastActivityAt = t; }
    public String getSeenIdsJson() { return seenIdsJson; }
    public void setSeenIdsJson(String s) { this.seenIdsJson = s; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String s) { this.metadataJson = s; }
}
