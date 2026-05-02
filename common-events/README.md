# common-events

> **Phase 1** — Saga Event Schemas + Validation

Shared library module containing saga event JSON Schemas and a base test class (`AbstractEventSchemaTest`) for producer-side drift detection. Ensures every saga event validates against the canonical schema locked in `.planning/saga-contracts/`.

## Schema Files

Classpath location: `saga-schemas/<event-type>.schema.json`

| Schema | Event Type |
|--------|-----------|
| `envelope.schema.json` | Every event (8 metadata fields) |
| `order-created.schema.json` | `order.created` |
| `stock-reserved.schema.json` | `stock.reserved` |
| `stock-reserve-failed.schema.json` | `stock.reserve_failed` |
| `payment-completed.schema.json` | `payment.completed` |
| `payment-failed.schema.json` | `payment.failed` |
| `order-confirmed.schema.json` | `order.confirmed` |
| `order-cancelled.schema.json` | `order.cancelled` |
| `stock-released.schema.json` | `stock.released` |
| `user-registered.schema.json` | `user.registered` |

## Drift Gate

`AbstractEventSchemaTest` loads the canonical schema and validates produced JSON at test time. Producer drift fails the build at the producing service — never at the consumer.

## RabbitMQ Retry Config

`RabbitRetryConfig` provides the locked retry policy:
- 3 total attempts (1 initial + 2 retries)
- Delays: 1s, then 5s
- Recovery: `RejectAndDontRequeueRecoverer` → message goes to DLQ
]]>
