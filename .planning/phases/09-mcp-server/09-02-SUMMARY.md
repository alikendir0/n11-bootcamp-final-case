---
phase: 09-mcp-server
plan: 02
subsystem: auth
tags: [identity-service, mcp, jwt, flyway, postgres, api-key, tdd]

requires:
  - phase: 03-identity-gateway-auth
    provides: [JwtIssuerService, users table, identity-service security posture]
  - phase: 09-mcp-server-plan-01
    provides: [mcp-server module foundation coordinated on HEAD]
provides:
  - identity-service DB-backed agent API key table
  - POST /agents/exchange API-key-to-JWT bridge
  - first-boot demo MCP_API_KEY generation with plaintext logged once
  - finalized API contract rows for /agents/exchange and /mcp
affects: [09-mcp-server, mcp-server, identity-service, api-contracts, phase-11-demo]

tech-stack:
  added: []
  patterns: [Flyway versioned migration, repeatable Flyway seed, Spring MVC thin controller, TDD RED-GREEN]

key-files:
  created:
    - identity-service/src/main/resources/db/migration/V5__agent_api_keys.sql
    - identity-service/src/main/resources/db/migration/R__seed_agent_api_keys.sql
    - identity-service/src/main/java/com/n11/identity/agent/AgentApiKey.java
    - identity-service/src/main/java/com/n11/identity/agent/AgentApiKeyRepository.java
    - identity-service/src/main/java/com/n11/identity/agent/AgentExchangeService.java
    - identity-service/src/main/java/com/n11/identity/agent/AgentExchangeController.java
    - identity-service/src/main/java/com/n11/identity/agent/AgentSeedRunner.java
    - identity-service/src/main/java/com/n11/identity/agent/dto/AgentExchangeRequest.java
    - identity-service/src/main/java/com/n11/identity/agent/dto/AgentTokenResponse.java
    - identity-service/src/test/java/com/n11/identity/agent/AgentExchangeServiceTest.java
    - identity-service/src/test/java/com/n11/identity/agent/AgentExchangeControllerTest.java
  modified:
    - config-server/src/main/resources/config/identity-service.yml
    - identity-service/src/test/resources/application-test.yml
    - .planning/api-contracts.md

key-decisions:
  - "The repeatable R__ migration remains an optional pre-baked-hash path; AgentSeedRunner is the default demo seed path and never persists plaintext."
  - "IdentitySecurityConfig stayed unchanged: /agents/exchange is public at the servlet layer and API-key validated inside AgentExchangeService."
  - "AgentExchangeControllerTest creates users directly via new User(UUID, email, passwordHash, fullName, Instant) and saves through UserRepository."

patterns-established:
  - "Agent API keys are looked up by lowercase SHA-256 base16 hash and revoked rows are excluded by repository method name."
  - "Agent JWTs use JwtIssuerService.issue(userId, agentLabel, \"Agent\", List.of(\"ROLE_USER\")); JWT.sub is UUID.toString()."

requirements-completed: [AI-13]

duration: 6 min
completed: 2026-05-02
---

# Phase 09 Plan 02: Auth Bridge Summary

**DB-backed MCP API-key exchange that mints normal ROLE_USER identity JWTs for external agents**

## Performance

- **Duration:** 6 min
- **Started:** 2026-05-02T00:38:51Z
- **Completed:** 2026-05-02T00:45:21Z
- **Tasks:** 6 completed
- **Files modified:** 14

## Accomplishments

- Added `agent_api_keys` with hash primary key, real `users.id` binding, audit timestamp, and revocation column.
- Implemented `POST /agents/exchange` with validation, SHA-256 hashing, active-key lookup, `last_used_at` update, and JWT minting via the existing RS256 path.
- Added first-boot demo key generation that logs plaintext once and persists only the hash.
- Locked API documentation for `/agents/exchange` and Streamable HTTP `/mcp`.

## Task Commits

1. **Task 1: Flyway V5 migration + repeatable seed migration** — `59b9ba2` (feat)
2. **Task 2: AgentApiKey entity + repository** — `bfe7d17` (feat)
3. **Task 3 RED: AgentExchangeService tests** — `7c01546` (test)
4. **Task 3 GREEN: AgentExchangeService implementation** — `58e7af6` (feat)
5. **Task 4 RED: AgentExchangeController integration tests** — `27ad4ae` (test)
6. **Task 4 GREEN: AgentExchangeController implementation** — `8a4ad94` (feat)
7. **Task 5: AgentSeedRunner** — `4855b22` (feat)
8. **Task 6: api-contracts.md updates** — `fea385e` (docs)

**Plan metadata:** pending final metadata commit.

## Files Created/Modified

