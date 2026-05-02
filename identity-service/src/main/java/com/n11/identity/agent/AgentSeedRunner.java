package com.n11.identity.agent;

import com.n11.identity.user.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Phase 9 (CLAUDE.md Rule #5 / QUAL-09): generates the demo MCP_API_KEY at first
 * boot and logs the plaintext ONCE, then persists only the hash to agent_api_keys.
 *
 * <p>Behavior:
 * <ul>
 *   <li>If table is non-empty (a key already exists OR the R__ migration filled it),
 *       skip silently.</li>
 *   <li>If empty, generate a 32-byte URL-safe random plaintext, hash via SHA-256
 *       base16, persist (hash, "demo-agent", admin user_id), log plaintext at
 *       WARN level prefixed with a delimiter banner so it stands out in compose
 *       logs.</li>
 * </ul>
 *
 * <p>The plaintext is NEVER stored — operator copies from logs into .env.
 *
 * <p>Disable via {@code mcp.seed.enabled=false} for production deployments.
 */
@Component
public class AgentSeedRunner {

    private static final Logger log = LoggerFactory.getLogger(AgentSeedRunner.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AgentApiKeyRepository agentRepo;
    private final UserRepository userRepo;
    private final boolean enabled;
    private final String adminEmail;

    public AgentSeedRunner(AgentApiKeyRepository agentRepo,
                           UserRepository userRepo,
                           @Value("${mcp.seed.enabled:true}") boolean enabled,
                           @Value("${admin.seed.email:admin@n11demo.com}") String adminEmail) {
        this.agentRepo = agentRepo;
        this.userRepo = userRepo;
        this.enabled = enabled;
        this.adminEmail = adminEmail;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedIfEmpty() {
        if (!enabled) {
            log.info("AgentSeedRunner: mcp.seed.enabled=false — skipping demo agent seed");
            return;
        }
        long existing = agentRepo.count();
        if (existing > 0) {
            log.info("AgentSeedRunner: agent_api_keys already has {} row(s) — skipping seed", existing);
            return;
        }
        UUID adminId = userRepo.findByEmail(adminEmail)
                .orElseThrow(() -> new IllegalStateException(
                        "AgentSeedRunner: admin user " + adminEmail + " not found — V3 seed must run first"))
                .getId();

        String plaintext = generatePlaintextKey();
        String hash = AgentExchangeService.sha256Hex(plaintext);
        agentRepo.save(new AgentApiKey(hash, "demo-agent", adminId, Instant.now()));

        log.warn("================================================================================");
        log.warn("Phase 9 demo MCP_API_KEY (LOG ONCE — copy into .env, NEVER commit):");
        log.warn("MCP_API_KEY={}", plaintext);
        log.warn("Bound to user_id={}, agent_label=demo-agent", adminId);
        log.warn("================================================================================");
    }

    private static String generatePlaintextKey() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
