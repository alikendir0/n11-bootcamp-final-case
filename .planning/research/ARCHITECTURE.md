# Architecture Research

**Domain:** Spring Boot microservices e-commerce with choreography SAGA + agentic-commerce layer
**Researched:** 2026-04-28
**Confidence:** HIGH (cross-verified against Spring Cloud, microservices.io, MCP Java SDK, Spring AI)

---

## 1. System Overview

### Topology (13 services + frontend + external integrations)

```
                                     ┌──────────────────────────┐
                                     │  React Storefront (TR)   │
                                     │  • Catalog/PDP/Cart      │
                                     │  • Chat bubble (SSE)     │
                                     └───────────┬──────────────┘
                                                 │ HTTPS + SSE
                                                 ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        api-gateway (Spring Cloud Gateway)              │
│   • JWT validation (only here) • CORS • rate limit • SSE passthrough   │
│   • Eureka client • injects X-User-Id / X-User-Roles headers           │
└────────────┬─────────────────────────────┬─────────────────────────────┘
             │ REST (Eureka discovery)     │
             ▼                             ▼
┌────────────────────────────┐  ┌─────────────────────────────────────────┐
│ Edge / customer-facing     │  │ Agent edge (also via gateway)           │
│ identity, product, cart,   │  │ ai-service  (chat orchestration)        │
│ order, search, ai-service  │  │ mcp-server  (MCP HTTP+SSE for ext.)     │
└────────────┬───────────────┘  └─────────────────────────────────────────┘
             │ REST (intra-service, sync)
             ▼
┌────────────────────────────────────────────────────────────────────────┐
│ Internal services                                                       │
│ inventory, payment, notification        (eureka-server, config-server   │
│                                          internal-only, NOT routed)     │
└──────────────┬─────────────────────────────────────────────────────────┘
               │
               ▼  publish/consume (events, async)
┌────────────────────────────────────────────────────────────────────────┐
│                     RabbitMQ — choreography bus                        │
│   exchanges: orders.tx (topic), payments.tx, inventory.tx, notify.tx   │
│   per-consumer queues + DLX (.dlx) per queue                           │
└────────────────────────────────────────────────────────────────────────┘
               │
               ▼
┌────────────────────────────────────────────────────────────────────────┐
│  PostgreSQL 16 (single host, schema-per-service) + pgvector            │
│  identity_db • product_db • inventory_db • cart_db • order_db          │
│  payment_db • notification_db • search_db (pgvector) • ai_db (chat)    │
└────────────────────────────────────────────────────────────────────────┘

                           External: Iyzico sandbox, Gemini API
```

### Confidence Notes

