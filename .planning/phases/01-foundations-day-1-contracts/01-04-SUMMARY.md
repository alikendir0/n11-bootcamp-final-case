---
phase: 01-foundations-day-1-contracts
plan: 04
subsystem: shared-libs
tags: [java-library, spring-boot-3, autoconfiguration-imports, rfc-7807, problem-detail, correlation-id, mdc, slf4j, spring-amqp, rabbittemplate-bpp, networknt-3, json-schema-2020-12, jackson, junit-platform-launcher]

requires:
  - phase: 01-01
    provides: "Gradle multi-module skeleton (settings.gradle.kts includes common-error/common-logging/common-events as flat top-level subprojects per D-12); root build.gradle.kts subprojects { } block imports Spring Boot + Spring Cloud + Testcontainers BOMs (so submodules can declare un-versioned spring-boot-starter coords); gradle/libs.versions.toml version catalog with `networknt-json-schema=3.0.2` and `logstash-logback=8.0` pinned"
  - phase: 01-02
    provides: ".planning/saga-contracts/*.schema.json (envelope + 8 events, 9 files total) — single source of truth, copied verbatim into common-events JAR resource path; .planning/api-contracts.md §7 RFC-7807 spec with the 7 locked fields (type, title, status, detail, instance, correlationId, errors[])"
provides:
  - "common-error library JAR — RFC-7807 ProblemDetailControllerAdvice + ApiErrorCode enum (5 locked codes mapping to https://n11clone/errors/<suffix>); 1 unit test (MockMvc) asserts the 7-field shape end-to-end including correlationId from MDC"
  - "common-logging library JAR — 5 correlation-ID propagation wires per RESEARCH §4.9: CorrelationIdFilter (inbound HTTP, OncePerRequestFilter HIGHEST_PRECEDENCE) + CorrelationIdRestClientInterceptor + RestClientConfig (outbound HTTP) + CorrelationIdMessagePostProcessor + RabbitTemplateConfig BPP (outbound AMQP) + RabbitListenerCorrelationAspect skeleton (inbound AMQP, Phase 5+ activates). Spring Boot 3 AutoConfiguration.imports lists RestClientConfig + RabbitTemplateConfig; explicit @Import chains pull the @Component helpers in (consumers do NOT need @ComponentScan over com.n11.logging)"
  - "common-events library JAR — Envelope record (8 fields per ARCHITECTURE.md §3.4, Jackson 2.x JsonNode payload), RabbitRetryConfig (StatefulRetryOperationsInterceptor with maxAttempts(3) + backOffOptions(1000L, 5.0, 30000L) + RejectAndDontRequeueRecoverer + Cross-Cutting #8 wording lock in Javadoc), AbstractEventSchemaTest (D-08 drift gate base class — networknt 3.0.2 SchemaRegistry with classpath-only ResourceLoader for T-01-06), 9 saga schemas shipped under /saga-schemas/ on the classpath inside the JAR, 4 self-tests passing (1 envelope + 3 security check)"
affects: [01-05, 01-06, 01-07, 01-08, all-Phase-3-onward]

tech-stack:
  added:
    - "Spring Boot 3 AutoConfiguration.imports (replaces deprecated spring.factories)"
    - "BeanPostProcessor pattern for augmenting auto-configured RabbitTemplate (avoids NoUniqueBeanDefinitionException)"
    - "networknt json-schema-validator 3.0.2 (Jackson 3.x internally — String-based validate API used to sidestep classpath split)"
    - "Spring AOP @Around aspect skeleton (RabbitListenerCorrelationAspect — Phase 5+ activates)"
    - "JUnit Platform launcher pinned at testRuntimeOnly to align Gradle 8.10 with Spring Boot BOM's junit-platform-engine"
  patterns:
    - "java-library plugin only (no Boot plugin) for shared library modules per Cross-Cutting #1 / D-11. Each module ships as a plain JAR consumed via `implementation(project(\":common-X\"))`."
    - "Self-contained @Configuration auto-config: top-level @Configuration registered in AutoConfiguration.imports, with explicit @Import chains for @Component helpers — consumers outside `com.n11.X` package do NOT need @ComponentScan over the library namespace. Each library has a single AutoConfiguration.imports entry-point set."
    - "RabbitTemplate augmentation via BeanPostProcessor.postProcessAfterInitialization: find the auto-configured bean and call `template.addBeforePublishPostProcessors(...)` (additive, not replace). NEVER register a second @Primary RabbitTemplate."
    - "Classpath-only schema loading for the saga drift gate: schemas ship inside the JAR under /saga-schemas/, loaded via `getResourceAsStream(\"/saga-schemas/\" + filename)`, never via java.io.File or Path.of (works in Jib-built containers because resource entries don't need to be on the host filesystem)."
    - "RFC-7807 anti-leak rule for the generic 500 handler: `detail = \"An unexpected error occurred\"` — never echo the raw exception message (T-01-04). Full exception logged at the consumer service via SLF4J keyed by the same correlationId."

