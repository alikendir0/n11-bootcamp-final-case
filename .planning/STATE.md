---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: executing
stopped_at: Phase 11 planned with accepted verification and decision-coverage overrides
last_updated: "2026-05-02T15:02:59.995Z"
last_activity: 2026-05-02 -- Phase 11 planning complete
progress:
  total_phases: 13
  completed_phases: 10
  total_plans: 66
  completed_plans: 60
  percent: 91
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-04-28)

**Core value:** A graders-impressing demonstration that the candidate can architect a clean, SOLID, microservices system AND layer differentiated AI capabilities on top of it.
**Current focus:** Phase 11 — frontend-chat-assistant + devops-deploy

## Current Position

Phase: 11 (frontend-chat-assistant + devops-deploy) — READY TO EXECUTE
Plan: 6 plans
Next: `/gsd-execute-phase 11` — run chat assistant UI + local docker-compose deploy + public tunnel handoff plans
Status: Ready to execute
Last activity: 2026-05-02 -- Phase 11 planning complete

Progress: [██████████] 98%

## Performance Metrics

**Velocity:**

- Total plans completed: 33 (Phase 1: 8 plans; Phase 2: 3 plans)
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
| 06 (payment-iyzico) Plan 01 | 1 | ~8 min | ~8 min |
| 07 | 6 | - | - |
| 09 | 7 | - | - |

**Recent Trend:**

- Last 6 plans: Plan 01-06 (~17 min, 6 tasks, 9 files + 2 deviations), Plan 01-07 / Plan 01-08 (Phase 1 close — 8/8 plans landed, recorded in .planning/phases/01-foundations-day-1-contracts/01-08-SUMMARY.md), Plan 02-01 (~7 min, 3 tasks, 12 files + 1 Rule-3 deviation: gradient-aware tsconfig.json contract edit), Plan 02-02 (~5 min capture wall-clock + 5 deviations: Xvfb host install, playwright timeout bump, /giris→/giris-yap URL fix, assemble-recon dash-collapse regex bug, artifact-lint regex tightened), Plan 02-03 (~6 min, 3 tasks, 3 files + 0 deviations — plan ran clean; the one Rule-1 was a chasmy outcome string that PROJECT.md mention-count expected exactly 2 vs 3, fixed inline by reflowing the legacy-row outcome wording).
- Phase 02 narrative: anti-bot fallback was NOT needed — n11 captures completed on rung 1 (configured Playwright posture: headless:false + real desktop UA + slowMo:250 + tr-TR locale). Final counts: 644 phrases (≥30 threshold) + 25 color tokens (≥10 threshold) + 7 fullpage screenshots ≥50 KB each. The cross-plan contract pin (Plan 02-02 tightened scripts/check-phase-02-artifacts.sh to require `Vite 8 + React 19 SPA` + `Locked YYYY-MM-DD` on the SAME line of PROJECT.md Key Decisions) closed cleanly in 02-03 Task 2.
- Trend: Wave 2 unit cost stabilized at ~13-17 min in Phase 1 once Jib pre-build pattern was canonized in 01-05 and replicated in 01-06. Phase 2's recon plans came in at ~5-7 min apiece — recon tooling is npm-driven (no Gradle pre-build), and the Decision Matrix subsection paste was a single Edit operation. Recommend Phase 3 plans (Identity + Gateway Auth) re-baseline back to Phase 1's ~14-min Wave 2 unit cost given the Jib + Spring Boot return.