- Spring Cloud Gateway as JWT enforcement edge with header injection — HIGH (Spring official docs, [securing-services-with-spring-cloud-gateway](https://spring.io/blog/2019/08/16/securing-services-with-spring-cloud-gateway/)).
- Choreography SAGA via RabbitMQ exchanges/queues — HIGH ([microservices.io saga pattern](https://microservices.io/patterns/data/saga.html), Chris Richardson).
- MCP server pattern (tool dispatch, HTTP+SSE/STDIO, Spring AI auto-registration) — HIGH ([Spring AI MCP reference](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-overview.html), [MCP Java SDK](https://github.com/modelcontextprotocol/java-sdk)).

---

## 2. Per-Service Contracts (the spine)

Format: each service has bounded context, owned data, REST surface (level: list, not signatures), events published, events consumed, sync deps, gateway exposure.

### 2.1 eureka-server

| Field | Value |
|-------|-------|
| Bounded context | Service registry + discovery only. No business logic. |
| Owned data | None (in-memory registry). |
| REST API | Eureka dashboard (`/`); Eureka REST API for clients. |
| Events pub/sub | None. |
| Sync deps | None. |
| Behind gateway? | NO — internal only. Bootstrap dep for everything else. |

### 2.2 config-server

| Field | Value |
|-------|-------|
| Bounded context | Centralized configuration (application properties per env, per service). |
| Owned data | Git-backed config repo (or `native` filesystem in dev). |
| REST API | `/{app}/{profile}` (Spring Cloud Config standard). |
| Events pub/sub | None. (Optionally `RefreshScope` via Spring Cloud Bus — out of scope.) |
| Sync deps | None. Eureka client (registers self for discovery). |
| Behind gateway? | NO — internal only. Bootstrap before all business services. |

### 2.3 api-gateway

| Field | Value |
|-------|-------|
| Bounded context | Edge: routing, JWT validation, header injection, CORS, rate limit, SSE passthrough. |
| Owned data | None. |
| REST API | Routes (no own endpoints): `/api/v1/auth/**` → identity, `/api/v1/products/**` → product, `/api/v1/cart/**` → cart, `/api/v1/orders/**` → order, `/api/v1/payments/**` → payment, `/api/v1/search/**` → search, `/api/v1/chat/**` (SSE) → ai, `/mcp/**` → mcp-server. |
| Events pub/sub | None. |
| Sync deps | Eureka (discovery), config-server (config), identity public key (for JWT verify). |
| Behind gateway? | IS the gateway. Public-facing (LB → 8080). |

**Gateway responsibilities (concrete):**

1. **JWT validation** — `ReactiveJwtDecoder` configured against identity-service public key (RSA) or shared secret. Reject expired/invalid tokens with 401. Public routes whitelisted: `POST /auth/login`, `POST /auth/register`, `GET /products/**`, `GET /search/**`, `POST /chat/**` (anonymous chat allowed; tools that mutate require auth).
2. **Header injection** — Custom `GlobalFilter` reads JWT claims, mutates `ServerWebExchange` to set `X-User-Id`, `X-User-Roles`, `X-Correlation-Id` (generated if absent). Verified pattern per [Spring Cloud Gateway workshop](https://andifalk.gitbook.io/spring-cloud-gateway-workshop/hands-on-labs/lab3) and [Securing Services with Spring Cloud Gateway](https://spring.io/blog/2019/08/16/securing-services-with-spring-cloud-gateway/).
3. **Strip Authorization header** before forwarding (defensive — downstream must NOT see the JWT, only trusted headers).
4. **CORS** — allow React dev origin (`http://localhost:5173`) + production frontend origin.
5. **Rate limiting** — `RequestRateLimiter` filter with Redis-backed `RedisRateLimiter` (or simpler in-memory if Redis is out of scope; decide in Phase: Gateway).
6. **SSE passthrough** — `/api/v1/chat/stream` must not buffer. Gateway routes need `spring.cloud.gateway.routes[].metadata.response-timeout` and the route filter must avoid `ModifyResponseBody`.
7. **OpenAPI aggregation** — Springdoc gateway aggregator pulls each service's `/v3/api-docs` and presents a single Swagger UI at `/swagger-ui.html`.

### 2.4 identity-service

| Field | Value |
|-------|-------|
| Bounded context | Users, credentials, roles, JWT issuance. NOT profile/preferences. |
| Owned data | `users(id, email, password_hash, full_name, created_at)`, `roles(id, name)`, `user_roles(user_id, role_id)`. |
| REST API | `POST /auth/register`, `POST /auth/login` (returns JWT), `GET /auth/me` (current user from header), `GET /.well-known/jwks.json` (public key for gateway). |
| Events published | `user.registered` (for notification welcome — optional). |
| Events consumed | None. |
| Sync deps | None (leaf on auth). |
| Behind gateway? | YES (login/register public; `/me` authed). |

### 2.5 product-service

| Field | Value |
|-------|-------|
| Bounded context | Catalog: products, categories, prices, descriptions, images. NOT stock counts (inventory owns those), NOT search index (search owns it). |
| Owned data | `products(id, sku, name_tr, description_tr, price, category_id, image_url, created_at)`, `categories(id, slug, name_tr, parent_id)`. |
| REST API | `GET /products?page&size&category&q`, `GET /products/{id}`, `GET /products/by-ids?ids=` (batch — used by cart/order to enrich), `GET /categories`, admin-ish `POST/PUT/DELETE /products` (gated by ROLE_ADMIN). |
| Events published | `product.created`, `product.updated`, `product.deleted` (search-service indexes these → embeddings). |
| Events consumed | None. |
| Sync deps | None. |
| Behind gateway? | YES — catalog is public read; admin write requires ROLE_ADMIN. |

### 2.6 inventory-service

| Field | Value |
|-------|-------|
| Bounded context | Stock per SKU + reservations. The saga participant for "reserve stock". NOT catalog data. |
| Owned data | `stock(product_id PK, available_qty, reserved_qty, version)`, `reservations(reservation_id, order_id, items_json, status, created_at, expires_at)`. |
| REST API | `GET /inventory/{productId}` (read available_qty for product list/PDP; called sync by product-service or directly by frontend via gateway), admin `POST /inventory/{productId}/restock`. |
| Events published | `stock.reserved` (saga happy), `stock.reserve_failed` (saga compensation trigger), `stock.released` (after compensation). |
| Events consumed | `order.created` → try reserve, `payment.failed` → release reservation, `order.cancelled` → release reservation. |
| Sync deps | None at runtime (event-driven for saga). |
| Behind gateway? | YES (read-only stock query). Reservations are NEVER created via REST — only via saga events. |

### 2.7 cart-service

| Field | Value |
|-------|-------|
| Bounded context | Per-user cart state. Survives session. NOT order placement (order-service owns that). |
| Owned data | `carts(user_id PK, updated_at)`, `cart_items(user_id, product_id, qty, unit_price_snapshot, added_at)`. |
| REST API | `GET /cart` (current user from `X-User-Id`), `POST /cart/items {productId, qty}`, `PUT /cart/items/{productId} {qty}`, `DELETE /cart/items/{productId}`, `DELETE /cart` (clear). |
| Events published | `cart.checked_out` (optional — fired when order-service confirms it converted the cart). |
| Events consumed | `order.confirmed` → clear cart for that user. |
| Sync deps | product-service (`GET /products/by-ids`) to enrich cart on read with current name/price. |
| Behind gateway? | YES (all endpoints require auth). |

### 2.8 order-service

| Field | Value |
|-------|-------|
| Bounded context | Order aggregate + saga initiator + saga state machine. The choreography "owner" of the order lifecycle. |
| Owned data | `orders(id, user_id, status, total, currency, created_at, updated_at, correlation_id)`, `order_items(order_id, product_id, name_snapshot, qty, unit_price)`, `outbox(id, aggregate_id, event_type, payload_json, created_at, sent_at)` (transactional outbox). |
| Status enum | `PENDING` → `STOCK_RESERVED` → `PAID` → `CONFIRMED`; failure paths: `STOCK_FAILED` / `PAYMENT_FAILED` → `CANCELLED`. |
| REST API | `POST /orders` (creates from cart snapshot — user must be authed), `GET /orders` (current user), `GET /orders/{id}`, `POST /orders/{id}/cancel` (only while PENDING/STOCK_RESERVED — emits compensating events). |
| Events published | `order.created`, `order.confirmed`, `order.cancelled`. |
| Events consumed | `stock.reserved` → mark STOCK_RESERVED + trigger payment, `stock.reserve_failed` → mark STOCK_FAILED + emit `order.cancelled`, `payment.completed` → mark PAID then CONFIRMED + emit `order.confirmed`, `payment.failed` → mark PAYMENT_FAILED + emit `order.cancelled`. |
| Sync deps | cart-service (`GET /cart`) at order creation to snapshot items; product-service (`GET /products/by-ids`) for price snapshot validation. |
| Behind gateway? | YES (all endpoints require auth). |

### 2.9 payment-service

| Field | Value |
|-------|-------|
| Bounded context | Iyzico integration + payment record. Saga participant for "take payment". |
| Owned data | `payments(id, order_id, amount, currency, iyzico_payment_id, iyzico_status, status, created_at)`, `payment_attempts(...)` for retries. |
| REST API | `POST /payments/checkout` (returns Iyzico checkout form URL/token — frontend redirects user there), `POST /payments/iyzico/callback` (Iyzico webhook → emits `payment.completed`/`payment.failed`), `GET /payments/{orderId}` (status). |
| Events published | `payment.completed`, `payment.failed`. |
| Events consumed | `stock.reserved` → initiate payment intent (or mark order ready for checkout, depending on UX choice — see flow below). |
| Sync deps | Iyzico Java SDK (external). |
| Behind gateway? | Customer endpoints YES. Iyzico webhook endpoint MUST be reachable but should be allowlisted by Iyzico signature, not by JWT. Either expose via gateway with public route or via separate ingress (decide in Phase: Payment). |

**Payment UX nuance:** Iyzico's checkout-form flow is browser-redirect-based. Two options:
- **A (cleaner saga):** Frontend calls `POST /payments/checkout` after order is in `STOCK_RESERVED`; user pays on Iyzico-hosted page; webhook drives the saga forward.
- **B (hidden):** Server initiates a non-3DS card charge from saved card. Out of scope (no card storage).

We use **option A**. The saga's "take payment" step thus has a human-in-the-loop tail; the saga is "in progress" until the webhook lands or a timeout fires.

### 2.10 notification-service

| Field | Value |
|-------|-------|
| Bounded context | Outbound notifications (email mock, in-app push optional). Pure leaf consumer. |
| Owned data | `notifications(id, user_id, channel, type, payload_json, status, sent_at)` (audit log). |
| REST API | `GET /notifications` (current user — optional, for in-app inbox). |
| Events published | None. |
| Events consumed | `order.confirmed`, `order.cancelled`, `payment.failed`, `user.registered`. |
| Sync deps | None. |
| Behind gateway? | Optional inbox endpoint YES, otherwise internal. |

### 2.11 search-service

| Field | Value |
|-------|-------|
| Bounded context | Semantic search over products via pgvector. NOT catalog data master (product-service is). Holds derived index. |
| Owned data | `product_embeddings(product_id PK, embedding vector(768), name_tr, indexed_at)` — pgvector table. |
| REST API | `GET /search?q=...&limit=10` (semantic), `POST /search/embed` (internal — accept text, return vector; used by ai-service for query-side embeddings). |
| Events published | `search.indexed` (audit, optional). |
| Events consumed | `product.created` / `product.updated` → embed name+description via ai-service `EmbeddingProvider`, upsert. `product.deleted` → remove. |
| Sync deps | ai-service (`POST /ai/embed`) for query-side embedding when user searches. (Alternative: pull Gemini embedding adapter into search-service directly. We keep it in ai-service for SOLID — single port, single adapter.) |
| Behind gateway? | YES (public read). |

### 2.12 ai-service

| Field | Value |
|-------|-------|
| Bounded context | LLM orchestration: chat (multi-turn, tool-calling), embeddings. Owns `ChatProvider` + `EmbeddingProvider` ports + Gemini adapters. Owns the **shared agent toolset** (definition + dispatch). |
| Owned data | `conversations(id, user_id, started_at, last_message_at)`, `messages(id, conversation_id, role, content, tool_calls_json, created_at)`. |
| REST API | `POST /chat` (body: conversationId?, message; returns assistant reply or stream), `GET /chat/stream` (SSE for streaming chat), `POST /ai/embed` (text → vector — internal). |
| Events published | None (synchronous request/reply with frontend). |
| Events consumed | None directly. (Optionally `order.confirmed` to push status into an open chat — out of scope for v1.) |
| Sync deps | product-service, cart-service, order-service, search-service, payment-service (tool dispatch calls these per user request); Gemini API (external). |
| Behind gateway? | YES (`/api/v1/chat/**` route, SSE-aware). |

### 2.13 mcp-server

| Field | Value |
|-------|-------|
| Bounded context | MCP protocol surface for external AI agents (Claude Desktop, etc.). Re-exposes the same toolset as ai-service via MCP HTTP+SSE (and optionally STDIO for local-pipe use). |
| Owned data | None (stateless; per-session conversation lives in the calling agent). |
| REST API | MCP endpoints: `GET/POST /mcp/sse` (Server-Sent Events transport per [Spring AI MCP reference](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-overview.html)) or HTTP streaming. Tool list discovery + tool invocation. |
| Events pub/sub | None. |
| Sync deps | Imports `agent-toolset` library (shared with ai-service) → tools call backing services via REST. Also requires identity-service (or accepts an API key for external agents — decide in Phase: MCP). |
| Behind gateway? | YES if hosted alongside (route `/mcp/**`). May alternatively be exposed via Cloudflare tunnel — open question in PROJECT.md. |

---

## 3. The Order SAGA (Choreography) — Concrete Event Flow

Pattern source: [microservices.io saga pattern](https://microservices.io/patterns/data/saga.html), [microservices.io transactional outbox](https://microservices.io/patterns/data/transactional-outbox.html). Choreography means each participant reacts to events; no central orchestrator.

### 3.1 Exchange / Queue Topology (RabbitMQ)

| Exchange | Type | Purpose |
|----------|------|---------|
| `orders.tx` | topic | Order lifecycle events |
| `inventory.tx` | topic | Stock reservation events |
| `payments.tx` | topic | Payment lifecycle events |
| `notifications.tx` | topic | Optional fan-out to notification-service |

**Per-consumer queues** (one queue per service per event-type-of-interest, bound with routing keys):

| Queue | Bound to | Routing key | Consumer |
|-------|----------|-------------|----------|
| `inventory.q.order-created` | `orders.tx` | `order.created` | inventory-service |
| `payment.q.stock-reserved` | `inventory.tx` | `stock.reserved` | payment-service |
| `order.q.stock-reserved` | `inventory.tx` | `stock.reserved` | order-service |
| `order.q.stock-failed` | `inventory.tx` | `stock.reserve_failed` | order-service |
| `order.q.payment-completed` | `payments.tx` | `payment.completed` | order-service |
| `order.q.payment-failed` | `payments.tx` | `payment.failed` | order-service |
| `inventory.q.payment-failed` | `payments.tx` | `payment.failed` | inventory-service (compensation) |
| `inventory.q.order-cancelled` | `orders.tx` | `order.cancelled` | inventory-service (compensation) |
| `cart.q.order-confirmed` | `orders.tx` | `order.confirmed` | cart-service |
| `notify.q.order-confirmed` | `orders.tx` | `order.confirmed` | notification-service |
| `notify.q.payment-failed` | `payments.tx` | `payment.failed` | notification-service |
| `search.q.product-events` | `products.tx` | `product.*` | search-service |

**DLX / DLQ:** every queue has `x-dead-letter-exchange = <exchange>.dlx` and `x-dead-letter-routing-key = <queue>.dlq`. Failed messages (after N retries via Spring Retry) land on DLQ for manual replay.

### 3.2 Happy Path

```
[Frontend] POST /orders (auth)
    │
    ▼
[order-service]
  TX BEGIN:
    INSERT orders(status=PENDING, correlation_id=UUID)
    INSERT order_items(...)
    INSERT outbox(event=order.created, payload={orderId, userId, items, total, correlationId})
  TX COMMIT
    │
    │ (outbox poller / @TransactionalEventListener publishes to RabbitMQ)
    ▼
RabbitMQ: orders.tx / order.created
    │
    ▼
[inventory-service] consumes order.created
  Idempotency: SELECT * FROM processed_events WHERE event_id=? (skip if exists)
  TX BEGIN:
    For each item: UPDATE stock SET reserved_qty=reserved_qty+qty WHERE product_id=? AND available_qty - reserved_qty >= qty
    If all rows updated:
      INSERT reservations(reservation_id, order_id, items, status=RESERVED)
      INSERT outbox(event=stock.reserved, payload={orderId, reservationId, correlationId})
    Else:
      INSERT outbox(event=stock.reserve_failed, payload={orderId, failedItems, correlationId})
  TX COMMIT
    │
    ▼
RabbitMQ: inventory.tx / stock.reserved
    │
    ├─→ [order-service] sets status=STOCK_RESERVED
    │
    └─→ [payment-service] consumes stock.reserved
            • idempotency check
            • creates Iyzico checkout token, persists payment(status=PENDING)
            • returns token to frontend (out-of-band: payment-service exposes
              GET /payments/{orderId}/checkout-url which frontend polls,
              OR ai-service / cart "place order" UI is informed via WS/SSE.
              For v1 simplest: frontend polls GET /orders/{id} and when
              status=STOCK_RESERVED calls POST /payments/checkout)
            ↓
        User redirected to Iyzico hosted form → pays
            ↓
        Iyzico → POST /payments/iyzico/callback (webhook, signed)
            ↓
        TX BEGIN:
          UPDATE payments SET status=COMPLETED, iyzico_payment_id=...
          INSERT outbox(event=payment.completed, payload={orderId, paymentId, amount, correlationId})
        TX COMMIT
            │
            ▼
RabbitMQ: payments.tx / payment.completed
    │
    ├─→ [order-service] consumes
    │     TX: UPDATE orders SET status=PAID; then status=CONFIRMED;
    │         INSERT outbox(event=order.confirmed)
    │
    ├─→ [notification-service] consumes order.confirmed → mock email
    │
    └─→ [cart-service] consumes order.confirmed → DELETE cart_items WHERE user_id=?
```

### 3.3 Compensations (failure paths)

**Path A — stock reservation fails:**

```
inventory.tx / stock.reserve_failed
    │
    └─→ [order-service] consumes
          UPDATE orders SET status=STOCK_FAILED → CANCELLED
          INSERT outbox(event=order.cancelled, reason=OUT_OF_STOCK)
                ▼
        orders.tx / order.cancelled → notification-service notifies user
```

(No stock to release; reservation never succeeded.)

**Path B — payment fails (declined / timeout):**

```
payments.tx / payment.failed
    │
    ├─→ [order-service] consumes
    │     UPDATE orders SET status=PAYMENT_FAILED → CANCELLED
    │     INSERT outbox(event=order.cancelled, reason=PAYMENT_DECLINED)
    │
    └─→ [inventory-service] consumes
          TX: UPDATE stock SET reserved_qty=reserved_qty-qty (per reservation)
              UPDATE reservations SET status=RELEASED
              INSERT outbox(event=stock.released, payload={orderId, reservationId})
                ▼
        Notification → user "Ödemeniz alınamadı."
```

**Path C — user cancels order while STOCK_RESERVED:**

```
POST /orders/{id}/cancel
    │
    ▼
[order-service]
  Allowed only if status ∈ {PENDING, STOCK_RESERVED}
  UPDATE orders SET status=CANCELLED
  INSERT outbox(event=order.cancelled, reason=USER_CANCELLED)
    │
    └─→ inventory-service releases reservation (same as Path B)
```

**Path D — payment timeout:**

A scheduled job in payment-service marks payments as TIMED_OUT after N minutes without a webhook → emits `payment.failed` → Path B.

### 3.4 Event Payload Schemas

All events share an envelope; business fields are inline.

**Envelope (every event):**

```json
{
  "eventId": "uuid",            // unique — for idempotency
  "eventType": "order.created",
  "eventVersion": 1,
  "occurredAt": "2026-04-28T10:15:30Z",
  "correlationId": "uuid",      // shared across the saga; equals first orderId
  "causationId": "uuid",        // eventId of the event that caused this one (or null)
  "producer": "order-service",
  "payload": { ... }            // type-specific
}
```

**`order.created` payload:**

```json
{
  "orderId": "uuid",
  "userId": "uuid",
  "currency": "TRY",
  "totalAmount": 1299.50,
  "items": [
    { "productId": "uuid", "qty": 2, "unitPrice": 549.50, "nameSnapshot": "..." }
  ]
}
```

**`stock.reserved` payload:** `{ orderId, reservationId, reservedItems: [{productId, qty}] }`

**`stock.reserve_failed` payload:** `{ orderId, failedItems: [{productId, requestedQty, availableQty}], reason }`

**`payment.completed` payload:** `{ orderId, paymentId, iyzicoPaymentId, amount, currency }`

**`payment.failed` payload:** `{ orderId, paymentId, reason, errorCode }`

**`order.confirmed` payload:** `{ orderId, userId, totalAmount, items }`

**`order.cancelled` payload:** `{ orderId, userId, reason }` where reason ∈ `{OUT_OF_STOCK, PAYMENT_DECLINED, USER_CANCELLED, PAYMENT_TIMEOUT}`

**`stock.released` payload:** `{ orderId, reservationId, releasedItems: [{productId, qty}] }`

### 3.5 Idempotency, Retries, DLQ

All verified against [microservices.io idempotent consumer](https://microservices.io/patterns/communication-style/idempotent-consumer.html) and [Transactional Outbox with RabbitMQ Part 2](https://dev.to/sagarmaheshwary/transactional-outbox-with-rabbitmq-part-2-handling-retries-dead-letter-queues-and-observability-4h19).

- **Producer idempotency: transactional outbox.** Every event is INSERTed into `outbox` in the same DB transaction as the business state change. A poller (Spring Scheduled or Debezium-style) publishes outbox rows to RabbitMQ and marks `sent_at`. Guarantees at-least-once delivery without dual-write inconsistency.
- **Consumer idempotency: inbox / processed_events table.** Each consumer maintains `processed_events(event_id PK, consumer, processed_at)`. On consume: SELECT-then-skip if exists. Insert in same TX as side effect. Guarantees exactly-once effect under at-least-once delivery.
- **Retries:** Spring AMQP `RetryTemplate` with exponential backoff (3 attempts: 1s, 5s, 30s). After exhaustion → DLQ.
- **DLQ:** per-queue dead-letter exchange `<exchange>.dlx`. DLQ messages keep envelope + original headers + `x-death` reason. Manual replay tool (admin endpoint or script) for Phase: Hardening.
- **Correlation IDs:** every event carries `correlationId` from `order.created`. Logback MDC pattern: `%X{correlationId}` in log line — same UUID flows through HTTP headers (`X-Correlation-Id`) and AMQP headers (`correlation_id`). Allows tracing a saga across all 13 services in flat logs.

---

## 4. Synchronous Service Dependency Graph (REST)

```
                  ┌─────────────┐
   ┌─────────────▶│   gateway   │◀─────────────┐
   │              └──────┬──────┘              │
   │                     │                     │
   │   ┌─────────┬───────┼────────┬──────┐     │
   │   ▼         ▼       ▼        ▼      ▼     ▼
   │ ident   product   cart    order   pay   ai/mcp
   │   ▲       ▲ ▲      │       │ │     │ │
   │   │       │ │      │       │ │     │ ├──▶ search ──▶ ai (embed)
   │   │       │ └──────┘       │ │     │ ├──▶ cart
   │   │       │ (cart enriches)│ │     │ ├──▶ order
   │   │       └────────────────┘ │     │ └──▶ product
   │   │       (order snapshots prices)│
   │   │                              ▼
   │   │                          Iyzico
   │   └─── all services pull JWKS at startup
   │
   └── eureka, config-server are bootstrap (every service registers)
```

**Sync REST calls (read-only, per-request, server-to-server):**

| Caller | Callee | Purpose |
|--------|--------|---------|
| cart-service | product-service `/products/by-ids` | Enrich cart on read |
| order-service | cart-service `/cart` | Snapshot at order create |
| order-service | product-service `/products/by-ids` | Validate price snapshot |
| search-service | ai-service `/ai/embed` | Embed query at search-time |
| ai-service | product/cart/order/search/payment | Tool dispatch |
| mcp-server | (same as ai-service via shared lib) | Tool dispatch |
| api-gateway | identity `/.well-known/jwks.json` | JWT public key |

All sync calls discovered via Eureka (use `LoadBalanced WebClient` / Spring Cloud LoadBalancer). Failures: circuit breaker (Resilience4j) with fallback responses (cached or "service unavailable").

---

## 5. Gateway Responsibilities (Detailed)

| Concern | Implementation |
|---------|----------------|
| Discovery | `spring.cloud.gateway.discovery.locator.enabled=true` + explicit route definitions per service |
| JWT validation | `spring-boot-starter-oauth2-resource-server` configured against identity JWKS endpoint |
| Header injection | Custom `GlobalFilter` that runs after authentication: extract `sub`, `roles` claims → set `X-User-Id`, `X-User-Roles` request headers; strip `Authorization` |
| Correlation ID | Generate `X-Correlation-Id` if missing; propagate; include in MDC |
| CORS | `CorsWebFilter` bean with allowed origins from config-server |
| Rate limit | `RequestRateLimiter` with `RedisRateLimiter` (1 req/s per `X-User-Id`, burst 10). If Redis is out of scope, use in-memory bucket — note this fails on multi-instance gateway. |
| SSE | Route definition with `metadata.response-timeout: 0` and no body-modifying filters; content-type `text/event-stream` is preserved |
| Springdoc aggregation | `springdoc-openapi-starter-webflux-ui` + `springdoc.swagger-ui.urls` listing each service's `/v3/api-docs` |

References: [Spring Cloud Gateway: Securing Services](https://spring.io/blog/2019/08/16/securing-services-with-spring-cloud-gateway/), [Spring Cloud Gateway Workshop Lab 3](https://andifalk.gitbook.io/spring-cloud-gateway-workshop/hands-on-labs/lab3).

---

## 6. AI / MCP Architecture (the agent layer)

### 6.1 Layering inside ai-service (hexagonal — direct SOLID demo)

```
ai-service/
├── domain/                        # pure business
│   ├── chat/
│   │   ├── Conversation.java
│   │   ├── Message.java
│   │   └── ChatService.java       # orchestrates: prompt → LLM → tool dispatch → loop
│   └── tools/
│       ├── AgentTool.java         # ★ interface (the shared port)
│       ├── ToolRegistry.java
│       └── ToolDispatcher.java
├── application/                   # use cases
│   └── ChatUseCase.java
├── infrastructure/                # adapters
│   ├── llm/
│   │   ├── ChatProvider.java      # ★ port
│   │   ├── EmbeddingProvider.java # ★ port
│   │   └── gemini/
│   │       ├── GeminiChatAdapter.java
│   │       └── GeminiEmbeddingAdapter.java
│   ├── http/                      # REST clients to backing services
│   │   ├── ProductClient.java
│   │   ├── CartClient.java
│   │   ├── OrderClient.java
│   │   └── SearchClient.java
│   └── persistence/
│       └── ConversationRepository.java
└── interfaces/                    # inbound adapters
    ├── rest/
    │   └── ChatController.java
    └── sse/
        └── ChatStreamController.java
```

### 6.2 The Shared Agent Toolset (defined once, consumed twice)

**Key design decision:** the toolset is a separate Maven/Gradle module (`agent-toolset`) imported by both `ai-service` (chat) and `mcp-server`. This is the DRY enforcement for "one agent backend, two surfaces."

**Tool interface (Java port):**

```java
public interface AgentTool {
    String name();                  // "search_products", "add_to_cart", ...
    String description();           // human-readable, also used by LLM
    JsonSchema parameterSchema();   // JSON Schema for inputs
    ToolResult execute(ToolContext ctx, JsonNode args);
}

public record ToolContext(String userId, String correlationId, String authToken) {}
public sealed interface ToolResult permits ToolResult.Ok, ToolResult.Err {
    record Ok(JsonNode data) implements ToolResult {}
    record Err(String code, String message) implements ToolResult {}
}
```

**Concrete toolset for v1:**

| Tool name | Description (TR-friendly) | Backing service | Auth required |
|-----------|---------------------------|-----------------|---------------|
| `search_products` | Search the catalog by natural-language query | search-service `/search` | No |
| `get_product` | Get full details for one product | product-service `/products/{id}` | No |
| `list_categories` | List all top-level categories | product-service `/categories` | No |
| `view_cart` | Read the current user's cart | cart-service `/cart` | Yes |
| `add_to_cart` | Add a product to the cart with a quantity | cart-service `POST /cart/items` | Yes |
| `update_cart_item` | Change qty of an item in the cart | cart-service `PUT /cart/items/{id}` | Yes |
| `remove_from_cart` | Remove an item from the cart | cart-service `DELETE /cart/items/{id}` | Yes |
| `place_order` | Create an order from the cart, return checkout URL | order-service `POST /orders` then payment-service `POST /payments/checkout` | Yes |
| `get_order_status` | Status of an order | order-service `GET /orders/{id}` | Yes |
| `list_my_orders` | Recent orders for the user | order-service `GET /orders` | Yes |

Each tool: implements `AgentTool`, registered via Spring `@Component`, auto-discovered by `ToolRegistry`. The Spring AI `@Tool` annotation can additionally drive auto-registration in mcp-server ([Spring AI MCP Tool Calling](https://docs.spring.io/spring-ai/reference/api/tools.html)).

**Tool dispatch from chat:**

```
User msg → GeminiChatAdapter.chat(messages, tools)
    ↓
Gemini returns either text OR function_call(name, args)
    ↓
If function_call:
    ToolDispatcher.dispatch(name, args, toolContext)
        ↓
    AgentTool.execute() — calls backing service via WebClient
        (with X-User-Id from toolContext + bearer/internal token)
        ↓
    ToolResult appended to message history as `tool` role
    ↓
Loop: send updated history back to Gemini until it returns text
```

**Tool dispatch from MCP:** identical, but the calling agent is external (Claude Desktop). Spring AI's MCP server starter exposes `@Tool` methods over MCP HTTP+SSE; the same `AgentTool` instances are wrapped as MCP `Tool` with the auto-generated JSON schema ([Spring AI MCP overview](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-overview.html)).

**Auth in MCP context:** MCP doesn't have a JWT. Options: (a) per-agent API key checked at mcp-server, mapped to a real user; (b) MCP runs in user's local context (Claude Desktop) and we accept config-supplied JWT. Decide in Phase: MCP. Until decided, mutating tools (cart/order) require an API-key→user mapping stored in identity-service.

---

## 7. Build Order (with parallelization)

Justified by the dependency graph above. **Bold = blocking**. Parallel groups can be worked simultaneously by AI agents.

```
Phase 0 — FOUNDATION (must come first, BLOCKING for everything)
  ┌── eureka-server
  ├── config-server
  └── api-gateway (skeleton; routes added as services come online)

Phase 1 — SECURITY ROOT (BLOCKING for cart/order/payment)
  └── identity-service        (issues JWT; gateway needs JWKS)

Phase 2 — CORE CATALOG (parallel group; depend only on Phase 0)
  ├── product-service         ┐
  ├── inventory-service       ├─ parallelizable
  └── cart-service            ┘

Phase 3 — ORDER + PAYMENT (depend on Phase 1 + Phase 2)
  ├── order-service           ┐ payment-service consumes order-service events
  └── payment-service         ┘ (and Iyzico SDK); build order-service first,
                                then payment-service against frozen contract

Phase 4 — LEAVES (depend on all above)
  ├── notification-service    ┐
  └── search-service          ├─ parallelizable
  └── ai-service              ┘ (search depends on ai-service for embeddings)

Phase 5 — AGENT EDGE
  └── mcp-server              (depends on agent-toolset shared lib from ai-service)

Phase 6 — FRONTEND
  └── React storefront        (needs gateway up; Phase 0 is hard prereq)

Phase 7 — DEVOPS / DEPLOY
  └── Jib + GitHub Actions + AWS
```

**Within Phase 2:** product-service first (catalog seed needed by everyone); inventory and cart can begin in parallel against the frozen product API.

**Within Phase 4:** ai-service first (search needs `EmbeddingProvider`); notification-service can run fully in parallel; search-service waits for ai-service's `/ai/embed` endpoint.

---

## 8. Cross-Cutting Concerns

### 8.1 API versioning

All routes prefixed `/api/v1/...`. Per-service controllers use `@RequestMapping("/v1/...")` so service-internal paths are also versioned (gateway preserves the prefix or rewrites — pick one, document at gateway config time).

### 8.2 Error response shape

Adopt RFC 7807 `application/problem+json` via `spring-boot-starter-web` + `org.springframework.web.ErrorResponseException`:

```json
{
  "type": "https://n11clone/errors/insufficient-stock",
  "title": "Insufficient stock",
  "status": 409,
  "detail": "Product abc requested 5, available 3",
  "instance": "/api/v1/orders",
  "correlationId": "uuid"
}
```

Each service: `@ControllerAdvice` mapping domain exceptions → `ProblemDetail`. Gateway's existing `ProblemDetail` 401/403 responses match the same shape.

### 8.3 Observability

- **Logging:** Logback JSON encoder (`logstash-logback-encoder`); always include `correlationId` from MDC.
- **Correlation propagation:** servlet filter at every service: read `X-Correlation-Id` header → MDC; outbound WebClient/RabbitTemplate interceptors: inject header.
- **Metrics:** Spring Boot Actuator + Micrometer; scrape-able `/actuator/prometheus` (optional in v1).
- **Health:** `/actuator/health` on every service; gateway exposes aggregated status.
- **Distributed tracing:** out of scope for v1 (per PROJECT.md). Correlation IDs in flat logs are the substitute.

### 8.4 OpenAPI

Springdoc-openapi-starter-webmvc-ui per service. Gateway aggregates via `springdoc.swagger-ui.urls[]` — single Swagger UI surface for graders.

### 8.5 Configuration

- Bootstrap config (`bootstrap.yml`): only Eureka URL + config-server URL.
- Everything else from config-server; profile per env (`dev`, `aws`).
- Secrets (Iyzico keys, Gemini API key, JWT signing key): config-server pulls from env vars in dev; from AWS Parameter Store in prod (or GitHub Actions secrets injected at deploy time).

---

## 9. Architectural Patterns

### Pattern 1 — Choreography SAGA via RabbitMQ + Transactional Outbox

**What:** distributed transaction split into local TXs, each emitting an event consumed by the next participant. No central orchestrator.
**When to use:** linear-ish flows with few branches (our order saga: stock → pay → confirm). Service ownership is naturally bounded.
**Trade-offs:** + simpler infra, scales horizontally; − harder to trace (mitigated by correlationId), − cyclical dependencies risk if events become bidirectional commands. Avoid by treating events as facts, not commands.
Source: [microservices.io saga pattern](https://microservices.io/patterns/data/saga.html).

### Pattern 2 — Hexagonal / Ports & Adapters (per service, esp. ai-service)

**What:** domain core depends only on ports (interfaces); adapters implement them at the edges (DB, HTTP, LLM, AMQP).
**When to use:** any service with external integrations whose vendors might change. ai-service's `ChatProvider`/`EmbeddingProvider` is the textbook example and the SOLID-marks-vehicle.
**Trade-offs:** + clean swap points, testable domain; − boilerplate. Worth it for ai-service (SOLID demo); medium for product/cart; minimal for notification.

### Pattern 3 — JWT-at-Edge + Trusted Header Mesh

**What:** gateway validates JWT, downstream trust `X-User-Id` injected by gateway. Downstream services have no JWT secret.
**When to use:** internal mesh you control. Standard pattern across Spring Cloud Gateway docs.
**Trade-offs:** + simpler downstream, fewer secret distributions; − gateway is now mission-critical and must be impossible to bypass (network policy: backing services bind only to internal LB / VPC). Mitigation in v1 (single VPC, ELB-only ingress).

### Pattern 4 — Schema-per-Service on Single Postgres Host

**What:** logical isolation (separate `*_db` databases on same Postgres) instead of per-service Postgres clusters.
**When to use:** budget-constrained demos and bootcamps. Maintains the boundary at the data layer (no cross-service joins) without paying for N RDS instances.
**Trade-offs:** + cheap, fast to provision; − single-host failure domain, no per-service scaling. Acceptable for 6-day demo.

---

## 10. Anti-Patterns (and how to avoid them)

### Anti-Pattern 1 — "Distributed Monolith via Direct REST Sagas"

**What people do:** Order-service synchronously REST-calls inventory, then payment, then notification, blocking on each.
**Why wrong:** any downstream service down = whole order endpoint fails; tight runtime coupling; no compensation primitive.
**Do instead:** event-driven saga (per Pattern 1). REST is for queries only.

### Anti-Pattern 2 — Dual Writes (DB + RabbitMQ)

**What people do:** `repo.save(order); rabbitTemplate.send(event);` outside any TX → DB committed, broker down → event lost.
**Why wrong:** silent inconsistency; saga stalls forever.
**Do instead:** transactional outbox + poller. ([microservices.io transactional outbox](https://microservices.io/patterns/data/transactional-outbox.html))

### Anti-Pattern 3 — Cross-Service Database Joins

**What people do:** order-service queries product-service's database directly because schemas live on the same Postgres.
**Why wrong:** breaks the bounded context; future schema changes ripple; defeats the microservice point.
**Do instead:** service owns its schema. Read via that service's REST API. Snapshot what you need at write time.

### Anti-Pattern 4 — Forwarding the JWT Downstream

**What people do:** gateway passes `Authorization: Bearer ...` to backing services, each re-validates.
**Why wrong:** N services need the JWT secret/JWKS; auth logic duplicated; performance hit.
**Do instead:** strip Authorization at gateway, inject `X-User-Id`. Downstream trusts the mesh.

### Anti-Pattern 5 — Shared Library for Domain Models Across Services

**What people do:** create `common-models` JAR with `Order`, `Product` shared across services.
**Why wrong:** versioning hell; changing `Product` forces redeploy of every service.
**Do instead:** each service defines its own DTOs (even if duplicative). The `agent-toolset` lib is a deliberate exception (it's an integration layer, not domain).

### Anti-Pattern 6 — Two Toolsets (one for chat, one for MCP)

**What people do:** define cart-tools twice — once as Spring REST controllers wrapped by Gemini function-calling, once as MCP tools.
**Why wrong:** drift; doubles surface area; defeats the "one agent backend, two surfaces" pitch.
**Do instead:** single `agent-toolset` module exporting `AgentTool` implementations; ai-service uses them via `ToolDispatcher`; mcp-server registers them as MCP tools through the same Spring AI `@Tool` machinery ([Spring AI MCP Tool Calling](https://docs.spring.io/spring-ai/reference/api/tools.html)).

---

## 11. Integration Points

### External Services

| Service | Integration Pattern | Notes / Gotchas |
|---------|---------------------|-----------------|
| Iyzico | Java SDK (sandbox) — checkout-form flow | Webhook needs public, signature-verified endpoint. URL must be reachable from Iyzico's network (not behind localhost) — use ngrok in dev, public ALB in prod. |
| Gemini | Google AI / Vertex AI HTTP API | Function-calling spec: tools array with name/description/parametersSchema. Gemini 3.0 Flash availability — verify at research time per PROJECT.md verify-policy. |
| MCP clients (Claude Desktop) | MCP HTTP+SSE | Spring AI starter handles transport. Auth: API key → user-id mapping. |
| RabbitMQ | Spring AMQP | Use `RabbitListener` with manual ack + retry interceptor; declare exchanges/queues via `@Bean Declarables`. |

### Internal Boundaries

| Boundary | Communication | Notes |
|----------|---------------|-------|
| order ↔ inventory | events (async) | Choreography saga |
| order ↔ payment | events (async) | Choreography saga |
| order ↔ cart | events (async on confirm) + REST (sync at create) | Cart snapshot at create via REST; cart cleared via event |
| ai ↔ all backing | REST (sync) | Tool dispatch — failures bubble to user as graceful errors |
| search ↔ product | events (async) | Index updated on product.* events |
| search ↔ ai | REST (sync) | Query embedding at search time |
| gateway ↔ identity | REST (JWKS pull at startup, refresh hourly) | Public key fetch |
| every service ↔ eureka | Eureka client | Discovery |
| every service ↔ config-server | HTTP | Bootstrap config |

---

## 12. Scaling Considerations

| Scale | Adjustments |
|-------|-------------|
| Demo (now) | Single instance per service; single Postgres; single RabbitMQ. All on AWS Beanstalk or compose. Fine. |
| 1k users | Horizontal scale gateway + ai-service (LLM latency = bottleneck). Add Redis for rate-limit + chat conversation cache. |
| 100k users | Split Postgres per critical service (order, product); RabbitMQ cluster; CDN for product images; cache layer (Redis) in front of product-service GETs. |

**First bottleneck:** ai-service (Gemini latency + token cost). Mitigation: streaming SSE (already in design), aggressive caching of search results, rate-limit per user.
**Second bottleneck:** payment webhook ingress. Mitigation: dedicated route + auto-scaling group on gateway.
**Third bottleneck:** Postgres single-host on order/product write contention. Mitigation: extract to dedicated RDS (no schema change required since DB-per-service is logical already).

---

## 13. Risk Register (architecture-flagged)

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Iyzico webhook unreachable from sandbox to localhost dev | High | Saga stalls in dev | ngrok or public dev tunnel; document in Phase: Payment |
| MCP auth model undefined → mcp-server demo not credible | Medium | Differentiator weakens | Decide API-key→user mapping by Phase: MCP planning; default to read-only tools if unresolved |
| Gateway becomes single point of failure | Medium (demo) | Total outage | Out-of-scope to mitigate in 6 days; document as known limit |
| Outbox poller adds latency to events | Low | Saga slow under load | Tune poll interval; switch to LISTEN/NOTIFY trigger if needed (Phase: Hardening) |
| Schema-per-service on single Postgres tempts cross-DB joins | High (developer error) | Boundary violation, kills SOLID story | Use distinct DB users per service; deny cross-DB SELECT at the role level |
| Eventual consistency confuses demo audience ("why is my order PENDING?") | Medium | Demo polish | Frontend polls order status with SSE/poll; chat assistant explains "Sipariş işleniyor..." |
| Two toolset definitions drift | Medium | DRY violation = SOLID demo weakens | Enforce single `agent-toolset` module from day 1; mcp-server has zero local tool definitions |
| Gemini 3.0 Flash availability unverified | Medium | Last-minute model swap | Verify in research before ai-service implementation; `ChatProvider` abstraction means swap = one file |
| JWT replay across services if gateway bypassed | Low (VPC) | Security breach | Backing services bind to internal subnet only; deny ingress from public on backing ALBs |
| RabbitMQ message loss if broker dies before outbox poll | Low | Saga stuck | Acceptable for demo; document; use durable queues + persistent messages |

---

## 14. Sources

### Spring & Spring Cloud (HIGH confidence)
- [Spring Cloud Gateway — Securing Services](https://spring.io/blog/2019/08/16/securing-services-with-spring-cloud-gateway/)
- [Spring Cloud Gateway Workshop — JWT Lab](https://andifalk.gitbook.io/spring-cloud-gateway-workshop/hands-on-labs/lab3)
- [Spring AI MCP Reference](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-overview.html)
- [Spring AI Tool Calling Reference](https://docs.spring.io/spring-ai/reference/api/tools.html)
- [Spring AI Getting Started with MCP](https://docs.spring.io/spring-ai/reference/guides/getting-started-mcp.html)
- [Spring AI MCP Boot Starters](https://spring.io/blog/2025/09/16/spring-ai-mcp-intro-blog/)

### Saga & Outbox (HIGH confidence — Chris Richardson canonical)
- [microservices.io — Saga Pattern](https://microservices.io/patterns/data/saga.html)
- [microservices.io — Transactional Outbox](https://microservices.io/patterns/data/transactional-outbox.html)
- [microservices.io — Idempotent Consumer](https://microservices.io/patterns/communication-style/idempotent-consumer.html)
- [Transactional Outbox with RabbitMQ — Part 1](https://dev.to/sagarmaheshwary/transactional-outbox-with-rabbitmq-part-1-building-reliable-event-publishing-in-microservices-2of) (MEDIUM)
- [Transactional Outbox with RabbitMQ — Part 2 (DLQ)](https://dev.to/sagarmaheshwary/transactional-outbox-with-rabbitmq-part-2-handling-retries-dead-letter-queues-and-observability-4h19) (MEDIUM)
- [Implementing Saga Patterns in RabbitMQ](https://medium.com/@robin5002234/implementing-saga-patterns-in-rabbitmq-a-practical-guide-to-choreography-and-orchestration-85033ee84d01) (MEDIUM)

### MCP (HIGH confidence)
- [MCP Java SDK (official)](https://github.com/modelcontextprotocol/java-sdk)
- [MCP — Build a Server](https://modelcontextprotocol.io/docs/develop/build-server)
- [Baeldung — Exploring MCP with Spring AI](https://www.baeldung.com/spring-ai-model-context-protocol-mcp) (MEDIUM)
- [Baeldung — Java SDK for MCP](https://www.baeldung.com/java-sdk-model-context-protocol) (MEDIUM)

### Gateway JWT patterns (MEDIUM — verified against Spring official)
- [ORIL — Spring Cloud Gateway with JWT](https://oril.co/blog/spring-cloud-gateway-security-with-jwt/)
- [Xoriant — Microservices Security with JWT Gateway](https://www.xoriant.com/blog/microservices-security-using-jwt-authentication-gateway)

---

*Architecture research for: Spring Boot microservices e-commerce + agentic-commerce*
*Researched: 2026-04-28*
