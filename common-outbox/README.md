# common-outbox

> **Phase 5** — Transactional Outbox Pattern

Shared library module implementing the **transactional outbox** pattern for reliable event publishing. Ensures business state change and event insertion happen in a single `@Transactional` boundary — no dual-write inconsistency.

## How It Works

1. Service writes business entity + outbox row in **one transaction**
2. `AbstractOutboxPoller` (scheduled) queries unsent rows with `FOR UPDATE SKIP LOCKED`
3. Poller publishes to RabbitMQ via `RabbitTemplate.convertAndSend()`
4. On success, marks `sent_at` timestamp
5. `OutboxMessagePostProcessor` ensures `messageId` and `correlationId` are always set from the envelope JSON

## Outbox Schema (per-service)

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

## Services Using Outbox

| Service | Events Published |
|---------|-----------------|
| identity-service | `user.registered` |
| inventory-service | `stock.reserved`, `stock.reserve_failed`, `stock.released` |
| order-service | `order.created`, `order.confirmed`, `order.cancelled` |
| payment-service | `payment.completed`, `payment.failed` |

## Key Invariant

The `OutboxMessagePostProcessor` (D-09 structural fix) ensures `messageId` and `correlationId` are always set from the envelope JSON. This structurally prevents the 999.2 per-service copy-paste regression.
]]>