key-files:
  created:
    - common-error/src/main/java/com/n11/error/ApiErrorCode.java
    - common-error/src/main/java/com/n11/error/ProblemDetailControllerAdvice.java
    - common-error/src/test/java/com/n11/error/ProblemDetailControllerAdviceTest.java
    - common-logging/src/main/java/com/n11/logging/CorrelationIdFilter.java
    - common-logging/src/main/java/com/n11/logging/CorrelationIdRestClientInterceptor.java
    - common-logging/src/main/java/com/n11/logging/RestClientConfig.java
    - common-logging/src/main/java/com/n11/logging/CorrelationIdMessagePostProcessor.java
    - common-logging/src/main/java/com/n11/logging/RabbitTemplateConfig.java
    - common-logging/src/main/java/com/n11/logging/RabbitListenerCorrelationAspect.java
    - common-logging/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
    - common-events/src/main/java/com/n11/events/Envelope.java
    - common-events/src/main/java/com/n11/events/RabbitRetryConfig.java
    - common-events/src/test/java/com/n11/events/AbstractEventSchemaTest.java
    - common-events/src/test/java/com/n11/events/ClasspathOnlySchemaLoader.java
    - common-events/src/test/java/com/n11/events/RemoteRefRefusedException.java
    - common-events/src/test/java/com/n11/events/AbstractEventSchemaTestSecurityCheck.java
    - common-events/src/test/java/com/n11/events/EnvelopeSchemaSelfTest.java
    - common-events/src/main/resources/saga-schemas/envelope.schema.json
    - common-events/src/main/resources/saga-schemas/order-created.schema.json
    - common-events/src/main/resources/saga-schemas/stock-reserved.schema.json
    - common-events/src/main/resources/saga-schemas/stock-reserve-failed.schema.json
    - common-events/src/main/resources/saga-schemas/payment-completed.schema.json
    - common-events/src/main/resources/saga-schemas/payment-failed.schema.json
    - common-events/src/main/resources/saga-schemas/order-confirmed.schema.json
    - common-events/src/main/resources/saga-schemas/order-cancelled.schema.json
    - common-events/src/main/resources/saga-schemas/stock-released.schema.json
  modified:
    - common-error/build.gradle.kts (replaced stub `plugins { java }` with full java-library config)
    - common-logging/build.gradle.kts (replaced stub)
    - common-events/build.gradle.kts (replaced stub)

