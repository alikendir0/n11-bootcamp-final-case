# order-service

> **Phase 5** — Order Lifecycle + Saga Initiator

Manages the full order lifecycle from cart checkout to delivery confirmation. The **saga initiator** — publishes `order.created` and drives the choreography flow through RabbitMQ events. Uses the transactional outbox pattern and `Idempotency-Key` header deduplication.

## Endpoints

| Verb | Path | Auth | Purpose |
|------|------|------|---------|
| POST | `/orders` | JWT (X-User-Id) + `Idempotency-Key` header | Create order from cart |
| GET | `/orders` | JWT (X-User-Id) | List user's orders (desc by date) |
| GET | `/orders/{id}` | JWT (X-User-Id) | Order detail with status timeline |
| POST | `/orders/{id}/cancel` | JWT (X-User-Id) | User-initiated cancellation |

## Order Status State Machine

```
PENDING ──▶ STOCK_RESERVED ──▶ PAID ──▶ CONFIRMED
   │              │              │
   ▼              ▼              ▼
CANCELLED     CANCELLED      CANCELLED
(stock fail)  (payment fail) (user cancel)
```

Turkish timeline labels: `Sipariş Alındı → Hazırlanıyor → Kargoya Verildi → Teslim Edildi`

## Saga Events

| Direction | Exchange | Routing Key | Purpose |
|-----------|----------|-------------|---------|
| OUT | orders.tx | order.created | Initiates saga — triggers inventory reservation |
| OUT | orders.tx | order.confirmed | Saga happy-path closure — triggers cart clear + notification |
| OUT | orders.tx | order.cancelled | Compensation — triggers stock release + notification |
| IN | inventory.tx | stock.reserved | Advances state to STOCK_RESERVED |
| IN | inventory.tx | stock.reserve_failed | Moves to CANCELLED |
| IN | payments.tx | payment.completed | Advances to PAID then CONFIRMED |
| IN | payments.tx | payment.failed | Compensation to CANCELLED |

## Idempotency

- **Idempotency-Key** — UUID in request header; `(idempotency_key, user_id)` composite PK prevents duplicate orders
- **Processed events inbox** — all 4 saga consumers check `processed_events` before any state mutation
- **Transactional outbox** — order + outbox row written in a single `@Transactional` boundary

## Price Drift Detection

Before creating an order, cart line item prices are compared against current product prices via sync REST call. If any price has drifted, a **409 Conflict** with `type=price-drift` and `updatedItems[]` is returned (RFC-7807).

## Required Environment Variables

| Variable | Description |
|----------|-------------|
| `ORDERS_DB_PASSWORD` | PostgreSQL password for `orders_user` (schema: `orders` — plural, SQL reserved word) |
| `RABBITMQ_DEFAULT_USER` | RabbitMQ username |
| `RABBITMQ_DEFAULT_PASS` | RabbitMQ password |

## Build & Run

```bash
./gradlew :order-service:jibDockerBuild
docker compose up -d order-service
```

## Tests

```bash
./gradlew :order-service:test
```

Integration tests cover saga consumer idempotency, outbox publishing, price-drift detection, and cancellation flow.
