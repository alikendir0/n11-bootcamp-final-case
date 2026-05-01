---
phase: 08-ai-port-adapter-agent-toolset
plan: 01
subsystem: ai
tags: [java21, spring-boot, ai-port, agent-toolset, flyway, pgvector, spring-cloud-gateway, sse, gemini, grading-critical]

# Dependency graph
requires:
  - phase: 07-payment-notification
    provides: common-error, common-logging, docker-compose base, config-server native profile

provides:
  - "ai-port: zero-dependency ChatProvider/EmbeddingProvider ports + 6 neutral DTOs (no Gemini SDK types)"
  - "agent-toolset: AgentTool/ToolContext/ToolResult/ToolRegistry interfaces consuming ai-port DTOs only"
  - "ai-service Spring Boot app on port 8088 with Flyway V1 (ai_conversations, messages tables)"
  - "search-service Spring Boot skeleton on port 8089 with Flyway V1 (product_embeddings vector(768))"
  - "AiPortContractTest: Wave 0 gate verifying zero com.google.genai artifacts on ai-port classpath"
  - "Gateway SSE route /api/v1/chat/stream/** with response-timeout=-1; sync route /api/v1/chat/**"
  - "config-server YAMLs for ai-service and search-service"

affects:
  - "08-02 (GeminiChatAdapter/GeminiEmbeddingAdapter — implements ai-port ports)"
  - "08-03 (agent tools — implements AgentTool interface from agent-toolset)"
  - "08-04 (ChatOrchestrator — uses ai-port + agent-toolset)"
  - "08-05 (ChatController SSE — connects to gateway SSE route)"
  - "09 (mcp-server — imports agent-toolset for MCP tool definitions)"

# Tech tracking
tech-stack:
  added:
    - "google-genai:1.52.0 (runtimeOnly ai-service only — Pitfall #7 guard)"
    - "pgvector vector(768) column in product_embeddings (search-service)"
    - "jackson-module-kotlin:runtimeOnly (google-genai KotlinModule service file workaround)"
    - "spring-amqp:runtimeOnly (CorrelationIdMessagePostProcessor class loading fix)"
  patterns:
    - "Java 21 sealed interface for ToolResult (Ok/Err variants) — SOLID open/closed"
    - "java-library plugin for zero-dep modules (ai-port, agent-toolset)"
    - "Flyway schema-scoped migrations: classpath:db/migration/<schema>/V1__init_<schema>.sql"
    - "scanBasePackages=com.n11 + spring.autoconfigure.exclude for selective AMQP exclusion"
    - "SSE gateway route: metadata.response-timeout=-1, no ModifyResponseBody/RetryFilter"

key-files:
  created:
    - "ai-port/build.gradle.kts"
    - "ai-port/src/main/java/com/n11/ai/port/ChatProvider.java"
    - "ai-port/src/main/java/com/n11/ai/port/EmbeddingProvider.java"
    - "ai-port/src/main/java/com/n11/ai/port/dto/MessageRole.java"
    - "ai-port/src/main/java/com/n11/ai/port/dto/ChatMessage.java"
    - "ai-port/src/main/java/com/n11/ai/port/dto/ChatResponse.java"
    - "ai-port/src/main/java/com/n11/ai/port/dto/ToolSchema.java"
    - "ai-port/src/main/java/com/n11/ai/port/dto/ToolCallRequest.java"
    - "ai-port/src/main/java/com/n11/ai/port/dto/ToolCallResult.java"
    - "ai-port/src/test/java/com/n11/ai/port/AiPortContractTest.java"
    - "agent-toolset/build.gradle.kts"
    - "agent-toolset/src/main/java/com/n11/agent/AgentTool.java"
    - "agent-toolset/src/main/java/com/n11/agent/ToolContext.java"
    - "agent-toolset/src/main/java/com/n11/agent/ToolResult.java"
    - "agent-toolset/src/main/java/com/n11/agent/ToolRegistry.java"
    - "ai-service/build.gradle.kts"
    - "ai-service/src/main/java/com/n11/ai/AiServiceApplication.java"
    - "ai-service/src/main/resources/application.yml"
    - "ai-service/src/main/resources/db/migration/ai/V1__init_ai.sql"
    - "ai-service/src/main/resources/logback-spring.xml"
    - "search-service/build.gradle.kts"
    - "search-service/src/main/java/com/n11/search/SearchServiceApplication.java"
    - "search-service/src/main/resources/application.yml"
    - "search-service/src/main/resources/db/migration/search/V1__init_search.sql"
    - "search-service/src/main/resources/logback-spring.xml"
    - "config-server/src/main/resources/config/ai-service.yml"
    - "config-server/src/main/resources/config/search-service.yml"
  modified:
    - "settings.gradle.kts (4 new module includes)"
    - ".env.example (uncommented GEMINI_API_KEY)"
    - "docker-compose.yml (ai-service + search-service service blocks)"
    - "config-server/src/main/resources/config/api-gateway.yml (SSE + sync chat routes, Springdoc entry)"
    - ".gitignore (exclude /META-INF/ stray from google-genai jar)"