key-decisions:
  - "networknt 3.0.2 API adapted in test code, not downgraded. Plan 01-04 was drafted against networknt 2.x classnames (JsonSchemaFactory, SpecVersion, SchemaValidatorsConfig, ValidationMessage); the 3.0.2 release renamed these to SchemaRegistry, SpecificationVersion, SchemaRegistryConfig, Error and migrated to Jackson 3.x (tools.jackson.databind.JsonNode). Plan 01-01's version catalog locked 3.0.2 — adapting test code to the 3.0.2 API is cheaper than churning the catalog. The public `assertEventValid` signature accepts a JSON String (not JsonNode) so producers serialize via their Jackson 2.x mapper and pass the resulting string straight in — sidesteps the Jackson 2/3 classpath split entirely. Tracked as Rule 1 deviation."
  - "StatefulRetryOperationsInterceptor imported from org.springframework.retry.interceptor (not org.springframework.amqp.rabbit.config). Spring AMQP 3.2.x removed the spring-rabbit shadow class; only RetryInterceptorBuilder remains in org.springframework.amqp.rabbit.config. Plan 01-04 Step 3 import path was stale — fixed inline. Tracked as Rule 1 deviation."
  - "JUnit platform launcher pinned at testRuntimeOnly. Gradle 8.10 ships an older junit-platform-launcher than the junit-platform-engine pulled by the Spring Boot 3.5.14 BOM (\"OutputDirectoryProvider not available; probably due to unaligned versions\"). Adding `testRuntimeOnly(\"org.junit.platform:junit-platform-launcher\")` to all three modules' build.gradle.kts lets the BOM resolve a matching launcher version. Tracked as Rule 3 deviation."
  - "ClasspathOnlySchemaLoader implements ResourceLoader (not SchemaLoader). networknt 3.0.2's SchemaLoader is a concrete class, not an interface; the corresponding interface for resource resolution is ResourceLoader. The behaviour of the T-01-06 control is unchanged — every non-classpath URI throws RemoteRefRefusedException — but the interface name shifted in 3.0.2."
  - "Defense-in-depth on remote-ref blocking: SchemaRegistry.builder().schemaLoader(...).fetchRemoteResources(false) AND custom ResourceLoader. networknt 3.0.2 exposes `fetchRemoteResources(false)` as a first-class flag (the default), making the structural T-01-06 control belt-and-braces. The companion AbstractEventSchemaTestSecurityCheck self-test asserts the ResourceLoader fires for https://, http://, and file:// URIs — proves the control is real, not documentation-only."
  - "Wording lock for RabbitRetryConfig.java Javadoc preserved verbatim on a single line for the regex acceptance criterion: \"3 total attempts (= 1 initial + 2 retries). Delays between attempts: 1s, then 5s. After the 3rd attempt fails, the message goes to DLQ. The 30s upper bound is a safety cap on the exponential growth of the backoff (multiplier=5, max=30000ms), not a delay between attempts 3 and 4 — there is no attempt 4.\" Cross-Cutting #8 honored in both code and comment."
  - "Version-catalog references resolved cleanly. `libs.networknt.json.schema` and `libs.logstash.logback` both bound from gradle/libs.versions.toml without falling back to plain coordinates — CD-03 catalog-drift signal stays green for Plan 01-04."

patterns-established:
  - "Library module Gradle template (Cross-Cutting #1): `plugins { \\\"java-library\\\" }` only, NO Boot plugin. compileOnly for Spring/Jakarta APIs (consumers' starters provide them at runtime), api for cross-cutting deps (jackson-databind, slf4j-api, spring-boot-autoconfigure), testImplementation + testRuntimeOnly junit-platform-launcher for the Gradle 8.10 / Boot BOM mismatch."
  - "AutoConfiguration self-containment via @Import: each top-level @Configuration uses `@Import({ Helper1.class, Helper2.class })` to pull @Component helpers in explicitly. Two-line AutoConfiguration.imports + @Import chains = consumers add `implementation(project(\":common-X\"))` and get all wires registered without ANY component-scan setup. Avoids the Phase-5+ landmine of \"my custom service in com.n11.cart doesn't see the propagation filter\"."
  - "RabbitTemplate augmentation via BeanPostProcessor (RESEARCH BLOCKER #1 fix): `postProcessAfterInitialization(bean, name)` checks `bean instanceof RabbitTemplate template` and calls `template.addBeforePublishPostProcessors(cidpp)`. Additive — preserves any other Boot defaults (transaction-aware connection wrapping, default exchange/routing-key, etc.). NEVER use `setBeforePublishPostProcessors` (replace, drops anything else) and NEVER register a second @Primary RabbitTemplate (loses Boot's auto-config wiring entirely)."
  - "Classpath-only schema loader for D-08 drift gate (and T-01-06 mitigation): SchemaRegistry.builder().schemaLoader(b -> b.fetchRemoteResources(false).resourceLoaders(loaders -> { loaders.values(list -> list.clear()); loaders.add(new ClasspathOnlySchemaLoader()); })). Phase 5+ saga producer integration tests extend AbstractEventSchemaTest and call assertEventValid(\"order-created.schema.json\", producedJsonString)."
  - "Sealed enum + URI helper for RFC-7807 codes: ApiErrorCode.VALIDATION.typeUri() returns URI.create(\"https://n11clone/errors/validation\"). 5 enum values are exhaustive — every error response across all services maps to exactly one. Switch-mapping HttpStatus → ApiErrorCode is a single static method on the advice; default arm is INTERNAL."

requirements-completed:
  - QUAL-06
  - QUAL-07

duration: ~17 min
completed: 2026-04-28
---

# Phase 1 Plan 04: common-error / common-logging / common-events Shared Libraries Summary

**Three java-library JARs locking the cross-cutting RFC-7807 ControllerAdvice (QUAL-07), 5 correlation-ID propagation wires registered via Spring Boot 3 AutoConfiguration.imports (QUAL-06), and the saga Envelope record + classpath-only schema-validation drift gate (D-08) — every Phase 3+ Boot app picks up all three concerns by adding three `implementation(project(":common-X"))` lines.**

