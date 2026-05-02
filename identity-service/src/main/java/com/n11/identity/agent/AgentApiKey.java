package com.n11.identity.agent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Phase 9 (D-05). Database row for one agent's API key.
 *
 * <p>{@code api_key_hash} is the SHA-256 base16 (lowercase) of the plaintext key
 * the agent presents at /agents/exchange. The plaintext NEVER hits the DB —
 * lookup is by exact hash equality, NOT password verification.
 *
 * <p>{@code @Entity(name = "AgentApiKey")} is explicit so infra-tests' multi-service
 * classpath does not collide with any future entity called "AgentApiKey" in another
 * module (Plan 05-04 lesson).
 */
@Entity(name = "AgentApiKey")
@Table(name = "agent_api_keys")
public class AgentApiKey {

    @Id
    @Column(name = "api_key_hash", nullable = false, updatable = false, length = 64, columnDefinition = "CHAR(64)")
    private String apiKeyHash;

    @Column(name = "agent_label", nullable = false, length = 100)
    private String agentLabel;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected AgentApiKey() { /* JPA */ }

    public AgentApiKey(String apiKeyHash, String agentLabel, UUID userId, Instant createdAt) {
        this.apiKeyHash = apiKeyHash;
        this.agentLabel = agentLabel;
        this.userId = userId;
        this.createdAt = createdAt;
    }

    public String getApiKeyHash()  { return apiKeyHash; }
    public String getAgentLabel()  { return agentLabel; }
    public UUID getUserId()        { return userId; }
    public Instant getCreatedAt()  { return createdAt; }
    public Instant getLastUsedAt() { return lastUsedAt; }
    public Instant getRevokedAt()  { return revokedAt; }

    public void revoke(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }
}