- `identity-service/src/main/resources/db/migration/V5__agent_api_keys.sql` — Creates hashed agent API key table.
- `identity-service/src/main/resources/db/migration/R__seed_agent_api_keys.sql` — Optional repeatable seed using `mcpAgentSeedHash` placeholder.
- `identity-service/src/main/java/com/n11/identity/agent/AgentApiKey.java` — JPA entity for agent keys.
- `identity-service/src/main/java/com/n11/identity/agent/AgentApiKeyRepository.java` — Active-key lookup and audit update methods.
- `identity-service/src/main/java/com/n11/identity/agent/AgentExchangeService.java` — Hash lookup, audit timestamp, JWT minting.
- `identity-service/src/main/java/com/n11/identity/agent/AgentExchangeController.java` — Thin `/agents/exchange` controller.
- `identity-service/src/main/java/com/n11/identity/agent/AgentSeedRunner.java` — First-boot random key generation and one-time logging.
- `identity-service/src/main/java/com/n11/identity/agent/dto/AgentExchangeRequest.java` — Validated request DTO.
- `identity-service/src/main/java/com/n11/identity/agent/dto/AgentTokenResponse.java` — JWT response DTO.
- `identity-service/src/test/java/com/n11/identity/agent/AgentExchangeServiceTest.java` — Unit tests for exchange behavior.
- `identity-service/src/test/java/com/n11/identity/agent/AgentExchangeControllerTest.java` — Testcontainers integration tests for endpoint behavior and JWT claims.
- `config-server/src/main/resources/config/identity-service.yml` — Flyway and seed-runner config bindings.
- `identity-service/src/test/resources/application-test.yml` — Test Flyway placeholder and seed-runner disablement.
- `.planning/api-contracts.md` — Finalized auth bridge and MCP endpoint rows.

## Decisions Made

- Reused the planned dual seed mechanism: `R__seed_agent_api_keys.sql` supports optional pre-baked hash demos, while `AgentSeedRunner` is the default runtime generation path.
- Kept `IdentitySecurityConfig` unchanged because it already has `anyRequest().permitAll()`; the API-key check is entirely inside `AgentExchangeService`.
- `AgentExchangeControllerTest` seeds users directly with `new User(UUID, email, passwordHash, fullName, Instant)` through `UserRepository`, matching the domain constructor used by `UserService.register`.
- Verified JWT.sub is the bound `UUID.toString()` value (lowercase canonical UUID string from Java) and `roles[0]` is `ROLE_USER`.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added missing test-profile seed controls**
- **Found during:** task 5 (AgentSeedRunner)
- **Issue:** `ApplicationReadyEvent` would run in Spring Boot integration tests and fail if the default admin email was absent.
- **Fix:** Added `mcp.seed.enabled=false` to `identity-service/src/test/resources/application-test.yml`.
- **Files modified:** `identity-service/src/test/resources/application-test.yml`
- **Verification:** `./gradlew :identity-service:test --tests "*AgentExchange*" --console=plain` passed.
- **Committed in:** `4855b22`

**2. [Rule 2 - Missing Critical] Bound runtime seed email to config-server admin seed**
- **Found during:** task 5 (AgentSeedRunner)
- **Issue:** The runner defaulted to `admin@n11demo.com`, but production admin seed email is configurable via `ADMIN_SEED_EMAIL`; a mismatch would prevent first-boot seeding.
- **Fix:** Added `admin.seed.email: ${ADMIN_SEED_EMAIL:admin@n11demo.com}` and `mcp.seed.enabled` config bindings.
- **Files modified:** `config-server/src/main/resources/config/identity-service.yml`
- **Verification:** `./gradlew :identity-service:compileJava --console=plain` passed.
- **Committed in:** `4855b22`

---

**Total deviations:** 2 auto-fixed (1 blocking, 1 missing critical).
**Impact on plan:** Both fixes were required for reliable test execution and runtime seeding; no feature scope creep.

## Issues Encountered

- TDD RED phases failed as expected before implementation: service test compile failures, then controller integration failures.
- Manual docker-compose smoke was not executed in this plan run; automated tests verify the endpoint and seed code path compiles.

## User Setup Required

None - no external service configuration required. At first identity-service boot, operators must copy the WARN-log `MCP_API_KEY=...` into `.env`; this is runtime demo operation, not source setup.

## Known Stubs

None.

## Threat Flags

None beyond the plan threat model. The new trust boundary (`/agents/exchange`) and plaintext-log exposure were already registered as T-09-04 through T-09-09.

## Verification

- `./gradlew :identity-service:test --tests "*AgentExchange*" --console=plain` → BUILD SUCCESSFUL.
- `ls identity-service/src/main/resources/db/migration/ | sort | tail -2` → `V4__init_outbox.sql`, `V5__agent_api_keys.sql` (V5 is highest versioned migration; repeatable R__ seed sorts earlier).
- `grep -q 'POST /agents/exchange' .planning/api-contracts.md` → pass.
- `! grep -R -E 'MCP_API_KEY=[a-zA-Z0-9_-]{20,}' identity-service/src/main/` → pass; no plaintext key committed.

## Next Phase Readiness

- Plan 09-04 can call identity-service `/agents/exchange` with `{"apiKey":"..."}` and parse `accessToken` + `expiresIn`.
- Plan 09-04 should extract JWT `sub` as the bound UUID string and use that as the agent `ToolContext.userId`.
- No blockers for remaining mcp-server plans.

## Self-Check: PASSED

- Created-file checks passed for migrations, agent entity/service/controller/runner, and this summary.
- Commit checks passed for all task commits: `59b9ba2`, `bfe7d17`, `7c01546`, `58e7af6`, `27ad4ae`, `8a4ad94`, `4855b22`, `fea385e`.

---
*Phase: 09-mcp-server*
*Completed: 2026-05-02*
