# Phase 5: Cart & Order Skeleton - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in `05-CONTEXT.md` — this log preserves the alternatives considered and why each was (or was not) selected.

**Date:** 2026-04-30
**Phase:** 05-cart-order-skeleton
**Mode:** `--auto` — Claude auto-selected the recommended option for every gray area; no user prompts issued.
**Areas discussed:** Cart line-item snapshot, Cart line-item identifier, Order-creation flow, Address snapshot, Order idempotency, Payment-service scope, Cart clearing, Order status enum + display mapping, OutboxPoller refactor, 999.2 architecture-test enforcement, Cart schema (one-user-one-cart)

---

## Cart Line-Item Snapshot Strategy

| Option | Description | Selected |
|--------|-------------|----------|
| Snapshot at add-time + re-validate at order creation | Cart-service stores `unit_price_snapshot`, `name_snapshot`, `image_url_snapshot` on `cart_items` row when added; order-service re-fetches `GET /products/by-ids` at `POST /orders` and returns 409 on price drift | ✓ |
| Live-fetch on every cart read | Cart-service calls `GET /products/{id}` for every line item on every `GET /cart` | |
| Hybrid (snapshot but TTL-refresh on read after N seconds) | Mixed | |

**Selection rationale:** Snapshot at add-time gives a stable line-total between page loads (matches user mental model of "I added this for X TL"), produces an audit trail of what the user agreed to pay, and matches the `order.created.items.unitPrice` saga contract. ARCHITECTURE.md §2.7 owned-data already shows `unit_price_snapshot` field, suggesting the architect already favored this. Live-fetch is chatty (every page reload) and breaks the "what did I agree to pay?" UX. Re-validation at order creation closes the price-drift window without paying the live-fetch tax.

**Notes:** Strict equality (±0%) on price-drift for v1 — any price change blocks the order until the user re-confirms with the new prices. A future polish (v2) could allow ±5% silent acceptance.

---

## Cart Line-Item Identifier (`/cart/items/{id}`)

| Option | Description | Selected |
|--------|-------------|----------|
| `{id}` = `productId` (UUID) | Composite PK on `(user_id, product_id)`; `productId` is unique-per-cart by construction; `POST /cart/items` is UPSERT | ✓ |
| `{id}` = synthetic `cart_item_id` UUID | Surrogate key on every line; frontend tracks the surrogate | |

**Selection rationale:** ARCHITECTURE.md §2.7 owned-data shows `cart_items(user_id, product_id, qty, unit_price_snapshot, added_at)` with no surrogate id — the architect already locked composite PK. Surrogate key adds index footprint, requires a frontend lookup (productId → cart_item_id) that's pure overhead, and breaks the natural UPSERT pattern.

**Notes:** UPSERT on `(user_id, product_id)` handles "user adds same product twice" by incrementing qty + refreshing snapshot. `PATCH /cart/items/{productId}` updates qty (replace, not increment); `DELETE /cart/items/{productId}` removes the line.

---

## Order-Creation Flow (sync vs async)

| Option | Description | Selected |
|--------|-------------|----------|
| Pure choreography — 202 Accepted with orderId, frontend polls `GET /orders/{id}` | Order-service writes `orders` + `order_items` + `order_shipping_addresses` + `outbox(order.created)` in one tx, returns 202 immediately. Saga drives status forward async. | ✓ |
| Sync call to inventory for instant reservation result | Order-service synchronously calls inventory-service `POST /reservations` before responding | |
| Sync price re-validation against product-service before outbox write | Order-service synchronously calls product-service for price check (this still happens — see D-01 — but as pre-saga validation, not as the saga step itself) | |

**Selection rationale:** Matches ARCHITECTURE.md §3.2 happy-path canonical flow verbatim. The HTTP layer's job ends at outbox-write + 202; the saga is the source of truth for status. Sync-call alternatives leak choreography internals into HTTP (couples API latency to saga step count, breaks under any saga-step retry, makes the inventory restart visible to the user). The textbook saga flow.