key-decisions:
  - "D-01: google-genai SDK ONLY in ai-service; ai-port zero-dep enforced by AiPortContractTest"
  - "D-09: search-service is a skeleton with no REST endpoints in v1 (no springdoc, no common-logging)"
  - "AMQP pattern: services without RabbitMQ use spring-amqp runtimeOnly (not spring-rabbit) + autoconfigure.exclude to satisfy CorrelationIdMessagePostProcessor class loading without triggering RabbitHealthIndicator"
  - "google-genai 1.52.0 Kotlin module workaround: add jackson-module-kotlin runtimeOnly (SDK registers KotlinModule via service file but does not include the JAR)"
  - "SSE gateway rule: response-timeout=-1 + no ModifyResponseBody/RetryFilter on /api/v1/chat/stream/**"

patterns-established:
  - "Port isolation: ai-port as java-library with zero runtime deps; verified by AiPortContractTest scanning classpath for com.google.genai"
  - "Non-AMQP services that scan com.n11.* namespace must add spring-amqp runtimeOnly + exclude RabbitAutoConfiguration"
  - "SSE routes in Spring Cloud Gateway 2025.0 use metadata.response-timeout=-1"

requirements-completed: [AI-01, AI-14]

# Metrics
duration: 29min
completed: 2026-05-01
---

# Phase 08 Plan 01: Module Bootstrap + Port Foundation Summary

**Zero-dependency ChatProvider/EmbeddingProvider port module + agent-toolset interfaces + ai-service/search-service Spring Boot apps booting healthy in docker-compose with SSE gateway route active**

## Performance

- **Duration:** 29 min
- **Started:** 2026-05-01T02:32:31+03:00
- **Completed:** 2026-05-01T03:01:03+03:00
- **Tasks:** 2
- **Files modified:** 32

## Accomplishments

- Built `ai-port` Gradle module with strictly zero Gemini SDK dependencies: `ChatProvider` (chat + chatStream), `EmbeddingProvider` (embed), and 6 neutral record DTOs (`ChatMessage`, `ChatResponse`, `ToolSchema`, `ToolCallRequest`, `ToolCallResult`, `MessageRole`). AiPortContractTest verifies no `com.google.genai` artifacts on classpath — SOLID grading gate.
- Built `agent-toolset` Gradle module with `AgentTool` interface (10 tools to implement in Plan 08-03), `ToolContext` record, sealed `ToolResult` (Ok/Err), and `ToolRegistry` Spring component — all referencing ai-port DTOs only, no Gemini types.
- ai-service and search-service boot to UP/healthy in docker-compose: Flyway V1 migrations applied (`ai_conversations`, `messages` tables in `ai` schema; `product_embeddings` with `vector(768)` in `search` schema). Gateway SSE route (`/api/v1/chat/stream/**`, `response-timeout: -1`) and sync route (`/api/v1/chat/**`) confirmed active via `/actuator/gateway/routes`.

## Task Commits

Each task was committed atomically:

