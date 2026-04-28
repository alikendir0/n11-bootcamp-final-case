# Project Research Summary

**Project:** n11 Bootcamp Final Case — Agentic E-Commerce Clone
**Domain:** Turkish online marketplace (Spring Boot microservices) + agentic-commerce layer (Gemini + MCP)
**Researched:** 2026-04-28
**Confidence:** HIGH (Spring/JVM stack, architecture, pitfall prevention); MEDIUM (Gemini 3 model ID, Iyzico sample location)

---

## Executive Summary

This is a 13-service Spring Boot microservices e-commerce backend with a React storefront, graded as a bootcamp capstone for an n11 interview slot. The differentiating wedge is **agentic commerce**: a Gemini-powered Turkish chat assistant and an MCP server that share a single agent toolset, demonstrating SOLID, DRY, and provider-agnostic AI architecture simultaneously. The grading lens weighs code quality and SOLID most heavily; every architectural decision must serve that signal. Most bootcamp candidates will ship a clean Spring Boot + React e-commerce app; this submission wins by going deep on one differentiator rather than wide on many.

The recommended approach is a conservative, well-documented Spring stack: **Java 21 + Spring Boot 3.5.14 + Spring Cloud 2025.0.x (Northfields) + Gradle multi-module**, with PostgreSQL 16 (schema-per-service on one host), RabbitMQ 4.x (choreography SAGA), and a carefully split AI library strategy. The AI layer uses google-genai 1.51.0 directly for the GeminiChatAdapter (Spring AI 1.1.5 does not enumerate gemini-3-flash-preview in its Gemini model list), and spring-ai-starter-mcp-server-webmvc 1.1.5 exclusively for the MCP server wire protocol. These are two libraries with clean, non-overlapping responsibilities. Spring Boot 4.0 is deliberately avoided; it is released but the Spring AI and Spring Cloud ecosystems remain on the 3.x line.

The three highest-severity risks are: (1) the leaky `ChatProvider` abstraction, which collapses the SOLID thesis if Gemini types bleed into the port; (2) Iyzico webhook unreachability — the sandbox cannot hit `localhost`, so a tunnel choice (Cloudflare Tunnel preferred, ngrok fallback) is the binding deploy-day constraint; and (3) Day-1 bikeshedding, where unresolved saga event contracts delay every downstream service. The earlier "AWS Elastic Beanstalk scope mismatch" risk has been removed — the deploy target is now local docker-compose on the candidate's machine, eliminating the EB-vs-13-microservices fit question entirely. All three remaining risks are preventable by front-loading decisions, and the research surfaces exactly how.

## Key Findings

### Recommended Stack

Full detail in [`STACK.md`](STACK.md).

**Core technologies (all HIGH confidence):**
- **Java 21 (Corretto)** + **Spring Boot 3.5.14** + **Spring Cloud 2025.0.x (Northfields)** — current OSS-supported pairing; Boot 4.0 ecosystem tail not ready
- **Gradle multi-module** — best fit with Jib, cleaner DSL than Maven for 13 modules
- **PostgreSQL 16** + **pgvector 0.8.2** — schema-per-service on one host; pgvector for the embedding index
- **RabbitMQ 4.3** + **Spring AMQP** — choreography SAGA bus
- **Spring Cloud Netflix Eureka 2025.0** + **Spring Cloud Gateway (reactive) 2025.0** + **Spring Cloud Config Server 2025.0** — service discovery, edge, central config
- **Spring Security Resource Server (JOSE)** + **JJWT 0.13.0** — JWT issuance in identity-service, validation at gateway
- **Springdoc OpenAPI 2.8.17** — per-service Swagger UI
- **Iyzico Java SDK 2.0.141** — sandbox endpoint `https://sandbox-api.iyzipay.com`; **sample code is in test sources** (`src/test/java/com/iyzipay/sample/CheckoutFormSample.java`), not the README — implementer must read directly
- **Flyway 12.5.0** — per-service migrations, distinct DB user per schema
- **Testcontainers 2.0.5** — Postgres + RabbitMQ for integration tests; **Awaitility** for async saga assertions
- **Jib Gradle 3.5.3** — no Dockerfiles, builds OCI images directly
- **GitHub Actions** — `setup-java`, `gradle/actions`, `aws-actions/configure-aws-credentials` (OIDC), `slackapi/slack-github-action`
- **Vite 8 + React 19.2** — frontend toolchain (final TS/styling/state choice deferred to post-Playwright recon)

