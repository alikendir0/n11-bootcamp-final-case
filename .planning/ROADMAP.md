# Roadmap: n11 Bootcamp Final Case — Agentic E-Commerce Clone

## Overview

A 6-day, AI-assisted build of a 13-service Spring Boot microservices e-commerce backend with a Turkish React storefront and an agentic-commerce layer (Gemini chat assistant + MCP server sharing one toolset). The journey runs Foundations → Identity → Catalog → Cart/Order → Payment → Notification → AI Port (the SOLID centerpiece) → MCP → Frontend → Deploy. Day-1 deliverables (saga + REST contracts, n11 Playwright recon) front-load every blocker that can sink the demo. Deploy target is the candidate's local machine via docker-compose (sufficient compute on hand); the demo URL is exposed publicly via a Cloudflare Tunnel or ngrok for the interview window. Granularity is fine; phases are sized so an AI agent can complete one in ~half a day. Parallelization is encoded: Phases 2 and 4 can run alongside earlier phases, and Phase 7 can run alongside Phase 6.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [x] **Phase 1: Foundations + Day-1 Contracts** - Multi-module skeleton, infra services, saga + REST contracts locked, deploy target locked to local docker-compose (no AWS) — completed 2026-04-28 (8/8 plans, 4 waves; SC-1 stack healthy in 25s under 60s budget)
- [ ] **Phase 2: Frontend Recon + Toolchain Lock** - Playwright recon of n11.com, frontend toolchain decision recorded
- [x] **Phase 3: Identity + Gateway Auth** - identity-service issues JWT; gateway validates and injects user headers — completed 2026-04-29 (6/6 plans, 4 waves; 5/5 must-haves verified by gsd-verifier)
- [ ] **Phase 4: Catalog + Inventory** - product-service, inventory-service, Turkish seed data, basic ILIKE search
- [ ] **Phase 5: Cart & Order Skeleton** - cart-service, order-service, RabbitMQ saga skeleton (no Iyzico yet), idempotency inbox
- [ ] **Phase 6: Payment (Iyzico)** - Iyzico Checkout Form, public webhook reachability, payment-timeout job, payment-fail compensation
- [ ] **Phase 7: Notification (Saga Closure)** - notification-service mock, saga happy-path closure, end-to-end saga integration test
- [ ] **Phase 8: AI Port + Adapter + Agent Toolset** - ai-port module, GeminiChatAdapter, EchoChatProvider second adapter, agent-toolset shared module, ai-service chat
- [ ] **Phase 9: MCP Server** - mcp-server consumes shared agent-toolset, stdio + HTTP+SSE transports, API-key auth bridge
- [ ] **Phase 10: Frontend Storefront** - Turkish React storefront: header/footer/hero/listing/PDP/cart/checkout/account
- [ ] **Phase 11: Frontend Chat Assistant + DevOps Deploy** - Floating chat bubble with SSE streaming, Jib for every service, GH Actions build/test, local docker-compose deploy on the candidate's machine, public demo URL via Cloudflare Tunnel / ngrok, Slack webhook

## Phase Details

### Phase 1: Foundations + Day-1 Contracts
**Goal**: Establish the multi-module Gradle skeleton, infrastructure services (eureka, config, gateway shell), shared cross-cutting conventions (logs, error shape, secrets, schema isolation), and lock Day-1 contracts that every downstream service depends on.
**Depends on**: Nothing (first phase)
**Requirements**: ARCH-01, ARCH-02, ARCH-03, ARCH-04, ARCH-05, ARCH-09, ARCH-10, ARCH-11, ARCH-12, QUAL-01, QUAL-06, QUAL-07, QUAL-09, DEV-07
**Success Criteria** (what must be TRUE):
  1. `docker-compose up` boots Postgres 16 (with pgvector), RabbitMQ 4.x, eureka-server, config-server, and an empty api-gateway, all healthy within 60 seconds, and gateway routes are visible in `/actuator/gateway/routes`.
  2. `.planning/saga-contracts.md` and `.planning/api-contracts.md` are committed and lock every saga event (envelope + 7 event payloads), every cross-service REST call, and every queue/exchange/DLX in RabbitMQ — verified by every later phase referencing them, not redefining them.
  3. The deploy target is locked to "local docker-compose on the candidate's machine, demo URL via tunnel" and recorded in PROJECT.md Key Decisions; no AWS coordinator query is sent.
  4. Per-service Postgres schemas + distinct DB users + Flyway migrations are in place; a smoke integration test fails when one service tries to query another schema.
  5. JSON-structured Logback config + correlation-ID MDC propagation + RFC-7807 problem+json error shape are scaffolded as shared conventions, and no secrets (JWT key, Iyzico key, Gemini key, Slack URL) live in source — gitleaks runs clean in CI.
