---
phase: 09-mcp-server
verified: 2026-05-02T00:00:00Z
status: passed
score: 12/12 must-haves verified
overrides_applied: 0
---

# Phase 9: MCP Server Verification Report

**Phase Goal:** Stand up `mcp-server` using `spring-ai-starter-mcp-server-webmvc 1.1.5`, register the SAME `agent-toolset` shared module from Phase 8 with zero copied tool definitions, wire stdio and HTTP/Streamable transport runbook/demo path, and bridge external-agent auth via `MCP_API_KEY` → identity-service `/agents/exchange` → internal JWT.
**Verified:** 2026-05-02T00:00:00Z
**Status:** passed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | mcp-server uses Spring AI MCP starter 1.1.5 | ✓ VERIFIED | Root `build.gradle.kts:21-27` imports `spring-ai-bom:1.1.5`; `mcp-server/build.gradle.kts:10-12` depends on `org.springframework.ai:spring-ai-starter-mcp-server-webmvc`; `./gradlew :mcp-server:test` passed. |
| 2 | mcp-server module is real, compiling, DB-free, and service-scaffolded | ✓ VERIFIED | `settings.gradle.kts:20-24` includes `mcp-server`; `mcp-server/build.gradle.kts:6-35` has no JPA/Flyway/Postgres/Springdoc deps; `McpServerApplication.java:27-29` is the Boot entry point; test suite passed. |
| 3 | `tools/list` catalog equals shared agent-toolset catalog | ✓ VERIFIED | `AgentToolMcpRegistration.java:62-79` builds callbacks from `toolRegistry.all()`; `AgentToolMcpRegistrationTest.java:61-107` and infra test `McpServerToolsListEqualityTest.java:86-134` assert 10 callbacks, exact name set, description equality, and JSON schema equality. Scoped infra test passed. |
| 4 | mcp-server has zero copied/local tool definitions | ✓ VERIFIED | `grep` for `implements AgentTool`, `extends AbstractAgentTool`, `@McpTool`, `@McpToolParam` in `mcp-server/src/main/java` found no matches; `McpServerToolsListEqualityTest.java:137-158` enforces this structurally. |
| 5 | Both stdio and Streamable HTTP transports are wired/documented | ✓ VERIFIED | `config-server/src/main/resources/config/mcp-server.yml:35-57` sets `protocol: STREAMABLE`, `stdio: ${MCP_STDIO_ENABLED:true}`, and `/mcp`; compose sets `MCP_STDIO_ENABLED: "false"` for HTTP mode (`docker-compose.yml:543-550`); RUNBOOK sections 4–6 document Inspector HTTP, Inspector stdio, and Claude Desktop stdio launch using the same image. |
| 6 | `/mcp/**` network transport is reachable only through the gateway and protected by JWT | ✓ VERIFIED | Gateway route exists at `api-gateway.yml:145-154` with `lb://MCP-SERVER`, `Path=/mcp/**`, `response-timeout: -1`, `StripPrefix=0`; `SecurityConfig.java:58-85` has no `/mcp/**` permit rule and ends with `anyExchange().authenticated()`. |
| 7 | External API-key exchange endpoint exists and mints normal user JWTs | ✓ VERIFIED | `AgentExchangeController.java:22-34` mounts `POST /agents/exchange`; `AgentExchangeService.java:47-59` hashes API key, requires active DB row, updates audit timestamp, and calls `JwtIssuerService.issue(... List.of("ROLE_USER"))`; controller tests decode JWT `sub` and role. |
| 8 | Agent API keys are hashed, revocable, audited, and bound to real users | ✓ VERIFIED | `V5__agent_api_keys.sql:6-15` creates `api_key_hash CHAR(64)` PK, `user_id REFERENCES users(id)`, `last_used_at`, `revoked_at`; repository uses active-key lookup and audit update; `AgentExchangeControllerTest.java:95-141` covers valid, unknown, blank, and missing key paths. |
| 9 | mcp-server exchanges `MCP_API_KEY` lazily, caches JWT, refreshes with 10-minute buffer, and extracts JWT.sub for ToolContext | ✓ VERIFIED | `AgentJwtClient.java:38-70` posts `apiKey` to `lb://identity-service/agents/exchange`; `AgentJwtCache.java:43-110` lazy-exchanges, caches token/sub, refreshes before `expiresAt - 10m`; `AgentJwtCacheTest.java:33-105` covers first exchange, cache reuse, refresh, failure retry, and sub decoding. |
| 10 | Tool HTTP calls receive the cached JWT and user context | ✓ VERIFIED | `JwtBearerInterceptor.java:34-39` sets `Authorization: Bearer <cached jwt>` on outbound RestClient calls; `McpRestClientConfig.java:60-75` makes this builder primary for agent-toolset clients while keeping exchange client un-intercepted; `AgentToolMcpRegistration.java:82-89` builds `ToolContext(jwtCache.userId(), correlationId, Set.of())`. |
| 11 | Demo/operator path for real clients and mutating e2e is documented and human-approved | ✓ VERIFIED | `RUNBOOK.md` contains build, key capture, `/agents/exchange`, `/mcp tools/list`, Inspector HTTP, Inspector stdio, Claude Desktop config, Turkish prompts, Iyzico card, and audit SQL. Per 09-07-SUMMARY.md: user approved all 8 hard gates; this is recorded as human-approved evidence, not independently rerun evidence. |
| 12 | Phase requirements AI-11, AI-12, AI-13 are all accounted for | ✓ VERIFIED | PLAN frontmatter references all three IDs; REQUIREMENTS.md lines 83-85 define them and traceability lines 265-267 map them to Phase 9 Complete; implementation evidence maps to all three below. |

