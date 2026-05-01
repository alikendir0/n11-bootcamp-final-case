---
phase: 08-ai-port-adapter-agent-toolset
plan: "05"
subsystem: search-service + infra-tests + smoke-runbook
tags: [solid-demo, qual-08, d-09, embedding-provider, infra-tests, smoke-runbook, pgvector]

dependency_graph:
  requires: [08-04]
  provides:
    - "search-service SearchService as second EmbeddingProvider consumer (QUAL-08 D-09)"
    - "SearchServiceContextTest: Spring context loads + EmbeddingProvider wired + float[768] returned"
    - "AiServiceTestConfig: bean disambiguation for infra-tests multi-service classpath"
    - "AiServiceClasspathTest: ai-service boots with echo provider + 10 tools in infra-tests"
    - "08-SMOKE-RUNBOOK.md: operator-runnable Phase 8 end-to-end smoke + SOLID swap demo"
  affects:
    - "09 (mcp-server — agent-toolset contract locked; search-service D-09 invariant documented)"

tech_stack:
  added:
    - "FlywayConfigurationCustomizer: initSql for pgvector extension before V1 migration in tests"
    - "pgvector/pgvector:pg16 Testcontainers image for search-service context test"
  patterns:
    - "No-op lambda EmbeddingProvider stub: (text, dims) -> new float[dims] — D-09 skeleton pattern"
    - "FlywayConfigurationCustomizer @TestConfiguration for pgvector extension activation in tests"
    - "AiServiceTestConfig public class with excludeFilters — Plan 05-04 disambiguation pattern applied"
    - "Explicit testImplementation for non-api deps (ai-port, agent-toolset) in infra-tests classpath"

key_files:
  created:
    - "search-service/src/main/java/com/n11/search/EmbeddingProviderConfig.java"
    - "search-service/src/main/java/com/n11/search/SearchService.java"
    - "search-service/src/test/java/com/n11/search/SearchServiceContextTest.java"
    - "infra-tests/src/test/java/com/n11/infratests/saga/AiServiceTestConfig.java"
    - "infra-tests/src/test/java/com/n11/infratests/ai/AiServiceClasspathTest.java"
    - ".planning/phases/08-ai-port-adapter-agent-toolset/08-SMOKE-RUNBOOK.md"
  modified:
    - "infra-tests/build.gradle.kts"

key_decisions:
  - "D-09 enforced: search-service EmbeddingProvider is a deterministic zero-vector lambda stub; zero google-genai deps; no /search endpoint in v1"
  - "FlywayVectorConfig @TestConfiguration: pgvector extension must be enabled via FlywayConfigurationCustomizer.initSql before V1 migration; connection-init-sql (HikariCP) does not apply to Flyway's internal connection"
  - "Explicit ai-port + agent-toolset testImplementation in infra-tests: ai-service uses implementation (not api) so those deps are not transitively available on infra-tests compile classpath"
  - "AiServiceTestConfig made public: cross-package access from com.n11.infratests.ai requires public visibility"

metrics:
  duration_minutes: 25
  tasks_completed: 3
  files_created: 6
  files_modified: 1
  completed_date: "2026-05-01"
---

# Phase 08 Plan 05: Search-Service EmbeddingProvider + Infra-Tests + Smoke Runbook Summary

search-service as second EmbeddingProvider port consumer (D-09 SOLID proof), infra-tests multi-service classpath with AiServiceTestConfig bean disambiguation, and 08-SMOKE-RUNBOOK.md covering all 5 ROADMAP Phase 8 success criteria.

## What Was Built

### Task 1: search-service EmbeddingProvider + infra-tests classpath (commit 68d5eaa)

**EmbeddingProviderConfig (search-service):**
No-op `EmbeddingProvider` bean via lambda `(text, dims) -> new float[dims]`. Logs startup with D-09 v1 skeleton message. Zero google-genai imports — the D-09 invariant. Config Javadoc explains the substitutability point: this `@Bean` could equally wire a Gemini-backed impl, an OpenAI impl, or a REST client to ai-service's `/embed` — the rest of search-service doesn't change.

**SearchService (search-service):**
Constructor-injected `EmbeddingProvider`. Second consumer alongside ai-service's `GeminiEmbeddingAdapter`. Logs the impl class name on startup. v2 body (`embeddings.embed(q, 768)` → pgvector cosine search) is a `// v2:` comment stub.

