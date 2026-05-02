---
phase: 09-mcp-server
plan: 04
subsystem: auth
tags: [mcp, jwt, restclient, spring-cloud-loadbalancer, tdd]

requires:
  - phase: 09-mcp-server
    provides: [mcp-server module, identity-service agent exchange endpoint, AgentToolMcpRegistration]
provides:
  - AgentJwtCache lazy API-key-to-JWT exchange with 10-minute refresh buffer
  - JwtBearerInterceptor for Authorization propagation on agent-toolset HTTP calls
  - Dual RestClient.Builder wiring that prevents AgentJwtClient/interceptor cycles
affects: [mcp-server, agent-toolset-http-auth, phase-09-verification]

tech-stack:
  added: []
  patterns: [TDD red-green commits, qualified un-intercepted RestClient builder, primary tool RestClient builder]

key-files:
  created:
    - mcp-server/src/main/java/com/n11/mcp/auth/AgentTokenResponseDto.java
    - mcp-server/src/main/java/com/n11/mcp/auth/AgentJwtClient.java
    - mcp-server/src/main/java/com/n11/mcp/auth/JwtBearerInterceptor.java
    - mcp-server/src/main/java/com/n11/mcp/config/McpRestClientConfig.java
    - mcp-server/src/test/java/com/n11/mcp/auth/AgentJwtCacheTest.java
    - mcp-server/src/test/java/com/n11/mcp/auth/JwtBearerInterceptorTest.java
  modified:
    - mcp-server/src/main/java/com/n11/mcp/auth/AgentJwtCache.java

key-decisions:
  - "AgentJwtClient uses explicit @Qualifier(\"loadBalancedRestClientBuilder\") for the un-intercepted exchange client, rather than relying on parameter-name matching."
  - "McpRestClientConfig retains @LoadBalanced on both tool and exchange builders because agent-toolset clients currently use HTTP defaults but are designed for service-discovery override URIs."
  - "AgentJwtCache uses a 10-minute refresh buffer per 09-RESEARCH.md Pitfall #4, superseding the earlier 5-minute D-07 note in CONTEXT.md."

patterns-established:
  - "MCP auth bridge: MCP_API_KEY -> identity-service /agents/exchange -> cached JWT -> Authorization bearer header on tool REST calls."
  - "RestClient cycle prevention: tool builder carries JwtBearerInterceptor; AgentJwtClient receives a separately qualified load-balanced builder without the interceptor."
  - "JWT.sub extraction in mcp-server is decode-only; gateway remains the authoritative signature validator."

requirements-completed: [AI-13]

duration: 5 min
completed: 2026-05-02
---

# Phase 09 Plan 04: MCP Auth Bridge Summary

**MCP API-key exchange now produces a cached user JWT whose UUID `sub` drives ToolContext and whose bearer token is injected into all agent-toolset REST calls.**

## Performance

- **Duration:** 5 min
- **Started:** 2026-05-02T08:08:36Z
- **Completed:** 2026-05-02T08:13:55Z
- **Tasks:** 4
- **Files modified:** 7

## Accomplishments

- Added `AgentJwtClient` and `AgentTokenResponseDto` matching identity-service `AgentTokenResponse(accessToken, expiresIn)` without coupling mcp-server to identity-service classes.
- Replaced the Plan 09-03 `AgentJwtCache` stub with lazy exchange, scheduled minute-tick refresh, 10-minute refresh buffer, cached JWT.sub extraction, and retry-on-failure behavior.
- Added `JwtBearerInterceptor` so agent-toolset HTTP calls receive `Authorization: Bearer <cached jwt>` while preserving existing `X-User-Id` behavior from ToolContext.
- Added `McpRestClientConfig` with a primary intercepted tool builder and a qualified un-intercepted load-balanced builder for `AgentJwtClient` cycle prevention.

## Task Commits

Each task was committed atomically:

1. **Task 1: AgentTokenResponseDto + AgentJwtClient** - `c560089` (feat)
2. **Task 2 RED: AgentJwtCache tests** - `8f78afd` (test)
3. **Task 2 GREEN: AgentJwtCache implementation** - `6a1c54d` (feat)
4. **Task 3 RED: JwtBearerInterceptor tests** - `13cda8c` (test)
5. **Task 3 GREEN: JwtBearerInterceptor implementation** - `dd94d48` (feat)
6. **Task 4: McpRestClientConfig wiring** - `a2818b3` (feat)

**Plan metadata:** pending final docs commit

## Files Created/Modified