**Notes:** Frontend (Phase 10) will poll. Phase 11 may add SSE push if cheap. Price re-validation against product-service still happens synchronously inside `POST /orders` (D-01), but as a guard BEFORE the outbox row is written — failure returns 409 without enqueueing a saga.

---

## Address Snapshot at Order Creation

| Option | Description | Selected |
|--------|-------------|----------|
| Separate `order_shipping_addresses` table | One row per order, FK to `orders(id)`, fields mirror Phase 3 `addresses` table | ✓ |
| Inline columns on `orders` row | Add 8 address columns directly to orders | |
| JSONB `shipping_address` column | Address fields as JSONB on orders row | |

**Selection rationale:** Phase 3 D-10 explicitly announced this contract: "order-service will COPY the address fields into its own `order_shipping_addresses` row at order-creation time so subsequent `PATCH /addresses/{id}` or deletes don't retro-mutate orders." Phase 5 just executes the contract Phase 3 forward-locked. Separate table normalizes nicely (queryable, indexable, Springdoc-visible), matches Phase 3's promise verbatim. JSONB hides the field set from Springdoc and DB consumers; inline columns bloat the orders row and mix lifecycle concerns.

**Notes:** `order_id` is the PK (1:1 with order). No soft-delete (orders are immutable after creation).

---

## Order Idempotency on `POST /orders`