**SearchServiceContextTest:**
`@SpringBootTest(webEnvironment=NONE)` + Testcontainers `pgvector/pgvector:pg16` + `FlywayVectorConfig` inner `@TestConfiguration`. Two assertions: context loads with both beans non-null; `embeddingProvider.embed("test", 768)` returns `float[768]`.

**infra-tests/build.gradle.kts:**
Added `testImplementation(project(":ai-service"))`, `testImplementation(project(":search-service"))`, `testImplementation(project(":ai-port"))`, `testImplementation(project(":agent-toolset"))`.

**AiServiceTestConfig:**
Public class, `@SpringBootApplication(scanBasePackages={"com.n11.ai","com.n11.agent","com.n11.error","com.n11.logging"})` + `@ComponentScan(excludeFilters={@Filter(SpringBootApplication.class)})` + `@EntityScan("com.n11.ai")` + `@EnableJpaRepositories("com.n11.ai")`. No `@EnableScheduling` (ai-service has no scheduled jobs in v1).

**AiServiceClasspathTest:**
`@SpringBootTest(classes=AiServiceTestConfig.class)` + `postgres:16` container + `ai.provider=echo` + flyway AI migration path. Single assertion: `chatProvider` non-null, `toolRegistry.all().size() == 10`, `stateService` non-null.

### Task 2: 08-SMOKE-RUNBOOK.md (commit f06459c)

12-section operator-runnable document:
- Section 1: Prerequisites (Docker, .env, GEMINI_API_KEY)
- Section 2: Build + boot commands with health-wait loop
- Section 3: Quick sanity (13 services up, gateway routes visible)
- Section 4 (SC-1): ai-port zero-Gemini-dep verification + D-09 search-service check
- Section 5 (SC-2): SOLID swap demo — gemini baseline → echo swap → confirm [ECHO] prefix → flip back → Pitfall #1 audit trail capture
- Section 6 (SC-3): AgentToolRegistryTest + ToolDispatcherIdProvenanceTest commands
- Section 7 (SC-4): SSE wire-format check + Postgres persistence rows + restart survival
- Section 8 (SC-5): AI-15 cart-bridge demo — chat add_to_cart → REST cart GET
- Section 9: Cross-service X-Correlation-Id trace recipe (ARCH-08)
- Section 10: Pitfall #9 demo-budget notes (10 RPM, 429 fallback, echo bypass)
- Section 11: Roll-up table mapping all 5 SC to sections
- Section 12: Human-verify checkpoint with required audit trail captures

### Task 3: Human-verify checkpoint (yolo mode auto-approved)

Checkpoint type `human-verify` auto-approved per `mode: yolo` project config. The smoke runbook is committed and provides exact curl/command sequences for the live demo.

## Test Results

```
./gradlew :search-service:test --tests SearchServiceContextTest -> BUILD SUCCESSFUL
  - context_loads_with_embedding_provider_injected: PASS
  - embedding_provider_returns_vector_of_requested_dims: PASS

./gradlew :infra-tests:test --tests AiServiceClasspathTest -> BUILD SUCCESSFUL
  - context_boots_with_echo_provider_and_10_tools: PASS

./gradlew :search-service:test :ai-port:test :agent-toolset:test -> BUILD SUCCESSFUL (all UP-TO-DATE or passed)
```

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] FlywayConfigurationCustomizer needed for pgvector extension in tests**
- **Found during:** Task 1 (SearchServiceContextTest) — first run
- **Issue:** `pgvector/pgvector:pg16` image ships the vector extension but does NOT enable it in the test database. The plan's `connection-init-sql=CREATE EXTENSION IF NOT EXISTS vector` applies to each HikariCP connection but NOT to Flyway's internal schema migration connection. Flyway's V1 migration hit `ERROR: type "vector" does not exist` at `vector(768)` column definition.
- **Fix:** Added `FlywayVectorConfig` inner `@TestConfiguration` with a `FlywayConfigurationCustomizer` bean that sets `initSql("CREATE EXTENSION IF NOT EXISTS vector")`. This runs before Flyway's migration connection is acquired.
- **Files modified:** `search-service/src/test/java/com/n11/search/SearchServiceContextTest.java` — added inner `@TestConfiguration` class, removed `connection-init-sql` property
- **Commit:** 68d5eaa

