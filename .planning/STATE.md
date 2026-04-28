---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: executing
stopped_at: "Phase 1 complete — all 8 plans across 4 waves landed. Foundations + Day-1 Contracts done: Gradle multi-module + gitleaks CI + saga/API contracts + Postgres+Rabbit infra + common-* libs + Eureka+Config+Gateway + service-template archetype + cross-schema deny test. Ready for Phase 2 (Frontend Recon + Toolchain Lock)."
last_updated: "2026-04-28T23:20:00.000Z"
last_activity: 2026-04-28 -- Plan 01-08 complete + Phase 1 closed (commits f4928f8 plan, finalization commit forthcoming)
progress:
  total_phases: 11
  completed_phases: 1
  total_plans: 8
  completed_plans: 8
  percent: 9
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-04-28)

**Core value:** A graders-impressing demonstration that the candidate can architect a clean, SOLID, microservices system AND layer differentiated AI capabilities on top of it.
**Current focus:** Phase 01 — foundations-day-1-contracts

## Current Position

Phase: 01 (foundations-day-1-contracts) — ✓ COMPLETE (8/8 plans)
Next: Phase 02 — Frontend Recon + Toolchain Lock (Playwright session against n11.com + toolchain decision in PROJECT.md)
Status: Phase 1 closed
Last activity: 2026-04-28 -- Plan 01-08 complete; all of Phase 1 (Foundations + Day-1 Contracts) shipped

Progress: [█████████░] 9% (1 of 11 phases complete)

## Performance Metrics

**Velocity:**

- Total plans completed: 6
- Average duration: ~14 min (01-01 ~30 min; 01-02 ~5 min; 01-03 ~5 min; 01-04 ~17 min; 01-05 ~8 min; 01-06 ~17 min)
- Total execution time: ~82 min

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 01 (foundations-day-1-contracts) | 6 | ~82 min | ~14 min |

**Recent Trend:**

- Last 6 plans: 01-01 (~30 min, 3 tasks, 23 files), 01-02 (~5 min, 2 tasks, 11 files), 01-03 (~5 min, 3 tasks, 4 files + 1 Rule-3 deviation), 01-04 (~17 min, 3 tasks, 27 files + 3 deviations: JUnit launcher Rule-3, Spring AMQP retry import Rule-1, networknt 3.0.2 API adaptation Rule-1), 01-05 (~8 min, 3 tasks, 8 files + 0 deviations -- plan ran clean on first attempt), 01-06 (~17 min, 6 tasks, 9 files + 2 deviations: Rule-1 :common-logging dep dropped due to servlet-filter-on-reactive-runtime crash, Rule-3 stale config-server Jib image rebuild required after touching config/api-gateway.yml; SC-1 5-service stack cold-boot 25s vs 60s budget => margin > 50%)
- Trend: Wave 2 unit cost stabilized at ~13-17 min once Jib pre-build pattern was canonized in 01-05 and replicated in 01-06. Plan 01-06's deviations were both pre-flagged in 01-04-SUMMARY and 01-05-SUMMARY (the :common-logging-on-reactive-runtime landmine and the stale-Jib-image-after-YAML-edit hazard) -- catching them at runtime rather than re-discovering them via stack trace would shave ~5 min off similar Wave-2 plans. Recommend Plan 01-07 cite these in CONTEXT.md to avoid the same churn.