1. **Task 1: Wire modules + ai-port + agent-toolset** - `3cc2fb0` (feat)
2. **Task 2: Bootstrap ai-service + search-service** - `0bf071d` (feat)

## Files Created/Modified

- `ai-port/build.gradle.kts` - Zero-dependency java-library; JUnit test only
- `ai-port/src/main/java/com/n11/ai/port/ChatProvider.java` - Port interface, neutral DTOs only
- `ai-port/src/main/java/com/n11/ai/port/EmbeddingProvider.java` - Port interface returning float[]
- `ai-port/src/main/java/com/n11/ai/port/dto/*.java` - 6 neutral records (MessageRole, ChatMessage, ChatResponse, ToolSchema, ToolCallRequest, ToolCallResult)
- `ai-port/src/test/java/com/n11/ai/port/AiPortContractTest.java` - Wave 0 gate test for classpath cleanliness
- `agent-toolset/build.gradle.kts` - java-library importing ai-port + jackson-databind + spring-context
- `agent-toolset/src/main/java/com/n11/agent/AgentTool.java` - Tool interface with name/descriptionTr/requiresAuth/parametersJsonSchema/execute
- `agent-toolset/src/main/java/com/n11/agent/ToolContext.java` - userId, correlationId, seenIds
- `agent-toolset/src/main/java/com/n11/agent/ToolResult.java` - Sealed Ok(JsonNode)/Err(code, message)
- `agent-toolset/src/main/java/com/n11/agent/ToolRegistry.java` - @Component aggregating AgentTool beans
- `ai-service/build.gradle.kts` - Spring Boot + Jib + google-genai:1.52.0, ai-port, agent-toolset
- `ai-service/src/main/java/com/n11/ai/AiServiceApplication.java` - @SpringBootApplication(scanBasePackages="com.n11")
- `ai-service/src/main/resources/db/migration/ai/V1__init_ai.sql` - ai_conversations + messages tables
- `search-service/build.gradle.kts` - Spring Boot + Jib + ai-port; no springdoc/common-logging/amqp
- `search-service/src/main/resources/db/migration/search/V1__init_search.sql` - product_embeddings vector(768)
- `config-server/src/main/resources/config/ai-service.yml` - Port 8088, hikari pool=3, gemini provider, RabbitAutoConfiguration excluded
- `config-server/src/main/resources/config/search-service.yml` - Port 8089, hikari pool=2, minimal config
- `config-server/src/main/resources/config/api-gateway.yml` - SSE route + sync route + ai-service Springdoc entry
- `settings.gradle.kts` - Added ai-port, agent-toolset, ai-service, search-service
- `docker-compose.yml` - ai-service (port 8088) and search-service (port 8089) service blocks

## Decisions Made

- `D-01` enforced: google-genai 1.52.0 lives ONLY in `ai-service/build.gradle.kts`. AiPortContractTest fails if any genai jar appears on ai-port's classpath.
- `D-09` enforced: search-service has no REST endpoints in v1. Removed `common-logging` from its classpath (the module's `CorrelationIdMessagePostProcessor` implements spring-amqp's `MessagePostProcessor` interface, which is not present in search-service's classpath).
- SSE rule enforced per api-contracts.md §6: `metadata.response-timeout: -1` on `ai-service-chat-stream` route; `PreserveHostHeader=true`; no retry or response-body filters.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] google-genai 1.52.0 KotlinModule service file triggers ObjectMapper failure**
- **Found during:** Task 2 (ai-service boot)
- **Issue:** `google-genai:1.52.0` ships `META-INF/services/com.fasterxml.jackson.databind.Module` registering `KotlinModule`, but does not include `jackson-module-kotlin` JAR. LogstashEncoder calls `ObjectMapper.findAndRegisterModules()` which triggers `ServiceConfigurationError: Provider not found`.
- **Fix:** Added `runtimeOnly("com.fasterxml.jackson.module:jackson-module-kotlin")` to `ai-service/build.gradle.kts`.
- **Files modified:** `ai-service/build.gradle.kts`
- **Verification:** ai-service starts clean with no ServiceConfigurationError.
- **Committed in:** `0bf071d` (Task 2)