**Plans**: 8 (4 waves)

  **Wave 0** — bootstrap (no deps)
  - 01-01: Gradle multi-module skeleton + version catalog + gitleaks CI + PROJECT.md deploy-target update (local docker-compose, no AWS) — `ARCH-01, QUAL-09, DEV-07`

  **Wave 1** *(blocked on Wave 0 completion)* — parallel docs + infra + libs
  - 01-02: Day-1 contracts (saga-contracts.md + 9 JSON schemas + api-contracts.md) — `ARCH-12, ARCH-05, QUAL-07`
  - 01-03: Postgres + RabbitMQ infra (init.sh env-aware bootstrap, 10 schemas, role-deny matrix, docker-compose infra-only) — `ARCH-09, ARCH-10, DEV-07`
  - 01-04: Shared library modules (common-error / common-logging / common-events with classpath-only schema loader) — `QUAL-06, QUAL-07`

  **Wave 2** *(blocked on Wave 1 completion)* — parallel Boot apps (Jib-built local images for SC-1 60s budget)
  - 01-05: Eureka server + Config server (native profile, shared application.yml baseline) — `ARCH-02, ARCH-03, ARCH-11`
  - 01-06: api-gateway WebFlux shell (Northfields starter, discovery locator, /actuator/gateway/routes, SC-1 stack smoke owner) — `ARCH-04, QUAL-01`
  - 01-07: service-template archetype (CD-02 hybrid: subproject + skeleton/) — `QUAL-01, ARCH-11, ARCH-10`

  **Wave 3** *(blocked on 01-03 + 01-07 completion)* — boundary verification
  - 01-08: infra-tests Testcontainers cross-schema deny smoke (D-05 / T-01-02 verification) — `ARCH-09`

  **Cross-cutting constraints** (truths recurring across ≥2 plans):
  - Pre-compose Jib build: `./gradlew :eureka-server:jibDockerBuild :config-server:jibDockerBuild :api-gateway:jibDockerBuild` is the single Wave-2 launch step (01-05, 01-06)
  - docker-compose.yml is touched additively by 01-03, 01-05, 01-06 — serialize 01-05 → 01-06 in Wave 2 to prevent file-write races
  - Northfields starter `spring-cloud-starter-gateway-server-webflux` (01-06) — old `spring-cloud-starter-gateway` is forbidden; 01-04 + 01-06 + 01-07 enforce via grep acceptance
  - `orders` (plural) schema rename — 01-02 saga-contracts §9, 01-03 init.sh, 01-08 connectAs all use `orders` not `order` (SQL reserved word)
  - `spring.config.import` (NOT bootstrap.yml) — 01-06 + 01-07 client posture per Boot 3.x

**Risks**: Pitfall #2 (gateway reactive vs MVC classpath collision), Pitfall #4 (Eureka cold-boot race), Pitfall #26 (Day-1 bikeshedding)
**Research need**: MEDIUM — Spring Cloud 2025.0 Northfields wiring quirks, distinct DB user policy specifics, gateway aggregator config.

### Phase 2: Frontend Recon + Toolchain Lock
**Goal**: Run Playwright against n11.com (since WebFetch returns 403) to capture header/nav/grid/PDP/cart structure, Turkish copy patterns, category taxonomy, and color tokens; lock the frontend toolchain (Vite + TS + Tailwind + Zustand likely, Next only if SSR-justified) and record the decision in PROJECT.md.
**Depends on**: Nothing (runs in parallel with Phase 1, Day-1 morning)
**Requirements**: FE-01
**Success Criteria** (what must be TRUE):
  1. `.planning/intel/n11-recon.md` exists with screenshots of header, nav, product grid, PDP, cart, checkout, and account pages from n11.com.
  2. A captured Turkish-copy table (>= 30 phrases like "Sepete Ekle", "Hemen Al", "Stokta", "Kargo Bedava", "Önceki/Sonraki") is committed in the recon report and is reused verbatim in Phase 10.
  3. The frontend toolchain decision (Vite vs Next, TS, styling, state) is recorded in PROJECT.md Key Decisions with the rationale tying back to recon findings.
  4. Category taxonomy and a small color/typography token list derived from n11 are extracted into the recon report so Phase 10 doesn't re-research them.
