# cart-service

> **Phase 5** — Per-User Cart State

Server-side cart service — the single source of truth for cart state. Both the web storefront and the AI chat assistant read/write the same cart via this service. Product prices are snapshot at add-time for price-drift detection at checkout.

## Endpoints

| Verb | Path | Auth | Purpose |
|------|------|------|---------|
| GET | `/cart` | JWT (X-User-Id) | Return current cart with line totals + KDV breakdown |
| POST | `/cart/items` | JWT (X-User-Id) | Add/upsert item (productId, qty) — UPSERT semantics, qty ≤ 99 |
| PATCH | `/cart/items/{id}` | JWT (X-User-Id) | Update quantity |
| DELETE | `/cart/items/{id}` | JWT (X-User-Id) | Remove item |

## Design Decisions

- **Server-side state** — no client-side cart; survives logout/login by userId
- **Product snapshot** — `unit_price_snapshot` captured at add-time via sync REST call to product-service
- **UPSERT semantics** — adding the same product twice increments quantity (qty 2 + 1 = 3)
- **Qty constraint** — max 99 per line item

## Saga Integration

| Direction | Exchange | Routing Key | Queue | Purpose |
|-----------|----------|-------------|-------|---------|
| IN | orders.tx | order.confirmed | cart.q.order-confirmed | Clear cart after successful order |

`OrderConfirmedConsumer` clears the user's cart when an order is confirmed. Idempotent via `processed_events` inbox.

## Required Environment Variables

| Variable | Description |
|----------|-------------|
| `CART_DB_PASSWORD` | PostgreSQL password for `cart_user` |
| `RABBITMQ_DEFAULT_USER` | RabbitMQ username |
| `RABBITMQ_DEFAULT_PASS` | RabbitMQ password |

## Build & Run

```bash
./gradlew :cart-service:jibDockerBuild
docker compose up -d cart-service
```

## Tests

```bash
./gradlew :cart-service:test
```

Testcontainers Postgres + RabbitMQ integration tests cover UPSERT semantics, cart clearing on order confirmation, and idempotency.