**2. [Rule 1 - Bug] ai-port and agent-toolset not on infra-tests compile classpath**
- **Found during:** Task 1 (`infra-tests:compileTestJava`)
- **Issue:** `AiServiceClasspathTest` imports `ChatProvider` (ai-port) and `ToolRegistry` (agent-toolset). These are `implementation` dependencies of ai-service (not `api`), so they don't appear on infra-tests' compile classpath via transitive resolution when doing `testImplementation(project(":ai-service"))`.
- **Fix:** Added explicit `testImplementation(project(":ai-port"))` and `testImplementation(project(":agent-toolset"))` to `infra-tests/build.gradle.kts`.
- **Files modified:** `infra-tests/build.gradle.kts`
- **Commit:** 68d5eaa

**3. [Rule 1 - Bug] AiServiceTestConfig must be public for cross-package access**
- **Found during:** Task 1 (`infra-tests:compileTestJava`)
- **Issue:** `AiServiceTestConfig` was package-private in `com.n11.infratests.saga`. `AiServiceClasspathTest` lives in `com.n11.infratests.ai` — different package. Compiler error: `AiServiceTestConfig is not public in com.n11.infratests.saga; cannot be accessed from outside package`.
- **Fix:** Changed `class AiServiceTestConfig` to `public class AiServiceTestConfig`.
- **Files modified:** `infra-tests/src/test/java/com/n11/infratests/saga/AiServiceTestConfig.java`
- **Commit:** 68d5eaa

## Known Stubs

- `SearchService.search()` — the v2 implementation body is a `// v2:` comment. The injection point exists; the query path is intentionally deferred to AI-V2-01. Not a UI-visible stub (search-service has no REST endpoint in v1 per D-09). Documented in EmbeddingProviderConfig Javadoc.

## Output Notes (from plan `<output>` section)

The plan requires these observations to be captured in the SUMMARY. Since the human-verify checkpoint was auto-approved (yolo mode), the live demo observations are noted as follows:

1. **Resolved Gemini model identifier (Pitfall #1):** Not observable without running the full docker-compose stack live. The `GeminiChatAdapter.verifyModel()` ApplicationReadyEvent listener logs the resolved model — expected to be `gemini-3-flash-preview` or `gemini-2.5-flash` as fallback. Actual observation deferred to Phase 11 final demo run.

2. **SSE wire-format deviation:** None observed in automated test (`ChatStreamSseTest` in Plan 04 verified `event:delta`, `event:tool_call`, `event:tool_result`, `event:done` format). No runtime deviation known.

3. **Cart-bridge demo confirmation:** AI-15 is verified at the integration test level by `ToolSchemaContractTest` + `add_to_cart` tool implementation in Plan 08-02. Live end-to-end verification deferred to Phase 11 demo run (full stack required).

4. **Pitfall #9 quota observations:** No live Gemini calls made during this plan (EchoChatProvider used for all tests). Demo budget notes documented in 08-SMOKE-RUNBOOK.md Section 10.

5. **Phase 9 entry-point reads:** mcp-server consumes `agent-toolset` module. Recommended reads before Phase 9 planning:
   - `agent-toolset/src/main/java/com/n11/agent/AgentTool.java` — interface contract (name, descriptionTr, requiresAuth, parametersJsonSchema, execute)
   - `agent-toolset/src/main/java/com/n11/agent/ToolRegistry.java` — Spring @Component collecting all AgentTool beans
   - `ai-service/src/main/java/com/n11/ai/domain/tools/ToolDispatcher.java` — D-04 auth gate + D-08 provenance; mcp-server needs its own dispatcher analog
   - `.planning/phases/08-ai-port-adapter-agent-toolset/08-CONTEXT.md` D-06, D-07, D-10 — tool backing service decisions (search_products backs onto product-service ILIKE in v1)

## Threat Flags

None. This plan adds a no-op stub bean (EmbeddingProviderConfig) and test infrastructure. No new network endpoints, auth paths, or schema changes introduced.

## Self-Check: PASSED

Files verified:
- FOUND: search-service/src/main/java/com/n11/search/EmbeddingProviderConfig.java
- FOUND: search-service/src/main/java/com/n11/search/SearchService.java
- FOUND: search-service/src/test/java/com/n11/search/SearchServiceContextTest.java
- FOUND: infra-tests/src/test/java/com/n11/infratests/saga/AiServiceTestConfig.java
- FOUND: infra-tests/src/test/java/com/n11/infratests/ai/AiServiceClasspathTest.java
- FOUND: .planning/phases/08-ai-port-adapter-agent-toolset/08-SMOKE-RUNBOOK.md

Commits verified:
- FOUND: 68d5eaa (Task 1 — search-service + infra-tests)
- FOUND: f06459c (Task 2 — smoke runbook)
