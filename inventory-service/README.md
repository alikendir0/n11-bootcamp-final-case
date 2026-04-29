# inventory-service

Phase 4, Plan 02 — Stock management and saga consumer for the n11 clone project.

## Purpose

inventory-service manages per-product stock quantities, processes `order.created` saga events to reserve stock, and publishes `stock.reserved` / `stock.reserve_failed` events via the transactional outbox pattern.

## Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/inventory/{productId}` | Public | Returns StockStateDto with Turkish stock-state labels |
| GET | `/actuator/health` | Public | Spring Boot Actuator health |
| GET | `/swagger-ui.html` | Public | Springdoc Swagger UI (PROD-07) |
| GET | `/v3/api-docs` | Public | OpenAPI 3 spec |

### StockStateDto response shape

```json
{
  "productId": "00000000-0000-4000-8000-000000000011",
  "availableQty": 15,
  "stockState": "STOKTA",
  "stockStateLabel": "Stokta",
  "displayQty": 15
}
```

Stock state label mapping:

| Condition | stockState | stockStateLabel |
|-----------|------------|-----------------|
| effectiveAvailable <= 0 | TUKENDI | Tükendi |
| 0 < effectiveAvailable <= lowStockThreshold | SON_URUN | Son {N} ürün! |
| effectiveAvailable > lowStockThreshold | STOKTA | Stokta |

## Saga Integration

| Direction | Exchange | Routing Key | Queue |
|-----------|----------|-------------|-------|
| IN | orders.tx | order.created | inventory.q.order-created |
| OUT | inventory.tx | stock.reserved | (payment-service binds in Phase 5) |
| OUT | inventory.tx | stock.reserve_failed | (compensating path) |

Dead-letter queue: `inventory.q.order-created.dlq` (messages rejected after 3 retries).

### CLAUDE.md Rule #3 Idempotency

Every incoming event is checked against the `processed_events` inbox table BEFORE any state change:

```java
if (processedEventsRepository.existsById(eventId)) return;
// ... reserve stock, save outbox, save processed_events — all in ONE @Transactional method
```

Re-delivering the same `eventId` is safe: second delivery returns immediately with no side effects.

## Required Environment Variables

| Variable | Description |
|----------|-------------|
| `INVENTORY_DB_PASSWORD` | PostgreSQL password for `inventory_user` |
| `RABBITMQ_DEFAULT_USER` | RabbitMQ username |
| `RABBITMQ_DEFAULT_PASS` | RabbitMQ password |

## Boot Procedure

```bash
# Build and push Docker image to local daemon
./gradlew :inventory-service:jibDockerBuild

# Start with full service stack
docker compose up -d inventory-service

# Verify health
docker compose ps inventory-service
```

## Pitfall Tripwires

- **CLAUDE.md Rule #3**: `processedEventsRepository.existsById(eventId)` MUST be called before any stock mutation. If omitted, duplicate deliveries corrupt stock_reservations.
- **Pitfall #11**: inventory_user is REVOKED from `product` schema. Never write cross-schema queries against `product.products`. V3 stock seed uses hardcoded UUID literals (B-04) that match product UUIDs — this is seeding, not a runtime query.
- **RESERVATION_CONFLICT**: `ObjectOptimisticLockingFailureException` from `@Version` on Stock entity MUST be caught in `OrderCreatedConsumer` and published as a `stock.reserve_failed` outbox event with `reason=RESERVATION_CONFLICT`. Never re-throw — doing so causes infinite AMQP retry loops.

## Tests

```bash
./gradlew :inventory-service:test --no-daemon
```

| Test Class | What it Proves |
|-----------|----------------|
| StockStateComputationTest | Turkish stock labels from Stock.getEffectiveAvailable() (pure unit, no DB) |
| StockEntityVersionTest | @Version optimistic lock conflict + version increment (Testcontainers JPA slice) |
| InventoryControllerIntegrationTest | GET /inventory/{productId} returns 200 with StockStateDto |
| InventoryFlywayMigrationsTest | V1/V2/V3 migrations apply cleanly; all tables exist; 52 seed rows |
| OrderCreatedConsumerIntegrationTest | CLAUDE.md Rule #3: duplicate eventId produces exactly 1 processed_events row |
