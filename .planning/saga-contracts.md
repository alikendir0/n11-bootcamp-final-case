# Saga Contracts (Day-1 Lock)

**Locked:** 2026-04-28
**Authority:** This document is the canonical contract for every saga event in the n11-clone system. Every Phase 5+ producer must publish events whose JSON validates against the corresponding schema file in `.planning/saga-contracts/`. Every consumer must understand the exchange/queue topology and the idempotency contract documented here. If a later phase needs a new event type or a payload-shape change, the change MUST be made here first (this doc + a new/edited `.schema.json`), then propagated into code — never the other way around.

## 1. Envelope (every event)

8 fields, JSON-Schema lives in `saga-contracts/envelope.schema.json`:

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| eventId | uuid | yes | Unique per event — primary idempotency key |
| eventType | string | yes | Dotted (`order.created`, `stock.reserved`) |
| eventVersion | integer | yes | Currently 1 for all events |
| occurredAt | RFC3339 timestamp | yes | UTC |
| correlationId | uuid | yes | Saga-wide; equals first orderId in the saga chain |
| causationId | uuid | no | eventId of the event that caused this one (null on saga-initiating events) |
| producer | string | yes | service name (e.g. `order-service`) |
| payload | object | yes | type-specific — see `<eventType>.schema.json` |

The envelope is wire-level metadata; the `payload` is the business event. Validation is two-step: validate envelope against `envelope.schema.json`, then validate `payload` against the per-eventType schema.

## 2. Exchange / Queue Topology

Exchanges (5 — all topic, durable):

| Exchange | Purpose |
|----------|---------|
| `orders.tx` | Order lifecycle (created, confirmed, cancelled) |
| `inventory.tx` | Stock reservation lifecycle (reserved, reserve_failed, released) |
| `payments.tx` | Payment lifecycle (completed, failed) |
| `notifications.tx` | Optional fan-out to notification-service (Phase 7 wiring optional) |
| `identity.tx` | User lifecycle (registered) — Phase 3 outbox |

Queues (13) — bound to exchanges by routing key, each queue has one logical consumer service:

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
| `notify.q.order-cancelled` | `orders.tx` | `order.cancelled` | notification-service |
| `notify.q.payment-failed` | `payments.tx` | `payment.failed` | notification-service |
| `search.q.product-events` | `products.tx` | `product.*` | search-service |
| `notify.q.user-registered` | `identity.tx` | `user.registered` | notification-service (Phase 7) |

> Note: `products.tx` is added in Phase 4 alongside the search-service skeleton; it is not strictly part of the saga but is documented here so the topology table is complete.

> Note: `notify.q.order-cancelled` was added to this table in Phase 7 (notification-service). The §7 event catalog already listed notification-service as a consumer of `order.cancelled`; this row reconciles the topology table with the catalog.

## 3. DLX / DLQ Convention

Every main queue has:

- `x-dead-letter-exchange = <exchange>.dlx`
- `x-dead-letter-routing-key = <queue>.dlq`

Every `<exchange>.dlx` is a topic exchange paired with a `<queue>.dlq` queue bound by `<queue>.dlq` routing key. Failed messages keep the original envelope + `x-death` headers for manual replay (replay tooling deferred to Phase 11 hardening).

Concrete examples:

| Main queue | DLX | DLQ | DLQ binding key |
|------------|-----|-----|-----------------|
| `inventory.q.order-created` | `orders.tx.dlx` | `inventory.q.order-created.dlq` | `inventory.q.order-created.dlq` |
| `payment.q.stock-reserved` | `inventory.tx.dlx` | `payment.q.stock-reserved.dlq` | `payment.q.stock-reserved.dlq` |
| `order.q.payment-completed` | `payments.tx.dlx` | `order.q.payment-completed.dlq` | `order.q.payment-completed.dlq` |

## 4. Retry Policy (LOCKED wording)

**3 total attempts (= 1 initial + 2 retries). Delays between attempts: 1s, then 5s. After the 3rd attempt fails, the message goes to DLQ. The 30s upper bound is a safety cap on the exponential growth of the backoff (multiplier=5, max=30000ms), not a delay between attempts 3 and 4 — there is no attempt 4.**

Spring AMQP implementation: `RetryInterceptorBuilder.stateful().maxAttempts(3).backOffOptions(1000L, 5.0, 30000L).recoverer(new RejectAndDontRequeueRecoverer())`. (See RESEARCH §4.6 lines 569–595 for the full bean.)

The "locked wording" above is verbatim — any later doc that paraphrases this MUST link back here rather than restate the policy in a different form. Pitfall #6 (research) specifically warned that earlier drafts conflated the 30s cap with a per-attempt delay.