- `mcp-server/src/main/java/com/n11/mcp/auth/AgentTokenResponseDto.java` - Local wire DTO for `/agents/exchange` response.
- `mcp-server/src/main/java/com/n11/mcp/auth/AgentJwtClient.java` - Exchanges `MCP_API_KEY` against `lb://identity-service/agents/exchange` and maps 401 to `SecurityException`.
- `mcp-server/src/main/java/com/n11/mcp/auth/AgentJwtCache.java` - Lazy cached JWT implementation with scheduled refresh and decode-only `sub` extraction.
- `mcp-server/src/main/java/com/n11/mcp/auth/JwtBearerInterceptor.java` - Adds cached JWT as a bearer header to outbound tool REST calls.
- `mcp-server/src/main/java/com/n11/mcp/config/McpRestClientConfig.java` - Provides the intercepted primary tool builder and un-intercepted exchange builder.
- `mcp-server/src/test/java/com/n11/mcp/auth/AgentJwtCacheTest.java` - Unit coverage for lazy exchange, caching, refresh, subject decoding, and failure retry.
- `mcp-server/src/test/java/com/n11/mcp/auth/JwtBearerInterceptorTest.java` - Unit coverage for bearer header injection, delegation, and failure propagation.

## Decisions Made

- Used explicit `@Qualifier("loadBalancedRestClientBuilder")` for `AgentJwtClient`, rather than parameter-name matching, because it is more robust and self-documenting in a context with multiple `RestClient.Builder` beans.
- Retained `@LoadBalanced` on both builders. The current agent-toolset defaults are direct HTTP service hostnames (for example `http://cart-service:8084`), but keeping the primary tool builder load-balanced preserves the planned `lb://` override posture without changing agent-toolset.
- Kept JWT subject format as the lowercase UUID string issued by identity-service; `AgentJwtCache.userId()` returns exactly that string, matching `ToolContext.userId()` expectations.
- Chose the 10-minute refresh buffer from `09-RESEARCH.md` Pitfall #4 over the earlier 5-minute note in `09-CONTEXT.md` D-07.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Normalized competing RestClient.Builder primary beans**
- **Found during:** Task 4 (McpRestClientConfig wiring)
- **Issue:** Full mcp-server context tests failed with `NoUniqueBeanDefinitionException` because common-logging already contributes a primary `correlationIdAwareRestClientBuilder`, while the plan added a primary `toolRestClientBuilder` and a separate `loadBalancedRestClientBuilder`.
- **Fix:** Added a static `BeanFactoryPostProcessor` in `McpRestClientConfig` to make `toolRestClientBuilder` the single primary candidate and mark the common-logging and exchange builders non-primary. `AgentJwtClient` still receives the exchange builder via explicit qualifier.
- **Files modified:** `mcp-server/src/main/java/com/n11/mcp/config/McpRestClientConfig.java`
- **Verification:** `./gradlew :mcp-server:test --console=plain` passed after the fix.
- **Committed in:** `a2818b3`

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** The fix preserves the planned dual-builder design and makes Spring injection deterministic; no scope expansion.

## Issues Encountered

- Initial RED tests failed at compile time because the planned collaborators/methods did not exist yet; this was expected TDD RED behavior against the Plan 09-03 stub and missing interceptor.
- Full context tests exposed the RestClient primary-bean conflict described above; fixed inline before committing Task 4.

## Known Stubs

None. Stub scan found only legitimate null checks and SLF4J `{}` placeholders in implementation code.

## Threat Flags

None. New outbound `/agents/exchange`, cached JWT memory, and bearer-header propagation were all covered by the plan threat model (T-09-14 through T-09-18).

## Verification

- `./gradlew :mcp-server:test --tests "*AgentJwt*" --tests "*JwtBearer*" --console=plain` — PASS (`/tmp/09-04-overall-auth.log` contains `BUILD SUCCESSFUL`).
- `./gradlew :mcp-server:test --console=plain` — PASS (`/tmp/09-04-overall-full.log` contains `BUILD SUCCESSFUL`).
- `grep -q '@Primary' mcp-server/src/main/java/com/n11/mcp/config/McpRestClientConfig.java` — PASS.
- `grep -q '@LoadBalanced' mcp-server/src/main/java/com/n11/mcp/config/McpRestClientConfig.java` — PASS.
- `! grep -E 'Set\.of\(\)|seenIds = new HashSet' mcp-server/src/main/java/com/n11/mcp/auth/*.java` — PASS.
- Manual runtime check with identity-service and valid `MCP_API_KEY` was not executed in this automated plan; it remains a Phase 9 smoke/UAT item.

## User Setup Required

None - no new external service configuration required by this plan. A valid `MCP_API_KEY` is still required for runtime smoke testing, provided by prior Phase 9 identity-service setup.

## Next Phase Readiness

- Plan 09-03 `AgentToolMcpRegistration` now has a real `AgentJwtCache` implementation behind its existing contract.
- The mcp-server unit suite is green against the real cache and bearer interceptor.
- Ready for the remaining Phase 9 plans to validate end-to-end MCP transport and tool invocation through the gateway.

## Self-Check: PASSED

- Verified all created/modified plan files exist on disk.
- Verified all task commits exist in git history: `c560089`, `8f78afd`, `6a1c54d`, `13cda8c`, `dd94d48`, `a2818b3`.

---
*Phase: 09-mcp-server*
*Completed: 2026-05-02*
