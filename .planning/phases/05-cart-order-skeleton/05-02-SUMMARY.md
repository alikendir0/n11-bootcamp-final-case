---
phase: 05-cart-order-skeleton
plan: 02
subsystem: cart-service
tags: [spring-boot, jpa, amqp, testcontainers, rest, saga, flyway, springdoc]

# Dependency graph
requires:
  - phase: 05-01
    provides: common-outbox library (not used by cart-service — consumer-only), D-10 ArchUnit gate

provides:
  - cart-service Spring Boot module (port 8084, schema cart)
  - GET /cart, POST /cart/items, PATCH /cart/items/{productId}, DELETE /cart/items/{productId}
  - cart.q.order-confirmed AMQP consumer (D-07 cart-clear on order.confirmed)
  - D-02 native UPSERT (ON CONFLICT DO UPDATE summing qty)
  - D-11 one-cart-per-user schema (carts + cart_items composite PK)
  - CartItemRepositoryUpsertTest (3 Testcontainers Postgres tests)
  - OrderConfirmedConsumerIdempotencyTest (idempotency proven via direct consumer invocation)

affects:
  - 05-03 (order-service): can call GET /cart at http://cart-service:8084/cart for sync read
  - 05-04 (payment-service skeleton / E2E test): consumer queue cart.q.order-confirmed is in
    place for the happy-path saga end-to-end test
  - 05-05 (gateway routes + docker-compose): cart-service added to include list; docker-compose
    entry + gateway route deferred to Plan 05-05

# Tech tracking
tech-stack:
  added:
    - "cart-service Spring Boot module (new)"
    - "CartItemRepository: schema-qualified native UPSERT with ON CONFLICT DO UPDATE"
    - "Two-bean @Transactional split: CartService (outer no-tx) + CartPersistenceService"
  patterns:
    - "Native UPSERT query schema-qualified (cart.cart_items) for Testcontainers compatibility"
    - "Two-bean split: outer no-tx orchestrator + separate @Transactional bean (Phase 4 Plan 04-02 lesson)"
    - "Direct consumer invocation in Testcontainers slice tests (Phase 4 Plan 04-02 lesson — avoid AMQP handshake instability)"
    - "ProductClient using RestClient.Builder with common-logging correlation-ID interceptor autowired"

key-files:
  created:
    - cart-service/build.gradle.kts
    - cart-service/src/main/java/com/n11/cart/CartServiceApplication.java
    - cart-service/src/main/java/com/n11/cart/health/SampleHealthController.java
    - cart-service/src/main/java/com/n11/cart/cart/CartItemId.java
    - cart-service/src/main/java/com/n11/cart/cart/Cart.java
    - cart-service/src/main/java/com/n11/cart/cart/CartItem.java
    - cart-service/src/main/java/com/n11/cart/cart/CartRepository.java
    - cart-service/src/main/java/com/n11/cart/cart/CartItemRepository.java
    - cart-service/src/main/java/com/n11/cart/cart/CartService.java
    - cart-service/src/main/java/com/n11/cart/cart/CartPersistenceService.java
    - cart-service/src/main/java/com/n11/cart/cart/CartController.java
    - cart-service/src/main/java/com/n11/cart/cart/dto/AddCartItemRequest.java
    - cart-service/src/main/java/com/n11/cart/cart/dto/UpdateCartItemRequest.java
    - cart-service/src/main/java/com/n11/cart/cart/dto/CartView.java
    - cart-service/src/main/java/com/n11/cart/cart/dto/CartLineView.java
    - cart-service/src/main/java/com/n11/cart/product/ProductSnapshot.java
    - cart-service/src/main/java/com/n11/cart/product/ProductClient.java
    - cart-service/src/main/java/com/n11/cart/messaging/ProcessedEvent.java
    - cart-service/src/main/java/com/n11/cart/messaging/ProcessedEventRepository.java
    - cart-service/src/main/java/com/n11/cart/messaging/CartRabbitConfig.java
    - cart-service/src/main/java/com/n11/cart/messaging/OrderConfirmedConsumer.java
    - cart-service/src/main/java/com/n11/cart/messaging/CartSagaService.java
    - cart-service/src/main/resources/application.yml
    - cart-service/src/main/resources/db/migration/V1__init_processed_events.sql
    - cart-service/src/main/resources/db/migration/V2__init_cart.sql
    - cart-service/src/test/resources/application.yml
    - cart-service/src/test/resources/application-test.yml
    - cart-service/src/test/java/com/n11/cart/cart/CartItemRepositoryUpsertTest.java
    - cart-service/src/test/java/com/n11/cart/messaging/OrderConfirmedConsumerIdempotencyTest.java
    - config-server/src/main/resources/config/cart-service.yml
  modified:
    - settings.gradle.kts (added "cart-service" to include block)

key-decisions:
  - "awaitility pinned to 4.2.0 (4.3.1 in plan does not exist in Maven Central; 4.2.0 matches inventory-service)"
  - "cart-service does NOT import :common-outbox (consumer-only service; no outbox needed; ProcessedEvent is local to com.n11.cart.messaging)"
  - "ProductClient uses RestClient.Builder (not RestTemplate) — Boot 3.x autoconfigures the builder with common-logging.RestClientConfig interceptors"

# Metrics
duration: ~9 min
completed: 2026-04-30
---

# Phase 5 Plan 02: cart-service Spring Boot Module Summary

**cart-service module: full REST surface (4 endpoints) + native UPSERT cart-items + D-07 order.confirmed saga consumer + 4 Testcontainers integration tests all green**

## Performance

- **Duration:** ~9 min
- **Started:** 2026-04-30T08:05:13Z
- **Completed:** 2026-04-30T08:14:37Z
- **Tasks:** 3 of 3
- **Files modified:** 30 (29 created, 1 modified)