## 5. Idempotency

Two complementary mechanisms (one at producer, one at consumer):

### 5.1 Producer: transactional outbox

Every event INSERT is in the SAME DB transaction as the business state change. A poller publishes outbox rows to RabbitMQ and marks `sent_at`. Guarantees at-least-once delivery without dual-write inconsistency. Schema (per-service, owned by the service's schema):

```sql
CREATE TABLE outbox (
  id          UUID PRIMARY KEY,
  aggregate   TEXT NOT NULL,
  event_type  TEXT NOT NULL,
  payload     JSONB NOT NULL,
  occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  sent_at     TIMESTAMPTZ NULL
);
CREATE INDEX outbox_unsent_idx ON outbox (occurred_at) WHERE sent_at IS NULL;
```

### 5.2 Consumer: processed_events inbox

Every saga consumer maintains `processed_events(event_id PK, consumer, event_type, processed_at)` (created by `service-template/src/main/resources/db/migration/V1__init_processed_events.sql`). On consume:

1. SELECT-then-skip if `event_id` already in inbox (clean re-delivery handling).
2. Apply side effect AND insert into `processed_events` in the same transaction.
3. Ack the AMQP message manually.

Guarantees exactly-once effect under at-least-once delivery.

## 6. Correlation IDs

Every event carries `correlationId` from `order.created`. This same UUID flows through:

- HTTP `X-Correlation-Id` header (gateway generates if absent)
- AMQP `correlation_id` property + envelope `correlationId` field
- SLF4J MDC key `correlationId` (logged in JSON via Logback `%X{correlationId}`)

This allows tracing one saga across all 13 services in flat logs (`grep '"correlationId":"<uuid>"' logs/*.log`). The full propagation table for HTTP and AMQP hops lives in `api-contracts.md` §5.

## 7. Event Catalog → Schema File Map

| Event Type | Schema File | Producer | Consumers (by routing key match) |
|------------|-------------|----------|----------------------------------|
| `order.created` | `order-created.schema.json` | order-service | inventory-service |
| `stock.reserved` | `stock-reserved.schema.json` | inventory-service | order-service, payment-service |
| `stock.reserve_failed` | `stock-reserve-failed.schema.json` | inventory-service | order-service |
| `payment.completed` | `payment-completed.schema.json` | payment-service | order-service |
| `payment.failed` | `payment-failed.schema.json` | payment-service | order-service, inventory-service (compensation), notification-service |
| `order.confirmed` | `order-confirmed.schema.json` | order-service | cart-service, notification-service |
| `order.cancelled` | `order-cancelled.schema.json` | order-service | inventory-service, notification-service |
| `stock.released` | `stock-released.schema.json` | inventory-service | (audit; no Phase-1-locked consumer) |
| `user.registered` | `user-registered.schema.json` | identity-service | notification-service (Phase 7 wiring) |

All 9 payload schema files plus `envelope.schema.json` live in `.planning/saga-contracts/`.

## 8. Drift Gate (D-08)

`AbstractEventSchemaTest` (in `common-events`, scaffolded by Plan 04) loads the canonical `.schema.json` for an event's `eventType` from the classpath and asserts the produced JSON validates. Every Phase 5+ saga integration test extends this base. Producer drift fails the build at the producing service.

To make the schemas classpath-loadable, Plan 04 will copy `.planning/saga-contracts/*.schema.json` into `common-events/src/main/resources/saga-schemas/` so `AbstractEventSchemaTest` can resolve them via `ClassLoader.getResourceAsStream("saga-schemas/" + eventType.replace('.', '-') + ".schema.json")`.

## 9. Schema Naming Note (Postgres)

The order-service's Postgres schema is named `orders` (plural), NOT `order`, because `order` is a SQL reserved word and quoting it everywhere is error-prone. This affects:

- `infra/postgres/init.sh` (Plan 03) creates the `orders` schema with `orders_user`
- `service-template.yml` (Plan 06) Flyway placeholders use `orders` for the order-service
- This document's references to "the order-service schema" mean `orders` in DDL contexts

The event-type and field names (`order.created`, `orderId`, etc.) are independent of the Postgres schema name and remain singular `order.*` per business semantics. This is the pragmatic split — saga vocabulary stays singular, DB schema goes plural.

---
*Updated 2026-04-29 (Phase 3): added `identity.tx` exchange + `notify.q.user-registered` queue + `user.registered` catalog entry. Schema file at `.planning/saga-contracts/user-registered.schema.json`.*
*Updated 2026-04-30 (Phase 7): added `notify.q.order-cancelled` to §2 queue topology — notification-service consumer of `order.cancelled` (already listed in §7 event catalog; topology row was missing).*
