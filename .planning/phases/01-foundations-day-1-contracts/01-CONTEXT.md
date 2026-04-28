# Phase 1: Foundations + Day-1 Contracts - Context

**Gathered:** 2026-04-28
**Status:** Ready for planning

<domain>
## Phase Boundary

Stand up the multi-module Gradle skeleton, the infra trio (eureka-server, config-server, api-gateway shell) running against a docker-compose Postgres 16 + pgvector and RabbitMQ 4.x, ship a copy-pasteable `service-template` Gradle subproject that every later phase clones, and lock the Day-1 contracts (saga events + REST surfaces + cross-cutting policies) so every downstream phase reads them rather than reinventing them. Lock the deploy target to "local docker-compose on the candidate's machine, demo URL via tunnel" in PROJECT.md (AWS dropped — no coordinator query, no Day-2 deadline).

In scope:
- Multi-module Gradle root + flat top-level subprojects (no business services scaffolded yet)
- `infra/postgres/init.sql` creating 13 schemas + 13 users + REVOKE/GRANT + per-user `search_path` defaults
- `docker-compose.yml` for Postgres+pgvector, RabbitMQ, eureka, config, gateway (infra-only profile)
- Three running infra services: eureka-server, config-server, api-gateway shell
- `service-template` archetype with all cross-cutting wiring
- Shared Gradle modules: `common-error`, `common-logging`, `common-events`, `infra-tests`
- Day-1 contracts: `.planning/saga-contracts.md` + `.planning/saga-contracts/*.schema.json`, `.planning/api-contracts.md`, plus the four cross-cutting policy docs/sections
- Deploy target locked (local docker-compose on the candidate's machine; demo URL via Cloudflare Tunnel / ngrok) — recorded in PROJECT.md Key Decisions; Phase 11 picks the tunnel implementation

Out of scope (later phases own these):
- Business service implementations (identity, product, cart, order, payment, notification, search, ai-service, mcp-server)
- JWT validation logic at gateway (Phase 3)
- Saga consumers and event publishers (Phase 5+)
- Iyzico, Gemini, MCP, frontend, deploy, CI

</domain>

<decisions>
## Implementation Decisions

### Postgres Isolation Model
- **D-01:** Schema-per-service in a **single database `n11`**. Each *stateful* service owns one schema (`identity`, `product`, `inventory`, `cart`, `orders`, `payment`, `notification`, `search`, `ai`, `mcp`) — naming follows the service name minus the `-service` suffix. **10 schemas total** (corrected during planning: eureka-server, config-server, and api-gateway are stateless compute and own no Postgres schema; the original "13 schemas" wording counted these three but they have no relational state to isolate). The schema for `order-service` is renamed to `orders` (plural) because `order` is a SQL reserved word — see saga-contracts.md §9 schema-naming note. Resolves the doc conflict where PROJECT.md said "DB-per-service" while ARCH-09 + CLAUDE.md said "schema-per-service with role-level deny" — ARCH-09 wording wins because role-level deny only meaningfully exists at schema granularity in Postgres.
- **D-02:** Each service has a **distinct DB user** (`identity_user`, `product_user`, …). Cross-schema deny enforced via `REVOKE USAGE ON SCHEMA <other> FROM <user>`; the user owns its own schema and has `USAGE` only on `public` (for the `pgvector` extension types) and its own schema.
- **D-03:** Boundary enforcement lives in **`infra/postgres/init.sql`**, mounted into the Postgres container by docker-compose. Single declarative file: creates all 13 schemas, all 13 users with passwords from environment variables, grants/revokes, sets `search_path`. Runs once on first container boot via Postgres entrypoint convention. Per-service Flyway runs *after* init.sql against its own schema as its own user.
- **D-04:** Per-service connection convention: **server-side `ALTER USER <svc>_user SET search_path = <svc>, public;`** baked into init.sql. Every service's JDBC URL is identical (`jdbc:postgresql://postgres:5432/n11`); HikariCP and Flyway need no per-service URL flags. Schema choice is server-side, hidden from app config.
- **D-05:** Boundary smoke test in `infra-tests/` Gradle subproject: a Testcontainers JUnit 5 test boots the same Postgres image, applies `init.sql`, opens two connections (e.g. `product_user` and `cart_user`), and asserts that `product_user` receives `permission denied` on `SELECT 1 FROM cart.cart_items`. Failing this test fails the build. Satisfies success criterion #4.

### Day-1 Contracts (the bikeshedding kill — Pitfall #26)
- **D-06:** `.planning/saga-contracts.md` is a **narrative doc + sibling JSON-Schema folder**. Narrative covers: envelope (`eventId`, `eventType`, `eventVersion`, `occurredAt`, `correlationId`, `causationId`, `producer`, `payload`), exchange/queue topology table, idempotency rules (transactional outbox + `processed_events` inbox), retry policy. Folder `.planning/saga-contracts/` ships one `.schema.json` per event: `envelope.schema.json`, `order-created.schema.json`, `stock-reserved.schema.json`, `stock-reserve-failed.schema.json`, `payment-completed.schema.json`, `payment-failed.schema.json`, `order-confirmed.schema.json`, `order-cancelled.schema.json`, `stock-released.schema.json`. Schemas are content sourced from ARCHITECTURE.md §3.4 — research already specified the payloads, this phase just formalizes them.
- **D-07:** `.planning/api-contracts.md` is **endpoint table + I/O sketches per service** (verb + path + auth requirement + brief request/response field list per endpoint, plus the gateway routing prefix map). Springdoc generates the full OpenAPI rigor from code at impl time — Day-1 doesn't duplicate it. Source: ARCHITECTURE.md §2 per-service contract tables — formalize, don't re-research.
- **D-08:** **CI drift gate:** an `AbstractEventSchemaTest` base class lives in `common-events` (or `infra-tests/`). Every Phase 5+ saga integration test that publishes an event uses it; the base loads the canonical `.schema.json` for the event's `eventType` and asserts the produced JSON validates. Producer drift fails the build at the producing service.
- **D-09:** Four cross-cutting policy docs/sections also lock Day 1 (all four selected):
  - **Correlation-ID propagation policy** — gateway always generates `X-Correlation-Id` if absent; every service's servlet filter reads the header into MDC; outbound `RestClient` + `RabbitTemplate` interceptors re-inject it; AMQP envelope carries `correlationId` (already in `D-06` envelope). Lives as a section in `api-contracts.md`.
  - **RFC-7807 problem+json shape spec** — exact fields locked: `type`, `title`, `status`, `detail`, `instance`, `correlationId`, `errors[]` (validation). 4–5 example error responses included. Implemented in `common-error` module's @ControllerAdvice. Satisfies QUAL-07.
  - **Gateway routing table** — authoritative mapping `/api/v1/<svc>/**` → service ID, plus public allowlist (`POST /auth/login`, `POST /auth/register`, `GET /products/**`, `GET /search/**`, `POST /chat/**`) and Authorization-strip rule. Lives in `api-contracts.md`. Pulled forward from ARCHITECTURE.md §2.3.
  - **DLQ + retry policy spec** — Spring AMQP RetryTemplate: 3 attempts, exponential backoff 1s/5s/30s. DLX naming `<exchange>.dlx`, DLQ `<queue>.dlq`. Manual replay convention noted. Lives as a section in `saga-contracts.md`.

### Skeleton Scope
- **D-10:** Phase 1 scaffolds **infra trio + a `service-template` Gradle subproject**. Business services are NOT scaffolded in Phase 1 — each later phase copies the archetype to scaffold its module. Avoids 13 empty modules churning in the repo and keeps Day-1 lean while still giving every later phase a consistent, no-decisions-required starting point.
- **D-11:** `service-template` ships with all four checked items wired:
  1. Spring Boot 3.5.14 + `spring-cloud-starter-netflix-eureka-client` + `springdoc-openapi-starter-webmvc-ui 2.8.17` + `spring-boot-starter-actuator` (`/actuator/health`, `/actuator/info`).
  2. `logstash-logback-encoder` + `logback-spring.xml` producing JSON logs; servlet filter reads `X-Correlation-Id` into MDC; outbound `RestClient` + `RabbitTemplate` interceptors propagate it.
  3. Common @ControllerAdvice from `common-error` (RFC-7807 mapper); `common-error` module is a dependency.
  4. Flyway 12.5.0 wired to `spring.datasource.*` from config-server; `db/migration/V1__init.sql` creates the schema-local `processed_events(event_id PK, consumer, processed_at)` inbox table that every saga consumer reuses.
- **D-12:** Gradle layout is **flat top-level subprojects**. `settings.gradle.kts` includes: `eureka-server`, `config-server`, `api-gateway`, `common-error`, `common-logging`, `common-events`, `service-template`, `infra-tests`. Phases 8–9 will add `ai-port` and `agent-toolset` at the same level. No `services/*` or `libs/*` parent dirs.
- **D-13:** Phase 1 docker-compose runs **infra services only** in containers (Postgres+pgvector, RabbitMQ, eureka-server, config-server, api-gateway). The 10 business services run from IDE / `:bootRun` against this infra during dev. DEV-07 ("full local stack ... for development and demo") will be satisfied in Phase 11 once Jib images exist for every service — Phase 11 extends this same compose with a `full` profile.
- **D-14:** **api-gateway shell behavior pre-services:** `spring.cloud.gateway.discovery.locator.enabled=true` (Eureka-driven auto-routes), `/actuator/health` + `/actuator/gateway/routes` exposed (success criterion #1: "routes visible"), Springdoc aggregator config present but `springdoc.swagger-ui.urls=[]` empty. JWT/CORS/header-injection `GlobalFilter` classes are coded but the security chain is `permitAll()` for now — Phase 3 flips to JWT validation when identity-service is up. Returns 503 on unmatched paths.

### Deployment (LOCKED — user decision 2026-04-28; revised same day to drop AWS entirely)
- **D-15:** **Deploy target = local docker-compose on the candidate's machine.** Original Day-1 lock (AWS Elastic Beanstalk + RDS) is superseded — the candidate confirmed local compute is sufficient to host all 13 services + Postgres + RabbitMQ in one compose stack. No AWS account, no coordinator query, no fallback deadline. PROJECT.md Open Questions "AWS deploy scope" row updated to RESOLVED-revised; Key Decisions row replaced; STATE.md Blockers/Concerns updated. **Caveat for the candidate:** the bootcamp brief originally listed AWS deployment as must-have — coordinator confirmation that local-host + tunnel deployment is acceptable is recommended.
- **D-16:** **Shape = single docker-compose stack on the candidate's host.** All 13 service Jib images + Postgres-16 + RabbitMQ-4 in one `docker-compose.yml` with a `full` profile. Pitfall #12 (the EB-vs-13-microservices mismatch) is no longer in scope — it only existed if EB had to host 13 separate apps. Phases 1–10 still produce identical artifacts; only Phase 11 changes shape.
- **D-17:** **Public exposure = tunnel from the candidate's host:**
  1. **Cloudflare Tunnel** (preferred) via `cloudflared` — stable hostname (requires a personal domain on Cloudflare; free tier covers one tunnel), survives reconnects cleanly, runs as a sidecar container in the same compose.
  2. **ngrok** (fallback) — zero-config, random subdomain on free tier, paid tier for stable hostname. Quick to swap if Cloudflare misbehaves the morning of the demo.
  Phase 6 verifies the chosen tool works for Iyzico webhook traffic per the verify-before-implement policy; Phase 11 wires it as the demo URL.
- **D-18:** **What Phase 11 owns** (not Phase 1): the `full` compose profile (13 Jib images by tag), the tunnel sidecar container (`cloudflared` or `ngrok`), demo-URL pointer in README, Slack webhook secret, GitHub Actions release-tag job that publishes Jib images to GHCR / Docker Hub, and the docker-compose `restart: unless-stopped` posture so the demo survives a candidate-machine reboot.

**Phase 1 implication:** nothing in Phase 1 changes. All deliverables (infra trio, service-template, contracts, init.sql, docker-compose) are identical under the local-deploy decision; the original "infra-only profile in compose" Day-1 scaffold matches exactly. The PROJECT.md update task in 01-01-PLAN now writes the local-deploy Key Decisions row instead of the EB+RDS one.

### Claude's Discretion
- **CD-01:** Concrete user passwords, exact pgvector extension version pinning vs latest 0.8.x, Logback JSON encoder field set (caller, thread, etc.) and verbosity per env, Springdoc aggregator URL rendering — pick reasonable defaults.
- **CD-02:** Whether `service-template` is a Gradle subproject that other modules `apply from:` vs a literal copy-paste skeleton — research at planning time which is more idiomatic for Spring Boot multi-module Gradle, pick. (User leaning: the latter is simpler to clone, the former gives more centralized cross-cutting; flag the trade-off in PLAN.md.)
- **CD-03:** Choice of JSON-Schema validator library for the drift gate (e.g., `networknt/json-schema-validator`, `everit-org/json-schema`) — pick whichever is most current, pin version.
- **CD-04:** gitleaks placement (pre-commit hook + CI vs CI-only) — recommend CI-only for Day-1 (cheaper) with a note that Phase 11 may add a pre-commit hook. Lock `.gitignore` to cover `.env`, `secrets/`, `application-local.yml`.
- **CD-05:** config-server backing — `native` filesystem (config repo lives at `config-server/src/main/resources/config/`) for dev, with a single `application.yml` + per-service `<svc>-service.yml`. Git-backed deferred unless an interview deliverable demands it.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project Context (always)
- `.planning/PROJECT.md` — locked decisions, constraints, open questions; especially Key Decisions table and Open Questions section
- `.planning/REQUIREMENTS.md` — Phase 1 reqs: ARCH-01, ARCH-02, ARCH-03, ARCH-04, ARCH-05, ARCH-09, ARCH-10, ARCH-11, ARCH-12, QUAL-01, QUAL-06, QUAL-07, QUAL-09, DEV-07
- `.planning/ROADMAP.md` §"Phase 1" — goal, depends-on, success criteria (5 numbered), risks, research need
- `.planning/STATE.md` — current position; deploy-target lock (local docker-compose, no AWS) flagged as Phase 1 deliverable
- `CLAUDE.md` — non-negotiable rules; rule 1 (provider-agnostic LLM not Phase 1), rule 5 (no secrets in source — Phase 1 deliverable), rule 6 (UI Turkish / code English)

### Architecture (the spine — research output)
- `.planning/research/ARCHITECTURE.md` §1 — Topology diagram (gateway, services, RabbitMQ exchanges, Postgres schemas)
- `.planning/research/ARCHITECTURE.md` §2 — Per-service contracts (REST surfaces + events) — source for `api-contracts.md` Day-1 endpoint table
- `.planning/research/ARCHITECTURE.md` §3 — Order saga happy path + 4 compensation paths + 7 event payload schemas — source for `saga-contracts.md` and `.planning/saga-contracts/*.schema.json`
- `.planning/research/ARCHITECTURE.md` §3.5 — Idempotency / retries / DLQ — source for processed_events inbox table in `service-template` and DLQ retry policy
- `.planning/research/ARCHITECTURE.md` §5 — Gateway responsibilities (CORS, JWT, header injection, SSE, Springdoc aggregation, rate limit) — source for gateway routing table policy
- `.planning/research/ARCHITECTURE.md` §8 — Cross-cutting concerns (API versioning, RFC-7807 error shape, observability, OpenAPI, configuration) — direct policy source
- `.planning/research/ARCHITECTURE.md` §10 — Anti-patterns (cross-DB joins, dual writes, JWT-forwarding) — Phase 1 must structurally prevent the cross-DB-join one via D-01..D-05
- `.planning/research/ARCHITECTURE.md` §13 — Risk Register: schema-per-service developer error mitigation row maps directly to D-05

### Stack & Pitfalls
- `.planning/research/STACK.md` — Java 21, Spring Boot 3.5.14, Spring Cloud 2025.0.x (Northfields), Gradle multi-module, Flyway 12.5.0, Springdoc 2.8.17, Testcontainers; "What NOT to use" subsection (no Spring Boot 4.0, no RestTemplate, no Hystrix/Ribbon)
- `.planning/research/PITFALLS.md` — esp. #2 (gateway reactive vs MVC classpath collision — Phase 1 must keep gateway WebFlux-only), #4 (Eureka cold-boot race — D-11 wires retry/backoff, satisfies ARCH-11), #11 (cross-schema joins — D-01..D-05 are the structural mitigation), #26 (Day-1 bikeshedding — D-06..D-09 are the kill)
- `.planning/research/SUMMARY.md` §"Top 5 to put on Day-1 watch" — pitfalls 2/3 directly map to Phase 1 deliverables

### External docs (verify-before-implement policy — read at impl time)
- https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway/index.html — Gateway 2025.0.x (Northfields) discovery locator + reactive routing config; required for D-14
- https://docs.spring.io/spring-cloud-config/reference/server.html — Spring Cloud Config Server 2025.0.x; native vs git backing options for CD-05
- https://www.postgresql.org/docs/16/ddl-schemas.html — Postgres 16 schema + ALTER USER SET search_path semantics; required for D-04
- https://docs.spring.io/spring-amqp/reference/index.html — Spring AMQP RetryTemplate, RabbitListener manual ack, DLX/DLQ declaration via `@Bean Declarables`; D-09 retry policy
- https://flywaydb.org/documentation/concepts/migrations — Flyway 12.5.0 schema/user wiring; per-service migration with constrained user
- https://docs.spring.io/spring-boot/3.5/reference/features/external-config.html — externalized config + env var binding; D-03 secret flow
- https://www.testcontainers.org/modules/databases/postgres/ — Testcontainers Postgres for D-05 boundary smoke
- https://datatracker.ietf.org/doc/html/rfc7807 — RFC-7807 problem+json normative spec for D-09 error shape

### Day-1 deliverables produced BY this phase (will become refs for later phases)
- `.planning/saga-contracts.md` — locked saga event taxonomy + envelope + queue topology + DLQ policy
- `.planning/saga-contracts/envelope.schema.json` + 8 payload `.schema.json` files
- `.planning/api-contracts.md` — locked per-service REST surface + gateway routing table + correlation-ID policy + RFC-7807 shape
- `infra/postgres/init.sql` — boundary enforcement DDL
- `service-template/` — the cross-cutting wiring archetype every later service clones

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **None — greenfield.** No `src/`, no Gradle build files yet exist; the project is `.planning/`-only at the moment. Every artifact this phase produces is genuinely new.

### Established Patterns
- **The .planning/ tree is the spine.** Pre-locked decisions live in PROJECT.md / ARCHITECTURE.md / STACK.md; this phase's job is to translate research into running scaffolding + machine-readable contracts. Researcher and planner read those files; they don't need to re-research stack versions or saga payloads.
- **Verify-before-implement policy** (PROJECT.md): before writing any code that touches an external SDK (Spring Cloud Gateway, Spring AMQP, Flyway, Testcontainers), the planner cites the relevant doc section. Phase 1 code is mostly Spring framework wiring — research already cited the canonical references; planner should re-fetch the 2025.0.x specifics for gateway routing and config-server native backend.
- **Schema-per-service is now structural, not aspirational.** D-01..D-05 turn the boundary into a runtime enforcement (REVOKE + smoke test) so future phases physically can't violate it.

### Integration Points
- **Gateway ↔ Eureka:** discovery locator is the integration mode (D-14). Service IDs registered in Eureka become routes automatically; explicit routes added per-phase only when discovery-locator behavior diverges from the canonical `/api/v1/<svc>/**` shape (e.g., SSE routes need `metadata.response-timeout: 0` per ARCHITECTURE.md §5).
- **Gateway ↔ Config Server:** gateway pulls its config (CORS allow-list, route metadata, future JWT issuer URI) from config-server. Phase 1 wires the bootstrap; Phase 3 puts JWT issuer there.
- **Every service ↔ Postgres:** via init.sql-defined user; default search_path means JDBC URL is uniform.
- **Every service ↔ Logback MDC:** correlation-ID propagation is template-baked, not per-service.
- **`common-events` ↔ saga-contracts/*.schema.json:** the JSON Schema files ship as resources inside `common-events`; `AbstractEventSchemaTest` loads them via classpath. Every saga producer (Phase 5+) gets free schema validation by extending the abstract test.

</code_context>

<specifics>
## Specific Ideas

- **Database name `n11`** (single, lowercase) — short, project-coded, consistent with the brand framing.
- **Schema names match service names minus `-service` suffix** (e.g., `cart-service` → schema `cart`). Consistent, predictable, no per-schema bikeshedding.
- **DB user names are `<schema>_user`** (e.g., `cart_user`). Same suffix everywhere; init.sql is one repeated pattern.
- **`infra/postgres/init.sql` is THE bootstrap file.** Every detail of D-01..D-05 lives in this one file; reviewers read it once.
- **`service-template` cloned, not depended-on, by later phases.** A planner choosing the alternative ("subproject + apply from") should flag it in PLAN.md; default is clone-and-edit so each service can drift independently when it has reason to.
- **Deploy is locked to local docker-compose on the candidate's machine** (D-15..D-18); no AWS, no coordinator query, no Day-2 deadline. Phase 11 owns the tunnel pick + `full`-profile compose wiring.
- **The gateway shell's `permitAll()` posture is intentional in Phase 1** and gets flipped in Phase 3 — this is in the success criteria of Phase 3 (JWT validation), not Phase 1. Don't accidentally over-deliver.

</specifics>

<deferred>
## Deferred Ideas

- **gitleaks pre-commit hook** — Phase 1 ships gitleaks in CI only (CD-04). Add the pre-commit hook alongside the GH Actions release pipeline in Phase 11 (DevOps).
- **Git-backed config-server** — CD-05 picks `native` filesystem for Day 1. If the demo posture later wants a git-backed config repo (more "real" prod feel), revisit during Phase 11. Trade-off: extra repo to maintain vs cleaner deploy story.
- **Pre-commit hooks generally (Spotless, ktlint, checkstyle)** — code-style enforcement deferred. Phase 1 ships only what's needed to compile and run.
- **Distributed tracing (Sleuth/OpenTelemetry)** — out of scope per PROJECT.md ("Production-grade observability stack ... only basic SLF4J/Logback per service"). Correlation IDs in flat logs are the substitute. Don't add Sleuth in Phase 1.
- **Redis for rate limiting** — gateway rate-limit can stay in-memory (or off) for v1. ARCHITECTURE.md §5 notes the trade-off; revisit if a multi-instance gateway becomes a Phase 11 concern.
- **Full multi-instance compose** — DEV-07's "all 13 services in compose" gets satisfied in Phase 11 via Jib + a `full` profile on the same compose file. Don't pre-build it now.
- **JWKS endpoint, JWT issuer config** — entirely Phase 3 territory, but a placeholder issuer-URI key in `application.yml` is harmless if it falls out naturally; otherwise leave for Phase 3.
- **Static-yıldız placeholder** (reviews mitigation) — Phase 10/11 frontend concern; not Phase 1.

</deferred>

---

*Phase: 1-foundations-day-1-contracts*
*Context gathered: 2026-04-28*
