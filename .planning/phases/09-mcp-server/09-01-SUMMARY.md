---
phase: 09-mcp-server
plan: 01
subsystem: ai-integration
tags: [spring-ai, mcp, gradle, spring-boot, config-server]

requires:
  - phase: 08-ai-port-adapter-agent-toolset
    provides: shared agent-toolset module consumed by mcp-server
provides:
  - mcp-server Gradle module registered and compiling
  - Spring AI BOM 1.1.5 imported at the root for MCP starter resolution
  - Streamable HTTP plus stdio MCP transport configuration externalized in config-server
  - McpServerContextTest proving Wave 1 context and transport properties boot green
affects: [phase-09-mcp-server, phase-11-deploy]

tech-stack:
  added: [spring-ai-starter-mcp-server-webmvc, spring-ai-bom-1.1.5, aspectjweaver]
  patterns: [Spring Cloud Config bootstrap split, tools-only MCP capability config, DB-free service autoconfig exclusion]

key-files:
  created:
    - mcp-server/build.gradle.kts
    - mcp-server/src/main/java/com/n11/mcp/McpServerApplication.java
    - mcp-server/src/main/resources/application.yml
    - mcp-server/src/main/resources/logback-spring.xml
    - config-server/src/main/resources/config/mcp-server.yml
    - mcp-server/src/test/java/com/n11/mcp/McpServerContextTest.java
  modified:
    - build.gradle.kts
    - settings.gradle.kts

key-decisions:
  - "mcp-server stays DB-free and AMQP-listener-free; only spring-amqp core plus AspectJ runtime are present to satisfy common-logging class loading."
  - "Spring AI MCP starter version is managed centrally through the root spring-ai-bom:1.1.5 import."

patterns-established:
  - "MCP transport config belongs in config-server, while mcp-server/application.yml remains a minimal config-server bootstrap."
  - "Wave 1 mcp-server context tests inline externalized config values and disable Config/Eureka for offline verification."

requirements-completed: [AI-11, AI-12]

duration: 5 min
completed: 2026-05-01
---

# Phase 09 Plan 01: MCP Server Foundation Summary

**Spring AI MCP WebMVC foundation with root BOM management, a DB-free mcp-server Boot module, and Streamable HTTP + stdio transport config.**

## Performance

- **Duration:** 5 min
- **Started:** 2026-05-01T19:58:34Z
- **Completed:** 2026-05-01T20:03:15Z
- **Tasks:** 4/4 completed
- **Files modified:** 8

## Accomplishments

- Registered `mcp-server` in the Gradle multi-module build and imported `spring-ai-bom:1.1.5` once in the root dependency-management block.
- Created the `mcp-server` Spring Boot + Jib module using `spring-ai-starter-mcp-server-webmvc`, `agent-toolset`, `common-error`, and `common-logging` without JPA, Flyway, Springdoc, PostgreSQL, or Rabbit listener dependencies.
- Externalized MCP runtime settings in `config-server/src/main/resources/config/mcp-server.yml`, including `protocol: STREAMABLE`, `stdio: ${MCP_STDIO_ENABLED:true}`, `/mcp`, tools-only capabilities, and no-DB autoconfig exclusions.
- Added `McpServerContextTest` and verified `./gradlew :mcp-server:compileJava :mcp-server:test --tests "*McpServerContextTest*"` exits green.

## Task Commits

Each task was committed atomically:

1. **Task 1: Add Spring AI BOM and register module** — `6a557bc` (`feat`)
2. **Task 2: Create mcp-server Gradle module and boot skeleton** — `13d6967` (`feat`)
3. **Task 3: Externalize MCP transport + capability config** — `520e3c4` (`feat`)
4. **Task 4: Add McpServerContextTest** — `64f74a5` (`test`)

**Plan metadata:** pending final commit

## Files Created/Modified

- `build.gradle.kts` — Spring AI BOM import added after `org.testcontainers:testcontainers-bom:2.0.5` in the root `subprojects` dependency-management imports block.
- `settings.gradle.kts` — `"mcp-server"` added as the last module include entry.
- `mcp-server/build.gradle.kts` — Boot + Jib module with Spring AI MCP WebMVC starter, shared toolset/common modules, JSON logging runtime, spring-amqp symbol runtime, and AspectJ runtime for common-logging aspect class loading.
- `mcp-server/src/main/java/com/n11/mcp/McpServerApplication.java` — `@SpringBootApplication(scanBasePackages = "com.n11")`, `@EnableDiscoveryClient`, and `@EnableScheduling` entry point.
- `mcp-server/src/main/resources/application.yml` — minimal config-server bootstrap with `spring.application.name=mcp-server`.
- `mcp-server/src/main/resources/logback-spring.xml` — JSON Logback config cloned from ai-service with MDC keys.
- `config-server/src/main/resources/config/mcp-server.yml` — externalized port, MCP API key binding, transport/capability config, Eureka settings, management endpoint surface, and no-DB autoconfig exclusion.
- `mcp-server/src/test/java/com/n11/mcp/McpServerContextTest.java` — Spring context test for STREAMABLE + stdio + `/mcp` + tools-only capability properties.