*Updated after each plan completion*
| Phase 06-payment-iyzico P02 | 4min | 2 tasks | 9 files |
| Phase 06-payment-iyzico P03 | 11min | 2 tasks | 13 files |
| Phase 08-ai-port-adapter-agent-toolset P04 | 3h | 3 tasks | 26 files |
| Phase 10-frontend-storefront P09 | ~3min | 3 tasks | 8 files |
| Phase 10-frontend-storefront P10 | 3min | 3 tasks | 11 files |
| Phase 09 P01 | 5 min | 4 tasks | 8 files |
| Phase 09 P02 | 6 min | 6 tasks | 14 files |
| Phase 09 P03 | 7h 17m | 2 tasks | 3 files |
| Phase 09 P04 | 5 min | 4 tasks | 7 files |
| Phase 09 P05 | 3 min | 4 tasks | 5 files |
| Phase 09 P06 | 9 min | 3 tasks | 3 files |
| Phase 09 P07 | operator-approved checkpoint | 3 tasks | 3 files |

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
- 2026-04-30 (Plan 06-01): PUBLIC_BASE_URL is the single source for Iyzico callback URL derivation; payment-service normalizes trailing slashes and appends /api/v1/payments/iyzico/callback.
- 2026-04-30 (Plan 06-01): Iyzico Checkout Form integration uses a narrow neutral adapter contract; SDK embedded-form HTML is not exposed by payment-service public contracts.
- [Phase 06]: Payment-service obtains Iyzico buyer/address/item inputs through order-service internal REST, preserving schema ownership and keeping PII out of stock.reserved.
- [Phase 06]: Payment context endpoint remains under /internal/orders and no api-gateway route was added; Docker mesh/gateway-only exposure remains the trust boundary.
- [Phase 06]: 404 from order-service is treated as a non-retryable payment initialization failure with a sanitized message.
- [Phase 06]: Checkout initialization is driven only by stock.reserved; clients fetch the persisted paymentPageUrl later instead of creating Iyzico sessions directly.
- [Phase 06]: Active PENDING checkout rows are reused before calling Iyzico so stock.reserved redelivery cannot create duplicate hosted sessions.
- [Phase 06]: Internal order payment context includes createdAt for deterministic Iyzico buyer registrationDate/lastLoginDate mapping.
- [Phase ?]: D-06 manual function-calling loop: ChatService.MAX_TOOL_LOOPS=6 with explicit looping, no SDK AFC
- [Phase ?]: D-08 IdProvenanceService: seenIds Set + regex ID field detection; hallucinated IDs rejected with UNKNOWN_ID before backend calls
- [Phase ?]: Native INSERT ON CONFLICT DO NOTHING for client-assigned UUID JPA entities avoids StaleObjectStateException
- [Phase ?]: liveHistory mutable list tracks in-session turns not yet flushed to DB snapshot
- [Phase 10]: Playwright E2E baseURL remains env-driven via PLAYWRIGHT_BASE_URL with localhost:5173 default; live validation can override to localhost:8083 without committed config drift.
- [Phase 10]: Product-service listing DTOs are normalized at frontend API boundary via normalizeProductPage before ProductCard rendering.
- [Phase 10]: Register form uses @hookform/resolvers 5.2.2 with Zod 4 and mirrors backend password policy in Turkish validation.
- [Phase 10]: Root React Router errorElement renders the shared Turkish RouteErrorFallback for route exceptions.
- [Phase 09]: mcp-server stays DB-free and AMQP-listener-free; only spring-amqp core plus AspectJ runtime are present to satisfy common-logging class loading.
- [Phase 09]: Spring AI MCP starter version is managed centrally through the root spring-ai-bom:1.1.5 import.
- [Phase 09]: Agent API-key exchange uses SHA-256 hashed keys and mints standard ROLE_USER JWTs with JWT.sub as the bound users.id UUID string. — Required by AI-13 so external MCP agents act as a real user through existing gateway-backed JWT semantics.
- [Phase 09]: AgentSeedRunner is the default demo MCP_API_KEY path; R__seed_agent_api_keys remains optional for pre-baked hashes and plaintext is never stored. — Satisfies no-secrets policy while still supporting reproducible demo hashes when explicitly configured.
- [Phase 09 P03]: Spring AI 1.1.5 FunctionToolCallback.Builder uses direct description/inputSchema methods rather than toolDefinition(ToolDefinition); MCP adapter still sources metadata verbatim from AgentTool.
- [Phase 09 P03]: AgentJwtCache is intentionally a fail-fast stub in P03 so P04 can replace the implementation without changing the contract path.
- [Phase 09]: [Phase 09 P04]: AgentJwtClient uses explicit @Qualifier("loadBalancedRestClientBuilder") for cycle-free token exchange instead of parameter-name matching. — Multiple RestClient.Builder beans exist in mcp-server; explicit qualification is more robust and preserves the un-intercepted exchange path.
- [Phase 09]: [Phase 09 P04]: McpRestClientConfig normalizes RestClient.Builder primaries so the JWT-intercepted tool builder wins while AgentJwtClient still receives the un-intercepted builder by qualifier. — common-logging already contributes a primary RestClient.Builder, so normalization prevents NoUniqueBeanDefinitionException in agent-toolset clients.
- [Phase 09]: [Phase 09 P04]: AgentJwtCache uses the 10-minute refresh buffer from 09-RESEARCH Pitfall #4, superseding the earlier 5-minute D-07 note. — The larger buffer avoids gateway JwtTimestampValidator clock-skew edge cases during demo tool calls.
- [Phase 09]: MCP Streamable HTTP is exposed only through /mcp/** at api-gateway with response-timeout -1 and JWT Bearer auth; no public allowlist entry was added. — Preserves gateway as the JWT validation edge and protects external MCP clients through the same auth posture as logged-in users.
- [Phase 09]: mcp-server compose service is internal-only with no host ports, no Postgres dependency, and no RabbitMQ dependency. — Keeps the gateway as the sole public front door while preserving the stateless MCP server boundary.
- [Phase 09 P06]: MCP infra-tests use explicit `org.springframework.ai:spring-ai-model` test scope because `:mcp-server` exposes Spring AI types through `implementation`, not `api`. — Keeps the integration proof compiling without changing production dependency visibility.
- [Phase 09 P06]: McpServerTestConfig disables scheduling and excludes foreign `@SpringBootApplication` classes instead of adding entity/repository scans. — mcp-server is DB-free, and the metadata-only test must not trigger AgentJwtCache exchange attempts.
- [Phase 09]: Human verification approved all 8 hard gates for the external MCP demo flow: Claude Desktop/Inspector list 10 tools over stdio + HTTP, mutating tools complete through Iyzico sandbox, order reaches CONFIRMED, and agent API-key audit trail updates.
- [Phase 09]: The same Jib image `n11/mcp-server:dev` remains the operator contract for both docker-compose mcp-server and Claude Desktop stdio launch; Phase 11 only needs to expose the gateway tunnel for `/mcp/**`.

### Pending Todos

None yet.

### Blockers/Concerns

- **AWS deploy scope** — RESOLVED 2026-04-28 (revised): AWS dropped. Deploy = local docker-compose on the candidate's machine; demo URL exposed via Cloudflare Tunnel (preferred) or ngrok. Earlier EB+RDS decision is superseded — Pitfall #12 is no longer in scope. **Caveat:** confirm with bootcamp coordinator that local-host + tunnel deployment is acceptable for grading (the brief originally listed AWS as must-have).
- **Gemini 3 Flash model identifier** (Phase 8 deliverable): verify `gemini-3-flash-preview` against ai.google.dev at impl time; fallback `gemini-2.5-flash`. MEDIUM confidence per stack research.
- **Iyzico webhook public reachability** (Phase 6 deliverable): Cloudflare Tunnel (preferred) or ngrok — choose and document in `payment-service/README.md`. HIGH severity (pitfall #5).
- **Phase 11 planning overrides** — ACCEPTED 2026-05-02: plan-checker still reported 4 blockers (refresh persistence explicitness, Slack shell guard, DevOps docs verification, 11-04 scope split) and decision coverage gate reported D-01..D-16 uncited. User chose to force proceed and proceed anyway; executor should read checker output concerns before implementation.

## Deferred Items

Items acknowledged and carried forward from previous milestone close:

| Category | Item | Status | Deferred At |
|----------|------|--------|-------------|
| Hygiene | IDE-generated `*/bin/main/` directories pollute `git status` (8 modules from Plan 01-01 scaffolding via IDE; not in `.gitignore`) — see `.planning/phases/01-foundations-day-1-contracts/deferred-items.md` D-01 | OPEN — recommend Phase 1 hygiene cleanup or Phase 11 DevOps adds `**/bin/` to `.gitignore` | 2026-04-28 (Plan 01-03) |
| Test infrastructure | `./gradlew :payment-service:test` starts AMQP infrastructure during `StockReservedConsumerIntegrationTest` and fails without a RabbitMQ test container/credentials (`AmqpAuthenticationException`). Plan-specific tests pass; see `.planning/phases/06-payment-iyzico/deferred-items.md` D-06-01 | OPEN — revisit when Phase 6 updates the stock-reserved consumer flow | 2026-04-30 (Plan 06-01) |

## Session Continuity

Last session: 2026-05-02T15:02:59.995Z
Stopped at: Phase 11 planned with accepted verification and decision-coverage overrides
Resume file: .planning/phases/11-frontend-chat-assistant-devops-deploy/11-01-PLAN.md
Next: `/gsd-execute-phase 11` — run chat assistant UI + local docker-compose deploy + public tunnel handoff plans.
