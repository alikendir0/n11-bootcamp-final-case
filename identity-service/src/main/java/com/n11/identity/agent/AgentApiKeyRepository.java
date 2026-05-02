package com.n11.identity.agent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

/**
 * Phase 9 (D-05). String PK = the SHA-256 base16 hash.
 *
 * <p>Lookup is by exact-hash equality only. There is no "search by user" path
 * exposed to the agent — that would be a privilege escalation surface. The
 * admin maintenance path (rotate / revoke) is out of scope for v1.
 */
public interface AgentApiKeyRepository extends JpaRepository<AgentApiKey, String> {

    /** Active key lookup. Used by AgentExchangeService.exchange(). */
    Optional<AgentApiKey> findByApiKeyHashAndRevokedAtIsNull(String apiKeyHash);

    /** Audit trail update on every successful exchange (D-05). */
    @Modifying
    @Query("UPDATE AgentApiKey k SET k.lastUsedAt = :now WHERE k.apiKeyHash = :hash")
    int updateLastUsed(@Param("hash") String apiKeyHash, @Param("now") Instant now);
}
