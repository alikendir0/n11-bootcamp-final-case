package com.n11.mcp.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Phase 9 (D-07). Lazily exchanges the configured MCP_API_KEY for an internal
 * JWT on first use; refreshes on a minute-tick schedule when the cached JWT
 * has less than the {@link #REFRESH_BUFFER} remaining.
 *
 * <p>Pitfall #4 in 09-RESEARCH.md (clock skew at gateway): the buffer is
 * 10 minutes (NOT 5 as in D-07) because the gateway's JwtTimestampValidator
 * has a 30-second skew tolerance — a 5-minute boundary leaves less safety
 * margin if cache refresh fires exactly at the boundary. 10 minutes
 * eliminates the edge case.
 *
 * <p>JWT decoding: Plan 09-04 only needs JWT.sub, NOT signature validation.
 * The gateway is the authoritative JWT validator (Phase 3 / D-15). Decoding
 * the payload via Base64.urlDecoder is sufficient and avoids importing
 * Nimbus / spring-security-oauth2-jose into mcp-server.
 *
 * <p>Thread-safety: {@code synchronized} on read paths is sufficient for the
 * single-key-per-process posture (D-08). Concurrent first-call returns the
 * same JWT (idempotent exchange).
 */
@Component
public class AgentJwtCache {

    private static final Logger log = LoggerFactory.getLogger(AgentJwtCache.class);
    private static final Duration REFRESH_BUFFER = Duration.ofMinutes(10);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final AgentJwtClient client;
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "agent-jwt-refresher");
                t.setDaemon(true);
                return t;
            });

    private volatile String jwt;
    private volatile String cachedSub;
    private volatile Instant expiresAt = Instant.EPOCH;

    public AgentJwtCache(AgentJwtClient client) {
        this.client = client;
    }

    @PostConstruct
    void schedule() {
        scheduler.scheduleAtFixedRate(this::refreshIfNeeded, 60, 60, TimeUnit.SECONDS);
        log.info("AgentJwtCache scheduled minute-tick refresh (buffer={} min)", REFRESH_BUFFER.toMinutes());
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
    }

    /** Returns the cached JWT.sub claim. Triggers lazy exchange on first call. */
    public synchronized String userId() {
        ensureValid();
        return cachedSub;
    }

    /** Returns the cached JWT compact serialization. Triggers lazy exchange on first call. */
    public synchronized String bearerToken() {
        ensureValid();
        return jwt;
    }

    private synchronized void ensureValid() {
        if (jwt == null || Instant.now().isAfter(expiresAt.minus(REFRESH_BUFFER))) {
            exchange();
        }
    }

    private void refreshIfNeeded() {
        try {
            ensureValid();
        } catch (Exception e) {
            // Background refresh failure is recoverable — next foreground call retries.
            log.warn("AgentJwtCache scheduled refresh failed: {}", e.getMessage());
        }
    }

    private void exchange() {
        AgentTokenResponseDto resp = client.exchange();
        String token = resp.accessToken();
        String sub = extractSubject(token);

        this.jwt = token;
        this.cachedSub = sub;
        this.expiresAt = Instant.now().plusSeconds(resp.expiresIn());

        log.info("AgentJwtCache: exchanged new JWT (sub={}, expiresAt={})", sub, expiresAt);
    }

    /**
     * Extracts the {@code sub} claim from a JWT compact serialization without
     * verifying the signature. The gateway is the authoritative signature
     * verifier; mcp-server only needs the subject for ToolContext building.
     */
    static String extractSubject(String jwt) {
        String[] parts = jwt.split("\\.");
        if (parts.length != 3) {
            throw new IllegalStateException("Malformed JWT (expected 3 segments): " + parts.length);
        }
        try {
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode claims = JSON.readTree(payload);
            JsonNode sub = claims.get("sub");
            if (sub == null || !sub.isTextual()) {
                throw new IllegalStateException("JWT missing 'sub' claim");
            }
            return sub.asText();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decode JWT payload", e);
        }
    }
}