**AI library split (the most consequential stack decision):**
- **google-genai 1.51.0** — direct adapter for `ChatProvider` and `EmbeddingProvider` ports inside ai-service
- **spring-ai-starter-mcp-server-webmvc 1.1.5** — MCP server wire protocol only, NOT used for Gemini calls
- Two libraries, two non-overlapping responsibilities, single source of truth for Gemini calls

**Gemini model identifier (MEDIUM confidence — verify at impl):**
- User directive: "Gemini 3.0 Flash"
- Verified actual model ID as of 2026-04-28: **`gemini-3-flash-preview`** (Preview tier)
- Fallback if unavailable in target region: **`gemini-2.5-flash`**
- Implementer must hit `https://ai.google.dev/gemini-api/docs/models` before locking the adapter

**What NOT to use:**
- Spring Boot 4.0 (ecosystem tail not ready)
- RestTemplate (deprecated; use `RestClient`)
- CRA / Webpack (replaced by Vite)
- Hystrix / Ribbon (deprecated Spring Cloud Netflix)
- Spring AI for Gemini chat (max documented model is `gemini-2.5-flash-preview-04-17`; doesn't list 3-flash)

### Expected Features

Full detail in [`FEATURES.md`](FEATURES.md). 4-band categorization with effort hints and a 3-tier MVP scoping ladder.

**Must have (Table Stakes — bootcamp brief floor):**
- Product listing with pagination (page index 0-based per Spring Data convention)
- Product detail page (PDP) with gallery, price, stock status
- Cart: add / remove / update qty / view total
- Order creation + order detail
- Iyzico Checkout Form payment (sandbox)
- JWT-based signup / login / session
- Logging + structured error responses
- Per-service Swagger UI

**Expected (Turkish-locale conventions — cheap wins):**
- KDV (VAT 20%) inclusive pricing display
- TRY currency formatting (`12.345,67 ₺`)
- Taksit (installment) preview on PDP — Iyzico-native set is 1/2/3/6/9/12 (PROJECT.md said 3/6/9/12; Iyzico controls actual quotes — go with native set)
- Turkish UI copy: "Sepete Ekle", "Hemen Al", "Kapıda Ödeme", "Taksit Seçenekleri"
- Address book (multi-address)
- Kapıda Ödeme (cash on delivery) — n11 has it; ship as no-op order path or disabled radio (~30 min) — graders cloning n11 will look for it

**Should have (Differentiating — the wedge):**
- **In-storefront Turkish chat assistant** — floating bubble, persistent across pages, Gemini-powered RAG over catalog + tool use for cart/checkout
- **MCP server** with full agent purchase flow — same toolset as the chat assistant, exposed via stdio (Claude Desktop) and/or HTTP+SSE
- **Semantic search** — pgvector over product embeddings; powers both the search-service and the chat assistant's `search_products` tool
- **Provider-agnostic LLM abstraction** — `ChatProvider` / `EmbeddingProvider` ports, Gemini adapter only (the abstraction itself is the SOLID demo)

**Defer / Anti-features (out of scope, documented in PROJECT.md):**
- Reviews and ratings — not in brief; mitigation: static "yıldız" placeholder on cards so the absence doesn't read as a bug
- Real email/SMS — log-only mock in notification-service
- Multi-vendor seller dashboards — not in scope
- Real-time WebSocket features — not in brief; chat streaming via SSE is the only "live" surface
- NL search and recommendations as separate UIs — emerge from chat tool-use, no separate panels

### Architecture Approach

Full detail in [`ARCHITECTURE.md`](ARCHITECTURE.md). 13 services with bounded contexts, full order saga + 4 compensation paths, and a shared `agent-toolset` Maven module.

**Major components:**
1. **Edge layer** — `eureka-server` (discovery), `config-server` (centralized config), `api-gateway` (JWT-at-edge + header injection + Eureka-aware routing + SSE passthrough + Springdoc aggregation)
2. **Identity** — `identity-service` (JWT issuance, user CRUD, password hashing)
3. **Catalog** — `product-service` (catalog, categories), `inventory-service` (stock holds + releases — the saga participant for stock reservation), `search-service` (pgvector index + retrieval)
4. **Commerce** — `cart-service` (cart state per user), `order-service` (order lifecycle, saga coordinator-by-events), `payment-service` (Iyzico Checkout Form integration, payment-timeout job), `notification-service` (log-only mock, RabbitMQ consumer, completes saga)
5. **Agent layer** — `ai-service` (hexagonal layering: domain ports `ChatProvider` + `EmbeddingProvider` + `AgentTool`, infrastructure adapters via google-genai), `mcp-server` (Spring AI MCP starter, registers tools from shared module)
6. **Shared library** — `agent-toolset` (standalone Maven/Gradle module, defines `AgentTool` interface + 10 concrete tools, consumed by BOTH ai-service and mcp-server — single source of truth)
7. **Frontend** — React storefront in Turkish (toolchain locked post-recon), gateway-aware

**The order saga (choreography via RabbitMQ):**
- Happy path: `OrderCreated` → inventory reserves → `StockReserved` → payment-service calls Iyzico → `PaymentCompleted` → order CONFIRMED → `OrderConfirmed` → notification logs/sends → done
- Compensations: `StockReserveFailed` (insufficient stock), `PaymentFailed` (Iyzico decline), `UserCancelled` (manual abort), `PaymentTimeout` (Iyzico hangs — payment-timeout job fires)
- Each event has correlation ID + idempotency key; consumers use transactional outbox + processed-events inbox to enforce idempotency
- DLQ per consumer queue with manual replay tooling

**Build order (with parallelization opportunities):**
1. **Foundations** (sequential): repo + multi-module Gradle skeleton + base CI + Postgres + RabbitMQ + eureka + config-server + gateway shell + Day-1 contract lock (saga-contracts.md, api-contracts.md)
2. **Identity** (sequential after Foundations): JWT issuance + gateway validation + header injection
3. **Catalog parallel block**: product-service + inventory-service + (frontend recon happens here — Day 1 morning)
4. **Commerce parallel block**: cart-service + order-service skeleton (events first, no Iyzico yet)
5. **Payment** (sequential after Commerce): Iyzico Checkout Form + public webhook tunnel decision + payment-timeout job
6. **Notification** (parallel with Payment): log-only mock saga consumer
7. **Search** (parallel with Commerce, after Catalog): pgvector setup + embedding pipeline
8. **AI/agent layer** (sequential after Search + Cart + Order): `agent-toolset` shared module FIRST → ai-service ports + adapters → mcp-server registers from shared module
9. **Frontend storefront + chat** (parallel with backend after gateway is up): foundation → storefront pages → chat assistant bubble
10. **DevOps & deploy** (final): Jib pipelines + local docker-compose deploy on the candidate's machine + Cloudflare Tunnel / ngrok exposure + Slack webhook

### Critical Pitfalls

Full detail in [`PITFALLS.md`](PITFALLS.md). 28 numbered pitfalls with severity, prevention, detection, and phase mapping.

**Top 5 to put on Day-1 watch:**

1. **Leaky `ChatProvider` abstraction (HIGH, P:AI-Core)** — the SOLID thesis lives in this port. If Gemini SDK types (`Content`, `Part`, `FunctionCall`) bleed into it, the differentiator collapses. Prevention: zero-dep `ai-port` Gradle module with neutral DTOs; ship a trivial `EchoChatProvider` adapter alongside the Gemini one to prove substitutability. Cannot be fixed at demo time.

2. **~~AWS EB single-app vs 13 microservices mismatch~~ (DROPPED 2026-04-28)** — original Day-1 risk #2 from this list. Resolved by the deploy-target revision: the candidate's local machine hosts the full docker-compose stack (13 services + Postgres + RabbitMQ); the demo URL is exposed via Cloudflare Tunnel (preferred) or ngrok. AWS is no longer in scope, so the EB-vs-13-microservices fit question vanishes. Pitfall #12 in PITFALLS.md is marked dropped accordingly. **Caveat:** the bootcamp brief originally listed AWS as must-have — coordinator confirmation that local-host + tunnel deployment is acceptable for grading is recommended.

3. **Day-1 bikeshedding instead of locking saga contracts (HIGH, P:Foundations)** — silent killer for 6-day timelines. Prevention: by EOD Day 1, `.planning/saga-contracts.md` and `.planning/api-contracts.md` must exist with every event name, payload schema, producer, consumer, and compensation event locked. Frontend toolchain debate happens **after** the morning Playwright recon, before lunch.

4. **Non-idempotent saga consumers cause duplicate orders (HIGH, P:Cart-Order, P:Payment)** — RabbitMQ at-least-once delivery + inevitable redelivery + non-idempotent consumers = duplicate stock holds, double payments. Prevention: every consumer uses a `processed_events` inbox table keyed by event ID; `INSERT OR IGNORE` semantics; integration test for redelivery.

5. **Iyzico webhook unreachable on localhost (HIGH, P:Payment)** — sandbox cannot hit `localhost`; saga stalls waiting for `payment.completed`. Prevention: payment phase plan must include public-tunnel decision (Cloudflare Tunnel preferred, ngrok fallback — same tunnel exposes the demo URL); document the chosen option in `payment-service/README.md`.

**Other phase-mapped pitfalls (sample):**
- Eureka registration timing → Foundations phase health-check policy
- JWT signing secret in source → Identity phase secrets handling via env vars + `.gitignore`
- Spring Cloud Gateway reactive vs MVC mismatch → Gateway must be reactive end-to-end (no `spring-boot-starter-web`)
- Schema-per-service developer-error → distinct Postgres user per schema with role-level deny on others
- Two-toolset drift → `agent-toolset` extracted as shared module BEFORE either ai-service or mcp-server is built
- Free-tier Gemini RPM limits during demo → caching + conversation summarization + graceful degrade
- Hallucinated tool args (model invents IDs) → tool layer validates IDs against repo, returns clean error to model
- Turkish prompt drift → system prompt forces `dil: tr-TR`, language detection assertion in tests
- Streaming + Spring MVC mismatch → SSE passthrough requires reactive gateway; chat clients use `EventSource`
- Mismatched CORS / Authorization headers between gateway and React → CORS config + `.env` URLs locked Day 1

## Implications for Roadmap

The candidate locked granularity at FINE (8–12 phases). Research suggests 12 phases is the natural decomposition — not coincidentally close to the locked granularity ceiling. The roadmapper may collapse adjacent phases, but should not exceed 12.

### Phase 1: Foundations + Day-1 Contracts
**Rationale:** Everything depends on it. Includes the Day-1-critical bikeshedding kill: lock saga + API contracts before any service code exists.
**Delivers:** Multi-module Gradle skeleton, base CI build, Postgres + RabbitMQ docker-compose, eureka-server, config-server, api-gateway shell, `saga-contracts.md` + `api-contracts.md` checked in.
**Addresses:** All "Foundations" pitfalls (eureka timing, gateway reactive vs MVC, schema isolation policy).
**Avoids:** Pitfall #26 (Day-1 bikeshedding), Pitfall #2 (eureka timing), Pitfall #11 (schema cross-joins). (Pitfall #12 — EB scope — no longer in scope per the local-deploy revision.)

### Phase 2: Identity + Gateway Auth
**Rationale:** Every other service needs JWT validation; gateway header-injection contract must lock before any service implements `@AuthenticationPrincipal`.
**Delivers:** identity-service (signup/login/JWT), gateway validates JWT and injects `X-User-Id` / `X-User-Roles`, JJWT key rotation hook, signup integration test.
**Uses:** Spring Security Resource Server + JJWT 0.13.0.
**Avoids:** Pitfall #14 (JWT secret in source), Pitfall #15 (token expiry mismatch), Pitfall #16 (CORS misconfig).

### Phase 3: Frontend Recon + Toolchain Lock
**Rationale:** Day-1 morning task. n11.com 403'd WebFetch — Playwright is the only path. Toolchain decision (Vite + TS + Tailwind + Zustand vs alternatives) is downstream.
**Delivers:** Playwright recon report (`.planning/intel/n11-recon.md`), screenshots of header/nav/grid/PDP/cart, captured Turkish UI strings, frontend toolchain decision recorded in PROJECT.md Key Decisions.
**Uses:** Playwright (recon only; not a project dependency).
**Avoids:** Pitfall #25 (Turkish lang pack flash — cause: copy not captured up-front).

### Phase 4: Catalog (product + inventory)
**Rationale:** Foundational data layer; cart/order can't exist without products. Inventory is the saga participant — its event contracts come from Phase 1's contracts.
**Delivers:** product-service (CRUD + listing pagination), inventory-service (stock holds + release events), Turkish-language seed data, per-service Flyway migrations with distinct DB users.
**Uses:** Spring Data JPA, Flyway, Springdoc.
**Implements:** Architecture §2.5 + §2.6.

### Phase 5: Cart & Order Skeleton
**Rationale:** The saga skeleton must exist before payment integration. Build it without Iyzico first; verify the choreography in tests with mocked payment events.
**Delivers:** cart-service (per-user cart state), order-service (order CRUD + saga state machine), `order.created` and `stock.reserved` event flow proven via Testcontainers.
**Uses:** Spring AMQP, transactional outbox pattern, Awaitility for async assertions.
**Avoids:** Pitfall #5 (non-idempotent consumers), Pitfall #8 (incomplete compensation).

### Phase 6: Payment (Iyzico)
**Rationale:** Hardest external integration. Requires Iyzico sandbox + public tunnel decision + payment-timeout job.
**Delivers:** payment-service with Iyzico Checkout Form, public webhook tunnel chosen and documented, payment-timeout scheduler, full happy + compensation paths verified end-to-end.
**Uses:** Iyzico SDK 2.0.141; reads `src/test/java/com/iyzipay/sample/CheckoutFormSample.java` for the integration shape.
**Avoids:** Pitfall #18 (sandbox/prod key mix), Pitfall #19 (3DS callback handling), Pitfall #20 (payment-timeout missing).

### Phase 7: Notification (Saga Closure)
**Rationale:** Closes the saga. Log-only mock, but its existence as a microservice + RabbitMQ consumer is what completes the SAGA story for graders.
**Delivers:** notification-service consumes `order.confirmed` / saga compensation events, structured logs, optional Slack webhook for "real" notifications during demo.
**Uses:** Spring AMQP listener.

### Phase 8: Search + Embeddings
**Rationale:** pgvector setup is its own concern; embedding pipeline can run on existing seed catalog data. Powers both search-service and the chat assistant's `search_products` tool.
**Delivers:** search-service with pgvector index, embedding pipeline (offline batch over seed data), search REST endpoint returning ranked product IDs.
**Uses:** pgvector 0.8.2, google-genai 1.51.0 for embeddings.
**Implements:** Architecture §2.11.

### Phase 9: AI Port + Adapter (the SOLID centerpiece)
**Rationale:** This is the SOLID grading wedge. Must be done right — not at the end where it gets rushed.
**Delivers:** `ai-port` zero-dep module with `ChatProvider`, `EmbeddingProvider`, `AgentTool` interfaces and neutral DTOs; Gemini adapter (google-genai); `EchoChatProvider` test adapter to prove port substitutability; `agent-toolset` shared module with 10 tool implementations; conversation state store (Redis or Postgres).
**Uses:** google-genai 1.51.0 (NOT Spring AI for chat).
**Avoids:** Pitfall #7 (leaky abstraction).

### Phase 10: MCP Server
**Rationale:** Final agent surface. Imports `agent-toolset` — proves the shared module is real.
**Delivers:** mcp-server using spring-ai-starter-mcp-server-webmvc 1.1.5, both transports (stdio + HTTP+SSE), MCP capability negotiation, auth bridge for external agents (API key → user mapping).
**Uses:** Spring AI MCP starter 1.1.5, MCP spec 2025-11-25.
**Avoids:** Pitfall #21 (transport choice), Pitfall #22 (capability negotiation), Pitfall #23 (auth bridge missing).

### Phase 11: Frontend Storefront
**Rationale:** Toolchain locked in Phase 3; now build the pages. Chat assistant comes in Phase 12.
**Delivers:** Listing + pagination, PDP, cart UI, checkout flow, account pages, all in Turkish per Phase 3 recon.
**Uses:** Vite 8 + React 19.2 + (TS/styling/state per Phase 3 decision).

### Phase 12: Frontend Chat Assistant + DevOps Deploy
**Rationale:** The differentiator UI on top of the storefront. Bundled with deploy because both depend on everything else being green. Deploy target is locked from Phase 1 (local docker-compose on the candidate's machine; demo URL via Cloudflare Tunnel / ngrok).
**Delivers:** Floating chat bubble, SSE streaming from gateway, tool-use round-trips visible in UI, Jib build for every service, GitHub Actions build/test pipeline + release-tag image publish to GHCR/Docker Hub, local `docker compose --profile full up` deploy artifact, public tunnel sidecar (cloudflared / ngrok), Slack webhook for build notifications.
**Uses:** Vite + EventSource, Jib Gradle 3.5.3, Cloudflare Tunnel `cloudflared` (or ngrok agent).
**Avoids:** Pitfall #24 (streaming UX), Pitfall #16 (CORS — already locked Day 1). (Pitfall #12 / Pitfall #13 deploy-mismatch risks no longer in scope per the local-deploy revision.)

### Phase Ordering Rationale

- **Foundations first** because eureka + config + gateway are dependencies of every other service (architecture-driven).
- **Identity + Gateway Auth before everything else with users** so the JWT contract is locked once.
- **Frontend recon parallel with Phase 1** so the toolchain decision unblocks Phase 11 — also Day-1 critical per pitfall #25.
- **Catalog before Cart/Order** because cart needs product references; **Payment after Cart/Order** because saga events flow that way.
- **Search and AI parallel-ish** — they share the embedding model but are different services; the AI port can build out while search is indexing.
- **MCP after AI** because mcp-server imports the shared `agent-toolset` module from the AI phase; building MCP first guarantees toolset drift.
- **Frontend pages before chat** so the chat assistant has a real UI to live inside.
- **DevOps/deploy last** so the deploy artifact is feature-complete; deploy target is locked Day 1 (local docker-compose on the candidate's machine, demo URL via Cloudflare Tunnel / ngrok).

### Research Flags

Phases likely needing their own `/gsd-research-phase` pass during planning:

- **Phase 6 (Payment):** HIGH — Iyzico Checkout Form sample lives in test sources; 3DS flow has callback specifics; sandbox webhook reachability decision.
- **Phase 9 (AI Port + Adapter):** HIGH — Gemini function-calling response shape, conversation state design, prompt template structure, model identifier verification (re-verify `gemini-3-flash-preview` at the moment of implementation).
- **Phase 10 (MCP Server):** HIGH — MCP spec 2025-11-25 details, Spring AI MCP starter capabilities, transport setup specifics.
- **Phase 3 (Frontend Recon):** HIGH — Playwright must run; this is research IS the deliverable.
- **Phase 1 (Foundations):** MEDIUM — Spring Cloud 2025.0 wiring quirks, distinct DB user policy specifics.
- **Phase 8 (Search):** MEDIUM — pgvector index type (HNSW vs IVFFlat) and parameter tuning, embedding dimensionality.
- **Phase 12 (DevOps):** LOW — local docker-compose on a single host is well-trodden; only fresh research is the public-tunnel choice (Cloudflare Tunnel quickstart vs ngrok agent token).

Phases with standard patterns (skip per-phase research):
- **Phase 2 (Identity):** standard JWT pattern.
- **Phase 4 (Catalog):** standard CRUD + pagination.
- **Phase 5 (Cart & Order):** documented in ARCHITECTURE.md saga.
- **Phase 7 (Notification):** trivial RabbitMQ consumer.
- **Phase 11 (Frontend Storefront):** patterns standard once toolchain locked.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Versions verified against official docs; Gemini model ID flagged MEDIUM with explicit fallback; Iyzico sample location flagged MEDIUM |
| Features | HIGH | n11.com blocked WebFetch (anti-bot 403); Trendyol/Hepsiburada used as proxy; Playwright recon covers the gap |
| Architecture | HIGH | Saga flow, contracts, build order all sourced from Spring Cloud + microservices.io + MCP spec |
| Pitfalls | HIGH | 28 pitfalls cited against real Spring Cloud GitHub issues, RabbitMQ docs, Iyzico docs, MCP spec; pitfall #12 (EB fit) marked dropped after the local-deploy revision |

**Overall confidence:** HIGH for architecture, stack, and pitfall coverage. MEDIUM for two specific items requiring impl-time verification (Gemini 3 Flash availability; Iyzico Checkout Form sample contents). The research surfaces the verification steps needed in each.

### Gaps to Address

- **Gemini 3.0 Flash availability** — verify model ID at AI-Service phase (Phase 9). Fallback `gemini-2.5-flash` documented. Adapter abstraction means swap is one-file.
- **~~AWS deploy scope~~** — RESOLVED 2026-04-28: AWS dropped. Deploy target = local docker-compose on the candidate's machine; demo URL via Cloudflare Tunnel / ngrok. Coordinator confirmation that this satisfies the brief's "AWS deployment" line is recommended.
- **MCP transport choice** — both stdio and HTTP+SSE feasible since dispatch core is shared; decision deferred to Phase 10 planning.
- **MCP auth bridge** — for mutating tools (cart, order), external agents need user mapping. Default to read-only if Phase 10 leaves this open. Differentiator weakens but doesn't break.
- **Frontend toolchain** — locked post-Playwright recon (Phase 3 deliverable).
- **Kapıda Ödeme inclusion** — n11 has it, Trendyol doesn't; decision left to Phase 5 (Cart & Order) as no-op vs disabled radio.
- **Static yıldız placeholder** — reviews are out of scope but their absence reads as "missing"; cheap mitigation in Phase 11.

## Sources

### Primary (HIGH confidence)
- spring.io / docs.spring.io — Spring Boot 3.5, Spring Cloud 2025.0, Spring Cloud Gateway, Eureka, Config Server, Spring Security Resource Server
- ai.google.dev — Gemini API model list, function calling, embedding models, rate limits
- modelcontextprotocol.io — MCP spec 2025-11-25, transport spec, Java SDK
- github.com/iyzico/iyzipay-java — Iyzico Java SDK 2.0.141 source + sample tests
- microservices.io (Chris Richardson) — choreography SAGA, transactional outbox, idempotent receiver
- pgvector.dev / github.com/pgvector/pgvector — pgvector 0.8.2 setup, HNSW vs IVFFlat
- docs.gradle.org/jib — Jib Gradle plugin 3.5.3
- rabbitmq.com/docs — at-least-once delivery semantics, DLQ patterns, reliability guide

### Secondary (MEDIUM confidence)
- trendyol.com (live recon 2026-04-28) — primary marketplace recon proxy after n11.com 403
- hepsiburada.com / Crunchbase / Turkpidya — n11 specifics where direct fetch blocked
- Spring Cloud Gateway issue tracker — reactive/MVC incompatibility
- Cloudflare Tunnel `cloudflared` quickstart docs — sidecar exposure of localhost gateway port (replaces the previous AWS Elastic Beanstalk reference; AWS dropped from scope)

### Tertiary (LOW confidence — needs validation at impl time)
- Gemini 3 Flash exact model ID and Preview-tier availability — verify at Phase 9
- Iyzico Checkout Form sample contents — read directly at Phase 6
- Spring AI 1.1.5 MCP starter Gemini integration extent — verify at Phase 10

---
*Research completed: 2026-04-28*
*Ready for roadmap: yes*