## Accomplishments

- Cart-service Spring Boot module scaffolded: Flyway V1 (processed_events) + V2 (carts + cart_items with composite PK per D-02, CD-10 qty<=99 check), config-server overlay (port 8084, cart_user)
- Full REST surface: GET /cart, POST /cart/items (UPSERT), PATCH /cart/items/{productId}, DELETE /cart/items/{productId} per api-contracts.md §1
- Native UPSERT via schema-qualified `ON CONFLICT (user_id, product_id) DO UPDATE SET qty = cart.cart_items.qty + EXCLUDED.qty` (D-02)
- Two-bean @Transactional split: CartService (outer orchestrator — sync REST before DB) + CartPersistenceService (all @Transactional persist methods)
- ProductClient: sync REST to product-service for D-01 snapshot fetch on add/update; RestClient.Builder wired with common-logging correlation-ID interceptor
- OrderConfirmedConsumer: canonical shape per D-10 invariant (Message param, no Channel); delegates to CartSagaService for idempotent cart-clear
- CartSagaService: @Transactional processOrderConfirmed — idempotency check + deleteByUserId + processed_events row in one transaction
- CartRabbitConfig: orders.tx exchange + cart.q.order-confirmed queue + DLQ topology (saga-contracts.md §2)
- CartItemRepositoryUpsertTest: 3 Testcontainers Postgres tests (UPSERT summing qty, deleteLine, cart persists by userId) — all green
- OrderConfirmedConsumerIdempotencyTest: duplicate delivery asserts cart cleared exactly once + processed_events count=1 — green
- D-10 ArchUnit gate still passes with new OrderConfirmedConsumer

## Task Commits

1. **Task 1: cart-service module scaffold** - `60cde57` (feat)
2. **Task 2: Cart domain + REST controller + UPSERT + product snapshot client** - `66dd389` (feat)
3. **Task 3: cart.q.order-confirmed consumer + idempotency test** - `b4ddb22` (feat)

## File Inventory

- 29 new files, 1 modified (settings.gradle.kts)
- 22 main source files: application class, health controller, 4 domain entities/repositories, 4 DTOs, 2 service beans, 1 controller, 2 product client files, 5 messaging files (consumer, saga service, config, processed event entity + repo)
- 3 resource files: application.yml, 2 Flyway migrations
- 2 test resource files, 2 test classes

## UPSERT Query Verification

The schema-qualified native UPSERT:
```sql
INSERT INTO cart.cart_items (user_id, product_id, qty, ...)
ON CONFLICT (user_id, product_id) DO UPDATE SET
    qty = cart.cart_items.qty + EXCLUDED.qty,
    ...
```
Proven by `upsertAddQty_sameProductTwice_sumsQty`: adding qty=2 then qty=3 for the same productId produces a single row with qty=5.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] awaitility version 4.3.1 does not exist in Maven Central**
- **Found during:** Task 2 (test dependency resolution)
- **Issue:** `org.awaitility:awaitility:4.3.1` resolves to a 404 in Maven Central. Plan specified 4.3.1 but only 4.2.0 and 4.2.1 exist.
- **Fix:** Changed to `4.2.0` (matching inventory-service and the confirmed version in Maven Central)
- **Files modified:** cart-service/build.gradle.kts
- **Commit:** `66dd389` (applied before commit)

---

**Total deviations:** 1 auto-fixed (version typo in plan)
**Impact on plan:** None — 4.2.0 is functionally equivalent for the Awaitility await-until patterns used in this plan.

## Consumer Shape Deviation Check

None. OrderConfirmedConsumer exactly mirrors the canonical `inventory-service.OrderCreatedConsumer` shape:
- NOT @Transactional on listener method
- Message amqpMessage parameter (D-10 invariant)
- No com.rabbitmq.client.Channel parameter (D-10 invariant)
- Delegates to @Service @Transactional CartSagaService
- Idempotency check INSIDE the @Transactional boundary (CLAUDE.md Rule #3)
- AmqpRejectAndDontRequeueException for malformed/unrecoverable; propagate for transient

## Notes for Plans 03-05

### Note for Plan 05-03 (order-service)
- cart-service exposes GET /cart at port 8084; order-service should use `http://cart-service:8084/cart` for sync read inside the Docker network (Plan 05-05 will register cart-service in docker-compose)
- cart-service reads X-User-Id from gateway header (Phase 3 D-15); order-service must forward the same header when calling GET /cart

### Note for Plan 05-04 (payment-service skeleton / E2E test)
- cart-service consumer queue is `cart.q.order-confirmed` bound to `orders.tx`/`order.confirmed`
- saga E2E test should publish a synthetic `order.confirmed` envelope to verify the cart-clear path
- Test already verified: duplicate delivery → exactly 1 processed_events row + 0 cart_items

## Known Stubs

None. All 4 endpoints are fully wired to persistence. ProductClient makes actual sync REST calls (product-service URL configurable via `app.clients.product.base-url`; defaults to `http://product-service:8082`).

## Threat Flags

| Flag | File | Description |
|------|------|-------------|
| threat_flag: missing_auth_enforcement | cart-service/CartController.java | X-User-Id is read from header but no validation that the gateway populated it (gateway strips auth and injects header per Phase 3 D-15 — reliance is correct by design, but a direct call bypassing the gateway would succeed with any UUID) |

This is by design (Phase 3 D-15 pattern: gateway enforces JWT, services trust X-User-Id). Documented for awareness only.

## Self-Check: PASSED

All 14 key files verified present. All 3 task commits (60cde57, 66dd389, b4ddb22) verified in git log.
