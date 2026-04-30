---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: executing
stopped_at: Phase 5 context gathered (auto mode) — 11 gray areas auto-resolved, common-outbox refactor + 999.2 ArchUnit gate locked
last_updated: "2026-04-30T09:45:13Z"
last_activity: 2026-04-30 -- Phase 05 Plan 04 complete (payment-service skeleton + CD-08/CD-09 compensation + saga E2E test)
progress:
  total_phases: 13
  completed_phases: 4
  total_plans: 25
  completed_plans: 24
  percent: 88
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-04-28)

**Core value:** A graders-impressing demonstration that the candidate can architect a clean, SOLID, microservices system AND layer differentiated AI capabilities on top of it.
**Current focus:** Phase 05 — cart-order-skeleton

## Current Position

Phase: 05 (cart-order-skeleton) — EXECUTING
Plan: 5 of 5
Next: Phase 05 Plan 05 (gateway routes + docker-compose + smoke runbook)
Status: Executing Phase 05
Last activity: 2026-04-30 -- Phase 05 Plan 04 complete (payment-service skeleton + CD-08/CD-09 compensation + saga E2E test)

Progress: [████░░░░░░] 36% (4 of 11 phases complete)

## Performance Metrics

**Velocity:**

- Total plans completed: 20 (Phase 1: 8 plans; Phase 2: 3 plans)
- Average duration: ~12 min (Phase 1 avg ~14 min; Phase 2 avg ~6 min — recon-tooling phase is shorter than backend-infra phase)
- Total execution time: ~100 min

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 01 (foundations-day-1-contracts) | 8 | ~110 min | ~14 min |
| 02 (frontend-recon-toolchain-lock) | 3 | ~18 min | ~6 min |
| 03 (identity-gateway-auth) | 6 | ~84 min | ~14 min |
| 04 (catalog-inventory) Plan 01 | 1 | ~32 min | ~32 min |
| 04 (catalog-inventory) Plan 02 | 4 | ~95 min | ~24 min |

**Recent Trend:**