## Performance

- **Duration:** ~17m 5s (1025s wall-clock)
- **Started:** 2026-04-28T19:13:05Z
- **Completed:** 2026-04-28T19:30:11Z
- **Tasks:** 3 (1 atomic commit per task)
- **Files created:** 27 (11 source + 4 test + 1 imports + 9 schemas + 2 deviation-driven helpers `ClasspathOnlySchemaLoader.java` and `RemoteRefRefusedException.java`)
- **Files modified:** 3 (each module's stub `build.gradle.kts` replaced with the full java-library config)

## Accomplishments

- **common-error JAR**: RFC-7807 cross-cutting `ProblemDetailControllerAdvice` shipping all 7 locked fields (type, title, status, detail, instance, correlationId, errors[]) with the anti-leak rule structurally enforced (no `ex.getMessage()` in the generic 500 handler — verified via grep). 1 unit test (MockMvc + `@SpringBootTest`) round-trips a `ResponseStatusException(400)` and asserts every field including correlationId from MDC.
- **common-logging JAR**: 5 correlation-ID propagation wires (inbound HTTP servlet filter, outbound HTTP RestClient interceptor, outbound AMQP MessagePostProcessor, inbound AMQP @RabbitListener aspect skeleton, plus the BeanPostProcessor that augments Spring Boot's auto-configured `RabbitTemplate`). Two-line Spring Boot 3 `AutoConfiguration.imports` + explicit `@Import` chains on the two `@Configuration` classes = consumers' `@SpringBootApplication` picks up everything with zero `@ComponentScan` configuration over `com.n11.logging`.
- **common-events JAR**: 8-field `Envelope` record (Jackson 2.x `JsonNode` payload), `RabbitRetryConfig` with the locked retry policy (`maxAttempts(3)` + `backOffOptions(1000L, 5.0, 30000L)` + `RejectAndDontRequeueRecoverer`), and the D-08 schema-validation drift gate (`AbstractEventSchemaTest` base + `ClasspathOnlySchemaLoader` for T-01-06). All 9 saga schemas (envelope + 8 events) ship inside the JAR under `/saga-schemas/` — verified via `unzip -l common-events-0.0.1-SNAPSHOT.jar | grep saga-schemas` (10 entries: 9 files + 1 directory). 4 self-tests pass (1 envelope schema + 3 security-control assertions for https://, http://, file:// rejection).
- **All three modules pass `./gradlew :common-error:build :common-logging:build :common-events:build` clean** in a single Gradle invocation. None applies the `org.springframework.boot` plugin (Cross-Cutting #1 / D-11 verified via grep).

## Task Commits

Each task was committed atomically:

1. **Task 1: Build common-error** — `c5f60d8` (feat) — ApiErrorCode enum + ProblemDetailControllerAdvice + 1 unit test
2. **Task 2: Build common-logging** — `a20f825` (feat) — 5 correlation wires + AutoConfiguration.imports
3. **Task 3: Build common-events** — `4125832` (feat) — Envelope + RabbitRetryConfig + 9 schemas on classpath + AbstractEventSchemaTest with networknt 3.0.2 API + EnvelopeSchemaSelfTest + AbstractEventSchemaTestSecurityCheck

**Plan metadata commit:** to follow (after this SUMMARY.md write).

## Files Created/Modified

### common-error/

- `common-error/build.gradle.kts` — java-library plugin; compileOnly Spring Web + Servlet API + slf4j-api; testImplementation Spring Boot Test + JUnit 5; **testRuntimeOnly junit-platform-launcher** (Rule-3 fix).
- `common-error/src/main/java/com/n11/error/ApiErrorCode.java` — 5-value enum (VALIDATION, NOT_FOUND, CONFLICT, UNAUTHORIZED, INTERNAL) mapping to https://n11clone/errors/<suffix> URIs.
- `common-error/src/main/java/com/n11/error/ProblemDetailControllerAdvice.java` — `@RestControllerAdvice` with three handlers: `MethodArgumentNotValidException` (validation), `ResponseStatusException` (mapped via HttpStatus switch), `Exception.class` (generic 500 — sanitized "An unexpected error occurred", anti-leak rule).
- `common-error/src/test/java/com/n11/error/ProblemDetailControllerAdviceTest.java` — `@SpringBootTest` + `@AutoConfigureMockMvc` slice with stub `ThrowingController` and explicit `MDC.put` in `@BeforeEach`. Asserts `application/problem+json` content-type + 7-field shape end-to-end.

### common-logging/

- `common-logging/build.gradle.kts` — java-library plugin; api Spring context + Spring Boot autoconfigure + slf4j-api; compileOnly Spring Web + Servlet + Spring AMQP + Spring AOP + AspectJ weaver; **testRuntimeOnly junit-platform-launcher**.
- `common-logging/src/main/java/com/n11/logging/CorrelationIdFilter.java` — Wire #1 (`OncePerRequestFilter`, `@Order(HIGHEST_PRECEDENCE)`).
- `common-logging/src/main/java/com/n11/logging/CorrelationIdRestClientInterceptor.java` — Wire #2A.
- `common-logging/src/main/java/com/n11/logging/RestClientConfig.java` — Wire #2B (`@Configuration` + `@Import({ CorrelationIdRestClientInterceptor.class, CorrelationIdFilter.class })`).
- `common-logging/src/main/java/com/n11/logging/CorrelationIdMessagePostProcessor.java` — Wire #3A (sets AMQP `correlation_id` property + `X-Correlation-Id` header).
- `common-logging/src/main/java/com/n11/logging/RabbitTemplateConfig.java` — Wire #3B (BeanPostProcessor pattern; `@ConditionalOnClass(RabbitTemplate.class)`; `@Import({ CorrelationIdMessagePostProcessor.class, RabbitListenerCorrelationAspect.class })`).
- `common-logging/src/main/java/com/n11/logging/RabbitListenerCorrelationAspect.java` — Wire #4 (`@Around("@annotation(...RabbitListener)")` skeleton; functional Phase 1 — extracts correlation from `Message` arg into MDC).
- `common-logging/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` — 2 lines: `com.n11.logging.RestClientConfig` + `com.n11.logging.RabbitTemplateConfig`.

### common-events/

- `common-events/build.gradle.kts` — java-library plugin; api jackson-databind; compileOnly Spring AMQP + Spring Retry + Spring Boot autoconfigure; testImplementation `libs.networknt.json.schema` (3.0.2) + AssertJ + Spring AMQP + Spring Retry; **testRuntimeOnly junit-platform-launcher**; `sourceSets.test.resources.srcDirs("src/main/resources", "src/test/resources")` so the 9 schemas land on the test classpath under `/saga-schemas/`.
- `common-events/src/main/java/com/n11/events/Envelope.java` — Java record (8 fields per ARCHITECTURE.md §3.4): `eventId`, `eventType`, `eventVersion`, `occurredAt`, `correlationId`, `causationId`, `producer`, `payload` (Jackson 2.x `JsonNode`).
- `common-events/src/main/java/com/n11/events/RabbitRetryConfig.java` — `@Configuration` with two `@Bean`s: `sagaRetryInterceptor` (`StatefulRetryOperationsInterceptor`, attempts 3, backoff [1s, 5s] cap 30s, `RejectAndDontRequeueRecoverer`, `messageKeyGenerator` enforces non-null `messageId`) and `rabbitListenerContainerFactory` (`SimpleRabbitListenerContainerFactory` with manual ack + interceptor advice chain).
- `common-events/src/test/java/com/n11/events/AbstractEventSchemaTest.java` — D-08 drift gate base. networknt 3.0.2 `SchemaRegistry.builder().schemaLoader(b -> b.fetchRemoteResources(false).resourceLoaders(loaders -> { loaders.values(list -> list.clear()); loaders.add(new ClasspathOnlySchemaLoader()); })).build()`. Public method `assertEventValid(String schemaFileName, String producedJson)` — String input sidesteps the Jackson 2.x/3.x classpath split.
- `common-events/src/test/java/com/n11/events/ClasspathOnlySchemaLoader.java` — `ResourceLoader` impl: only `classpath:` URIs resolve; everything else throws `RemoteRefRefusedException`.
- `common-events/src/test/java/com/n11/events/RemoteRefRefusedException.java` — `RuntimeException` with the T-01-06 message format.
- `common-events/src/test/java/com/n11/events/AbstractEventSchemaTestSecurityCheck.java` — 3 `assertThatThrownBy` self-tests (https://, http://, file://) — proves the security control fires structurally.
- `common-events/src/test/java/com/n11/events/EnvelopeSchemaSelfTest.java` — extends `AbstractEventSchemaTest`; validates a fixture envelope JSON string against `envelope.schema.json`.
- `common-events/src/main/resources/saga-schemas/{envelope,order-created,stock-reserved,stock-reserve-failed,payment-completed,payment-failed,order-confirmed,order-cancelled,stock-released}.schema.json` — verbatim byte-identical copies from `.planning/saga-contracts/`. Source of truth remains `.planning/`; classpath copy is a derived artifact.

## Decisions Made

See frontmatter `key-decisions` for the full list. Highlights:

1. **Adapt to networknt 3.0.2 API rather than downgrade**: the version catalog from Plan 01-01 locked 3.0.2; adapting the test code (SchemaRegistry / SpecificationVersion / Error / String-based validate) is a single afternoon — downgrading to 1.x would require touching `gradle/libs.versions.toml` and re-validating CD-03 (catalog-drift signal). Trade-off accepted: networknt 3.0.2 internally uses Jackson 3.x, but the public `assertEventValid(String, String)` signature shields consumers from that — they pass a JSON string serialized via their own Jackson 2.x mapper.
2. **JUnit platform launcher pinned at testRuntimeOnly in all three modules' build.gradle.kts**: Gradle 8.10 ships `junit-platform-launcher 1.10.x`; Spring Boot 3.5.14 BOM pulls `junit-platform-engine 1.13.x`. Without the explicit launcher dep, the test JVM throws `OutputDirectoryProvider not available; probably due to unaligned versions of the junit-platform-engine and junit-platform-launcher jars on the classpath/module path` and the test process never starts. The fix is universal across Spring Boot 3.5.x + Gradle 8.10 projects — recommend rolling it into Plan 01-07's `service-template/build.gradle.kts` as a baseline.
3. **BeanPostProcessor over @Primary RabbitTemplate**: a second `@Primary RabbitTemplate` would shadow the auto-configured bean entirely, losing Spring Boot's transaction-aware wrapping, `spring.rabbitmq.template.*` defaults, and any retry policies installed via properties. The BPP layers our `MessagePostProcessor` on top of whatever Boot built — additive via `addBeforePublishPostProcessors`, never `setBeforePublishPostProcessors`. Documented in `RabbitTemplateConfig.java` Javadoc.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking issue] Gradle 8.10 / JUnit Platform launcher version mismatch**

- **Found during:** Task 1 (first `./gradlew :common-error:test` invocation)
- **Issue:** Test process failed to start with `Caused by: org.junit.platform.commons.JUnitException: OutputDirectoryProvider not available; probably due to unaligned versions of the junit-platform-engine and junit-platform-launcher jars on the classpath/module path.` Gradle 8.10 ships an older bundled `junit-platform-launcher` than the `junit-platform-engine` resolved transitively from the Spring Boot 3.5.14 BOM.
- **Fix:** Added `testRuntimeOnly("org.junit.platform:junit-platform-launcher")` (un-versioned — picks up the Boot BOM's version) to all three modules' `build.gradle.kts`.
- **Files modified:** `common-error/build.gradle.kts`, `common-logging/build.gradle.kts`, `common-events/build.gradle.kts`.
- **Verification:** `./gradlew :common-error:build` → BUILD SUCCESSFUL with 1 test executed, 0 failures. Same fix applied prophylactically to the other two modules before their first test run.
- **Committed in:** `c5f60d8` (Task 1), `a20f825` (Task 2), `4125832` (Task 3).
- **Recommendation for Plan 01-07:** apply the same `testRuntimeOnly` line in `service-template/build.gradle.kts` so business services don't trip on this too.

**2. [Rule 1 - Bug] Stale Spring AMQP import path in plan code**

- **Found during:** Task 3 (`./gradlew :common-events:compileJava`)
- **Issue:** Plan 01-04 Step 3 imported `org.springframework.amqp.rabbit.config.StatefulRetryOperationsInterceptor`. That class does not exist in Spring AMQP 3.2.x — only `RetryInterceptorBuilder` (and its inner `StatefulRetryInterceptorBuilder` builder class) lives at that path. The actual `StatefulRetryOperationsInterceptor` lives in `org.springframework.retry.interceptor` (Spring Retry 2.0.10).
- **Fix:** Updated import to `org.springframework.retry.interceptor.StatefulRetryOperationsInterceptor` and added an inline comment explaining the move.
- **Files modified:** `common-events/src/main/java/com/n11/events/RabbitRetryConfig.java`.
- **Verification:** `./gradlew :common-events:build` → BUILD SUCCESSFUL.
- **Committed in:** `4125832` (Task 3).

**3. [Rule 1 - Bug] networknt 3.0.2 API surface differs from plan-referenced 2.x classnames**

- **Found during:** Task 3 (writing `AbstractEventSchemaTest.java` against the plan's code template)
- **Issue:** Plan 01-04 Steps 5/5b/5c/5d referenced `JsonSchemaFactory`, `SpecVersion.VersionFlag.V202012`, `SchemaValidatorsConfig`, `JsonSchema`, `ValidationMessage`, `SchemaLoader` (as interface), `SchemaLoaders` — these are networknt **2.x** classnames. The 3.0.2 release (locked in `gradle/libs.versions.toml` by Plan 01-01) renamed them to `SchemaRegistry`, `SpecificationVersion.DRAFT_2020_12`, `SchemaRegistryConfig`, `Schema`, `Error`, and made `SchemaLoader` a concrete class (with `ResourceLoader` as the interface). It also migrated the validator core to Jackson 3.x (`tools.jackson.databind.JsonNode`).
- **Fix:** Adapted all test classes to the 3.0.2 API. Used `Schema.validate(String, InputFormat.JSON)` to keep the public signature String-based, sidestepping the Jackson 2.x (Envelope record's `JsonNode`) vs Jackson 3.x (validator internals) classpath split. Configured `SchemaRegistry.builder().schemaLoader(b -> b.fetchRemoteResources(false).resourceLoaders(loaders -> { loaders.values(list -> list.clear()); loaders.add(new ClasspathOnlySchemaLoader()); })).build()` for the T-01-06 control. `ClasspathOnlySchemaLoader` implements `ResourceLoader` (the 3.0.2 interface name).
- **Files modified:** `common-events/src/test/java/com/n11/events/AbstractEventSchemaTest.java`, `common-events/src/test/java/com/n11/events/ClasspathOnlySchemaLoader.java`.
- **Verification:** `./gradlew :common-events:build` → 4 tests pass (1 envelope + 3 security check); the security tests confirm the structural T-01-06 control works via `RemoteRefRefusedException`.
- **Committed in:** `4125832` (Task 3).
- **Note for documentation drift:** RESEARCH.md §3 line 192 references the same 2.x API. Recommend a follow-up doc patch in Plan 01-08 or as a Phase 1 hygiene cleanup to update the canonical research note.

---

**Total deviations:** 3 auto-fixed (1 Rule-3 blocking issue, 2 Rule-1 bugs in plan code).
**Impact on plan:** All auto-fixes were necessary for the modules to build / tests to run — no scope creep. The networknt API adaptation is the most substantive change but stays within the plan's intent (D-08 drift gate, T-01-06 control, classpath-only schema loading); the public `assertEventValid` signature changed from `(String, JsonNode)` to `(String, String)` which is actually friendlier for Phase 5+ consumers (they don't have to construct a JsonNode — just hand over the bytes their producer would publish).

## Issues Encountered

None beyond the three auto-fixed deviations above. The Spring AMQP and networknt deviations are typical of plans drafted before agents have a chance to inspect the actual JAR classpath; they're caught at first compile/test, fixed inline, and documented.

## User Setup Required

None — these are internal library JARs consumed only via `implementation(project(":common-X"))` in downstream subprojects. No environment variables, no external services, no secrets.

## Next Phase Readiness

**For Plan 01-05 (api-gateway / eureka-server / config-server skeletons):** these are reactive (api-gateway) or non-web (eureka-server, config-server) services — they should NOT depend on `:common-logging` (whose `CorrelationIdFilter` is a servlet filter; the gateway uses its own reactive `GatewayCorrelationIdFilter` per Plan 01-06). Plan 01-05's services may safely depend on `:common-error` (the `ProblemDetail` advice is web-MVC-specific but the `ApiErrorCode` enum is plain Java, useable from anywhere) and `:common-events` (Envelope + RabbitRetryConfig — though Phase 1 doesn't yet wire them at the gateway).

**For Plan 01-06 (api-gateway reactive correlation filter):** the gateway depends on `:common-logging` only for the `CorrelationIdFilter.HEADER` and `MDC_KEY` constants (NOT the filter bean itself — the reactive filter is gateway-owned). The auto-config classes are gated by `@ConditionalOnClass` so they don't fire on the reactive runtime, but the `@Component` filter would still get picked up by gateway's `@SpringBootApplication`. Plan 01-06 should explicitly exclude `CorrelationIdFilter` via `@SpringBootApplication(exclude=...)` or use `@ComponentScan(excludeFilters=...)`. Document this in Plan 01-06's CONTEXT.md.

**For Plan 01-07 (service-template):** add three lines to `service-template/build.gradle.kts`:
```kotlin
implementation(project(":common-error"))
implementation(project(":common-logging"))
implementation(project(":common-events"))
testRuntimeOnly("org.junit.platform:junit-platform-launcher")  // same Gradle 8.10 / JUnit launcher fix
```
The `AutoConfiguration.imports` mechanism brings in the 5 correlation wires + the RFC-7807 ControllerAdvice for free. service-template's `logback-spring.xml` adds `<includeMdcKeyName>correlationId</includeMdcKeyName>` to render the MDC value into JSON logs (T-01-07 mitigation: use `LogstashEncoder`, NOT `PatternLayout` with `%X{...}` direct interpolation).

**For Plan 01-08 (infra-tests) and Phase 5+ saga producers:** extend `AbstractEventSchemaTest` and call `assertEventValid("<eventType>.schema.json", producedJsonString)`. The classpath copy of the 9 schemas under `/saga-schemas/` ships with the `common-events` test JAR (because `sourceSets.test.resources.srcDirs` includes `src/main/resources`); Phase 5+ services that depend on `:common-events` for tests will see them automatically. **Producer requirement:** when publishing events through Spring AMQP, the producer MUST set `MessageProperties.setMessageId(eventId)` before publish — `RabbitRetryConfig.sagaRetryInterceptor` requires a non-null messageId for stateful retry (it throws `AmqpException` otherwise to fail fast at publish time rather than at first consumer redelivery).

**Wave 1 status:** Plan 01-04 was the last unit in Wave 1 (01-01, 01-02, 01-03, 01-04). Wave 2 begins with Plan 01-05 (eureka-server / config-server / api-gateway skeletons).

## Self-Check: PASSED

**Files verified to exist:**
- common-error/build.gradle.kts — FOUND
- common-error/src/main/java/com/n11/error/ApiErrorCode.java — FOUND
- common-error/src/main/java/com/n11/error/ProblemDetailControllerAdvice.java — FOUND
- common-error/src/test/java/com/n11/error/ProblemDetailControllerAdviceTest.java — FOUND
- common-logging/build.gradle.kts — FOUND
- common-logging/src/main/java/com/n11/logging/CorrelationIdFilter.java — FOUND
- common-logging/src/main/java/com/n11/logging/CorrelationIdRestClientInterceptor.java — FOUND
- common-logging/src/main/java/com/n11/logging/RestClientConfig.java — FOUND
- common-logging/src/main/java/com/n11/logging/CorrelationIdMessagePostProcessor.java — FOUND
- common-logging/src/main/java/com/n11/logging/RabbitTemplateConfig.java — FOUND
- common-logging/src/main/java/com/n11/logging/RabbitListenerCorrelationAspect.java — FOUND
- common-logging/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports — FOUND
- common-events/build.gradle.kts — FOUND
- common-events/src/main/java/com/n11/events/Envelope.java — FOUND
- common-events/src/main/java/com/n11/events/RabbitRetryConfig.java — FOUND
- common-events/src/test/java/com/n11/events/AbstractEventSchemaTest.java — FOUND
- common-events/src/test/java/com/n11/events/ClasspathOnlySchemaLoader.java — FOUND
- common-events/src/test/java/com/n11/events/RemoteRefRefusedException.java — FOUND
- common-events/src/test/java/com/n11/events/AbstractEventSchemaTestSecurityCheck.java — FOUND
- common-events/src/test/java/com/n11/events/EnvelopeSchemaSelfTest.java — FOUND
- common-events/src/main/resources/saga-schemas/*.schema.json (9 files) — ALL FOUND, byte-identical to .planning/saga-contracts/ originals

**Commits verified to exist:**
- c5f60d8 (Task 1: feat(01-04): build common-error library) — FOUND in `git log`
- a20f825 (Task 2: feat(01-04): build common-logging library) — FOUND in `git log`
- 4125832 (Task 3: feat(01-04): build common-events library) — FOUND in `git log`

**Build verified:**
- `./gradlew :common-error:build :common-logging:build :common-events:build --no-daemon` → BUILD SUCCESSFUL, 13 actionable tasks (12 up-to-date, 1 executed), 4 tests passing in common-events + 1 test passing in common-error + 0 tests in common-logging (Phase 5+ adds integration tests for wires #3/#4 against real RabbitMQ Testcontainers).

---
*Phase: 01-foundations-day-1-contracts*
*Completed: 2026-04-28*