| Option | Description | Selected |
|--------|-------------|----------|
| Required `Idempotency-Key` HTTP header (Stripe pattern) | Frontend generates UUID per submit; `order_idempotency_keys` table dedupes; repeat key returns existing orderId | ✓ |
| Cart-version check (use cart's `updated_at` as natural idempotency token) | Submit `If-Match` with cart updated_at; mismatch = 409 | |
| Accept double orders (let user manage) | No server-side dedup | |
| One-active-pending-order-per-user heuristic | Server rejects 2nd PENDING order from same user within N seconds | |

**Selection rationale:** Stripe-pattern is the industry standard for idempotent POSTs; small surface area; small implementation cost (~30 LOC + a table). Cart-version-check couples cart freshness with order placement (race-prone if cart is concurrently updated). Accept-doubles is what Plan 04-03's smoke test surfaced as a real problem (rapid re-submit). One-active-pending heuristic has semantic overload (what if the user genuinely wants to place a 2nd order while the first is in flight?).

**Notes:** Missing `Idempotency-Key` header returns 400 RFC-7807. CD-NOTE: TTL cleanup deferred (~30 grader orders is nothing). Frontend generates one UUID per submit attempt; retry uses the same UUID.

---

## Phase 5 Payment-Service Scope

| Option | Description | Selected |
|--------|-------------|----------|
| Minimal payment-service skeleton (~200 LOC, mock delay, no `PaymentProvider` port) | Just enough to consume `stock.reserved` and emit `payment.completed` so Phase 5 SC-2's E2E test runs | ✓ |
| No payment-service in Phase 5 — test-only synthetic event publisher | Phase 5 stops the saga at `stock.reserved`; tests use a one-off helper to publish `payment.completed` | |
| Full payment-service with `PaymentProvider` port + `MockPaymentProvider` adapter | Introduce SOLID port abstraction now | |

**Selection rationale:** SC-2 requires "verified by a Testcontainers + Awaitility integration test" of the saga end-to-end; this is unprovable without ANY payment producer. Test-only publisher leaks test code into production fixtures. Full `PaymentProvider` port pre-empts Phase 6's grading wedge (the abstraction symmetry with `ChatProvider` in Phase 8 is intentional — Phase 6 introduces the port, Phase 8 reinforces the pattern). Minimal skeleton is right-sized: small enough that Phase 6's swap-out is a clean replacement, big enough to demonstrate the saga.

**Notes:** Configurable mock delay (`mock.payment.delay-ms: 100`, can be 0 for tests). No REST surface in Phase 5. Phase 6 adds Iyzico, port abstraction, webhook, timeout job, the gateway route.

---

## Cart Clearing on `order.confirmed`

| Option | Description | Selected |
|--------|-------------|----------|
| Clear ENTIRE cart on `order.confirmed` consumer | DELETE all cart_items WHERE user_id=?; carts row stays | ✓ |
| Clear only items present in the order's snapshot | Saga payload carries item list; consumer deletes only those | |
| No clear (user manually empties) | Cart persists post-checkout | |

**Selection rationale:** v1 has no partial checkout (no "buy 1 of 3 cart items" flow); `POST /orders` snapshots ALL cart items. Clear-only-ordered-items adds saga payload weight without v1 use-case justification. No-clear breaks user expectation (they just bought it).

**Notes:** Edge case: user adds new item to cart between `POST /orders` and `order.confirmed` arrival → that item gets cleared too. Acceptable for v1 (sub-second window on local-host demo). Phase 10 frontend optimistically clears the cart UI on `POST /orders` 202 response and lets the saga re-confirm; consumer is the source of truth.

---

## Order Status Enum + Saga ↔ Display-Label Mapping

| Option | Description | Selected |
|--------|-------------|----------|
| Saga states stay backend-canonical; Turkish display labels are frontend mapping | Backend: PENDING → STOCK_RESERVED → PAID → CONFIRMED + STOCK_FAILED/PAYMENT_FAILED → CANCELLED + USER_CANCELLED. Phase 10 maps to "Sipariş Alındı → Hazırlanıyor → Kargoya Verildi → Teslim Edildi" timeline. | ✓ |
| Bake the 4 Turkish display states into the backend enum | Backend status values are Turkish | |
| Flat status field with separate stage/reason fields | Decompose | |

**Selection rationale:** Keeps backend faithful to saga semantics (any future provider/inspector reads canonical enum); decouples saga lifecycle from UI labels (UI copy can change without DB migration); CLAUDE.md Rule #6 is explicit ("UI Turkish; identifiers/logs English"). The "Kargoya Verildi" + "Teslim Edildi" labels in ROADMAP SC-3 are explicitly placeholders — shipping/fulfillment events are out of scope per PROJECT.md. Baking them into backend would force inventing fake events; flat status with separate stage/reason fields is over-engineered for v1.

**Notes:** `cancel_reason VARCHAR(64)` column on orders captures `OUT_OF_STOCK`, `PAYMENT_DECLINED`, `USER_CANCELLED`, `PAYMENT_TIMEOUT`. Phase 10 owns the display mapping table.

---

## OutboxPoller Refactor — Extract `common-outbox`

| Option | Description | Selected |
|--------|-------------|----------|
| Extract OutboxPoller pattern into `common-outbox` Gradle library; migrate identity + inventory; new services consume directly | New library module ships `OutboxEvent`, `AbstractOutboxPoller`, `OutboxMessagePostProcessor` (sets `MessageProperties.setMessageId` from envelope eventId) | ✓ |
| Keep per-service pollers; copy-paste the message_id setter | Each service maintains its own; no shared module | |

**Selection rationale:** Phase 3 D-13 deferred extraction "until Phase 5 introduces a second use-case." Phase 5 introduces THREE more use-cases (order, payment-skeleton; cart isn't a producer here) — the 4-use-case threshold is decisively past D-13's trigger. Refactor cost is small (~80 LOC moved). Copy-paste the message_id setter is exactly the regression Plan 04-03 surfaced (and that 999.2 explicitly named as the Phase 5 prerequisite).

**Notes:** Identity-service + inventory-service tests run unchanged after migration. Existing per-service Flyway `outbox` table DDL stays per-service (only Java artifacts live in the shared module).

---

## 999.2 Architecture-Test Enforcement

| Option | Description | Selected |
|--------|-------------|----------|
| ArchUnit / scan test in `infra-tests/` that fails build if any `@RabbitListener` class uses `MANUAL` ack without `Channel` parameter | Structural CI gate prevents the bug Plan 04-03 fixed from re-introducing | ✓ |
| Rely on code review + CLAUDE.md Rule #3 reminder text | No automated check | |

**Selection rationale:** 999.2 backlog explicitly named Phase 5 as the host. The regression already happened once silently (Plan 04-03); structural enforcement is the right shape. Phase 5 introduces 3+ new consumers (cart `order.q.order-confirmed`, order's 4 saga consumers, payment-skeleton's `payment.q.stock-reserved`) and is the natural addition point.

**Notes:** ArchUnit is the cleaner option. A plain JUnit test using `Reflections` is a fallback if ArchUnit is overweight. The `infra-tests/` module already has Testcontainers wiring from Plan 01-08.

---

## Cart-Service Schema: One-User-One-Cart

| Option | Description | Selected |
|--------|-------------|----------|
| One cart per user, lazy-create on first POST | `carts(user_id PK, updated_at)`; `GET /cart` lazy-creates; `cart_items` keyed on `(user_id, product_id)` | ✓ |
| Multi-cart per user (carts with names — "Wishlist", "Buy later") | Multiple `carts` rows per user_id | |
| Anonymous cart with `cart_id` UUID; merge-on-login | Guest carts | |

**Selection rationale:** Matches CART-05 ("Cart persists per logged-in user"); LOC has no multi-cart use case; chat assistant's mutating tools require JWT (Phase 8 AI-14) so guest checkout is impossible by design. Multi-cart is feature-creep. UPSERT on `(user_id, product_id)` handles add-same-product-twice naturally.

**Notes:** `user_id` from gateway-injected `X-User-Id` header — no JWT decoding in cart-service (Phase 3 D-15 pattern, applied uniformly).

---

## Claude's Discretion (planner picks the concrete value)

- **CD-01:** Concrete DDL field types for `orders` table (UUID PK, status check constraint, NUMERIC(12,2) total, etc.)
- **CD-02:** Concrete DDL for `order_items` (composite PK on order_id+product_id; NUMERIC(10,2) unit_price)
- **CD-03:** Concrete DDL for `cart_items` (price precision must match product-service's `price_gross`)
- **CD-04:** AMQP listener-factory bean reference (each new service's `RabbitConfig` autowires `RabbitRetryConfig.rabbitListenerContainerFactory`)
- **CD-05:** `OutboxEvent` JPA entity field set in `common-outbox` (mirror inventory-service's existing entity exactly)
- **CD-06:** Test pattern for QUAL-03 — per-service slice tests + ONE shared E2E in `infra-tests/`
- **CD-07:** Wave breakdown — 4 plans/4 waves likely
- **CD-08:** Wire `inventory.q.payment-failed` consumer in Phase 5 (recommendation: YES)
- **CD-09:** `POST /orders/{id}/cancel` in scope for Phase 5 backend (recommendation: YES; frontend is Phase 10)
- **CD-10:** Cart line-item soft-cap qty 99, no line-count cap

## Deferred Ideas

- Iyzico Checkout Form integration + 3DS callback + signature verification — Phase 6
- `PaymentProvider` port + `IyzicoPaymentProvider` adapter — Phase 6
- Payment-timeout job — Phase 6
- `order.q.payment-failed` real-failure compensation integration test — Phase 6
- notification-service consumer for `order.confirmed` / `payment.failed` — Phase 7
- Saga happy-path integration test that includes notification logging (QUAL-04) — Phase 7
- `cart.checked_out` event — out of v1 scope; revisit in v2 if a consumer emerges
- Real-time SSE push of order status to frontend — Phase 11
- Anonymous / guest cart with session merge on login — out of scope per Phase 8 AI-14
- Bulk `POST /products/by-ids` endpoint on product-service — plan-phase decides whether to add 30 LOC to product-service or loop individual `GET /products/{id}` calls
- `Idempotency-Key` TTL cleanup job — v1 keeps rows forever
- Saga visualization endpoint (`GET /orders/{id}/saga`) — Phase 11 polish candidate
- `POST /orders/{id}/cancel` frontend UI — Phase 10
- Cart line-count / qty hard limits beyond soft-cap 99 — not a real-world constraint for the demo
