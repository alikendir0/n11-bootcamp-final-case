# Requirements: n11 Bootcamp Final Case — Agentic E-Commerce Clone

**Defined:** 2026-04-28
**Core Value:** A graders-impressing demonstration that the candidate can architect a clean, SOLID, microservices system AND layer differentiated AI capabilities on top of it.
**Source spec:** [`REQUIREMENTS-n11.md`](../REQUIREMENTS-n11.md) (Patika.dev × n11 bootcamp brief)
**Research:** [`research/SUMMARY.md`](research/SUMMARY.md)

> v1 = Tier 1 (Demo Floor — entire bootcamp brief) + Tier 2 (Strong Submission — full agentic differentiators). v2 = Tier 3 (stretch polish; built only if Tier 2 lands solid). Out of Scope = anti-features explicitly cut.

---

## v1 Requirements

### Authentication & Identity

- [ ] **AUTH-01**: User can register with email and password
- [ ] **AUTH-02**: User can log in and receive a JWT (`Authorization: Bearer <token>`)
- [ ] **AUTH-03**: User session persists across browser refresh (token stored client-side, refreshed on 401)
- [ ] **AUTH-04**: User can log out from any page
- [ ] **AUTH-05**: identity-service issues JWTs with user-id and roles claims
- [ ] **AUTH-06**: api-gateway validates every JWT on protected routes; downstream services trust gateway-injected `X-User-Id` / `X-User-Roles` headers (defense-at-edge pattern)
- [ ] **AUTH-07**: Passwords hashed with BCrypt (cost factor 10) before persistence
- [ ] **AUTH-08**: User can manage multiple delivery addresses (Mahalle / İlçe / İl, Türkiye-only)

### Product Catalog & Inventory

- [ ] **PROD-01**: User can browse paginated product listing (page index 0-based, configurable size, default 20)
- [ ] **PROD-02**: User can view product detail page (PDP) with title, gallery, price (KDV-inclusive), stock state, seller info
- [ ] **PROD-03**: User can browse product categories via top-level navigation (Elektronik, Moda, Ev & Yaşam, Anne & Bebek, Kozmetik, Spor & Outdoor, Süpermarket, Kitap-Müzik-Film-Oyun)
- [ ] **PROD-04**: User can search products by text (Postgres ILIKE + GIN index for v1; semantic upgrade through chat assistant)
- [ ] **PROD-05**: User can sort listing by price (ascending / descending) and date (newest first)
- [x] **PROD-06**: User can see stock indicator on PDP — "Stokta", "Tükendi", "Son N ürün!" when low
- [x] **PROD-07**: product-service exposes REST API documented via Springdoc OpenAPI; Swagger UI reachable at `/swagger-ui.html`
- [x] **PROD-08**: inventory-service holds stock per SKU; supports `reserve` and `release` event handlers as saga participant
- [ ] **PROD-09**: Seed data ships with at least 50 products in Turkish (title, description, category, price, stock, image URL)

### Cart

- [ ] **CART-01**: User can add a product to cart (`productId`, `quantity`)
- [ ] **CART-02**: User can view cart contents with line totals, subtotal, shipping preview, KDV breakdown
- [ ] **CART-03**: User can update line item quantity
- [ ] **CART-04**: User can remove line items
- [ ] **CART-05**: Cart persists per logged-in user (cart-service is the single source of truth for both web UI and chat assistant)
- [ ] **CART-06**: cart-service exposes REST API documented via Springdoc OpenAPI

### Order

- [x] **ORD-01**: User can convert cart to a pending order via checkout (`POST /orders` accepts `addressId`, `paymentMethod`)
- [x] **ORD-02**: order-service publishes `order.created` event on order creation, kicking off the saga
- [x] **ORD-03**: User can view their order list ("Siparişlerim") sorted by date desc
- [x] **ORD-04**: User can view order detail with status timeline ("Sipariş Alındı → Hazırlanıyor → Kargoya Verildi → Teslim Edildi")
- [x] **ORD-05**: Order status reflects saga progress (PENDING → STOCK_RESERVED → PAID → CONFIRMED → FAILED → CANCELLED)
- [x] **ORD-06**: order-service exposes REST API documented via Springdoc OpenAPI