*Updated after each plan completion*

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- 2026-04-28: 11-phase roadmap (collapsed search-foundation phase into Phase 8 since all v1 search reqs are covered by PROD-04 in Phase 4, and ai-service's `EmbeddingProvider` port + search-service skeleton naturally co-locate with the AI port build).
- 2026-04-28: 13-service decomposition locked (eureka, config, gateway, identity, product, inventory, cart, order, payment, notification, search, ai-service, mcp-server).
- 2026-04-28: Choreography SAGA via RabbitMQ events; transactional outbox + processed-events inbox for idempotency.
- 2026-04-28: Provider-agnostic LLM abstraction (`ChatProvider` / `EmbeddingProvider` ports + Gemini adapter only); `EchoChatProvider` second adapter is the SOLID artifact.
- 2026-04-28 (Plan 01-03): Postgres boundary = 10 schemas (not 13 — eureka/config/gateway are stateless). All schemas, users, search_path defaults, and 10×9 cross-schema REVOKE deny matrix bootstrapped via `infra/postgres/init.sh` mounted into pgvector/pgvector:pg16 by docker-compose. `orders` (plural) used everywhere — `order` is SQL reserved.
- 2026-04-28 (Plan 01-03): Added `.gitattributes` enforcing LF for `*.sh`, YAML, JSON, SQL, Dockerfiles. Greenfield Windows repo with `core.autocrlf=true` would otherwise have committed init.sh as CRLF and broken the Postgres container's bash shebang.
- 2026-04-28 (Plan 01-04): networknt 3.0.2 API adapted in test code rather than downgrading to 2.x. SchemaRegistry / SpecificationVersion / Error / String-based validate replace JsonSchemaFactory / SpecVersion / ValidationMessage / JsonNode-based validate. `assertEventValid(String, String)` signature shields Phase 5+ consumers from the validator's internal Jackson 3.x classpath.
- 2026-04-28 (Plan 01-04): RabbitTemplate augmentation pattern locked: BeanPostProcessor.postProcessAfterInitialization → `addBeforePublishPostProcessors(...)` (additive). NEVER register a second @Primary RabbitTemplate; that would shadow Spring Boot's auto-configured bean and lose all Boot defaults (transaction-aware connection wrapping, spring.rabbitmq.template.* settings, etc.).
- 2026-04-28 (Plan 01-04): JUnit Platform launcher pinned at testRuntimeOnly across all three library modules. Gradle 8.10 ships an older bundled launcher than the engine resolved transitively from the Spring Boot 3.5.14 BOM ("OutputDirectoryProvider not available"). `testRuntimeOnly("org.junit.platform:junit-platform-launcher")` is the universal fix; recommend rolling into Plan 01-07's service-template build.gradle.kts.
- 2026-04-28 (Plan 01-04): StatefulRetryOperationsInterceptor lives in `org.springframework.retry.interceptor`, NOT `org.springframework.amqp.rabbit.config`. Spring AMQP 3.2.x removed the spring-rabbit shadow class — only RetryInterceptorBuilder (and its inner builder class) remains there.
- 2026-04-28 (Plan 01-05): Boot-app Gradle shape locked: `plugins { id("org.springframework.boot"); id("com.google.cloud.tools.jib") }` only — `java`, `io.spring.dependency-management`, Java 21 toolchain, and Spring Boot + Spring Cloud BOM imports all flow from the root `subprojects { }` block (Plan 01-01 Task 2). Per Cross-Cutting #1: Boot+Jib plugins applied selectively (eureka-server + config-server here; api-gateway in 01-06 next). Library modules (common-error/logging/events) keep `java-library` only.
- 2026-04-28 (Plan 01-05): Compose healthcheck for Spring Boot services in Jib images uses `wget` (not `curl`) — `eclipse-temurin:21-jre` ships BusyBox utilities including wget but NOT curl. Idiom: `["CMD-SHELL", "wget -q -O- http://localhost:<PORT>/actuator/health | grep -q '\"status\":\"UP\"'"]`. Replicate for api-gateway (port 8080) in 01-06.
- 2026-04-28 (Plan 01-05): Pitfall #4 boundary kept tight on the eureka-server self-config — NO `spring.config.import` and NO `registry-fetch-interval-seconds` keys. The server is the discovery root (no peer to retry against); cold-boot CLIENT retry config belongs in service-template/application.yml owned by Plan 01-07. Verified by grep -- both keys absent.
- 2026-04-28 (Plan 01-05): config-server self-config (src/main/resources/application.yml) versus content-config (src/main/resources/config/application.yml) are TWO DIFFERENT FILES. Self-config tells the server how to serve (port, profile, search-locations); content-config is the SHARED BASELINE (CD-05) served to every business service requesting profile `default`. The shared baseline ships ONLY keys that legitimately apply to ALL services (Eureka URL, datasource template with placeholder substitution for db.user/db.password, Hikari/JPA defaults, Springdoc paths, actuator surface, logging defaults) — no `spring.cloud.gateway.*` (owned by 01-06), no `spring.flyway.*` with concrete schemas (owned by 01-07), no hardcoded passwords.
- 2026-04-28 (Plan 01-05): Cold-boot smoke timing observed for eureka-server + config-server: both `(healthy)` in ~15s from `docker compose up -d` on a warm Docker daemon (Jib images pre-populated). Pitfall #4 budget allowance was 30-60s + 20-30s = ~50-90s combined; we came in at less than half the lower bound. The Jib pre-build pattern (`./gradlew :<svc>:jibDockerBuild` once per code change) is the canonical Wave-2 launch sequence — 01-PLAN-OUTLINE.md and PATTERNS Cross-Cutting #1 record this.
- 2026-04-28 (Plan 01-05): docker-compose additive-merge pattern verified — read existing file, ADD new keys under the same `services:` map, never re-write the file. `grep -c '^  postgres:'` and `grep -c '^  rabbitmq:'` returning exactly 1 after the merge prove additivity. Plan 01-06 will follow the same idiom for api-gateway.
- 2026-04-28 (Plan 01-06): :common-logging dependency intentionally NOT carried into api-gateway. Spring's ConfigurationClassParser reads class metadata for @Import targets BEFORE @ConditionalOnClass gates fire, so common-logging's RestClientConfig (which @Imports the servlet CorrelationIdFilter referencing jakarta.servlet.Filter) cannot load on the reactive runtime even with the gate. The dependency-drop is the cleanest structural fix; the gateway's own GatewayCorrelationIdFilter shares the X-Correlation-Id wire-format header name with common-logging's servlet filter (no Java import sharing required). This decision is binding: any future reactive Spring service in this repo must follow the same pattern.
- 2026-04-28 (Plan 01-06): GlobalFilter import package is org.springframework.cloud.gateway.filter (UNCHANGED in Northfields/Spring Cloud 2025.0). The 2025.0 rename was property-prefix-only (spring.cloud.gateway.* → spring.cloud.gateway.server.webflux.*) and starter-coordinate-only (spring-cloud-starter-gateway → spring-cloud-starter-gateway-server-webflux); class packages stayed the same. Verified by unzip of spring-cloud-gateway-server-4.3.0.jar.
- 2026-04-28 (Plan 01-06): Stale-Jib-image hazard for config-server YAML edits surfaced. Editing config-server/src/main/resources/config/*.yml requires `./gradlew :config-server:jibDockerBuild` BEFORE `docker compose up -d` for the change to be served (Spring Cloud Config native profile reads classpath:/config/ which is JAR-bound at image-build time). Plan 01-07 should cite this as a CONTEXT note. Recommend baking the Wave-2+ workflow rule into all future plans that touch config-server YAMLs.
- 2026-04-28 (Plan 01-06): Phase 1 SC-1 success criterion satisfied with margin -- 5-service stack (postgres, rabbitmq, eureka-server, config-server, api-gateway) cold-boots to (healthy) in 25s on a warm Docker daemon (budget 60s, margin > 50%). /actuator/gateway/routes returns 200 with one self-route entry (the gateway is itself a Eureka client and discovery-locator auto-routes API-GATEWAY under /api-gateway/**); D-14's "[]" expectation was conservative -- the actual single self-route is correct Northfields behavior. Recorded in .planning/phases/01-foundations-day-1-contracts/01-06-SC1-SMOKE.log.
- 2026-04-28 (Plan 01-06): Pitfall #2 (gateway reactive vs MVC classpath collision) structurally locked down. configurations.all { exclude(group=org.springframework.boot, module=spring-boot-starter-tomcat); exclude(... starter-web); exclude(org.springdoc, springdoc-openapi-starter-webmvc-ui) } in api-gateway/build.gradle.kts. ./gradlew :api-gateway:dependencies --configuration runtimeClasspath shows zero matches for any of those.

### Pending Todos

None yet.

### Blockers/Concerns

- **AWS deploy scope** — RESOLVED 2026-04-28 (revised): AWS dropped. Deploy = local docker-compose on the candidate's machine; demo URL exposed via Cloudflare Tunnel (preferred) or ngrok. Earlier EB+RDS decision is superseded — Pitfall #12 is no longer in scope. **Caveat:** confirm with bootcamp coordinator that local-host + tunnel deployment is acceptable for grading (the brief originally listed AWS as must-have).
- **Gemini 3 Flash model identifier** (Phase 8 deliverable): verify `gemini-3-flash-preview` against ai.google.dev at impl time; fallback `gemini-2.5-flash`. MEDIUM confidence per stack research.
- **Iyzico webhook public reachability** (Phase 6 deliverable): Cloudflare Tunnel (preferred) or ngrok — choose and document in `payment-service/README.md`. HIGH severity (pitfall #5).

## Deferred Items

Items acknowledged and carried forward from previous milestone close:

| Category | Item | Status | Deferred At |
|----------|------|--------|-------------|
| Hygiene | IDE-generated `*/bin/main/` directories pollute `git status` (8 modules from Plan 01-01 scaffolding via IDE; not in `.gitignore`) — see `.planning/phases/01-foundations-day-1-contracts/deferred-items.md` D-01 | OPEN — recommend Phase 1 hygiene cleanup or Phase 11 DevOps adds `**/bin/` to `.gitignore` | 2026-04-28 (Plan 01-03) |

## Session Continuity

Last session: 2026-04-28 (execute-phase 1, plan 01-06)
Stopped at: Plan 01-06 complete; api-gateway is a runnable Spring Cloud Gateway 2025.0 (Northfields) reactive WebFlux Boot app with a Jib-built local image (n11/api-gateway:dev), permitAll() chain (D-14 -- Phase 3 will flip to JWT), reactive GatewayCorrelationIdFilter (D-09) + GatewayHeaderInjectionFilter stub (T-01-09), CD-05 overlay at config-server/src/main/resources/config/api-gateway.yml (discovery-locator on, /actuator/gateway/routes exposed, Springdoc aggregator with empty urls list, commented-out Phase 8 SSE forward-ref), and docker-compose.yml has been additively merged so `docker compose up -d` brings the full 5-service stack (postgres, rabbitmq, eureka-server, config-server, api-gateway) to (healthy) in 25s on a warm Docker daemon. Wave 2 continues with 01-07 (service-template archetype). 01-08 (infra-tests Testcontainers cross-schema deny smoke) blocks on 01-03 + 01-07 and opens Wave 3.
Resume file: .planning/phases/01-foundations-day-1-contracts/01-07-PLAN.md (next dispatch unit)
Next: /gsd-execute-phase 1 (continue Wave 2)
