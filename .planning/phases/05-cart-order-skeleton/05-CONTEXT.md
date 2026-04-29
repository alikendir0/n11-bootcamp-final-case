# Phase 5: Cart & Order Skeleton - Context

**Gathered:** 2026-04-30
**Status:** Ready for planning
**Mode:** `--auto` (Claude auto-selected the recommended option for every gray area; see DISCUSSION-LOG.md for the alternatives considered)

<domain>
## Phase Boundary

Stand up two new business services — `cart-service` and `order-service` — and prove the choreography saga **end-to-end against a mock payment producer** so Phase 6 can swap in real Iyzico against a frozen contract. This phase ALSO ships a tiny `payment-service` skeleton (no Iyzico, no `PaymentProvider` port yet — Phase 6 introduces both) whose only role is to consume `stock.reserved` and emit `payment.completed` so the full happy path runs in `Testcontainers + Awaitility` integration tests.

**In scope:**
- `cart-service` Spring Boot module cloned from `service-template/skeleton/`, port 8084, schema `cart`, Eureka-registered, behind gateway at `/api/v1/cart/**`. REST surface locked in `api-contracts.md` §1: `GET /cart`, `POST /cart/items`, `PATCH /cart/items/{id}`, `DELETE /cart/items/{id}`. (`DELETE /cart` for full clear is reserved for the saga `cart.q.order-confirmed` consumer — internal, not REST.)
- `order-service` Spring Boot module cloned from `service-template/skeleton/`, port 8085, schema `orders` (plural — SQL reserved word), Eureka-registered, behind gateway at `/api/v1/orders/**`. REST surface: `POST /orders`, `GET /orders`, `GET /orders/{id}`, `POST /orders/{id}/cancel`.
- `payment-service` MINIMAL skeleton (Phase 5 — no Iyzico, no `PaymentProvider` port; Phase 6 owns both), port 8086, schema `payment`, NO public REST endpoints (Phase 6 adds `/payments/checkout`). Single responsibility: consume `stock.reserved` → publish `payment.completed` after a configurable mock delay (`mock.payment.delay-ms: 100`). This makes the saga happy path runnable for SC-2's integration test.
- Saga skeleton end-to-end (CART-01..CART-06, ORD-01..ORD-06, ARCH-06..ARCH-08, QUAL-03):
  - `OrderCreated` → inventory consumes (already in Phase 4) → `StockReserved` → payment-service-skeleton consumes → `PaymentCompleted` → order-service consumes → order CONFIRMED → `OrderConfirmed` → cart-service consumes → cart cleared
  - All 4 compensation paths wired: `StockReserveFailed` → order CANCELLED + `OrderCancelled`; `PaymentFailed` → inventory releases stock + order CANCELLED; user-cancel → inventory releases stock + order CANCELLED; payment-timeout placeholder is OUT OF SCOPE here (Phase 6 owns the timeout job — but the consumer wiring on `payment.failed` MUST be in place now).
- Idempotency: every consumer uses the existing `processed_events` inbox (Phase 1 service-template archetype); every producer uses transactional outbox (Phase 3 outbox poller pattern). Re-delivery test asserts a single side effect.
- Springdoc Swagger UI on cart-service + order-service; gateway aggregator dropdown gets two new entries; payment-service has no Springdoc surface in v1 (no REST).
- Address-snapshot pattern (Phase 3 D-10 forward-compat): order-service copies the address fields into its own `order_shipping_addresses` row at order-creation time so subsequent `PATCH /addresses/{id}` doesn't retro-mutate orders.
- `Idempotency-Key` HTTP header on `POST /orders` (Stripe-pattern, see D-05).
- 999.2 prerequisite (CRITICAL): every outbox publisher MUST set `MessageProperties.setMessageId(eventId)` on every published AMQP message; every saga consumer MUST follow `AcknowledgeMode.AUTO + RejectAndDontRequeueRecoverer` pattern. Phase 5 ships an architecture test (ArchUnit or grep-based CI gate) that fails the build if any new consumer reverts to `MANUAL` ack without `Channel` parameter.
- One Testcontainers + Awaitility integration test PER service on critical path (QUAL-03): cart-service line-item upsert, order-service end-to-end happy-path saga (across all four services + RabbitMQ + Postgres).

**Out of scope (later phases own these):**
- Iyzico Checkout Form integration, 3DS callback, payment webhook, payment-timeout job, `PaymentProvider` abstraction port — Phase 6.
- notification-service consumer for `order.confirmed` / `payment.failed` — Phase 7.
- Saga happy-path integration test that includes notification logging — Phase 7 (QUAL-04 explicitly maps there).
- Frontend cart/order UI — Phase 10.
- `Hemen Al` ("Buy Now") instant-checkout — out of scope per PROJECT.md (goes through cart-add internally if implemented later).
- Shipping/fulfillment events ("Kargoya Verildi", "Teslim Edildi") — explicitly out of scope; Phase 10 frontend renders these as static placeholder steps in the UI timeline (D-08).
- `cart.checked_out` event from cart-service — ARCHITECTURE.md §2.7 marks it optional; v1 skips it (cart clearing is driven by `order.confirmed`).
- Free-shipping calculation, taksit preview, KDV breakdown rendering — backend stores `priceGross` + `kdvRate` (LOC-01); frontend (Phase 10) handles all derived display values.

</domain>

<decisions>
## Implementation Decisions

### Cart Line-Item Snapshot Strategy
- **D-01:** **Snapshot `unitPriceSnapshot` + `nameSnapshot` at add-time (and on quantity-update).** When `POST /cart/items` runs, cart-service synchronously calls `GET /products/{id}` once and stores `unit_price_snapshot` (KDV-inclusive `priceGross`) + `name_snapshot` + `image_url_snapshot` + `added_at` on the `cart_items` row. `GET /cart` returns these snapshotted fields directly (no per-read product-service round-trip). At `POST /orders`, order-service re-fetches `GET /products/by-ids` to re-validate prices; if any item drifted by more than ±0% (strict equality for v1), return `409 Conflict` with `{"updatedItems": [...]}` so the frontend can re-render the cart with new prices and ask the user to confirm. Why now: matches `order.created.items.unitPrice` saga contract (`order-created.schema.json`); cart UI shows stable totals between page loads; price-drift detection is a free-shipping-style nicety the n11 storefront genuinely demonstrates.
  - **Auto-selected (recommended).** Alternative: live-fetch on every cart read (rejected — chatty, no audit trail of what the user agreed to pay).

