---
phase: 01-foundations-day-1-contracts
type: plan-outline
created: 2026-04-28
status: draft
---

# Phase 1 — Plan Outline (Chunked Mode)

> Outline-only artifact. The orchestrator will spawn one chunked-detail planner per Wave-1+ row to write the actual `01-NN-PLAN.md` files. Plans 01-01 and 01-02 are already on disk and reproduced here for the dependency map; planners must not regenerate them.

## Plan Table

| Plan ID | Objective | Wave | Depends On | Requirements |
|---------|-----------|------|------------|--------------|
| 01-01 | (existing on disk) Gradle multi-module bootstrap (`settings.gradle.kts` + 8 subprojects + `libs.versions.toml`), Java 21 toolchain, `.gitignore`/`.gitleaks.toml`/`.env.example` secret hygiene, gitleaks + CI GH Actions workflows, PROJECT.md updated to reflect D-15..D-18 revised (deploy = local docker-compose on the candidate's machine, demo URL via Cloudflare Tunnel / ngrok; AWS dropped) | 0 | — | ARCH-01, QUAL-09, DEV-07 |
| 01-02 | (existing on disk) Day-1 contracts: `.planning/saga-contracts.md` (envelope, 4 exchanges, 12 queues, DLX/DLQ convention, locked retry wording "1s/5s then DLQ", outbox + processed_events idempotency rules) + 9 JSON-Schema 2020-12 files (envelope + 8 payloads from ARCHITECTURE.md §3.4) + `.planning/api-contracts.md` (per-service endpoint inventory, gateway routing prefix map, public allowlist, Authorization-strip rule, correlation-ID propagation policy, RFC-7807 problem+json shape with 5 example responses, SSE caveat for Phase 8) | 1 | 01-01 | ARCH-12, ARCH-05, QUAL-07 |
| 01-03 | Postgres + RabbitMQ infra bootstrap: `infra/postgres/init.sh` (env-var-aware shell script per RESEARCH §4.4 — pure `.sql` cannot interpolate envs) creating 13 schemas, 13 distinct DB users, role-level deny matrix (D-01..D-04), `ALTER USER … SET search_path` per service, pgvector extension; `docker-compose.yml` infra-only profile (`pgvector/pgvector:pg16`, `rabbitmq:4.3-management`, healthchecks for `pg_isready` + `rabbitmq-diagnostics ping`, env-file sourcing from `.env`); applies the `orders` (plural) schema rename (Cross-Cutting #7 from PATTERNS.md — `order` is SQL reserved) so init.sh writes `orders` schema + `orders_user` and the env var is `ORDERS_DB_PASSWORD` | 1 | 01-01 | ARCH-09, ARCH-10 |
| 01-04 | Shared library modules: `common-error` (RFC-7807 `ProblemDetailControllerAdvice` with the 7 locked fields per D-09 + `ApiErrorCode` enum + 1 unit test); `common-logging` (5 wires per RESEARCH §4.9 — `CorrelationIdFilter extends OncePerRequestFilter` for inbound HTTP, `CorrelationIdRestClientInterceptor` + `RestClientConfig` for outbound HTTP, `CorrelationIdMessagePostProcessor` + `RabbitTemplateConfig` for outbound AMQP, `RabbitListenerCorrelationAspect` skeleton for inbound AMQP — Phase 5+ activates) + Spring Boot 3 auto-config registration via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`; `common-events` (`Envelope` record per ARCHITECTURE.md §3.4, `RabbitRetryConfig` with `RetryInterceptorBuilder.stateful().maxAttempts(3).backOffOptions(1000L, 5.0, 30000L)` per Cross-Cutting #8, `AbstractEventSchemaTest` base class loading classpath `saga-schemas/<eventType>.schema.json` via `networknt/json-schema-validator 3.0.2` per D-08 drift gate); copies the 9 schemas from `.planning/saga-contracts/*.schema.json` into `common-events/src/main/resources/saga-schemas/` so they ship on the test classpath. All three modules are plain `java`-plugin-only (no Boot plugin per Cross-Cutting #1) | 1 | 01-01, 01-02 | QUAL-06 |
| 01-05 | Infra services pair: `eureka-server` (`@EnableEurekaServer` + `application.yml` with `register-with-eureka:false`, `enable-self-preservation:false`, `response-cache-update-interval-ms:5000` per RESEARCH §4.3) and `config-server` (`@EnableConfigServer` + `spring.profiles.active=native` + `search-locations: classpath:/config/` per CD-05; ships the shared `config-server/src/main/resources/config/application.yml` baseline with default Eureka URL + datasource template + Springdoc keys); both Spring Boot apps wire `spring.config.import` retry per RESEARCH §4.3 to satisfy ARCH-11 cold-boot resilience | 2 | 01-01, 01-04 | ARCH-02, ARCH-03, ARCH-11 |
| 01-06 | `api-gateway` Spring Boot WebFlux shell using **NEW Northfields starter `spring-cloud-starter-gateway-server-webflux`** (RESEARCH §4.1 headline — old `spring-cloud-starter-gateway` deprecated) with `runtimeOnly("spring-boot-properties-migrator")` bridge; explicit Tomcat/web exclusions in `build.gradle.kts` configurations block per Pitfall #2; `GatewayCorrelationIdFilter` (reactive `GlobalFilter`) generating UUID `X-Correlation-Id` if absent and propagating through Reactor Context; `GatewayHeaderInjectionFilter` no-op stub (Phase 3 fleshes); `permitAll()` security chain (D-14 — Phase 3 flips); `config-server/src/main/resources/config/api-gateway.yml` with `spring.cloud.gateway.server.webflux.discovery.locator.enabled=true`, `lower-case-service-id:true`, `httpclient.response-timeout:60s`, `management.endpoints.web.exposure.include=health,info,gateway` (success criterion #1: `/actuator/gateway/routes` reachable), Springdoc aggregator config with `urls=[]` empty list (Phase 4+ phases append), commented-out SSE-route shape forward-reference for Phase 8 (per `.planning/api-contracts.md` §6); 503 on unmatched paths; uses `springdoc-openapi-starter-webflux-ui:2.8.17` (NOT `-webmvc-ui` — would re-import Tomcat) | 2 | 01-01, 01-02, 01-04 | ARCH-04, QUAL-01 |
| 01-07 | `service-template` archetype (CD-02 hybrid pick — runnable Gradle subproject AND `service-template/skeleton/` copy-paste dir): Spring Boot 3.5.14 main class, `application.yml` using `spring.config.import=configserver:http://config-server:8888?fail-fast=true&max-attempts=10&max-interval=1500&multiplier=1.2&initial-interval=1100` (Cross-Cutting #2 — NOT bootstrap.yml; satisfies ARCH-11 retry); Eureka client cold-boot resilience config (`registry-fetch-interval-seconds:5`, `prefer-ip-address:true` per RESEARCH §4.3); dependencies on `common-error` and `common-logging` modules; `logback-spring.xml` using `logstash-logback-encoder:8.0` with `<includeMdcKeyName>correlationId</includeMdcKeyName>`; Springdoc per-service config (`/v3/api-docs` + `/swagger-ui.html`); Flyway 12.5.0 + `flyway-database-postgresql:12.5.0` wired with `create-schemas:false`, `default-schema:${flyway.schema}`, non-owner-user pattern per RESEARCH §4.5; ships `db/migration/V1__init_processed_events.sql` (saga consumer inbox table per D-11 + ARCHITECTURE.md §3.5); writes the `config-server/src/main/resources/config/service-template.yml` baseline that downstream services override per CD-05; surfaces the CD-02 hybrid-vs-pure-skeleton trade-off in PLAN.md per CONTEXT.md `<specifics>` instruction | 2 | 01-01, 01-02, 01-04 | QUAL-01, ARCH-11 |
| 01-08 | `infra-tests` boundary smoke verification (D-05 / T-01-02 mitigation): `CrossSchemaDenyTest` Testcontainers JUnit 5 test booting `pgvector/pgvector:pg16`, mounting `infra/postgres/init.sh` via `withCopyFileToContainer(MountableFile.forClasspathResource("init.sh"), "/docker-entrypoint-initdb.d/00-init.sh")`, `withEnv` for all 13 `*_DB_PASSWORD` slots, opening two non-superuser connections (e.g. `product_user` and `cart_user`) and asserting `SELECT 1 FROM identity.users` / cross-pairs throw `SQLException` containing "permission denied for schema identity"; copies `infra/postgres/init.sh` to `infra-tests/src/test/resources/init.sh` for classpath access; `@Container` static field for singleton container reuse per Pitfall #22 mitigation; satisfies ROADMAP success criterion #4 for Phase 1 — verifies ARCH-09 boundary at runtime | 3 | 01-03, 01-07 | ARCH-09 |

## Coverage Audit

| REQ-ID | Plan(s) | Notes |
|--------|---------|-------|
| ARCH-01 | 01-01 | Gradle 8-subproject layout > 10-service mandate (eureka, config, gateway, service-template + 4 libs/test → final 13 services in Phases 3-9) |
| ARCH-02 | 01-05 | Eureka server up + cold-boot config in template baseline (01-07) |
| ARCH-03 | 01-05 | Config server with native profile and shared `application.yml` |
| ARCH-04 | 01-06 | Northfields gateway with discovery locator |
| ARCH-05 | 01-02 | Saga + DLQ contract locked (RabbitMQ topology in saga-contracts.md) |
| ARCH-09 | 01-03 (writes init.sh DDL), 01-08 (verifies via Testcontainers) | Two-arm coverage — DDL + smoke test |
| ARCH-10 | 01-03 (schemas exist), 01-07 (Flyway non-owner-user wiring) | DB schemas + per-service Flyway migration story |
| ARCH-11 | 01-05 (config retry on infra services), 01-07 (Eureka client retry in template) | Cold-boot resilience |
| ARCH-12 | 01-02 | Day-1 contracts EOD deliverable |
| QUAL-01 | 01-06 (gateway aggregator stub), 01-07 (per-service Springdoc baseline) | Aggregator + per-service halves |
| QUAL-06 | 01-04 | JSON Logback + correlation-ID propagation wires |
| QUAL-07 | 01-02 (spec lock), 01-04 (implementation in common-error) | Spec doc + ControllerAdvice |
| QUAL-09 | 01-01 | gitleaks + .gitignore + .env.example baseline |
| DEV-07 | 01-01, 01-03 | docker-compose infra-only profile in 01-03; full profile (DEV-07's "all 13 services") deferred to Phase 11 per D-13 |

All 14 phase requirement IDs are mapped to at least one plan. Plans 01-01 and 01-02 are pre-existing; the orchestrator must spawn detail planners only for 01-03 through 01-08 (six new plans).

## Wave Structure

```
Wave 0:  [01-01]                              (Gradle bootstrap)
Wave 1:  [01-02] [01-03] [01-04]              (parallel — disjoint files)
Wave 2:  [01-05] [01-06] [01-07]              (parallel — disjoint subprojects + disjoint config-repo YAMLs)
Wave 3:  [01-08]                              (boundary smoke verification)
```

### File-ownership matrix (verifies parallelism in each wave)

**Wave 1 (no overlap):**
- 01-02 → `.planning/saga-contracts*`, `.planning/api-contracts.md`
- 01-03 → `infra/postgres/init.sh`, `docker-compose.yml`, `infra/postgres/.gitignore` (optional)
- 01-04 → `common-error/**`, `common-logging/**`, `common-events/**` (replaces the `.gitkeep` stubs from 01-01)

**Wave 2 (no overlap):**
- 01-05 → `eureka-server/**`, `config-server/**` (incl. only `config-server/src/main/resources/config/application.yml`)
- 01-06 → `api-gateway/**`, `config-server/src/main/resources/config/api-gateway.yml`
- 01-07 → `service-template/**`, `config-server/src/main/resources/config/service-template.yml`

The three Wave-2 plans each own their boot subproject AND their dedicated config-repo YAML — no shared file. (01-05 must intentionally NOT touch `api-gateway.yml` or `service-template.yml`; 01-06 and 01-07 each create their own.)

**Wave 3 (sequential):**
- 01-08 → `infra-tests/**` (replaces the `.gitkeep` from 01-01); reads `infra/postgres/init.sh` (created in 01-03) by classpath copy

## Rationale and Deviations from Suggested Structure

The suggested structure proposed merging eureka-server, config-server, and api-gateway into a single Plan 01-05. I split it into **01-05 (eureka + config-server)** and **01-06 (api-gateway shell)** for two reasons:

1. **Context budget.** The api-gateway plan alone carries the heaviest cognitive load in Phase 1: the Northfields starter rename (`spring-cloud-starter-gateway-server-webflux` per RESEARCH §4.1 — verified high-impact finding), the Pitfall #2 Tomcat-exclusion configuration block, two `GlobalFilter` classes (`GatewayCorrelationIdFilter` reactive variant + `GatewayHeaderInjectionFilter` stub), the `api-gateway.yml` config with discovery-locator + Springdoc aggregator + commented-out SSE forward-reference, plus a verify-time `:bootRun --debug` step (Cross-Cutting #6) to confirm the property-prefix binding. Bundling that with eureka and config-server (each effectively a 30-LOC `@EnableX` Boot main + a small yml) would push the plan past the ~50% context target. Eureka and config-server, being very thin, comfortably fit in one plan together (01-05), keeping the boot-service trio split 2-and-1 by complexity rather than 3-in-one.

2. **Parallelism gain.** Three small plans in Wave 2 can all execute in parallel because (a) each owns its own Gradle subproject and (b) each writes its dedicated YAML in `config-server/src/main/resources/config/` (`application.yml`, `api-gateway.yml`, `service-template.yml`) with zero file overlap. Combining them into one plan would force serial execution of work that has no real dependency. The split also makes 01-07 (service-template) cleanly independent of 01-05 and 01-06 — it doesn't need them to be coded before it can be coded; it only needs them at runtime.

I retained the suggested 01-08 boundary smoke test as a Wave-3 verification plan (its own plan rather than tacked onto 01-03) because it depends on **both** 01-03 (init.sh content) and the `infra-tests/` Gradle subproject having Testcontainers wired (which 01-07's archetype patterns inform — the testcontainers-bom is already imported in the root build from 01-01, but the actual `infra-tests/build.gradle.kts` module wiring is cleanest as part of the verification plan). Putting it in its own wave also gives clean separation between "build the infra" (Waves 1–2) and "prove the infra holds the boundary" (Wave 3) — which is the actual structure the ROADMAP success criterion #4 rewards.

I kept the suggested REQ-ID assignments unchanged with one clarification: ARCH-10 is split across 01-03 (creates the schemas Flyway will operate against) and 01-07 (wires Flyway with `create-schemas:false` non-owner-user config). DEV-07 is split across 01-01 (the `.env.example` and `.gitignore` Wave-0 hygiene) and 01-03 (the `docker-compose.yml` infra-only profile that satisfies the Phase-1 portion of DEV-07 per D-13 — the full multi-service profile is deferred to Phase 11). QUAL-01 is split across 01-06 (gateway Springdoc aggregator with empty `urls=[]`) and 01-07 (per-service Springdoc baseline in service-template). These splits reflect the natural seam between "set up the slot" and "wire the consumer" without inflating any single plan.

## OUTLINE COMPLETE

**Plan count:** 8 total (2 existing + 6 new to be detailed)