**Score:** 12/12 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|---|---|---|---|
| `mcp-server/build.gradle.kts` | MCP Boot/Jib module with Spring AI starter and shared deps | ✓ VERIFIED | Contains starter and `project(":agent-toolset")`; omits JPA/Flyway/Postgres/Springdoc. |
| `config-server/src/main/resources/config/mcp-server.yml` | Streamable HTTP + stdio + tools-only config | ✓ VERIFIED | `protocol: STREAMABLE`, `stdio`, `/mcp`, capabilities tool=true/resource=false/prompt=false. |
| `mcp-server/src/main/java/com/n11/mcp/config/AgentToolMcpRegistration.java` | Adapter from shared ToolRegistry to ToolCallbackProvider | ✓ VERIFIED | Uses `toolRegistry.all()` and no local tool definitions. |
| `identity-service/src/main/java/com/n11/identity/agent/*` | API-key exchange bridge | ✓ VERIFIED | Controller/service/entity/repository/seed runner present and tested. |
| `mcp-server/src/main/java/com/n11/mcp/auth/*` | JWT client/cache/interceptor | ✓ VERIFIED | Lazy exchange, cache, bearer injection, and DTO implemented. |
| `config-server/src/main/resources/config/api-gateway.yml` | `/mcp/**` gateway route | ✓ VERIFIED | Route is configured with long response timeout and no public allowlist. |
| `docker-compose.yml` | Internal mcp-server service | ✓ VERIFIED | Service block exists, no host `ports`, no Postgres/RabbitMQ dependency, uses `.env` MCP_API_KEY. |
| `.planning/phases/09-mcp-server/RUNBOOK.md` | Operator demo/runbook | ✓ VERIFIED | Covers HTTP, stdio, Claude Desktop, auth bridge, Iyzico, audit trail. |

### Key Link Verification