**Plans**: 3 (3 waves)

  **Wave 0** — recon project bootstrap (no deps)
  - 02-01: tools/recon/ npm project + Playwright 1.59.x + helpers (dismiss-banners, harvest-copy, harvest-colors) + assembler + checker + scripts/check-phase-02-artifacts.sh — `FE-01`

  **Wave 1** *(blocked on 02-01)* — n11.com capture run
  - 02-02: 7 page-capture specs (homepage, category, pdp, cart, checkout, account, login) + capture run + assemble `.planning/intel/n11-recon.md` (8 sections, ≥30 phrases, ≥10 tokens) — `FE-01`
    *Has human-verify checkpoint: screenshot authenticity + Turkish copy verbatim spot-check + Pitfall #19 callout.*

  **Wave 2** *(blocked on 02-02)* — recon enrichment + toolchain lock
  - 02-03: enrich n11-recon.md (§5-§8 + Decision Matrix subsection) + append PROJECT.md Key Decisions row (Vite 8 + React 19 SPA, with VITE_API_BASE_URL carry-forward) + resolve Open Question + STATE.md update — `FE-01`

**UI hint**: yes
**Risks**: Pitfall #20 (Turkish prompt drift — captured copy prevents drift in chat assistant later), Pitfall #19 (chat streaming UX — n11 has no chat to copy, so Phase 11 must invent — recon flags the gap), Pitfall #16 (CORS/auth mismatch — toolchain decision drives `.env` URL setup at the edge)
**Research need**: HIGH — Playwright IS the research deliverable. Live-site reconnaissance is the only way around the 403.

### Phase 3: Identity + Gateway Auth
**Goal**: Stand up identity-service (signup, login, JWT issuance, address book) and wire api-gateway to validate every JWT and inject `X-User-Id` / `X-User-Roles` headers, so every later service trusts the gateway-injected mesh and never sees the raw JWT.
**Depends on**: Phase 1
**Requirements**: AUTH-01, AUTH-02, AUTH-03, AUTH-04, AUTH-05, AUTH-06, AUTH-07, AUTH-08, QUAL-02
**Success Criteria** (what must be TRUE):
  1. User can register with email + password (BCrypt cost 10), log in, receive an RS256-signed JWT, and the token survives browser refresh + 401 retry against `/auth/me`.
  2. Hitting any protected route (`/api/v1/*` excluding the public allow-list) without a Bearer token returns 401 from the gateway, and with a valid token, downstream services see only `X-User-Id` and `X-User-Roles` (the `Authorization` header is stripped).
  3. User can save and list multiple Türkiye delivery addresses (Mahalle / İlçe / İl) — visible via `GET /addresses`.
  4. identity-service exposes `/.well-known/jwks.json`; the gateway fetches it at startup and refreshes hourly; rotating the keypair causes new tokens to validate without a gateway restart.
  5. identity-service has at least one passing smoke unit test (password hashing) — establishing the per-service test pattern that QUAL-02 mandates everywhere.