### Payment (Iyzico)

- [x] **PAY-01**: payment-service integrates with Iyzico Checkout Form (sandbox)
- [x] **PAY-02**: payment-service consumes `stock.reserved` events and initiates Iyzico payment
- [x] **PAY-03**: payment-service publishes `payment.completed` or `payment.failed` events to drive saga
- [x] **PAY-04**: 3DS callback handler URL implemented; Iyzico can return to the app and complete the order
- [x] **PAY-05**: Public webhook reachability solution chosen (Cloudflare Tunnel preferred, ngrok fallback) and documented in `payment-service/README.md`. The same tunnel exposes the gateway for the demo URL, so PAY-05 and DEV-05 share infrastructure.
- [ ] **PAY-06**: Payment timeout job fires compensation events if Iyzico hangs (order stuck in `STOCK_RESERVED` for > N minutes)
- [ ] **PAY-07**: User can complete a full sandbox payment with Iyzico test card `5528 7900 0000 0008`

### Notification

- [ ] **NOTIF-01**: notification-service consumes `order.confirmed` / `order.failed` / `order.cancelled` events from RabbitMQ
- [ ] **NOTIF-02**: notification-service logs structured "email payload" (recipient, subject, body) instead of sending real email — closes the saga loop without SMTP
- [ ] **NOTIF-03**: notification-service is a fully independent microservice with its own Postgres schema and Spring AMQP listener

### AI Layer (the differentiating wedge)

- [ ] **AI-01**: `ChatProvider` and `EmbeddingProvider` ports defined in zero-dependency `ai-port` Gradle module with neutral DTOs (no Gemini types leaked)
- [ ] **AI-02**: `GeminiChatAdapter` implements `ChatProvider` using `google-genai 1.51.0` directly
- [ ] **AI-03**: `GeminiEmbeddingAdapter` implements `EmbeddingProvider` using `google-genai 1.51.0`
- [ ] **AI-04**: `EchoChatProvider` second adapter ships in tests to prove port substitutability (the SOLID artifact)
- [ ] **AI-05**: `agent-toolset` shared Gradle module defines `AgentTool` interface + the 10 canonical tools (`search_products`, `get_product`, `list_categories`, `add_to_cart`, `view_cart`, `update_cart_item`, `remove_from_cart`, `create_order`, `get_payment_link`, `get_order_status`)
- [ ] **AI-06**: ai-service hosts the in-storefront chat assistant; serves Turkish responses via Gemini 3 Flash; verifies model identifier (`gemini-3-flash-preview` per research, fallback `gemini-2.5-flash`) at impl time per official Google AI docs
- [ ] **AI-07**: ai-service dispatches Gemini function-calls to the shared agent toolset; tool layer validates IDs against repos and returns clean errors for hallucinated args
- [ ] **AI-08**: ai-service streams chat responses to the browser via SSE, with mid-stream tool-call indicators ("araç çalıştırılıyor...")
- [ ] **AI-09**: Chat conversation state is persisted (Redis or Postgres — decide at AI phase planning) keyed by user-id; survives page refresh
- [ ] **AI-10**: System prompt forces Turkish (`dil: tr-TR`); language-detection assertion in tests catches drift to English
- [ ] **AI-11**: mcp-server registers the SAME `agent-toolset` (single source of truth — no copy-paste) via Spring AI MCP starter (`spring-ai-starter-mcp-server-webmvc 1.1.5`)
- [ ] **AI-12**: mcp-server supports stdio transport (Claude Desktop demo) AND HTTP+SSE transport (network demo); both wired
- [ ] **AI-13**: mcp-server auth bridge: external agents authenticate via `MCP_API_KEY` env var; key exchanged for an internal JWT against identity-service `/agents/exchange`; JWT propagates through gateway like a normal user
- [ ] **AI-14**: Chat assistant and MCP both hit the same gateway and use the same JWT/header injection — no parallel auth tree
- [ ] **AI-15**: Cart added by chat assistant is visible in the cart page (and vice-versa); `cart-service` is the single source of truth