### Cart Line-Item Identifier (`/cart/items/{id}` — what is `{id}`?)
- **D-02:** **`{id}` is `productId` (UUID).** Composite primary key on `cart_items(user_id, product_id)`; `productId` is unique-per-cart by construction. `POST /cart/items {productId, qty}` is an UPSERT (INSERT ... ON CONFLICT (user_id, product_id) DO UPDATE SET qty = qty + EXCLUDED.qty, unit_price_snapshot = EXCLUDED.unit_price_snapshot, name_snapshot = ...). Why: zero need for a synthetic surrogate id, matches ARCHITECTURE.md §2.7's owned-data shape, simpler frontend wiring (cart UI already keys by productId for re-add). Also: api-contracts.md §1's `PATCH /cart/items/{id}` uses `{id}` exactly; we lock the semantic here so Phase 10 doesn't have to ask.
  - **Auto-selected (recommended).** Alternative: synthetic `cart_item_id UUID` (rejected — no value, doubles index footprint, frontend needs an extra lookup).

### Order-Creation Flow (sync vs async)
- **D-03:** **Pure choreography — `POST /orders` returns `202 Accepted` with `{orderId, status: "PENDING"}` immediately.** Order-service writes `orders` + `order_items` + `order_shipping_addresses` + `outbox(order.created)` in ONE `@Transactional` boundary. Outbox poller publishes `order.created` to `orders.tx`. The frontend polls `GET /orders/{id}` (Phase 10 frontend handles polling; Phase 11 will also wire SSE/`order.status` push if cheap, but Phase 5 ships polling-only). Why: matches ARCHITECTURE.md §3.2 happy-path canonical flow, decouples sync HTTP latency from saga step count, makes the saga the single source of truth for order status. The synchronous-call alternatives (frontend waits for stock-reserved before returning 200) leak choreography internals into the HTTP layer and break under any retry.
  - **Auto-selected (recommended).** Alternative (b): sync call to inventory for instant reservation result (rejected — couples HTTP to saga, breaks under inventory restart). Alternative (c): sync price re-validation against product-service before outbox write (NOTE: this is still happening — see D-01's price-drift check — but it's pre-saga, not part of the saga itself).

### Address Snapshot at Order Creation (Phase 3 D-10 forward-compat lock)
- **D-04:** **Separate `order_shipping_addresses` table** in the `orders` schema, one row per order, FK to `orders(id)`. Fields mirror Phase 3's `addresses` table (D-09): `recipient_name`, `phone`, `il`, `ilce`, `mahalle`, `street_line`, `postal_code`, `title` (the user-visible label like "Ev"). Order-service calls `GET /api/v1/identity/addresses/{addressId}` (gateway-routed, JWT-forwarded as `X-User-Id`) at order creation time, validates the address belongs to the `X-User-Id`, copies fields, and persists. Why separate table (not inline columns or JSONB): normalized, easy to query for "ship-to" reports, matches Phase 3 D-10's announced contract verbatim, JSONB hides the field set from Springdoc / DB consumers. NOTE: `order_id` is the PK (1:1 with order); no soft-delete (orders are immutable after creation).
  - **Auto-selected (recommended).** Alternative: inline columns on `orders` (rejected — bloats the orders row, mixes lifecycle concerns). JSONB column (rejected — opaque to Springdoc).

### Order Idempotency on `POST /orders`
- **D-05:** **Required `Idempotency-Key` HTTP header on `POST /orders`** — UUID generated by the frontend (Stripe-pattern). Order-service maintains an `order_idempotency_keys(idempotency_key UUID PK, user_id UUID, order_id UUID, created_at TIMESTAMPTZ)` table in the `orders` schema; UNIQUE constraint on (idempotency_key, user_id) — same key by a different user is a 401 conflict that we treat as a 400 (suspicious). Repeat key by same user returns the existing `orderId` with HTTP 200 (idempotent — caller cannot tell whether this is the first call or a retry). Missing header on `POST /orders` returns `400 Bad Request` with RFC-7807 `errors[]`. Why: Stripe / common API discipline; small surface area; prevents the classic "user double-clicks Siparişi Tamamla" double-charge case from ever existing; saga remains idempotent at every layer (HTTP, AMQP, DB).
  - **Auto-selected (recommended).** Alternative: cart-version check (rejected — couples cart freshness with order placement; race-prone). One-active-pending-order-per-user heuristic (rejected — semantic overload, breaks if user genuinely wants a second order).
  - **CD-NOTE:** Idempotency-Key TTL: planner can pick (recommend 24h cleanup job, but for v1 Phase 5 just keeps rows forever — ~30 grader orders is nothing).

### Phase 5 Payment-Service Scope (what's IN now, vs Phase 6)
- **D-06:** **Ship a 200-LOC payment-service skeleton in Phase 5** — Spring Boot app, port 8086, schema `payment`, Eureka-registered, NOT routed through gateway in Phase 5 (no public REST endpoints yet — Phase 6 adds `/payments/checkout` etc and the gateway route). The skeleton's only consumer is `StockReservedConsumer` listening on `payment.q.stock-reserved` (per saga-contracts.md §2). On consume: idempotency check via `processed_events`, then write a `payments` row with `status='COMPLETED'`, then write an outbox row with `eventType='payment.completed'`, `eventVersion=1`, payload `{orderId, paymentId, amount}`. Configurable mock delay (`mock.payment.delay-ms: 100`, default 100ms; setting to 0 disables for tests). NO `PaymentProvider` port (Phase 6 introduces it — that's where the SOLID demo for payment lives). Why now: Phase 5 SC-2 explicitly requires a verifiable `Testcontainers + Awaitility` end-to-end happy-path saga; without ANY payment producer the saga stops at `stock.reserved` and SC-2 is unprovable. Why minimal: avoid building a payment-service module in Phase 5 that gets ~80% rewritten in Phase 6 — keep the skeleton small enough that Phase 6's swap-out is a clean replacement, not a refactor.
  - **Auto-selected (recommended).** Alternative (a): no payment-service in Phase 5; use a test-only synthetic event publisher (rejected — leaks test code into production fixture; saga happy-path on the actual deployed stack stops at stock.reserved). Alternative (b): full payment-service with `PaymentProvider` port + `MockPaymentProvider` adapter (rejected — Phase 6 owns the abstraction; introducing it here pre-empts the SOLID demo for payment).