**Plans**: 6 plans (3 waves)

  **Wave 0** — module scaffold + schema + saga lock (parallel, no deps)
  - [x] 03-01-PLAN.md — identity-service Gradle module clone from service-template/skeleton/ + docker-compose entry + .env.example matrix + README runbook (`AUTH-01, AUTH-05, AUTH-07, QUAL-02`)
  - [x] 03-02-PLAN.md — Flyway V2/V3/V4 migrations (users + addresses + admin seed + outbox) + config-server identity-service.yml + user-registered.schema.json + classpath mirror + saga-contracts.md catalog edits (`AUTH-01, AUTH-05, AUTH-08`)

  **Wave 1** — identity-service business code (parallel after Wave 0)
  - [x] 03-03-PLAN.md — JwtConfig (RSA keypair) + JwtIssuerService + JwksController + IdentitySecurityConfig + PasswordEncoderTest (`AUTH-02, AUTH-05, AUTH-07, QUAL-02`)
  - [x] 03-04-PLAN.md — User/Role/Address JPA entities + repositories + UserService + AuthController + AddressController + Turkish-validation DTOs (`AUTH-01, AUTH-02, AUTH-03, AUTH-05, AUTH-07, AUTH-08`)
  - [x] 03-05-PLAN.md — Outbox pattern: OutboxEvent entity + OutboxRepository (FOR UPDATE SKIP LOCKED) + OutboxBackedUserRegistrationOutboxPublisher + OutboxPoller + IdentityRabbitConfig (identity.tx) + Testcontainers OutboxIntegrationTest with schema-drift gate (`AUTH-01, QUAL-02`)

  **Wave 2** — gateway flip + e2e smoke (depends on Wave 1)
  - [x] 03-06-PLAN.md — api-gateway/build.gradle.kts oauth2-resource-server starter + SecurityConfig REPLACE (oauth2ResourceServer + JwtTimestampValidator(30s) + roles-claim authority converter) + GatewayHeaderInjectionFilter REPLACE (Authorization-strip + X-User-Id injection) + config-server api-gateway.yml jwk-set-uri block + Springdoc aggregator entry + docker-compose api-gateway depends_on identity-service + e2e smoke runbook (`AUTH-02, AUTH-03, AUTH-04, AUTH-06`)

  **Cross-cutting truths** (recurring across ≥2 plans):
  - RSA keypair from `JWT_PRIVATE_KEY` env (PEM PKCS#8); public key derived at boot; never logged (Plan 03-01 .env.example, 03-03 JwtConfig)
  - Authorization header STRIPPED at gateway BEFORE forwarding (Plan 03-04 X-User-Id read pattern, 03-06 GatewayHeaderInjectionFilter implementation)
  - Schema-per-service: identity-service uses `identity_user` (Plan 03-01 docker-compose, 03-02 config-server YAML, 03-04 entities)
  - 30s clock skew via `JwtTimestampValidator(Duration.ofSeconds(30))` (Plan 03-06 SecurityConfig)
  - Transactional outbox: user row + outbox row in single `@Transactional` boundary (Plan 03-04 UserService.register seam, 03-05 real publisher)

**Risks**: Pitfall #6 (JWT secret leaked — must live in config-server / env only), Pitfall #18 (clock skew on JWT validation), Pitfall #14 (forwarding JWT downstream — strip at gateway)
**Research need**: LOW — JWT + Spring Security Resource Server is a well-documented pattern.

### Phase 4: Catalog + Inventory
**Goal**: Stand up product-service (catalog, categories, listing pagination, basic ILIKE search, sort, PDP fields) and inventory-service (stock holds + release events as the saga participant for stock reservation), with Turkish seed data and per-service Flyway migrations.
**Depends on**: Phase 3 (auth gates admin product writes via ROLE_ADMIN)
**Requirements**: PROD-01, PROD-02, PROD-03, PROD-04, PROD-05, PROD-06, PROD-07, PROD-08, PROD-09
**Success Criteria** (what must be TRUE):
  1. User can browse a paginated listing (page index 0-based, default size 20) of >= 50 Turkish-language seed products across all 8 top-level categories ("Elektronik", "Moda", "Ev & Yaşam", "Anne & Bebek", "Kozmetik", "Spor & Outdoor", "Süpermarket", "Kitap-Müzik-Film-Oyun").
  2. User can sort the listing by price asc/desc and by date (newest first), and can search products by free text (Postgres ILIKE + GIN index, case-insensitive Turkish match).
  3. User can view a PDP returning title, gallery, KDV-inclusive price, stock state ("Stokta" / "Tükendi" / "Son N ürün!"), and seller info; PDP is reachable through the gateway.
  4. inventory-service exposes `GET /inventory/{productId}` for stock reads and consumes `order.created` events to attempt a reservation against a row-versioned `stock` table — but **does not** publish `stock.reserved` to live consumers yet (Phase 5 wires the saga downstream).
  5. Both services expose Springdoc Swagger UI at `/swagger-ui.html`, and the gateway aggregator surfaces them at the root.
**Plans**: 3 plans (3 waves)

  **Wave 1** -- product-service (depends on Phase 3 docker network only)
  - [x] 04-01-PLAN.md -- product-service module scaffold + Category/Product JPA + ProductController + CategoryController + ILIKE GIN search + V2/V3 Flyway + config-server product-service.yml + docker-compose entry + tests (PROD-01, PROD-02, PROD-03, PROD-04, PROD-05, PROD-09)

  **Wave 2** -- inventory-service (depends on 04-01 exchange declaration)
  - [x] 04-02-PLAN.md -- inventory-service module scaffold + Stock @Version JPA + StockController (Turkish labels) + OrderCreatedConsumer (idempotent CLAUDE.md Rule #3) + OutboxPoller + Testcontainers idempotency test (PROD-06, PROD-07, PROD-08)

  **Wave 3** -- gateway integration + smoke (depends on 04-01 + 04-02)
  - [ ] 04-03-PLAN.md -- api-gateway.yml product/categories/inventory routes + Springdoc aggregator entries + docker-compose depends_on chain + smoke runbook (PROD-07, PROD-09)

**Risks**: Pitfall #11 (cross-service DB joins — distinct DB users + role-deny enforce), Pitfall #24 (pagination off-by-one — Spring Data 0-indexed convention documented), Pitfall #13 (schema migration coordination on a shared Postgres host)
**Research need**: LOW — standard CRUD + pagination + ILIKE GIN.

### Phase 5: Cart & Order Skeleton
**Goal**: Build cart-service (per-user cart state, single source of truth for both web UI and chat assistant) and order-service (order lifecycle, saga initiator, transactional outbox), and prove the saga skeleton end-to-end with a mocked payment producer — so Phase 6 swaps in real Iyzico against a frozen contract.
**Depends on**: Phase 4
**Requirements**: CART-01, CART-02, CART-03, CART-04, CART-05, CART-06, ORD-01, ORD-02, ORD-03, ORD-04, ORD-05, ORD-06, ARCH-06, ARCH-07, ARCH-08, QUAL-03
**Success Criteria** (what must be TRUE):
  1. User can add, view (with line totals + KDV breakdown + shipping preview), update qty, and remove cart items via REST; cart state survives logout / login by userId, and the cart-service is the single source of truth (no client-side cart state).
  2. User can `POST /orders` with `addressId` + `paymentMethod`; order-service writes order + outbox row in one transaction, the outbox poller publishes `order.created` to RabbitMQ, and inventory-service consumes it and emits `stock.reserved` (or `stock.reserve_failed`) — verified by a Testcontainers + Awaitility integration test.
  3. User can list "Siparişlerim" sorted by date desc and view an order detail with the full status timeline ("Sipariş Alındı → Hazırlanıyor → Kargoya Verildi → Teslim Edildi") that reflects the saga state machine (PENDING → STOCK_RESERVED → PAID → CONFIRMED, plus FAILED / CANCELLED branches).
  4. Saga consumers are idempotent — every consumer has a `processed_events(event_id PK, ...)` inbox table, and an integration test that re-delivers the same event twice asserts a single side effect; correlation IDs flow through MDC and are visible in JSON logs.
  5. Both services expose Springdoc Swagger UI; cart-service and order-service each have at least one critical-path integration test (Testcontainers Postgres + RabbitMQ).
**Plans**: TBD
**Risks**: Pitfall #3 (non-idempotent saga consumers → duplicate orders), Pitfall #11 (incomplete compensation — wire the skeleton with all 4 compensation paths from Day 1), dual-writes anti-pattern (transactional outbox is non-negotiable)
**Research need**: LOW — saga choreography pattern and outbox are documented in `research/ARCHITECTURE.md` §3.

### Phase 6: Payment (Iyzico)
**Goal**: Wire payment-service to Iyzico Checkout Form (sandbox), make the webhook publicly reachable, drive the saga forward on `payment.completed` / `payment.failed`, add a payment-timeout job for stuck orders, and prove the payment-failure compensation path (stock release).
**Depends on**: Phase 5
**Requirements**: PAY-01, PAY-02, PAY-03, PAY-04, PAY-05, PAY-06, PAY-07, QUAL-05
**Success Criteria** (what must be TRUE):
  1. User can complete a full sandbox payment with Iyzico test card `5528 7900 0000 0008` end-to-end: cart → checkout → Iyzico hosted form → 3DS OTP → callback → order CONFIRMED.
  2. The webhook URL is publicly reachable through the same Cloudflare Tunnel / ngrok used for the demo (choice documented in `payment-service/README.md`); Iyzico's POST hits a non-JWT-gated callback path; signature is verified server-side via `payment.retrieve(paymentId)`.
  3. payment-service consumes `stock.reserved` → initiates Iyzico checkout (publishes `payment.completed` on success or `payment.failed` on decline); the saga compensation integration test forces a payment failure and asserts inventory releases stock and order moves to CANCELLED.
  4. A scheduled payment-timeout job marks payments TIMED_OUT after N minutes and emits `payment.failed`, taking the same compensation path; the timeout integration test simulates a stuck Iyzico response.
  5. payment-service has Springdoc Swagger UI; idempotency on the callback uses `iyzico_payment_id` as the dedup key.
**Plans**: TBD
**Risks**: Pitfall #5 (Iyzico callback unreachable on localhost), Pitfall #11 (compensation path missing), Pitfall #18 (sandbox/prod key mix — env vars only)
**Research need**: HIGH — Iyzico Checkout Form Java sample lives in `src/test/java/com/iyzipay/sample/CheckoutFormSample.java` (per stack research); 3DS callback specifics; sandbox webhook reachability decision.

### Phase 7: Notification (Saga Closure)
**Goal**: Stand up notification-service as a fully independent saga participant (own Postgres schema, own AMQP listener), have it consume `order.confirmed` / `order.cancelled` / `payment.failed` / `user.registered` events and log structured "email payloads" — closing the saga loop and unlocking a complete happy-path saga integration test.
**Depends on**: Phase 5 (saga skeleton must exist; can run in parallel with Phase 6 since notification-service is a leaf consumer)
**Requirements**: NOTIF-01, NOTIF-02, NOTIF-03, QUAL-04
**Success Criteria** (what must be TRUE):
  1. notification-service consumes `order.confirmed`, `order.cancelled`, `payment.failed`, and `user.registered` events from RabbitMQ and writes a structured JSON log line (recipient, subject, Turkish body) for each — visible via `grep correlationId=X` across all services in flat logs.
  2. notification-service is a fully independent microservice — own Spring Boot app, own Postgres schema, own DB user, own Flyway migrations, registers with Eureka — and its `notifications(...)` audit table records every "sent" payload.
  3. The saga happy-path integration test (QUAL-04) covers `OrderCreated → StockReserved → PaymentCompleted → OrderConfirmed → notification logged`, using Testcontainers + Awaitility, and passes on green CI.
  4. Each consumer is idempotent (uses the same processed-events pattern as Phase 5); poison messages land on DLQ within 3 retries.
**Plans**: TBD
**Risks**: Pitfall #3 (idempotent consumers — same pattern as P5), Pitfall #28 (saga tests deferred — explicit QUAL-04 deliverable here, not later)
**Research need**: LOW — trivial Spring AMQP listener.

### Phase 8: AI Port + Adapter + Agent Toolset
**Goal**: Build the SOLID grading wedge — a zero-dependency `ai-port` Gradle module with neutral DTOs, a `GeminiChatAdapter` + `GeminiEmbeddingAdapter` (via google-genai 1.51.0), a trivial second `EchoChatProvider` that proves port substitutability, the `agent-toolset` shared Gradle module (10 tools, single source of truth), conversation state persistence, ai-service chat (Turkish, function-calling, SSE streaming), and an empty search-service skeleton that consumes the `EmbeddingProvider` port (v2 semantic search will fill it in).
**Depends on**: Phase 5 (tools call cart-service + order-service); Phase 4 (tools call product-service); Phase 6 (tools generate Iyzico checkout link)
**Requirements**: AI-01, AI-02, AI-03, AI-04, AI-05, AI-06, AI-07, AI-08, AI-09, AI-10, AI-14, AI-15, QUAL-08
**Success Criteria** (what must be TRUE):
  1. `mvn dependency:tree` (or Gradle equivalent) on the `ai-port` module shows zero `com.google.genai` artifacts; port DTOs (`ChatMessage`, `ToolCall`, `ToolResult`) carry no Gemini-specific types — verified by a CI guard.
  2. Setting `ai.provider=echo` swaps `GeminiChatAdapter` for `EchoChatProvider` and the chat assistant still answers (echoing the prompt back) — proving the port is substitutable. This is the SOLID artifact graders will inspect.
  3. The `agent-toolset` shared Gradle module exports the 10 canonical tools (`search_products`, `get_product`, `list_categories`, `add_to_cart`, `view_cart`, `update_cart_item`, `remove_from_cart`, `create_order`, `get_payment_link`, `get_order_status`); ai-service imports it and dispatches Gemini function-calls through `ToolDispatcher` with ID validation against repos (no hallucinated IDs accepted).
  4. ai-service streams a Turkish chat response via SSE through the gateway, with mid-stream tool-call indicators ("araç çalıştırılıyor..."); the system prompt forces `dil: tr-TR` and a 5-turn mixed-language assertion test passes; conversation state is persisted (Postgres `ai_conversations` + `messages`) and survives page refresh keyed by user-id.
  5. Items added by the chat assistant are visible in the cart-service REST cart (and vice versa) — verified by an integration test that adds via chat tool, reads via cart REST, and asserts equality; chat and a future external MCP both authenticate through the same gateway/JWT path (no parallel auth tree).
**Plans**: TBD
**Risks**: Pitfall #1 (Gemini 3 Flash model identifier — verify `gemini-3-flash-preview` at impl, fallback `gemini-2.5-flash`), Pitfall #7 (leaky `ChatProvider` abstraction — collapses the SOLID thesis), Pitfall #10 (hallucinated tool args — dispatcher must validate IDs)
**Research need**: HIGH — Gemini function-calling response shape, conversation state design, prompt template, model identifier verification at impl time.

### Phase 9: MCP Server
**Goal**: Stand up mcp-server using `spring-ai-starter-mcp-server-webmvc 1.1.5`, register the SAME `agent-toolset` shared module from Phase 8 (no copy-paste — single source of truth), wire both stdio (for Claude Desktop demo) and HTTP+SSE (for network demo) transports, and bridge external-agent auth via `MCP_API_KEY` → identity-service `/agents/exchange` → internal JWT.
**Depends on**: Phase 8 (imports `agent-toolset`)
**Requirements**: AI-11, AI-12, AI-13
**Success Criteria** (what must be TRUE):
  1. mcp-server `tools/list` returns identical tool names + schemas as ai-service uses — verified by a single integration test that asserts catalog equality (the DRY proof).
  2. A real MCP client (`npx @modelcontextprotocol/inspector` or Claude Desktop) connects via stdio AND HTTP+SSE, lists the 10 tools, and successfully invokes `search_products` and `view_cart` (read tools).
  3. External agent authenticates with `MCP_API_KEY=...`; mcp-server exchanges the key for an internal JWT against identity-service `/agents/exchange`; the JWT then propagates through the gateway like a normal user — proving mutating tools (`add_to_cart`, `create_order`) work end-to-end from an external MCP client.
  4. mcp-server has zero local tool definitions (greps for tool implementations point only to the `agent-toolset` module), proving the "one toolset, two surfaces" architecture.
**Plans**: TBD
**Risks**: Pitfall #15 (MCP transport mismatch — stdio vs HTTP+SSE pick the wrong one for Claude Desktop), Pitfall #16 (toolset duplicated — must consume the shared module), Pitfall #23 (auth bridge missing — mutating tools fail without it)
**Research need**: HIGH — MCP spec 2025-11-25 transport details, Spring AI MCP starter capability negotiation, auth bridge handshake.

### Phase 10: Frontend Storefront
**Goal**: Build the Turkish React storefront — header / footer / hero / category nav / paginated listing / PDP / cart / multi-step checkout / account section / login+register — all in Turkish per the Phase 2 recon report, all calling the backend through the gateway only, with KDV-inclusive prices, taksit preview, and Türkiye locale conventions throughout.
**Depends on**: Phase 2 (toolchain locked), Phase 6 (Iyzico checkout works end-to-end so the frontend's checkout flow has a real backend)
**Requirements**: FE-02, FE-03, FE-04, FE-05, FE-06, FE-07, FE-08, FE-09, FE-10, FE-11, FE-13, FE-14, FE-15, FE-16, LOC-01, LOC-02, LOC-03, LOC-04, LOC-05
**Success Criteria** (what must be TRUE):
  1. User lands on the homepage and sees a sticky header (logo / search / account+cart with item-count badge), a 3–4-slide hero carousel, a "Çok Satanlar" rail, and a footer with help links + payment-method icons — every visible string in Turkish, no English flashes on first paint.
  2. User can paginate the listing ("Önceki / Sonraki / Sayfa N / M" in Turkish), open a PDP at `/urun/:slug-:id` with image gallery + tabs (Açıklama / Özellikler / Kargo) + KDV-inclusive price + taksit preview (1/2/3/6/9/12) + "Kargo Bedava" badge above threshold + "Sepete Ekle" CTA, and add to cart.
  3. User can complete a multi-step checkout (address selection from the address book → Iyzico Checkout Form → confirmation page); cart page shows line items + KDV breakdown + "Siparişi Tamamla" CTA; all prices use `Intl.NumberFormat('tr-TR', { style: 'currency', currency: 'TRY' })` rendering as `1.299,90 ₺`; dates render `28 Nisan 2026`.
  4. User can register / log in (Turkish form validation messages), view "Siparişlerim" with status timeline, and manage their address book.
  5. The frontend hits the backend exclusively via the api-gateway base URL (no direct service URLs in source); skeleton loaders show on PDP + listing fetches; failures show Turkish toast / inline error messages; the React Query cache + auth header injection survive 401-retry.
**Plans**: TBD
**UI hint**: yes
**Risks**: Pitfall #19 (streaming UI freeze — relevant in P11 not P10, but cart-update reactivity matters here), Pitfall #23 (CORS/auth mismatch — gateway origin allow-list locked from P1), Pitfall #20 (Turkish drift — copy table from P2 prevents)
**Research need**: LOW — toolchain locked in Phase 2; remaining work is straightforward React + recon-driven layout.

### Phase 11: Frontend Chat Assistant + DevOps Deploy
**Goal**: Add the floating Turkish chat-assistant bubble to the storefront (persistent across pages, SSE token streaming, tool-use round-trips visible in UI), and ship the deploy: Jib for every service, GitHub Actions CI for build + test (with image-publish on release tags), local docker-compose stack on the candidate's machine running all 13 services + Postgres + RabbitMQ, public demo URL via Cloudflare Tunnel (preferred) or ngrok, Slack notifications on CI build success/failure, plus the Jenkins comparison doc and a complete README.
**Depends on**: Phase 8 (chat backend), Phase 10 (storefront to embed the bubble in), Phase 1 (deploy target locked in PROJECT.md)
**Requirements**: FE-12, DEV-01, DEV-02, DEV-03, DEV-04, DEV-05, DEV-06, DEV-08, DEV-09
**Success Criteria** (what must be TRUE):
  1. A floating chat bubble appears bottom-right on every page (homepage, listing, PDP, cart, checkout, account); opening it overlays a chat panel that streams Gemini responses token-by-token via SSE without freezing the UI; tool-use indicators appear mid-stream; the conversation persists across page navigation and refresh.
  2. Adding a product to cart from the chat bubble is reflected in the header cart-counter within 1 second (visual feedback that bridges chat-action and UI), and the chat panel summarizes the cart with localized Turkish text.
  3. `gradle :<service>:jib` builds an OCI image for every backend service with no Dockerfile in the repo; GitHub Actions pipeline runs `build` + `test` on push/PR; on a `v*` release tag the same pipeline pushes the 13 Jib images to GHCR (or Docker Hub) so the local docker-compose can pull them by tag; build success/failure produces a Slack notification ("✅ build green on <ref>" / "❌ build failed on <ref>").
  4. The full stack runs on the candidate's machine via `docker compose --profile full up`; an external `curl https://<tunnel-hostname>/api/v1/products` (Cloudflare Tunnel preferred, ngrok fallback) returns a 200 with seed products; tunnel access tokens live in env vars (never in repo); the public demo URL pointer + env-var matrix + demo card numbers + local-run instructions live in `README.md`; `docs/devops-pipeline-comparison.md` covers the Jenkins-vs-GH-Actions pipeline-logic comparison.
**Plans**: TBD
**UI hint**: yes
**Risks**: Pitfall #19 (chat streaming UI freeze — token buffering), Pitfall #21 (Slack webhook leaked — env var only), tunnel-dependency risk (interview-time tunnel outage — mitigation: keep an ngrok fallback ready in the README so the candidate can re-expose in under a minute if Cloudflare misbehaves), candidate-machine-dependency risk (machine reboot mid-demo — mitigation: docker-compose `restart: unless-stopped`, and a 30-second `compose up` rehearsal documented in the README).
**Research need**: LOW — docker-compose on a single host is well-trodden; tunnel choice is the only fresh research (Cloudflare Tunnel with `cloudflared` quickstart vs ngrok agent token).

## Progress

**Execution Order:**
Phases execute in numeric order. Parallel groups (per `Depends on:` lines):
- Phase 1 ‖ Phase 2 (both Day-1)
- Phase 6 ‖ Phase 7 (after Phase 5)
- Phase 9 sequential after Phase 8
- Phase 10 ‖ Phase 8 partial overlap (chat backend can build alongside frontend storefront after gateway is up)

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Foundations + Day-1 Contracts | 6/8 | In progress | - |
| 2. Frontend Recon + Toolchain Lock | 0/TBD | Not started | - |
| 3. Identity + Gateway Auth | 0/6 | Not started | - |
| 4. Catalog + Inventory | 0/TBD | Not started | - |
| 5. Cart & Order Skeleton | 0/TBD | Not started | - |
| 6. Payment (Iyzico) | 0/TBD | Not started | - |
| 7. Notification (Saga Closure) | 0/TBD | Not started | - |
| 8. AI Port + Adapter + Agent Toolset | 0/TBD | Not started | - |
| 9. MCP Server | 0/TBD | Not started | - |
| 10. Frontend Storefront | 0/TBD | Not started | - |
| 11. Frontend Chat Assistant + DevOps Deploy | 0/TBD | Not started | - |
