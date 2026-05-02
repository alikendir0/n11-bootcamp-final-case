---
phase: 09-mcp-server
plan: 06
subsystem: testing
tags: [mcp-server, infra-tests, spring-ai, agent-toolset, ai-11]

requires:
  - phase: 09-mcp-server
    provides: [mcp-server module, AgentToolMcpRegistration, agent-toolset registration]
provides:
  - infra-tests integration proof that mcp-server exposes the shared 10-tool ToolRegistry through Spring AI ToolCallbackProvider
  - SC-4 structural DRY gate preventing local AgentTool definitions in mcp-server source
  - multi-service classpath test config for mcp-server using Plan 05-04 excludeFilters pattern
affects: [phase-10-frontend-storefront, phase-11-demo-posture, ai-11-verification]

tech-stack:
  added: [org.springframework.ai:spring-ai-model test dependency]
  patterns: [infra-tests SpringBootTest slice, Plan 05-04 excludeFilters, Jackson schema tree equality, Java filesystem grep gate]

key-files:
  created:
    - infra-tests/src/test/java/com/n11/infratests/mcp/McpServerTestConfig.java
    - infra-tests/src/test/java/com/n11/infratests/mcp/McpServerToolsListEqualityTest.java
  modified:
    - infra-tests/build.gradle.kts

key-decisions:
  - "MCP infra-tests use an explicit spring-ai-model test dependency because :mcp-server exposes Spring AI types through implementation, not api."
  - "McpServerTestConfig keeps scheduling disabled and excludes foreign @SpringBootApplication classes instead of entity/repository scanning because mcp-server is DB-free."

patterns-established:
  - "MCP metadata parity test: compare ToolCallbackProvider definitions against ToolRegistry AgentTool source with set equality and Jackson JsonNode schema equality."
  - "SC-4 structural proof regex: class\\s+\\w+\\s+(extends\\s+\\w*AgentTool|implements\\s+\\w*AgentTool)|extends\\s+AbstractAgentTool."

requirements-completed: [AI-11]

duration: 9 min
completed: 2026-05-02
---

# Phase 09 Plan 06: MCP Server Infra-Tests Proof Summary

**Infra-tests now boot the real mcp-server registration path and prove its 10 Spring AI callbacks match the shared agent-toolset source exactly.**

## Performance

- **Duration:** 9 min
- **Started:** 2026-05-02T08:23:29Z
- **Completed:** 2026-05-02T08:32:16Z
- **Tasks:** 3
- **Files modified:** 3
- **Scoped infra-test runtime:** 15s on the first green run; 7s when up-to-date on final scoped rerun

## Accomplishments

- Added `:mcp-server` to `infra-tests` so the integration suite can boot the real `AgentToolMcpRegistration` implementation with `agent-toolset` on the same classpath.
- Added `McpServerTestConfig` using the Plan 05-04 `excludeFilters` pattern, with no JPA scan and no scheduling enablement for the DB-free mcp-server slice.
- Added `McpServerToolsListEqualityTest`, covering callback count, name set equality, Turkish description equality, Jackson-canonical input schema equality, boot log proof, and the SC-4 local-tool-definition ban.

## Task Commits

Each task was committed atomically:

1. **Task 1: infra-tests/build.gradle.kts — add :mcp-server test dependency** - `5b09a1d` (test)
2. **Task 2: McpServerTestConfig — Plan 05-04 excludeFilters + scheduling-disabled** - `aacc1e8` (test)
3. **Task 3 RED: McpServerToolsListEqualityTest proof** - `44f6149` (test)
4. **Task 3 GREEN: Spring AI model compile dependency** - `166ba80` (fix)

**Plan metadata:** pending final docs commit

## Files Created/Modified

- `infra-tests/build.gradle.kts` - Adds `:mcp-server` and `org.springframework.ai:spring-ai-model` to infra-tests test dependencies.
- `infra-tests/src/test/java/com/n11/infratests/mcp/McpServerTestConfig.java` - Provides the mcp-server infra-tests Spring Boot slice, scanning only mcp/agent/common packages and excluding foreign boot apps.
- `infra-tests/src/test/java/com/n11/infratests/mcp/McpServerToolsListEqualityTest.java` - Provides the AI-11 SC-1/SC-4 integration proof against `ToolCallbackProvider` and `ToolRegistry`.