### Cart Clearing on `order.confirmed`
- **D-07:** **Clear ENTIRE cart on `order.confirmed` consumer** in cart-service — `DELETE FROM cart_items WHERE user_id = ?` (the `carts` table row stays — it's a per-user record, not a per-cart aggregate). Why: v1 has no partial checkout (no "buy this 1 of 3 items" flow); `POST /orders` snapshots ALL cart items into the order. Edge case: if the user added a new item to cart between `POST /orders` and `order.confirmed` arriving, that item gets cleared too — acceptable for v1 (the user-perceived window is sub-second on the local-host demo). Phase 10 frontend optimistically clears the cart UI on `POST /orders` 202 response and lets the saga re-confirm; the consumer is the source of truth.
  - **Auto-selected (recommended).** Alternative: clear only the items present in the order's snapshot (rejected — adds saga payload weight, doesn't match v1's all-or-nothing checkout).

### Order Status Enum + Saga ↔ Display-Label Mapping
- **D-08:** **Backend canonical status enum (`OrderStatus.java`)** = exactly the 6-state machine ROADMAP SC-3 + ARCHITECTURE.md §2.8 prescribe: `PENDING → STOCK_RESERVED → PAID → CONFIRMED`, plus failure tails `STOCK_FAILED → CANCELLED` and `PAYMENT_FAILED → CANCELLED` (and `USER_CANCELLED` direct from PENDING/STOCK_RESERVED via `POST /orders/{id}/cancel`). Add a `cancel_reason VARCHAR(64)` column on `orders` for cancellation cause (`OUT_OF_STOCK`, `PAYMENT_DECLINED`, `USER_CANCELLED`, `PAYMENT_TIMEOUT`). The Turkish display timeline ("Sipariş Alındı → Hazırlanıyor → Kargoya Verildi → Teslim Edildi") is **frontend-only** — Phase 10 maps {`PENDING`, `STOCK_RESERVED`} → "Sipariş Alındı"; {`PAID`, `CONFIRMED`} → "Hazırlanıyor"; "Kargoya Verildi" + "Teslim Edildi" are static placeholder steps (greyed out — explicitly out of scope per PROJECT.md). Backend exposes `GET /orders/{id}` with the canonical enum value + `cancelReason` + `updatedAt`; the timeline is a presentation concern. Why: keeps backend faithful to saga semantics, lets v1 ship without inventing fake shipping events, gives Phase 10 a clear contract to render.
  - **Auto-selected (recommended).** Alternative: bake the 4-step Turkish-display states into the backend (rejected — couples saga lifecycle to UI labels, breaks if UI copy changes, conflates real saga states with placeholder steps).

### OutboxPoller Refactor — Extract `common-outbox` Shared Module
- **D-09:** **Extract the OutboxPoller pattern into a new `common-outbox` Gradle library module** — Phase 3 D-13 deferred this until the second use case existed; Phase 5 introduces THREE more pollers (cart-service has none — cart isn't a saga producer here, just a consumer; order-service + payment-service-skeleton get one each; and we MIGRATE inventory-service + identity-service to consume the shared module). The shared module exports: `OutboxEvent` (JPA entity), `AbstractOutboxPoller` (Spring `@Scheduled` driver — concrete subclasses just specify the per-service `OutboxRepository`), and a critical `MessagePostProcessor` that reads outbox row's UUID-shaped envelope `eventId` from the payload JSON and sets `MessageProperties.setMessageId(eventId)` on every AMQP publish. This fixes the 999.2 prerequisite uniformly. Migrating identity-service + inventory-service is part of Phase 5's plan; their existing tests run unchanged. Why now: 4-use-case threshold is decisively past Phase 3's "wait for 2nd" trigger; refactor cost is small (~80 LOC moved); duplication risk on the message_id setter is otherwise high (every poller would have to remember independently).
  - **Auto-selected (recommended).** Alternative: keep per-service pollers, copy-paste the message_id setter into each (rejected — exactly the kind of copy-paste-bug the 999.2 retrospective warned against; CLAUDE.md Rule #4 spirit applies).

### 999.2 Architecture-Test Enforcement
- **D-10:** **Add an ArchUnit test** in `infra-tests/` that asserts: every class annotated with `@RabbitListener` is in a class whose factory does NOT use `AcknowledgeMode.MANUAL` unless the listener method has a `Channel` parameter. Failure path: any future Phase 5+ consumer that reverts to `MANUAL` without `Channel` fails the build. Implementation idea: ArchUnit's `noClasses().that().areAnnotatedWith(RabbitListener.class)...` plus a custom `ArchCondition` that inspects bean names. ALTERNATIVELY (simpler), a plain JUnit test that runs at build time, scans the classpath via `Reflections`, and grep-asserts the listener-factory configuration. Why: 999.2 backlog explicitly asked for this CI gate; Phase 5 introduces 3+ new consumers (cart, order, payment-service-skeleton stock-reserved) and is the natural home; without the test, the bug we just fixed in Plan 04-03 (commit `2b61689`) can silently come back the next time someone copy-pastes a consumer.
  - **Auto-selected (recommended).** Alternative: rely on code review + CLAUDE.md Rule #3 reminder text alone (rejected — the regression already happened once silently; structural enforcement is the right shape).

### Cart-Service Schema: One-User-One-Cart vs Multi-Cart
- **D-11:** **One cart per user, lazy-create on first `POST /cart/items`.** `carts(user_id UUID PK, updated_at TIMESTAMPTZ)` — single row per user, `user_id` from gateway-injected `X-User-Id` header (no JWT decoding in cart-service per Phase 3 D-15). `cart_items(user_id, product_id, qty, unit_price_snapshot, name_snapshot, image_url_snapshot, added_at)` with composite PK on (user_id, product_id). `GET /cart` lazy-creates an empty `carts` row if none exists (returns `{userId, items: [], updatedAt}`). Why: matches CART-05 ("Cart persists per logged-in user (cart-service is the single source of truth for both web UI and chat assistant)"); no anonymous carts in v1 (chat assistant requires JWT for mutating tools per Phase 8 AI-14); UPSERT on (user_id, product_id) handles the "add same product twice" case naturally.
  - **Auto-selected (recommended).** Alternative: anonymous cart with `cart_id UUID` and merge-on-login (rejected — out of scope per LOC/AI design; chat assistant can't act for guests anyway because `add_to_cart` mutates).

### Claude's Discretion (planner picks the concrete value)
- **CD-01:** Concrete DDL field types for `orders` table — recommend `id UUID PK, user_id UUID NOT NULL, status VARCHAR(32) NOT NULL CHECK (status IN (...)), total_amount NUMERIC(12,2) NOT NULL, currency CHAR(3) NOT NULL CHECK (currency = 'TRY'), correlation_id UUID NOT NULL, idempotency_key UUID NOT NULL, cancel_reason VARCHAR(64), created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()`. Index on `(user_id, created_at DESC)` for the listing.
- **CD-02:** Concrete DDL for `order_items` — `order_id UUID FK, product_id UUID, name_snapshot TEXT, qty INT CHECK (qty > 0), unit_price NUMERIC(10,2) CHECK (unit_price >= 0)`. Composite PK on (order_id, product_id) (no partial-checkout means product_id is unique per order).
- **CD-03:** Concrete DDL for `cart_items` (`unit_price_snapshot` precision must match `price_gross` from product-service; check Phase 4's product table — recommend `NUMERIC(10,2)` to match `price_gross` exactly).
- **CD-04:** AMQP listener-factory bean — recommend each new service's `RabbitConfig` autowires `RabbitRetryConfig.rabbitListenerContainerFactory` (the bean already in `common-events`, exposed by Plan 01-04). New consumers reference this factory by name (Spring picks the only `SimpleRabbitListenerContainerFactory` bean in context). No re-declaration.
- **CD-05:** `OutboxEvent` JPA entity in `common-outbox` — keep the field set identical to inventory-service's existing entity (`id UUID PK, aggregate, event_type, payload JSONB, occurred_at, sent_at`) so the migration is a pure move. Per-service Flyway migrations stay unchanged (they already create the `outbox` table — common-outbox provides Java-only artifacts, no Flyway shipping).
- **CD-06:** Test pattern for QUAL-03 — recommend ONE `Testcontainers` Postgres-only test per service for repository / domain logic (e.g., cart upsert idempotency, order Idempotency-Key dedup), PLUS ONE shared end-to-end Testcontainers + Awaitility test in `infra-tests/` that boots Postgres + RabbitMQ and exercises the full happy-path saga (4 services). The shared E2E test is the SC-2 deliverable. Per-service slices are cheaper.
- **CD-07:** Wave breakdown — planner territory; likely 4 waves: (W0) `common-outbox` shared module + identity-service + inventory-service migration to it + 999.2 ArchUnit test; (W1) cart-service module scaffold + Flyway + REST + repository test (parallel to W0 mid-stream); (W2) order-service module scaffold + Flyway + saga consumer + outbox publisher + Idempotency-Key + REST + repository test; (W3) payment-service skeleton + happy-path E2E test in `infra-tests/`; (W4) gateway routes for cart + orders + Springdoc aggregator + smoke runbook. Plan 04 tightened to 3 plans/3 waves; Phase 5 likely needs 4-5 plans/4 waves — three new services + a refactor + an arch test is genuinely larger than Phase 4.
- **CD-08:** Whether Phase 5 ships an `order.q.payment-failed` consumer wiring TODAY for the inventory-side compensation (saga-contracts.md §2 lists `inventory.q.payment-failed` already in topology). Recommendation: **YES — wire it now.** Even though no `payment.failed` event will fire in Phase 5 (mock payment always succeeds), the consumer plumbing is identical to `OrderCreatedConsumer` and Phase 6 picks up real Iyzico-failure events seamlessly. Cost: ~50 LOC. Avoids a Phase 6 schedule slip if Iyzico decline behavior surfaces edge cases.
- **CD-09:** Whether `POST /orders/{id}/cancel` is in scope for Phase 5. Recommendation: **YES — partial.** Wire the endpoint + the `order-service → publish order.cancelled` flow + the `inventory.q.order-cancelled` consumer (already in saga-contracts.md §2). Skip the user-cancellation FRONTEND in Phase 5 (Phase 10 owns it). Backend smoke verifies the path via curl in the runbook.
- **CD-10:** Cart line-item upper bound (max items per cart, max qty per line). Recommendation: soft-cap qty at 99 per line (frontend constraint, server-side `@Max(99)` validation), no cap on line count. Out-of-scope: real e-commerce inventory/per-product purchase limits.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents (researcher, planner, executor) MUST read these before any code is written.**

### Project Context (always)
- `.planning/PROJECT.md` — Key Decisions table (locked architectural choices: 13-service decomposition, choreography SAGA, JWT-at-gateway, schema-per-service); Open Questions (Phase 6 tunnel decision is irrelevant here, but acknowledged); Out of Scope (no Hemen Al, no real-time WebSocket, no shipping/fulfillment events)
- `.planning/REQUIREMENTS.md` — Phase 5 reqs: `CART-01..CART-06`, `ORD-01..ORD-06`, `ARCH-06` (saga choreography), `ARCH-07` (saga idempotency), `ARCH-08` (correlation-IDs), `QUAL-03` (1-2 integration tests per service)
- `.planning/ROADMAP.md` §"Phase 5: Cart & Order Skeleton" — goal, 5 success criteria, risks (Pitfall #3 idempotency, Pitfall #11 compensation completeness, dual-writes), research need = LOW
- `.planning/STATE.md` — current position (Phase 4 complete, ready for Phase 5)
- `CLAUDE.md` — Rule #3 (saga consumers MUST be idempotent — `processed_events` inbox, redelivery test); Rule #4 (verify external SDK docs — applies to RabbitMQ docs at impl time); Rule #6 (Turkish UI / English code; cart/order entities and logs in English)

### Architecture & Saga (the spine — research output)
- `.planning/research/ARCHITECTURE.md` §2.7 — cart-service per-service contract (REST surface, owned data with `unit_price_snapshot` field, sync deps, gateway exposure) — direct input to D-01, D-02, D-11
- `.planning/research/ARCHITECTURE.md` §2.8 — order-service per-service contract (status enum, REST surface, owned data with `outbox` table, all 4 events published, all 4 events consumed, sync deps on cart-service + product-service) — direct input to D-03, D-08
- `.planning/research/ARCHITECTURE.md` §2.9 — payment-service per-service contract (Phase 5 ships ONLY the saga-consumer half; Phase 6 owns Iyzico)
- `.planning/research/ARCHITECTURE.md` §3.1 — full RabbitMQ exchange/queue topology with DLX/DLQ — D-09's `MessagePostProcessor` and consumer wiring all derive here
- `.planning/research/ARCHITECTURE.md` §3.2 — happy-path saga sequence diagram — D-03's pure-choreography flow tracks this verbatim
- `.planning/research/ARCHITECTURE.md` §3.3 — compensation paths A (stock-fail), B (payment-fail), C (user-cancel) — Phase 5 wires ALL three (CD-08, CD-09)
- `.planning/research/PITFALLS.md` #3 (Non-idempotent SAGA consumers — drives D-09 outbox refactor + D-10 ArchUnit test); #11 (SAGA compensation incomplete — drives full 4-path wiring); #26 (Day-1 bikeshedding — saga + REST contracts already locked in `saga-contracts.md` + `api-contracts.md`, do NOT re-debate them)
- `.planning/research/STACK.md` — Java 21, Spring Boot 3.5.14, Spring AMQP 3.2.x (StatefulRetryInterceptor lives in spring-retry NOT spring-rabbit per Phase 1 Plan 01-04), Postgres 16

### Day-1 Locked Contracts (authoritative — never re-decide)
- `.planning/saga-contracts.md` §1 (envelope shape — 8 fields), §2 (5 exchanges + 13 queues — Phase 5 uses `orders.tx`, `inventory.tx`, `payments.tx` and the 8 queues bound to them; `cart.q.order-confirmed` and `inventory.q.payment-failed`/`inventory.q.order-cancelled` are Phase 5 deliverables), §3 (DLX/DLQ convention — every queue gets one), §4 (retry policy LOCKED wording — 3 attempts, 1s/5s, cap 30s), §5 (idempotency: transactional outbox + processed_events inbox), §6 (correlation-IDs flow through HTTP + AMQP + MDC), §7 (event catalog — all 9 schemas live in `.planning/saga-contracts/`), §9 (orders schema name is `orders` plural — SQL reserved word)
- `.planning/saga-contracts/envelope.schema.json` — every event payload must validate against this
- `.planning/saga-contracts/order-created.schema.json` — `{orderId, userId, currency:"TRY", totalAmount, items: [{productId, qty, unitPrice, nameSnapshot}]}` — order-service publish payload format
- `.planning/saga-contracts/stock-reserved.schema.json` — what payment-service-skeleton consumes
- `.planning/saga-contracts/payment-completed.schema.json` — payment-service-skeleton publish format
- `.planning/saga-contracts/payment-failed.schema.json` — Phase 5 wires the consumers (CD-08); Phase 6 wires the producer
- `.planning/saga-contracts/order-confirmed.schema.json` — order-service publishes; cart-service consumes (clears cart per D-07)
- `.planning/saga-contracts/order-cancelled.schema.json` — order-service publishes (CD-09); inventory-service consumes (release stock — Phase 4 already has the consumer wiring; verify it triggers on `order.cancelled` routing key)
- `.planning/api-contracts.md` §1 cart-service + order-service + payment-service tables — REST surface locked (cart 4 endpoints; orders 4 endpoints; payment 0 in v1)
- `.planning/api-contracts.md` §2 gateway routing table — `/api/v1/cart/**` (StripPrefix=2 per Phase 4 Plan 04-03 fix), `/api/v1/orders/**` (StripPrefix=2)
- `.planning/api-contracts.md` §3 public allowlist — cart and orders are JWT-protected (NO public allowlist entry)
- `.planning/api-contracts.md` §4 Authorization-strip + `X-User-Id` injection — cart-service + order-service read `X-User-Id` from header (Phase 3 D-15 pattern)
- `.planning/api-contracts.md` §5 correlation-ID propagation — Phase 5 ACTIVATES the `@Around` AMQP-listener MDC aspect from common-logging (per the table footnote "Phase 5 activates")
- `.planning/api-contracts.md` §7 RFC-7807 error shape — order-service uses for Idempotency-Key conflicts + price-drift 409s

### Phase 4 Hand-Off Artifacts (read before touching saga code)
- `.planning/phases/04-catalog-inventory/04-03-SUMMARY.md` — bug-fix retrospective for AMQP ack mode (commit `2b61689`), pg_trgm provisioning (commit `f6b38af`), StripPrefix=2 (commit `bc10e37`), message_id property (commit `06338b1`). Phase 5 inherits ALL these fixes; do not re-introduce the regressions.
- `.planning/phases/04-catalog-inventory/04-03-SMOKE-RUNBOOK.md` — manual smoke test pattern; Phase 5's runbook will mirror this shape (cart create → order create → poll status; verify all 4 saga steps land via `processed_events` row counts)
- `.planning/phases/04-catalog-inventory/04-03-PLAN.md` — gateway-routes config pattern; cart + orders routes will follow this exact shape
- `inventory-service/src/main/java/com/n11/inventory/messaging/OrderCreatedConsumer.java` — canonical consumer pattern (split listener / `@Transactional` service; throw `AmqpRejectAndDontRequeueException` on unrecoverable; `AcknowledgeMode.AUTO` factory; envelope deserialization with null guards). New cart + order + payment-skeleton consumers MUST follow this exact shape.
- `inventory-service/src/main/java/com/n11/inventory/messaging/InventoryOrderService.java` — `@Transactional` service delegate; idempotency check INSIDE the transaction; processed_events row written ALWAYS (success OR failure path)
- `inventory-service/src/main/java/com/n11/inventory/messaging/InventoryRabbitConfig.java` — exchange/queue/DLQ Declarables pattern; cart + order + payment-skeleton each ship one of these
- `inventory-service/src/main/java/com/n11/inventory/outbox/OutboxPoller.java` — pre-refactor poller (will be replaced by `common-outbox.AbstractOutboxPoller` per D-09)
- `inventory-service/src/main/java/com/n11/inventory/outbox/OutboxEvent.java` — JPA entity to be lifted into `common-outbox`
- `identity-service/src/main/java/com/n11/identity/outbox/OutboxPoller.java` — second instance of the pre-refactor pattern; also migrates to `common-outbox` per D-09
- `identity-service/src/main/java/com/n11/identity/outbox/OutboxBackedUserRegistrationOutboxPublisher.java` — canonical envelope-construction pattern (eventId, correlationId-equals-eventId for saga roots, causationId, instant occurredAt) — order-service publisher mirrors this for `order.created`
- `common-events/src/main/java/com/n11/events/RabbitRetryConfig.java` — `StatefulRetryOperationsInterceptor` (3/1s/5s/30s) + `SimpleRabbitListenerContainerFactory` with `AcknowledgeMode.AUTO`. New consumers reference this factory bean by name. CRITICAL: `messageKeyGenerator` requires `MessageProperties.setMessageId(eventId)` on every published message — D-09 enforces this in the shared module.
- `common-events/src/main/java/com/n11/events/Envelope.java` — `record Envelope(eventId, eventType, eventVersion, occurredAt:Instant, correlationId, causationId, producer, payload:JsonNode)`
- `service-template/src/main/resources/db/migration/V1__init_processed_events.sql` — every new service's V1 migration
- `service-template/skeleton/` — clone-and-rename source for cart-service + order-service + payment-service (per Phase 1 Plan 01-07 archetype)
- `infra/postgres/init.sh` — `cart_user`, `orders_user`, `payment_user` already provisioned with role-deny matrix; CART_DB_PASSWORD / ORDERS_DB_PASSWORD / PAYMENT_DB_PASSWORD env-var names locked
- `config-server/src/main/resources/config/application.yml` — shared baseline; per-service yamls (`cart-service.yml`, `order-service.yml`, `payment-service.yml`) just set `db.user`, `db.password`, `flyway.schema`
- `config-server/src/main/resources/config/api-gateway.yml` — gateway routes table; Phase 5 adds 2 routes (`cart-service`, `order-service`); payment-service does NOT get a Phase 5 route (no public REST). StripPrefix=2 (NOT 3 — Phase 4 Plan 04-03 fix).
- `api-gateway/src/main/java/com/n11/gateway/SecurityConfig.java` — Phase 3 final form; Phase 5 adds NOTHING (cart + orders inherit `.anyExchange().authenticated()` — already gated)
- `infra-tests/` — Testcontainers boundary smoke pattern (Plan 01-08); Phase 5's saga E2E test follows this shape but adds RabbitMQ container

### External Docs (verify-before-implement policy — read at impl time)
- https://docs.spring.io/spring-amqp/reference/amqp/listener-container.html — `SimpleRabbitListenerContainerFactory` config, `AcknowledgeMode.AUTO` semantics, listener method parameter binding
- https://docs.spring.io/spring-amqp/reference/amqp/receiving-messages.html#async-consumer — `@RabbitListener` with `Message` parameter; throwing `AmqpRejectAndDontRequeueException` for DLQ routing
- https://www.rabbitmq.com/docs/dlx — DLX/DLQ semantics; `x-dead-letter-exchange` queue argument
- https://docs.spring.io/spring-data/jpa/reference/jpa/transactions.html — `@Transactional` boundary in service classes (NOT on listeners — Phase 4 Plan 04-02 lesson)
- https://stripe.com/docs/api/idempotent_requests — `Idempotency-Key` header semantics (D-05 reference)
- https://www.archunit.org/userguide/html/000_Index.html — ArchUnit `noClasses().that()...` and custom `ArchCondition` for D-10
- https://www.testcontainers.org/modules/rabbitmq/ + https://www.testcontainers.org/modules/databases/postgres/ — multi-container test setup for the saga E2E (verify version compatibility — Plan 04-02 noted `rabbitmq:3.13-management` is preferred over 4.0 for Testcontainers stability)
- https://github.com/awaitility/awaitility — `await().atMost(Duration.ofSeconds(5)).untilAsserted(...)` pattern for saga-step assertions

### Phase 5 Deliverables (will become refs for later phases)
- `common-outbox/` — new Gradle library module (D-09); Phase 6 payment-service + Phase 7 notification-service + Phase 8 ai-service all import this
- `cart-service/`, `order-service/`, `payment-service/` — three new Spring Boot modules; Phase 6 swaps payment-service internals; Phases 7-11 reuse the per-service clone-of-skeleton pattern
- `infra-tests/.../SagaHappyPathE2ETest.java` — first 4-service Testcontainers + RabbitMQ test; Phase 7 extends to include notification, Phase 6 extends with Iyzico-failure compensation
- `infra-tests/.../AmqpAckModeArchTest.java` — D-10 ArchUnit gate; Phase 6+ consumers all flow through it
- `.planning/phases/05-cart-order-skeleton/05-CONTEXT.md` (this file)
- `.planning/phases/05-cart-order-skeleton/05-PLAN.md` (next step)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`service-template/skeleton/`** — clone-and-rename source; cart-service, order-service, payment-service each spin up from a copy. Phase 3 (`identity-service`) and Phase 4 (`product-service`, `inventory-service`) prove the procedure works in three different shapes.
- **`common-events/`** — `Envelope` record + `RabbitRetryConfig` (StatefulRetryInterceptor + listener-factory bean) + `AbstractEventSchemaTest` for drift-gating. Three new services depend on it directly.
- **`common-error/`** — `ProblemDetailControllerAdvice` + `ApiErrorCode` enum produces RFC-7807 errors automatically (cart-service `404 cart not found`, order-service `409 idempotency-key reused with different body`, etc).
- **`common-logging/`** — `CorrelationIdFilter` (servlet inbound), `ClientHttpRequestInterceptor` (REST outbound), `RabbitTemplate` `MessagePostProcessor` (AMQP outbound), `@Around` aspect on `@RabbitListener` for MDC re-hydration on inbound (api-contracts.md §5 footnote: "Phase 5 activates" — confirm aspect is wired and tested).
- **`inventory-service`'s saga consumer + outbox** — pattern reference. Plan 04-02 + 04-03 already encoded the right shape; D-09 lifts the outbox half into shared module.
- **`identity-service`'s outbox** — second instance of the pattern; D-09 lifts it.
- **`api-gateway/SecurityConfig.java` + `GatewayHeaderInjectionFilter.java`** — Phase 3 final form; cart + orders inherit `.anyExchange().authenticated()` automatically (no edits needed). Gateway already strips `Authorization` and injects `X-User-Id` + `X-User-Roles` + `X-Correlation-Id`.
- **`infra/postgres/init.sh`** — `cart_user`, `orders_user`, `payment_user` already exist with role-deny matrix; Phase 5 only needs to set the env-var passwords in `.env` and let Flyway run.

### Established Patterns
- **Schema-per-service is structural.** `cart_user` cannot SELECT from `orders` schema (or any other); cross-service data access goes through REST or saga events ONLY. cart-service has a sync read on product-service for snapshotting (D-01), order-service has a sync read on product-service (price re-validation per D-01) and identity-service (`GET /addresses/{id}` per D-04).
- **`processed_events` inbox in every service** (service-template archetype). cart-service is a consumer (`order.confirmed`); order-service is a consumer of all 4 saga responses; payment-service is a consumer (`stock.reserved`). Three new inbox writers join.
- **Transactional outbox** (saga-contracts.md §5). order-service introduces the third instance of the pattern (after identity + inventory); D-09 lifts the abstraction now.
- **Pre-compose Jib build** (Plan 01-05): `./gradlew :cart-service:jibDockerBuild :order-service:jibDockerBuild :payment-service:jibDockerBuild` BEFORE `docker compose up -d`. README the rule for new services.
- **docker-compose additive merge** (Plan 01-05/06): read existing file, ADD new services, never re-write. Verify with `grep -c '^  cart-service:'` returning 1 etc.
- **Per-service Flyway** (Phase 1): `db/migration/V1__init_processed_events.sql` (inherited), `V2__init_<domain>.sql` (cart_items / orders / order_items / order_shipping_addresses / payments), `V3__init_outbox.sql` (or merged into V2).
- **AMQP ack mode = AUTO; consumers throw `AmqpRejectAndDontRequeueException` for unrecoverable, propagate transient** (Plan 04-03 hard lesson; D-10 ArchUnit gate enforces).
- **`MessageProperties.setMessageId(eventId)` on every publish** (Plan 04-03 hard lesson; D-09 `MessagePostProcessor` enforces uniformly via shared module).
- **`spring.config.import` (NOT bootstrap.yml)** — Boot 3.x convention inherited from service-template.

### Integration Points
- **cart-service ↔ Postgres** — via `cart_user`, search_path = cart, public.
- **cart-service ↔ Eureka** — registers as `cart-service`; gateway picks up automatically via discovery-locator.
- **cart-service ↔ product-service** — sync `GET /products/{id}` on cart-add (snapshot). HTTP via gateway-injected `X-Correlation-Id` header (REST), gateway-injected `X-User-Id` flows through. Phase 4 Plan 04-03 confirmed `/api/v1/products/**` is StripPrefix=2-routed.
- **cart-service ↔ RabbitMQ** — single consumer `cart.q.order-confirmed` bound to `orders.tx` with routing key `order.confirmed`. No producer (D-07: cart clearing is event-driven, no `cart.checked_out` event in v1).
- **order-service ↔ Postgres** — via `orders_user`, search_path = orders, public.
- **order-service ↔ Eureka** — registers as `order-service`; gateway routes `/api/v1/orders/**` (StripPrefix=2).
- **order-service ↔ cart-service** — sync `GET /api/v1/cart` (gateway-routed, JWT-forwarded as `X-User-Id`) at order creation to read cart contents.
- **order-service ↔ product-service** — sync `GET /api/v1/products/by-ids` (need to add this endpoint? — VERIFY Phase 4 product-service exposes batch lookup; if not, individual `GET /products/{id}` calls in a loop are acceptable for v1).
- **order-service ↔ identity-service** — sync `GET /api/v1/identity/addresses/{addressId}` for address snapshot (D-04). Returns RFC-7807 404 if address not found OR not owned by `X-User-Id` (identity-service must enforce ownership).
- **order-service ↔ RabbitMQ** — producer on `orders.tx` (`order.created`, `order.confirmed`, `order.cancelled`); consumer on 4 queues: `order.q.stock-reserved`, `order.q.stock-failed`, `order.q.payment-completed`, `order.q.payment-failed`.
- **payment-service-skeleton ↔ Postgres** — via `payment_user`, search_path = payment, public.
- **payment-service-skeleton ↔ RabbitMQ** — consumer on `payment.q.stock-reserved`; producer on `payments.tx` (`payment.completed`).
- **inventory-service** — Phase 4 already wires the `OrderCreatedConsumer`; Phase 5 wires the `inventory.q.payment-failed` and `inventory.q.order-cancelled` compensation consumers (CD-08, CD-09). Same pattern as Phase 4 OrderCreatedConsumer; +50 LOC each.

### Verified Caveats from Phase 4
- **`rabbitmq:3.13-management` for Testcontainers** (NOT 4.0-management — Plan 04-02 noted handshake instability)
- **Direct consumer invocation in Testcontainers** (`consumer.handleX(amqpMsg)`) for slice tests; full AMQP delivery only in the saga E2E test
- **Native `@Query` sort fields use snake_case** (Plan 04-01 — Spring Data passes the field as-is to native SQL)
- **`hikari.connection-init-sql=SET search_path=<schema>` in application-test.yml** (Plan 04-01 — Testcontainers won't honor `init.sh` `ALTER USER ... SET search_path` for the test user)
- **`src/test/resources/application.yml` with `optional:configserver:`** (Plan 04-01 — slice tests bypass config-server bootstrap)

</code_context>

<specifics>
## Specific Ideas

- **Idempotency-Key on `POST /orders` is the killer feature** (D-05). Bigger payoff than its size suggests: graders see Stripe-pattern discipline; demo `curl -H "Idempotency-Key: <uuid>"` twice → both return the same orderId, second is HTTP 200 not 201. One bullet in README sells the discipline.
- **`common-outbox` extraction is the cleanest place for Phase 3 D-13's promised refactor** (D-09). Identity + inventory + order + payment-skeleton = 4 outbox publishers; the abstraction is right-sized at 4 use cases. The MessagePostProcessor that sets `message_id` is a structural fix to the bug Plan 04-03 surfaced.
- **999.2 ArchUnit test is a Phase 5 deliverable** (D-10) — the backlog item explicitly named Phase 5 as the host. Without it, Phase 6+ regressions silently re-introduce the AMQP ack-mode bug.
- **Pure choreography on `POST /orders`** (D-03): the HTTP layer's job ends at outbox-write + 202. The frontend (Phase 10) does the polling. This is the saga textbook flow and the cleanest separation of concerns.
- **Display-label timeline mapping is a frontend concern** (D-08) — backend canonical enum stays faithful to the saga; "Kargoya Verildi" + "Teslim Edildi" are static placeholders in v1, not fake events. Phase 10 owns the mapping table.
- **Ship the payment-service-skeleton minimal** (D-06) — 200 LOC, no `PaymentProvider` port. Phase 6 introduces the SOLID-flavored abstraction (the symmetry with `ChatProvider` in Phase 8 is intentional — Phase 6 telegraphs the AI port grading wedge).
- **CD-08 + CD-09 are not nice-to-haves** — wire the `inventory.q.payment-failed` and `inventory.q.order-cancelled` compensation consumers in Phase 5 even though they don't fire in Phase 5 testing. Phase 6 (real Iyzico failures) and Phase 5 user-cancel both need them.
- **One smoke runbook** mirroring `04-03-SMOKE-RUNBOOK.md` shape — `curl POST /orders` → poll `GET /orders/{id}` until status=CONFIRMED → assert `processed_events` row counts (1 per service) → re-publish original AMQP message → assert NO new side effects (CLAUDE.md Rule #3 over real AMQP).

</specifics>

<deferred>
## Deferred Ideas

- **Iyzico Checkout Form integration + 3DS callback + signature verification** — Phase 6.
- **`PaymentProvider` port + `IyzicoPaymentProvider` adapter** — Phase 6 (mirrors the `ChatProvider` SOLID pattern from Phase 8; introducing it in Phase 5 would steal Phase 6's grading wedge).
- **Payment-timeout job** (orders stuck in STOCK_RESERVED for > N minutes) — Phase 6.
- **`order.q.payment-failed` actual firing path with real-failure compensation matrix tested** — Phase 5 wires the consumer (CD-08); Phase 6 adds the real-failure integration test.
- **notification-service consumer for `order.confirmed` / `payment.failed`** — Phase 7. Saga happy-path integration test that includes notification logging is QUAL-04 → Phase 7.
- **`cart.checked_out` event** — ARCHITECTURE.md §2.7 marked optional; v1 skips. Could revisit in v2 if a downstream consumer ever appears.
- **Real-time SSE push of order status to frontend** — Phase 11 (chat assistant + storefront polish). Phase 5 + Phase 10 ship polling-based UX.
- **Anonymous / guest cart with session merge on login** — Out of scope per chat-assistant's mutating-tools-require-JWT design (Phase 8 AI-14).
- **Bulk `POST /products/by-ids` endpoint on product-service** (would let order-service price-revalidate in one call) — recommendation is to verify whether Phase 4 already shipped it; if not, defer to a v1.5 polish item OR Phase 5 plan adds a 30-LOC endpoint to product-service. Plan-phase decides.
- **`Idempotency-Key` TTL cleanup job** — v1 keeps rows forever (~30 grader orders is nothing); revisit if a real-volume environment ever materializes.
- **Saga visualization endpoint** (`GET /orders/{id}/saga` returning the per-step timeline with timestamps) — out of scope; ARCHITECTURE.md §3.6 mentions the pattern; v1 just exposes saga state via order status. Phase 11 polish candidate.
- **`POST /orders/{id}/cancel` frontend UI** — Phase 10. Backend ships the path in Phase 5 (CD-09).
- **Cart line-count / qty hard limits beyond soft-cap 99** (CD-10) — not a real-world constraint for the demo.

</deferred>

---

*Phase: 05-cart-order-skeleton*
*Context gathered: 2026-04-30 (auto mode — Claude selected the recommended option for every gray area)*