### Frontend (Storefront, in Turkish)

- [ ] **FE-01**: Frontend toolchain locked post-Playwright n11 recon (likely Vite + TypeScript + Tailwind + Zustand; locked in PROJECT.md Key Decisions at end of Phase 3)
- [ ] **FE-02**: Header layout — left logo, center search bar, right cluster (account, cart with item-count badge); sticky on scroll
- [ ] **FE-03**: Footer with help links ("Hakkımızda", "İletişim", "Yardım", "Sözleşmeler") and payment-method icon strip
- [ ] **FE-04**: Hero carousel on homepage (3–4 static slides; no CMS)
- [ ] **FE-05**: Bestseller / "Çok Satanlar" rail on homepage (single endpoint, hard-coded seed for v1)
- [ ] **FE-06**: Product listing page with paginated grid + numbered pages ("Önceki / Sonraki / Sayfa 1 / N") in Turkish
- [ ] **FE-07**: Product detail page (`/urun/:slug-:id`) with image gallery, tabs (Açıklama, Özellikler, Kargo), price (KDV-inclusive), stock, "Sepete Ekle" CTA
- [ ] **FE-08**: Cart page with line items, totals, KDV breakdown, taksit preview, "Siparişi Tamamla" CTA
- [ ] **FE-09**: Multi-step checkout (address → payment → confirmation); Iyzico Checkout Form embedded
- [ ] **FE-10**: Account section: "Siparişlerim" (orders list), order detail with status timeline, address book
- [ ] **FE-11**: Login / register pages in Turkish with form validation messages in Turkish
- [ ] **FE-12**: Floating chat assistant bubble bottom-right; persistent across all pages; opens overlay panel; streams Gemini responses token-by-token
- [ ] **FE-13**: All UI copy in Turkish — "Sepete Ekle", "Hemen Al", "Stokta", "Tükendi", "Kargo Bedava", "Siparişi Tamamla", "Önceki", "Sonraki", "Sepetim Boş"
- [ ] **FE-14**: Pagination component with Turkish labels and accessible keyboard navigation
- [ ] **FE-15**: Skeleton loaders on PDP and listing; toast or inline error messages on action failures (React Query handles most retry/cache)
- [ ] **FE-16**: Frontend integrates with backend via api-gateway only (no direct service URLs)

### Localization (Turkish-Locale Conventions)

- [ ] **LOC-01**: KDV-inclusive price display everywhere; backend stores `price_gross` + `kdv_rate`
- [ ] **LOC-02**: TRY currency formatting via `Intl.NumberFormat('tr-TR', { style: 'currency', currency: 'TRY' })` → `1.299,90 ₺`
- [ ] **LOC-03**: Taksit preview on PDP — client-side `Math.ceil(price / installments)` for 2/3/6/9/12 tiers
- [ ] **LOC-04**: "Kargo Bedava" badge on PDP / listing when `price >= FREE_SHIPPING_THRESHOLD` (config value)
- [ ] **LOC-05**: Date formatting with tr-TR locale (`28 Nisan 2026`)

### Architecture Mandates (microservices coordinator requirements)