## Decisions Made

- Added explicit `spring-ai-model` to infra-tests compile scope because `:mcp-server` uses `implementation` for Spring AI; runtime transitivity is enough to boot, but the test source imports `ToolCallback` and `ToolCallbackProvider` directly.
- Kept `McpServerTestConfig` free of `@EntityScan`, `@EnableJpaRepositories`, and `@EnableScheduling`; scheduling is disabled through test properties so `AgentJwtCache` does not attempt `/agents/exchange` during metadata-only tests.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Exposed Spring AI model types to infra-tests compile classpath**
- **Found during:** task 3 (McpServerToolsListEqualityTest)
- **Issue:** The RED test failed to compile because `org.springframework.ai.tool.ToolCallback` and `ToolCallbackProvider` were not on infra-tests compile classpath. `:mcp-server` brings Spring AI at runtime via `implementation`, but those classes are not exposed transitively for test source compilation.
- **Fix:** Added `testImplementation("org.springframework.ai:spring-ai-model")` to `infra-tests/build.gradle.kts`.
- **Files modified:** `infra-tests/build.gradle.kts`
- **Verification:** `./gradlew :infra-tests:test --tests "*McpServerToolsListEqualityTest*" --console=plain` passed.
- **Committed in:** `166ba80`

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Required for the integration proof to compile; no production scope change and no toolset duplication.

## TDD Gate Compliance

- **RED:** `44f6149` added the failing integration proof; failure mode was missing Spring AI model compile dependency.
- **GREEN:** `166ba80` added the minimal infra-tests compile dependency; scoped MCP infra-test passed.
- **REFACTOR:** Not needed.

## Verification

- ✅ `grep -q 'project(":mcp-server")' infra-tests/build.gradle.kts`
- ✅ `./gradlew :infra-tests:dependencies --configuration testRuntimeClasspath --console=plain` showed both `project :mcp-server` and `spring-ai-starter-mcp-server-webmvc -> 1.1.5`.
- ✅ `./gradlew :infra-tests:test --tests "*McpServerToolsListEqualityTest*" --console=plain` passed.
- ✅ `! grep -rE 'class\s+\w+\s+(extends\s+\w*AgentTool|implements\s+\w*AgentTool)|extends\s+AbstractAgentTool' mcp-server/src/main/java/` returned empty.
- ⚠️ `./gradlew :infra-tests:test --console=plain` did not complete green: pre-existing saga E2E `SagaHappyPathE2ETest > publishingStockReserved_yieldsPaymentCompletedOnPaymentsTx_within15s()` failed, then the run exceeded the 180s executor timeout during RabbitMQ listener shutdown noise. The new `McpServerToolsListEqualityTest` passed in isolation; the observed failure is in existing payment/saga infra-tests outside this plan's files.

## Known Stubs

None. The word "placeholder" appears only in a test-only property comment for the dummy `mcp.api-key`; no UI or runtime data path is stubbed by this plan.

## Threat Flags

None. Changes are test-only and add no network endpoint, auth path, file access beyond test-source static scanning, or schema change at a runtime trust boundary.

## Issues Encountered

- Full `:infra-tests:test` remains blocked by an existing saga E2E failure outside the MCP plan scope. This was documented rather than fixed to avoid touching unrelated dirty/payment saga files.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Ready for 09-07. AI-11 now has an infra-tests regression gate proving mcp-server uses the shared agent-toolset and that Spring AI honors explicit input schema metadata from `AgentTool.parametersJsonSchema()`.

## Self-Check: PASSED

- Confirmed created files exist: `McpServerTestConfig.java`, `McpServerToolsListEqualityTest.java`, and this summary.
- Confirmed task commits exist in git history: `5b09a1d`, `aacc1e8`, `44f6149`, `166ba80`.
- Confirmed scoped MCP infra-test and SC-4 grep gate passed.

---
*Phase: 09-mcp-server*
*Completed: 2026-05-02*
