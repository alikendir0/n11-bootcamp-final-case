package com.n11.identity.agent;

import com.n11.identity.agent.dto.AgentExchangeRequest;
import com.n11.identity.agent.dto.AgentTokenResponse;
import com.n11.identity.auth.JwtIssuerService;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

/**
 * Phase 9 (D-05 / D-06): SHA-256 hash → exact lookup → mint JWT bound to the
 * agent's user_id with ROLE_USER claim. Same RS256 path as /auth/login.
 *
 * <p>JWT claims (D-06):
 * <ul>
 *   <li>sub  = bound user_id (real user — agent's web actions are indistinguishable)</li>
 *   <li>email = agent_label  (audit-friendly; the JWT's "who am I" string)</li>
 *   <li>fullName = "Agent"   (constant — agents have no human name)</li>
 *   <li>roles = [ROLE_USER]   (no separate ROLE_AGENT in v1)</li>
 * </ul>
 *
 * <p>@Transactional (read-only would not work — the updateLastUsed call mutates).
 */
@Service
public class AgentExchangeService {

    private static final HexFormat HEX = HexFormat.of();

    private final AgentApiKeyRepository repository;
    private final JwtIssuerService jwtIssuerService;

    public AgentExchangeService(AgentApiKeyRepository repository,
                                JwtIssuerService jwtIssuerService) {
        this.repository = repository;
        this.jwtIssuerService = jwtIssuerService;
    }

    @Transactional
    public AgentTokenResponse exchange(AgentExchangeRequest req) {
        String hash = sha256Hex(req.apiKey());
        AgentApiKey key = repository.findByApiKeyHashAndRevokedAtIsNull(hash)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Geçersiz API anahtarı"));
        repository.updateLastUsed(key.getApiKeyHash(), Instant.now());
        String token = jwtIssuerService.issue(
                key.getUserId(),
                key.getAgentLabel(),
                "Agent",
                List.of("ROLE_USER"));
        return new AgentTokenResponse(token, jwtIssuerService.tokenLifetimeSeconds());
    }

    /**
     * SHA-256 of the UTF-8 plaintext, hex-encoded lowercase. JDK 17+ HexFormat.
     * Avoids pulling Apache Commons Codec just for this — JDK MessageDigest is sufficient.
     */
    static String sha256Hex(String plaintext) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(plaintext.getBytes(StandardCharsets.UTF_8));
            return HEX.formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable on JDK runtime", e);
        }
    }
}