- [ ] **ARCH-01**: System decomposed into ≥ 10 microservices — actual: 13 (eureka-server, config-server, api-gateway, identity-service, product-service, inventory-service, cart-service, order-service, payment-service, notification-service, search-service, ai-service, mcp-server)
- [x] **ARCH-02**: Eureka service discovery — every business service registers and discovers via Eureka client *(Plan 01-05: eureka-server runnable Boot app at port 8761; clients land in 01-06+)*
- [x] **ARCH-03**: Spring Cloud Config Server centralizes per-service config; bootstrap properties point to it *(Plan 01-05: config-server native profile + shared CD-05 baseline at config/application.yml; clients use spring.config.import per Cross-Cutting #2 in 01-06+)*
- [x] **ARCH-04**: Spring Cloud Gateway (reactive) fronts all business services; routes via Eureka discovery
- [ ] **ARCH-05**: RabbitMQ messaging — exchanges and queues defined per saga step; dead-letter queue per consumer
- [x] **ARCH-06**: SAGA pattern implemented as choreography via RabbitMQ events — full happy path + 4 compensation paths (stock-fail, payment-fail, user-cancel, payment-timeout)
- [x] **ARCH-07**: Saga consumers are idempotent — `processed_events` inbox table keyed by event ID; integration test for redelivery
- [x] **ARCH-08**: Saga events carry correlation ID + idempotency key + business payload; correlation ID flows through MDC/SLF4J for log tracing
- [ ] **ARCH-09**: Schema-per-service on a single PostgreSQL host; each service has a distinct DB user with role-level deny on other schemas (boundary enforced at the data layer)
- [ ] **ARCH-10**: Per-service Flyway migrations; no cross-service joins; no shared tables
- [~] **ARCH-11**: All services start cleanly even if Eureka is briefly unreachable (registration retry with backoff) *(Plan 01-05 SERVER-half: eureka-server posture documented — no client retry config installed on the server itself per Pitfall #4 boundary; CLIENT-half retry config baked into service-template/application.yml in Plan 01-07)*
- [ ] **ARCH-12**: Saga event contracts and inter-service REST contracts locked into `.planning/saga-contracts.md` and `.planning/api-contracts.md` by EOD Phase 1 (Day 1 deliverable)

### Cross-Cutting Quality

- [ ] **QUAL-01**: Each service has Springdoc OpenAPI; Swagger UI per service; gateway aggregates docs
- [ ] **QUAL-02**: Each service has at least one smoke unit test on its core domain logic
- [x] **QUAL-03**: Each service has 1–2 integration tests on the critical path (Testcontainers for Postgres + RabbitMQ where applicable)
- [ ] **QUAL-04**: Saga happy-path integration test (with Awaitility async assertions) covering OrderCreated → StockReserved → PaymentCompleted → OrderConfirmed
- [ ] **QUAL-05**: Saga compensation integration test for `payment.failed` (covers stock release)
- [~] **QUAL-06**: Structured JSON logs (Logback configured) with correlation ID propagated via MDC — MDC propagation wires complete (Plan 01-04: 5 wires in common-logging registered via Spring Boot 3 AutoConfiguration.imports); Logback structured-JSON config (LogstashEncoder + `<includeMdcKeyName>correlationId</includeMdcKeyName>`) pending in Plan 01-07 service-template/logback-spring.xml
- [x] **QUAL-07**: Standardized error response shape (problem+json or custom) across all services — RFC-7807 spec locked in Plan 01-02 (api-contracts.md §7); ProblemDetailControllerAdvice + ApiErrorCode enum shipped in Plan 01-04 common-error library
- [ ] **QUAL-08**: SOLID principles auditable — clean layering per service (controller / application / domain / infrastructure), explicit ports for external integrations (LLM providers, payment gateway)
- [ ] **QUAL-09**: No secrets in source — Iyzico keys, Gemini API key, JWT signing key, Slack webhook URL all via env vars / Spring Cloud Config

### DevOps & Deployment

- [ ] **DEV-01**: Backend services containerized with Jib (Gradle plugin 3.5.3) — no Dockerfiles
- [ ] **DEV-02**: GitHub Actions CI pipeline (`build`, `test`) on push/PR
- [ ] **DEV-03**: GitHub Actions release pipeline on `v*` tags publishes the 13 Jib images to a container registry (GHCR or Docker Hub) so the local docker-compose can pull them by tag (this is the "deploy" half of CI/CD reframed for a local-host deploy)
- [ ] **DEV-04**: Jenkins comparison documented in `docs/devops-pipeline-comparison.md` (pipeline-logic understanding requirement)
- [ ] **DEV-05**: Full deployment runs on the candidate's machine via `docker compose --profile full up` (Postgres + RabbitMQ + all 13 services); the gateway is exposed publicly via a Cloudflare Tunnel (preferred) or ngrok (fallback); a `curl https://<tunnel-hostname>/api/v1/products` from outside the candidate's network returns 200 with seed products
- [ ] **DEV-06**: Slack webhook fires CI build notifications on success/failure (the deploy-equivalent signal for the local-host deploy model)
- [ ] **DEV-07**: docker-compose.yml provides full local stack (Postgres + RabbitMQ + all 13 services) for development and demo (this *is* the deploy artifact, not just a dev convenience)
- [ ] **DEV-08**: Tunnel access token (Cloudflare Tunnel token or ngrok authtoken) and registry-publish credentials live as env vars / GitHub Actions secrets — never committed; OIDC is not in scope (no AWS account)
- [ ] **DEV-09**: README.md covers local-run instructions, env-var matrix, demo card numbers, the public tunnel hostname pointer, and a 30-second `docker compose up` rehearsal so the candidate can re-launch the demo cleanly during the interview

---

## v2 Requirements

Tier 3 stretch features. Implemented only if Tier 2 lands solid; otherwise documented as "next iteration".

### Search Polish

- **AI-V2-01**: Semantic search with pgvector — embeddings populated for all products on upsert; cosine-similarity query fallback to ILIKE if embedding service down
- **AI-V2-02**: Semantic search exposed as the `search_products` tool's primary mode (with text fallback)

### Frontend Polish

- **FE-V2-01**: Streaming chat UX with mid-stream cancellation (user can stop generation)
- **FE-V2-02**: Free-shipping badge on every listing card (not just PDP)
- **FE-V2-03**: Kapıda Ödeme as a working order path (no-op fulfillment)
- **FE-V2-04**: Bestseller rail backed by a dynamic "ordered most" query (currently hard-coded seed)

### AI Polish

- **AI-V2-03**: Conversational PDP summaries — assistant can summarize a product description in 2–3 bullets when asked
- **AI-V2-04**: Compare-products Q&A — `get_product` × 2 + reasoning prompt
- **AI-V2-05**: Cart-aware suggestions — "bunu da almak ister misin?" via cart embeddings + similarity search

---

## Out of Scope

Explicitly excluded. Documented to prevent scope creep and to demonstrate to graders that exclusions are deliberate.

| Feature | Reason |
|---------|--------|
| Reviews and ratings | Not in bootcamp brief; no AI summary feature requested. Mitigation: optional static "yıldız" placeholder so graders don't read the absence as a bug |
| Real email / SMS sending | No SMTP/Twilio account; not in brief. notification-service mocks via structured logs |
| Multi-vendor seller dashboards | 5x scope explosion; not in brief. All seed products owned by fictional "n11 Pazaryeri" |
| Multiple LLM providers (OpenAI, Claude adapters) | The abstraction itself is the SOLID demo. Ship Gemini adapter only; document second adapter as 30-LOC future extension |
| Separate NL-search bar | Don't dilute AI investment across shallow features. Semantic search is available via the chat assistant; the visible search bar stays plain text |
| Recommendations panel | Emerges from chat assistant tool use, not a separate UI |
| Admin dashboard / merchant tools | Not in brief. Postman + Swagger + DB seed scripts cover admin-grade flows |
| Mobile app / PWA | Brief is React-only. Responsive web only |
| Real-time WebSocket cart sync | Polling/invalidation is sufficient. SSE for chat streaming is the only real-time we ship |
| Production observability stack (ELK / Loki / tracing) | 6-day window. Structured JSON logs + correlation IDs is enough |
| Strict DB-per-service on separate Postgres instances | Local docker-compose deploy = single Postgres host; multiple instances add ops surface for zero architectural gain. Schema-per-service + per-service DB user with role-deny is the boundary |
| Real-time stock updates ("Son 1 kaldı!") | Not in brief; distracts. Stock checked at page load + saga reservation step |
| Test coverage > 70% | 6-day window. Smoke unit + 1–2 integration per service on critical path |
| Reviews / ratings backend | Not in brief; out of agentic-commerce wedge focus |
| Custom CSS framework / design system | Use Tailwind utility classes + n11-recon-derived tokens; don't build a system |
| Email verification at signup | Brief doesn't mandate; cuts SMTP dep |
| Password reset via email | Same; defer to v2+ if SMTP appears |
| OAuth login | Email/password sufficient for v1 brief |
| TC kimlik validation | Optional in real e-commerce; cuts complexity for v1 |
| AWS Elastic Beanstalk + RDS deployment | Candidate's local machine has sufficient compute to host all 13 services + Postgres + RabbitMQ via docker-compose; demo URL is exposed via Cloudflare Tunnel / ngrok. Eliminates Pitfall #12 (EB-vs-13-microservices fit), $0 cloud spend, and faster iteration. Note: original brief listed AWS as must-have; coordinator confirmation recommended |

---

## Traceability

Populated by the roadmapper agent on 2026-04-28. Every v1 requirement maps to exactly one phase.

| Requirement | Phase | Status |
|-------------|-------|--------|
| AUTH-01 | Phase 3 | Pending |
| AUTH-02 | Phase 3 | Pending |
| AUTH-03 | Phase 3 | Pending |
| AUTH-04 | Phase 3 | Pending |
| AUTH-05 | Phase 3 | Pending |
| AUTH-06 | Phase 3 | Pending |
| AUTH-07 | Phase 3 | Pending |
| AUTH-08 | Phase 3 | Pending |
| PROD-01 | Phase 4 | Pending |
| PROD-02 | Phase 4 | Pending |
| PROD-03 | Phase 4 | Pending |
| PROD-04 | Phase 4 | Pending |
| PROD-05 | Phase 4 | Pending |
| PROD-06 | Phase 4 | Complete |
| PROD-07 | Phase 4 | Complete |
| PROD-08 | Phase 4 | Complete |
| PROD-09 | Phase 4 | Pending |
| CART-01 | Phase 5 (05-02) | Complete |
| CART-02 | Phase 5 (05-02) | Complete (KDV display deferred to Phase 10 — see 05-HUMAN-UAT.md) |
| CART-03 | Phase 5 (05-02) | Complete |
| CART-04 | Phase 5 (05-02) | Complete |
| CART-05 | Phase 5 (05-02) | Complete |
| CART-06 | Phase 5 (05-05) | Complete |
| ORD-01 | Phase 5 (05-03) | Complete |
| ORD-02 | Phase 5 (05-03) | Complete |
| ORD-03 | Phase 5 (05-03) | Complete |
| ORD-04 | Phase 5 (05-03, 05-04) | Complete |
| ORD-05 | Phase 5 (05-03) | Complete |
| ORD-06 | Phase 5 (05-03, 05-05) | Complete |
| PAY-01 | Phase 6 | Complete |
| PAY-02 | Phase 6 | Complete |
| PAY-03 | Phase 6 | Complete |
| PAY-04 | Phase 6 | Complete |
| PAY-05 | Phase 6 | Complete |
| PAY-06 | Phase 6 | Pending |
| PAY-07 | Phase 6 | Pending |
| NOTIF-01 | Phase 7 | Pending |
| NOTIF-02 | Phase 7 | Pending |
| NOTIF-03 | Phase 7 | Pending |
| AI-01 | Phase 8 | Pending |
| AI-02 | Phase 8 | Pending |
| AI-03 | Phase 8 | Pending |
| AI-04 | Phase 8 | Pending |
| AI-05 | Phase 8 | Pending |
| AI-06 | Phase 8 | Pending |
| AI-07 | Phase 8 | Pending |
| AI-08 | Phase 8 | Pending |
| AI-09 | Phase 8 | Pending |
| AI-10 | Phase 8 | Pending |
| AI-11 | Phase 9 | Pending |
| AI-12 | Phase 9 | Pending |
| AI-13 | Phase 9 | Pending |
| AI-14 | Phase 8 | Pending |
| AI-15 | Phase 8 | Pending |
| FE-01 | Phase 2 | Pending |
| FE-02 | Phase 10 | Pending |
| FE-03 | Phase 10 | Pending |
| FE-04 | Phase 10 | Pending |
| FE-05 | Phase 10 | Pending |
| FE-06 | Phase 10 | Pending |
| FE-07 | Phase 10 | Pending |
| FE-08 | Phase 10 | Pending |
| FE-09 | Phase 10 | Pending |
| FE-10 | Phase 10 | Pending |
| FE-11 | Phase 10 | Pending |
| FE-12 | Phase 11 | Pending |
| FE-13 | Phase 10 | Pending |
| FE-14 | Phase 10 | Pending |
| FE-15 | Phase 10 | Pending |
| FE-16 | Phase 10 | Pending |
| LOC-01 | Phase 10 | Pending |
| LOC-02 | Phase 10 | Pending |
| LOC-03 | Phase 10 | Pending |
| LOC-04 | Phase 10 | Pending |
| LOC-05 | Phase 10 | Pending |
| ARCH-01 | Phase 1 | Pending |
| ARCH-02 | Phase 1 (01-05) | Complete |
| ARCH-03 | Phase 1 (01-05) | Complete |
| ARCH-04 | Phase 1 (01-06) | Complete |
| ARCH-05 | Phase 1 | Pending |
| ARCH-06 | Phase 5 (05-04) | Complete |
| ARCH-07 | Phase 5 (05-01, 05-04) | Complete |
| ARCH-08 | Phase 5 (05-04) | Complete |
| ARCH-09 | Phase 1 | Pending |
| ARCH-10 | Phase 1 | Pending |
| ARCH-11 | Phase 1 (01-05 + 01-07) | Partial (01-05 server-half done — Pitfall #4 boundary; 01-07 client-half pending — service-template retry+backoff config) |
| ARCH-12 | Phase 1 | Pending |
| QUAL-01 | Phase 1 (01-06 + Phase 4+) | Partial (01-06: Springdoc aggregator config with urls=[] empty; Phase 4+ phases append per-service entries as they come online) |
| QUAL-02 | Phase 3 | Pending |
| QUAL-03 | Phase 5 (05-04) | Complete |
| QUAL-04 | Phase 7 | Pending |
| QUAL-05 | Phase 6 | Pending |
| QUAL-06 | Phase 1 (01-04 + 01-07) | Partial (01-04 done — MDC wires; 01-07 pending — Logback JSON encoder) |
| QUAL-07 | Phase 1 (01-02 + 01-04) | Complete (01-04: ProblemDetailControllerAdvice in common-error) |
| QUAL-08 | Phase 8 | Pending |
| QUAL-09 | Phase 1 | Pending |
| DEV-01 | Phase 11 | Pending |
| DEV-02 | Phase 11 | Pending |
| DEV-03 | Phase 11 | Pending |
| DEV-04 | Phase 11 | Pending |
| DEV-05 | Phase 11 | Pending |
| DEV-06 | Phase 11 | Pending |
| DEV-07 | Phase 1 | Pending |
| DEV-08 | Phase 11 | Pending |
| DEV-09 | Phase 11 | Pending |

**Coverage:**
- v1 requirements: 105 total (AUTH 8 · PROD 9 · CART 6 · ORD 6 · PAY 7 · NOTIF 3 · AI 15 · FE 16 · LOC 5 · ARCH 12 · QUAL 9 · DEV 9)
- v2 requirements: 9 (stretch — not yet mapped)
- Mapped to phases: 105 / 105 (100% v1 coverage)
- Unmapped: 0
- Phase distribution: P1=14 · P2=1 · P3=9 · P4=9 · P5=16 · P6=8 · P7=4 · P8=13 · P9=3 · P10=19 · P11=9

---
*Requirements defined: 2026-04-28*
*Last updated: 2026-04-28 — DEV-03/-05/-06/-08/-09 reworded for local docker-compose deploy + tunnel exposure (AWS dropped). PAY-05 narrowed to Cloudflare Tunnel / ngrok. Out-of-Scope row added for AWS EB+RDS.*