- Last 6 plans: Plan 01-06 (~17 min, 6 tasks, 9 files + 2 deviations), Plan 01-07 / Plan 01-08 (Phase 1 close — 8/8 plans landed, recorded in .planning/phases/01-foundations-day-1-contracts/01-08-SUMMARY.md), Plan 02-01 (~7 min, 3 tasks, 12 files + 1 Rule-3 deviation: gradient-aware tsconfig.json contract edit), Plan 02-02 (~5 min capture wall-clock + 5 deviations: Xvfb host install, playwright timeout bump, /giris→/giris-yap URL fix, assemble-recon dash-collapse regex bug, artifact-lint regex tightened), Plan 02-03 (~6 min, 3 tasks, 3 files + 0 deviations — plan ran clean; the one Rule-1 was a chasmy outcome string that PROJECT.md mention-count expected exactly 2 vs 3, fixed inline by reflowing the legacy-row outcome wording).
- Phase 02 narrative: anti-bot fallback was NOT needed — n11 captures completed on rung 1 (configured Playwright posture: headless:false + real desktop UA + slowMo:250 + tr-TR locale). Final counts: 644 phrases (≥30 threshold) + 25 color tokens (≥10 threshold) + 7 fullpage screenshots ≥50 KB each. The cross-plan contract pin (Plan 02-02 tightened scripts/check-phase-02-artifacts.sh to require `Vite 8 + React 19 SPA` + `Locked YYYY-MM-DD` on the SAME line of PROJECT.md Key Decisions) closed cleanly in 02-03 Task 2.
- Trend: Wave 2 unit cost stabilized at ~13-17 min in Phase 1 once Jib pre-build pattern was canonized in 01-05 and replicated in 01-06. Phase 2's recon plans came in at ~5-7 min apiece — recon tooling is npm-driven (no Gradle pre-build), and the Decision Matrix subsection paste was a single Edit operation. Recommend Phase 3 plans (Identity + Gateway Auth) re-baseline back to Phase 1's ~14-min Wave 2 unit cost given the Jib + Spring Boot return.

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
- 2026-04-29 (Plan 04-01): Native @Query sort fields must use snake_case column names (created_at, price_gross) not JPA camelCase property names. Spring Data passes sort fields as-is to native SQL; camelCase causes PSQLException.
- 2026-04-29 (Plan 04-01): src/test/resources/application.yml with optional:configserver: is required for slice tests (@DataJpaTest/@JdbcTest) — Spring Boot 3.x loads this before profile-specific overrides, preventing ConfigClientFailFastException on bootstrap.
- 2026-04-29 (Plan 04-01): Testcontainers Postgres native queries require hikari.connection-init-sql=SET search_path=<schema> in application-test.yml — no init.sh sets search_path for the test DB user.
- 2026-04-29 (Plan 04-01): GIN trigram index EXPLAIN ANALYZE tests with small datasets (<1000 rows) must use SET enable_seqscan=off to force index use (planner chooses SeqScan for small tables). This proves index correctness without requiring full production data volume.
- 2026-04-29 (Plan 04-02): @Transactional on @RabbitListener is unreliable — Spring AMQP invokes listeners via the AMQP container thread, potentially bypassing AOP proxy. Split pattern: @RabbitListener method deserializes/routes; @Transactional @Service method (InventoryOrderService) handles all DB writes atomically. Idempotency check (processed_events.existsById) INSIDE @Transactional before any state change.
- 2026-04-29 (Plan 04-02): Testcontainers RabbitMQ listener auto-subscription fails with EOFException in redeclareElementsIfNecessary() during AMQP handshake in some test configurations. Integration tests proving business-logic idempotency should use direct consumer invocation (consumer.handleOrderCreated(amqpMsg)) rather than depending on AMQP delivery mechanics.
- 2026-04-29 (Plan 04-02): rabbitmq:3.13-management preferred over 4.0-management for Testcontainers stability (4.0-management caused Connection reset errors during AMQP handshake).
- 2026-04-30 (Plan 05-01): @EntityScan("com.n11") required on @SpringBootApplication classes when a shared Gradle library module contributes JPA entities. @SpringBootApplication(scanBasePackages="com.n11") only sets component scan, not entity scan. Apply to ALL future services importing common-outbox.
- 2026-04-30 (Plan 05-01): D-09 structural fix landed — AbstractOutboxPoller.poll() passes OutboxMessagePostProcessor as 4th arg to convertAndSend; messageId + correlationId always set from envelope JSON. The 999.2 per-service copy-paste regression (commit 06338b1) is now structurally impossible for any service extending AbstractOutboxPoller.
- 2026-04-30 (Plan 05-01): D-10 ArchUnit gate landed — AmqpAckModeArchTest in infra-tests/com.n11.infra.arch asserts every @RabbitListener method uses Message parameter + no Channel parameter. Fail-fast gate for the 999.2 MANUAL-ack regression (commit 2b61689).
- 2026-04-30 (Plan 05-02): awaitility pinned to 4.2.0 in cart-service (plan specified 4.3.1 which does not exist in Maven Central; 4.2.0 matches inventory-service).
- 2026-04-30 (Plan 05-02): cart-service does NOT import :common-outbox (consumer-only service — no outbox needed). ProcessedEvent entity is local to com.n11.cart.messaging.
- 2026-04-30 (Plan 05-03): Two-bean @Transactional split: OrderService (orchestration/sync REST/idempotency check, no @Transactional) + OrderTransactionalService (@Transactional — DB persist). Sync REST calls (CartClient, IdentityClient, ProductClient) MUST happen BEFORE any @Transactional boundary opens (Pitfall #1).
- 2026-04-30 (Plan 05-03): D-01 price drift detection: strict BigDecimal equality check of cart unit_price_snapshot vs current product price BEFORE @Transactional opens; raises PriceDriftException → HTTP 409 with ProblemDetail type=price-drift + updatedItems[] custom property (RFC-7807).
- 2026-04-30 (Plan 05-03): Idempotency-Key (UUID) dedup on POST /orders: (idempotency_key, user_id) composite PK in order_idempotency_keys table. Repeat call returns existing orderId (200) not new order (202). Cross-user collision on same key returns 409.
- 2026-04-30 (Plan 05-03): Saga consumer shared count-assertion fix: tests sharing same Spring context + Postgres container must filter processed_events by eventId (not count() all rows) — otherwise later-running tests see counts from previous test data accumulated in the same table.
- 2026-04-30 (Plan 05-03): PaymentCompletedConsumer accepts both PENDING and STOCK_RESERVED as valid source states — race condition where payment.completed arrives before stock.reserved is processed requires both to be valid transition sources.
- 2026-04-30 (Plan 05-04): PaymentServiceTestConfig with excludeFilters for @SpringBootApplication annotations required in infra-tests — prevents other service Application classes from expanding @EntityScan to entire codebase when multi-service classpath is active.
- 2026-04-30 (Plan 05-04): Bean disambiguation mandatory for multi-service classpath: @Entity(name=...), @RestController(beanName), @Component(beanName) required for all shared-name classes (SampleHealthController, ProcessedEvent, OutboxPoller) — apply to any new service before adding to infra-tests deps.
- 2026-04-30 (Plan 05-04): infra-tests Flyway must use classpath:db/migration/<schema> subdirectory not classpath:db/migration — multiple services have V1+V2 at base path causing version collision. Pattern: copy service migrations to infra-tests/src/test/resources/db/migration/<schema>/.
- 2026-04-30 (Plan 05-04): Sniffer queue in E2E tests must NOT use .autoDelete() — autoDelete causes queue deletion between Awaitility poll iterations when RabbitTemplate.receive() terminates its internal consumer. Use nonDurable + no autoDelete for sniffer queues.
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

Last session: 2026-04-30T09:45:13Z
Stopped at: Phase 05 Plan 04 complete — payment-service skeleton (D-06) + inventory CD-08/CD-09 compensation consumers + SagaHappyPathE2ETest (Testcontainers Postgres + RabbitMQ, real AMQP, Awaitility 15s, D-09 messageId invariant)
Resume file: .planning/phases/05-cart-order-skeleton/05-05-PLAN.md
Next: Phase 05 Plan 05 (gateway routes + docker-compose + smoke runbook)