## Verification

- `./gradlew projects --console=plain` — PASS; lists `Project ':mcp-server'`.
- `./gradlew :mcp-server:compileJava :mcp-server:test --tests "*McpServerContextTest*" --console=plain` — PASS; `BUILD SUCCESSFUL` (scoped context-test runtime observed at ~8s on the successful non-up-to-date run; final cached verification completed in 18s).
- `grep -c 'spring-ai-bom:1.1.5' build.gradle.kts` — PASS; returned `1`.
- `! grep -E 'spring-boot-starter-data-jpa|flyway|springdoc' mcp-server/build.gradle.kts` — PASS; no JPA/Flyway/Springdoc footprint.
- `grep -q 'protocol: STREAMABLE' config-server/src/main/resources/config/mcp-server.yml` and `grep -q 'stdio: ${MCP_STDIO_ENABLED:true}' ...` — PASS.
- `./gradlew :mcp-server:dependencies --configuration runtimeClasspath --console=plain` — PASS; resolved `org.springframework.ai:spring-ai-starter-mcp-server-webmvc -> 1.1.5`.

## Decisions Made

- Followed the plan's DB-free mcp-server posture exactly: no JPA, no Flyway, no PostgreSQL, no Springdoc, and no Spring Rabbit listener infrastructure.
- Added `org.aspectj:aspectjweaver` as a runtime-only dependency because `common-logging` contributes `RabbitListenerCorrelationAspect` and the Spring context must be able to load AspectJ types even though mcp-server itself has no AMQP listeners.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added AspectJ runtime for common-logging aspect class loading**
- **Found during:** Task 4 (McpServerContextTest)
- **Issue:** The first context test failed with `NoClassDefFoundError: org/aspectj/lang/ProceedingJoinPoint` while creating `rabbitListenerCorrelationAspect` from `common-logging`.
- **Fix:** Added `runtimeOnly("org.aspectj:aspectjweaver")` to `mcp-server/build.gradle.kts`, preserving the no-AMQP-listener/no-DB posture.
- **Files modified:** `mcp-server/build.gradle.kts`
- **Verification:** Re-ran `./gradlew :mcp-server:compileJava :mcp-server:test --tests "*McpServerContextTest*" --console=plain`; build succeeded.
- **Committed in:** `64f74a5`

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Necessary classpath completion for the planned `common-logging` dependency; no scope creep and no forbidden service footprint added.

## Issues Encountered

- Initial `McpServerContextTest` context boot failed due to the missing AspectJ runtime described above. Fixed and verified in the task commit.

## User Setup Required

None - no external service configuration required for this foundation plan. Later Phase 9 plans will wire `MCP_API_KEY` into `.env`/compose.

## Known Stubs

None. The only matched word "placeholder" is a test comment explaining future compatibility for `mcp.api-key`; it does not feed UI or runtime behavior.

## Threat Flags

None. This plan introduces build/module/config surface only; no new network endpoint handler, auth path, file access pattern, or database trust boundary is implemented yet.

## Next Phase Readiness

- Ready for Phase 9 Plan 02 (`identity-service` agent API-key exchange) and later Plans 09-03/09-04, which can now depend on the compiled `mcp-server` module and the Spring AI MCP starter resolving from the root BOM.
- Reminder: edits to `config-server/src/main/resources/config/mcp-server.yml` require rebuilding the config-server Jib image before docker-compose smoke runs.

## Self-Check: PASSED

- Verified created files exist: `mcp-server/build.gradle.kts`, `McpServerApplication.java`, `application.yml`, `logback-spring.xml`, `config-server` `mcp-server.yml`, `McpServerContextTest.java`, and this summary.
- Verified task commits exist in git history: `6a557bc`, `13d6967`, `520e3c4`, `64f74a5`.

---
*Phase: 09-mcp-server*
*Completed: 2026-05-01*