| From | To | Via | Status | Details |
|---|---|---|---|---|
| Root Gradle BOM | mcp-server Spring AI starter | dependency management | ✓ WIRED | BOM line in root resolves starter version; mcp-server suite passed. |
| mcp-server | agent-toolset | Gradle dependency + component scan | ✓ WIRED | `project(":agent-toolset")`, `scanBasePackages="com.n11"`, adapter injects `ToolRegistry`. |
| AgentToolMcpRegistration | ToolRegistry | `toolRegistry.all()` | ✓ WIRED | Names/descriptions/schemas asserted equal by unit + infra tests. |
| MCP tool invocation | ToolContext user | `jwtCache.userId()` | ✓ WIRED | Adapter calls cache on each tool invocation. |
| AgentJwtCache | identity-service `/agents/exchange` | AgentJwtClient RestClient | ✓ WIRED | Client posts to `/agents/exchange`; service endpoint exists and is tested. |
| AgentJwtCache | agent-toolset HTTP clients | JwtBearerInterceptor + primary RestClient.Builder | ✓ WIRED | Primary builder carries interceptor; tool clients use injected `RestClient.Builder`. |
| Gateway `/mcp/**` | mcp-server | `lb://MCP-SERVER` route | ✓ WIRED | Route present with `Path=/mcp/**` and `response-timeout: -1`. |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|---|---|---|---|---|
| `AgentToolMcpRegistration` | `callbacks` | `ToolRegistry.all()` real Spring bean list from `agent-toolset` | Yes — tests assert 10 real AgentTool beans | ✓ FLOWING |
| `AgentJwtCache` | `jwt`, `cachedSub` | `AgentJwtClient.exchange()` response from identity-service | Yes — identity test mints RS256 JWT via `JwtIssuerService` | ✓ FLOWING |
| `JwtBearerInterceptor` | Authorization header | `cache.bearerToken()` | Yes — unit test verifies exact bearer header injection | ✓ FLOWING |
| `AgentExchangeService` | `AgentTokenResponse.accessToken` | DB-backed `agent_api_keys` active-row lookup + `JwtIssuerService.issue` | Yes — controller integration test decodes JWT sub/roles | ✓ FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|---|---|---|---|
| mcp-server unit/context tests | `./gradlew :mcp-server:test --console=plain` | `BUILD SUCCESSFUL in 5s` | ✓ PASS |
| MCP tool catalog equality infra test | `./gradlew :infra-tests:test --tests "*McpServerToolsListEqualityTest*" --console=plain` | `BUILD SUCCESSFUL in 34s` | ✓ PASS |
| identity `/agents/exchange` tests | `./gradlew :identity-service:test --tests "*AgentExchange*" --console=plain` | `BUILD SUCCESSFUL in 22s` | ✓ PASS |
| External client stdio/HTTP + Iyzico e2e | Human checkpoint from 09-07 | User approved all 8 hard gates; not rerun by verifier | ✓ PASS (human-approved) |

Note: full `:infra-tests:test` was not used as Phase 9 evidence because 09-06 documented an unrelated existing `SagaHappyPathE2ETest` failure/timeout. The scoped MCP infra-test is the relevant Phase 9 criterion and passes.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|---|---|---|---|---|
| AI-11 | 09-01, 09-03, 09-06 | mcp-server registers SAME agent-toolset via Spring AI MCP starter 1.1.5 | ✓ SATISFIED | BOM/starter present; adapter wraps `ToolRegistry.all()`; unit + infra tests assert catalog equality; zero local tool defs. |
| AI-12 | 09-01, 09-05, 09-07 | stdio and HTTP+SSE/Streamable transports both wired | ✓ SATISFIED | `mcp-server.yml` config, gateway `/mcp/**` route, compose HTTP mode, RUNBOOK stdio/HTTP client commands, human-approved client checks. |
| AI-13 | 09-02, 09-04, 09-07 | MCP_API_KEY exchanged for internal JWT and propagated for tool calls | ✓ SATISFIED | `agent_api_keys` schema, `/agents/exchange`, `AgentJwtClient`, `AgentJwtCache`, `JwtBearerInterceptor`, tests, and human-approved mutating e2e. |

No orphaned Phase 9 requirements found: REQUIREMENTS.md maps only AI-11, AI-12, and AI-13 to Phase 9.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|---|---:|---|---|---|
| — | — | None found in Phase 9 runtime code | ℹ️ Info | Grep found no TODO/FIXME/placeholders/stubs, no local AgentTool implementations, and no committed MCP_API_KEY secret. |

### Human Verification Required

None pending. The inherently manual MCP client + Iyzico flow was approved by the user during 09-07 and is recorded as human-approved evidence, not independently rerun evidence.

### Gaps Summary

No blocking gaps found. The Phase 9 goal is achieved in code and supported by scoped automated tests plus the approved manual client/e2e checkpoint.

---

_Verified: 2026-05-02T00:00:00Z_
_Verifier: OpenCode (gsd-verifier)_