**2. [Rule 1 - Bug] spring-amqp runtimeOnly triggers RabbitHealthIndicator when using spring-rabbit**
- **Found during:** Task 2 (ai-service boot, second iteration)
- **Issue:** An initial fix attempt used `spring-rabbit` to satisfy `MessagePostProcessor` class loading. `spring-rabbit` includes `RabbitAutoConfiguration` which auto-registers `RabbitHealthIndicator`, causing connection refused errors since ai-service has no RabbitMQ.
- **Fix:** Downgraded to `spring-amqp` (core only, no auto-config). Added `spring.autoconfigure.exclude: RabbitAutoConfiguration` in `config-server/src/main/resources/config/ai-service.yml` as belt-and-braces guard.
- **Files modified:** `ai-service/build.gradle.kts`, `config-server/src/main/resources/config/ai-service.yml`
- **Verification:** ai-service starts healthy; `/actuator/health` shows UP without RabbitMQ.
- **Committed in:** `0bf071d` (Task 2)

**3. [Rule 1 - Bug] search-service fails to start due to missing MessagePostProcessor class**
- **Found during:** Task 2 (search-service boot)
- **Issue:** `common-logging` module's `CorrelationIdMessagePostProcessor implements MessagePostProcessor`. When search-service uses `scanBasePackages="com.n11"`, Spring's ConfigurationClassParser fails with `FileNotFoundException: org/springframework/amqp/core/MessagePostProcessor.class` because search-service has no AMQP dependency.
- **Fix:** Removed `common-logging` from `search-service/build.gradle.kts`. search-service is a skeleton with no REST calls, so correlation ID propagation is not needed.
- **Files modified:** `search-service/build.gradle.kts`
- **Verification:** search-service starts healthy.
- **Committed in:** `0bf071d` (Task 2)

**4. [Rule 1 - Bug] /META-INF/ stray directory left untracked by google-genai service file extraction**
- **Found during:** Post-Task 2 cleanup
- **Issue:** During Gradle build, google-genai JAR extraction created a top-level `/META-INF/services/` directory in the worktree. This appeared as an untracked path in `git status`.
- **Fix:** Added `/META-INF/` to `.gitignore` with explanatory comment.
- **Files modified:** `.gitignore`
- **Committed in:** `0bf071d` (Task 2)

---

**Total deviations:** 4 auto-fixed (4x Rule 1 - runtime startup bugs)
**Impact on plan:** All fixes were necessary for the services to boot. No scope creep — all fixes directly caused by google-genai 1.52.0 classpath interactions and the com.n11 scan-base pattern established in prior phases.

## Issues Encountered

- `docker port n11-api-gateway` showed external port 8088 (not 8080) — AMP panel already occupies 8080 on the host machine. Gateway actuator confirmed via `http://localhost:8088/actuator/gateway/routes`. This is a host-environment quirk, not a project issue.

## User Setup Required

None — no external service configuration required for this plan. `GEMINI_API_KEY` is already documented in `.env.example`; actual key injection happens when Plan 08-02 (GeminiChatAdapter) is wired and tested.

## Next Phase Readiness

- Plan 08-02 (GeminiChatAdapter + GeminiEmbeddingAdapter) can proceed immediately — ai-port ports are defined, ai-service boots, google-genai SDK is on the classpath.
- Plan 08-03 (agent tool implementations) can proceed immediately — AgentTool interface + ToolRegistry defined in agent-toolset.
- Plans 08-02 and 08-03 are independent (wave 1 parallelization).
- Blocker for Plan 08-04: needs both 08-02 and 08-03 complete.

---
*Phase: 08-ai-port-adapter-agent-toolset*
*Completed: 2026-05-01*

## Self-Check: PASSED

- All 17 key files exist on disk
- Task 1 commit `3cc2fb0` found in git log
- Task 2 commit `0bf071d` found in git log
- n11-ai-service: healthy (Up 7 min)
- n11-search-service: healthy (Up 12 min)
