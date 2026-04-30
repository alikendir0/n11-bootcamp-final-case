---
phase: 05-cart-order-skeleton
plan: 04
subsystem: payment-service + inventory compensation + saga E2E
tags: [spring-boot, jpa, amqp, saga, testcontainers, compensation, e2e, rabbitmq]

# Dependency graph
requires:
  - phase: 05-01
    provides: common-outbox library (PaymentOutboxPoller extends AbstractOutboxPoller, D-09)
  - phase: 05-02
    provides: cart-service with order.confirmed consumer (saga chain tail)
  - phase: 05-03
    provides: order-service PaymentCompletedConsumer (consumes payment.completed)

provides:
  - payment-service Spring Boot module (port 8086, schema payment, D-06 mock skeleton)
  - StockReservedConsumer: idempotent saga consumer (payment.q.stock-reserved)
  - PaymentTransactionalService: persists payments row + payment.completed outbox (W4: real totalAmount)
  - PaymentOutboxPoller extends AbstractOutboxPoller (D-09 messageId injection uniform)
  - inventory-service PaymentFailedConsumer: CD-08 compensation (inventory.q.payment-failed)
  - inventory-service OrderCancelledConsumer: CD-09 compensation (inventory.q.order-cancelled)
  - StockReservedPayload with totalAmount (W4 closure — inventory forwards order amount to payment)
  - SagaHappyPathE2ETest: Testcontainers Postgres + RabbitMQ; real AMQP delivery; Awaitility 15s window
  - D-09 messageId invariant verified end-to-end by SagaHappyPathE2ETest
  - Phase 5 QUAL-03 fully satisfied (7 integration tests across 4 services + infra-tests)

affects:
  - 05-05 (gateway routes + docker-compose): payment-service port 8086 must be in docker-compose
  - Phase 6 (Iyzico): replaces PaymentTransactionalService internals; PaymentSagaService + consumer topology unchanged
  - Phase 6 (payment-failed producer): PaymentFailedConsumer + OrderCancelledConsumer in inventory already wired

# Tech tracking
tech-stack:
  added:
    - "payment-service Spring Boot module (new, ~200 LOC core)"
    - "D-06 mock payment skeleton: StockReservedConsumer → PaymentTransactionalService → payment.completed outbox"
    - "W4 closure: totalAmount flows order.created → stock.reserved → payment.completed (not hardcoded)"
    - "CD-08 PaymentFailedConsumer in inventory-service (compensation)"
    - "CD-09 OrderCancelledConsumer in inventory-service (compensation)"
    - "SagaHappyPathE2ETest: Testcontainers RabbitMQ 3.13-management + Postgres + Awaitility"
  patterns:
    - "Two-bean @Transactional split: PaymentSagaService (mock delay, no @Tx) → PaymentTransactionalService (@Tx persist)"
    - "Bean disambiguation for multi-service classpath: @Entity(name), @RestController(beanName), @Component(beanName)"
    - "infra-tests Flyway isolation: classpath:db/migration/payment avoids version collision"
    - "PaymentServiceTestConfig: isolated @SpringBootApplication with excludeFilters for other service apps"

key-files:
  created:
    - payment-service/build.gradle.kts
    - payment-service/src/main/java/com/n11/payment/PaymentServiceApplication.java
    - payment-service/src/main/java/com/n11/payment/payment/Payment.java + PaymentStatus.java + PaymentRepository.java
    - payment-service/src/main/java/com/n11/payment/messaging/StockReservedConsumer.java
    - payment-service/src/main/java/com/n11/payment/messaging/PaymentSagaService.java
    - payment-service/src/main/java/com/n11/payment/messaging/PaymentTransactionalService.java
    - payment-service/src/main/java/com/n11/payment/messaging/ProcessedEvent.java + ProcessedEventRepository.java
    - payment-service/src/main/java/com/n11/payment/messaging/PaymentRabbitConfig.java
    - payment-service/src/main/java/com/n11/payment/outbox/PaymentOutboxPoller.java + PaymentOutboxRepository.java
    - payment-service/src/main/java/com/n11/payment/health/SampleHealthController.java
    - payment-service/src/main/resources/db/migration/V1__init_processed_events.sql + V2__init_payment.sql
    - payment-service/src/main/resources/application.yml + test configs
    - payment-service/src/test/java/com/n11/payment/messaging/StockReservedConsumerIntegrationTest.java
    - config-server/src/main/resources/config/payment-service.yml
    - inventory-service/src/main/java/com/n11/inventory/messaging/PaymentFailedConsumer.java
    - inventory-service/src/main/java/com/n11/inventory/messaging/OrderCancelledConsumer.java
    - inventory-service/src/main/java/com/n11/inventory/messaging/StockReservedPayload.java
    - inventory-service/src/test/java/com/n11/inventory/messaging/InventoryCompensationTest.java
    - infra-tests/src/test/java/com/n11/infratests/saga/SagaHappyPathE2ETest.java
    - infra-tests/src/test/java/com/n11/infratests/saga/PaymentServiceTestConfig.java
    - infra-tests/src/test/resources/application.yml
    - infra-tests/src/test/resources/db/migration/payment/V1__init_processed_events.sql + V2__init_payment.sql
  modified:
    - settings.gradle.kts (added payment-service)
    - inventory-service/src/main/java/com/n11/inventory/messaging/InventoryRabbitConfig.java (CD-08 + CD-09 queues)
    - inventory-service/src/main/java/com/n11/inventory/messaging/InventoryOrderService.java (releaseStockForOrder + W4)
    - inventory-service/src/main/java/com/n11/inventory/reservation/StockReservation.java (markReleased method)
    - inventory-service/src/main/java/com/n11/inventory/reservation/StockReservationRepository.java (findByOrderIdAndStatus)
    - infra-tests/build.gradle.kts (added rabbitmq + awaitility + payment-service + spring-boot-test)
    - Multiple service files: @Entity(name) disambiguation, @RestController(beanName), @Component(beanName) for multi-service classpath

key-decisions:
  - "PaymentServiceTestConfig isolated @SpringBootApplication with excludeFilters for @SpringBootApplication annotations — prevents inventory/identity service application classes from expanding scan scope to entire codebase when payment-service is loaded in infra-tests context"
  - "infra-tests Flyway uses classpath:db/migration/payment not classpath:db/migration — both payment and inventory services have V1+V2 migrations at the base path; using the subdirectory avoids FlywayException: Found more than one migration with version 2"
  - "Bean disambiguation: @Entity(name=...), @RestController(beanName), @Component(beanName) added across all per-service classes that share class names — required because infra-tests classpath includes all services and Spring's bean registry rejects duplicate names"
  - "SagaHappyPathE2ETest sniffer queue uses QueueBuilder.nonDurable(name).build() NOT .autoDelete() — autoDelete causes queue deletion between Awaitility poll iterations when RabbitTemplate.receive()'s internal consumer unsubscribes after 100ms timeout"

# Metrics
duration: ~45 min (execution started after prior plan commits; includes test runs)
completed: 2026-04-30
---

# Phase 5 Plan 04: payment-service skeleton + inventory compensation + saga E2E Summary

**payment-service D-06 mock skeleton wired into saga; CD-08/CD-09 compensation consumers added to inventory-service; SagaHappyPathE2ETest proves real AMQP delivery of stock.reserved → payment.completed within 15s; QUAL-03 fully satisfied**

## Performance

- **Duration:** ~45 min
- **Started:** 2026-04-30T11:30:00Z
- **Completed:** 2026-04-30T09:45:13Z
- **Tasks:** 3 of 3
- **Files modified:** 48 (38 created, 10 modified)

## Accomplishments

- payment-service Spring Boot module (port 8086, schema `payment`, D-06): `StockReservedConsumer` (idempotent, D-10: Message param no Channel) → `PaymentSagaService` (mock delay outside @Tx) → `PaymentTransactionalService` (@Tx persist: payments row + payment.completed outbox). W4 closure: amount comes from `stock.reserved.totalAmount`, never hardcoded. `PaymentOutboxPoller` extends `AbstractOutboxPoller` (D-09 messageId uniform).
- CD-08: `PaymentFailedConsumer` added to inventory-service, bound to `payments.tx/payment.failed` via `inventory.q.payment-failed`. Calls `InventoryOrderService.releaseStockForOrder`. Wired now even though no real payment.failed fires in Phase 5 — Phase 6 Iyzico decline picks up seamlessly.
- CD-09: `OrderCancelledConsumer` added to inventory-service, bound to `orders.tx/order.cancelled` via `inventory.q.order-cancelled`. Same `releaseStockForOrder` delegate path.
- W4 totalAmount propagation: `StockReservedPayload` extracted to top-level type + `totalAmount: BigDecimal` field added. `InventoryOrderService.processOrderCreated` forwards `order.created.payload.totalAmount` verbatim into `stock.reserved.payload.totalAmount`. Closes the saga-contracts schema W4 requirement.
- `SagaHappyPathE2ETest`: Testcontainers Postgres + RabbitMQ 3.13-management (Plan 04-02 lesson); publishes synthetic `stock.reserved` to `inventory.tx`; Awaitility 15s window asserts `payment.completed` appears on sniffer queue and `messageProperties.messageId == envelope.eventId` (D-09 invariant). SC-2 proof.
- `InventoryCompensationTest`: 2 compensation tests (paymentFailed, orderCancelled) + redelivery idempotency for paymentFailed path.
- `StockReservedConsumerIntegrationTest`: W4 invariant asserted — `payments.amount` equals the `totalAmount` from the inbound envelope (250.50 exact match), not a hardcoded mock value.

## Task Commits

1. **Task 1: payment-service skeleton** - `b0d1cf8` (feat)
2. **Task 2: Inventory compensation consumers (CD-08 + CD-09) + W4 propagation** - `22114b0` (feat)
3. **Task 3: Saga publishing E2E test + bean disambiguation** - `3d65e1c` (feat)

## Files Created

- 38 new files: payment-service module (22 files), inventory-service additions (3), infra-tests saga test + config + migrations (5 files), settings.gradle.kts (1)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Bean disambiguation required for multi-service classpath**
- **Found during:** Task 3 (SagaHappyPathE2ETest)
- **Issue:** When infra-tests loads payment-service + inventory-service + identity-service on the same classpath, Spring's bean registry throws `BeanDefinitionOverrideException` for duplicate bean names: `SampleHealthController` (defined in every service) and `ProcessedEvent` JPA entity (same class name in every service's `messaging` package).
- **Fix:** Added unique bean names: `@Entity(name = "PaymentProcessedEvent")`, `@RestController("paymentSampleHealthController")`, `@Component("inventoryOutboxPoller")`, etc. across all affected service files.
- **Files modified:** 8 files across cart-service, identity-service, inventory-service, order-service, payment-service
- **Commit:** `3d65e1c`

**2. [Rule 2 - Missing Critical] PaymentServiceTestConfig with excludeFilters for @SpringBootApplication**
- **Found during:** Task 3 (SagaHappyPathE2ETest)
- **Issue:** `@SpringBootTest(classes = PaymentServiceApplication.class)` caused Spring to also load `InventoryServiceApplication` and `IdentityServiceApplication` (both on classpath) as secondary configuration sources, pulling in all their entities and beans. The JPA `EntityScan("com.n11")` from one of them expanded entity scanning to the entire codebase.
- **Fix:** Created `PaymentServiceTestConfig` — a `@SpringBootApplication` with `excludeFilters` blocking all other `@SpringBootApplication`-annotated classes from being processed. Used this as the primary context class instead of `PaymentServiceApplication`.
- **Files modified:** `infra-tests/src/test/java/com/n11/infratests/saga/PaymentServiceTestConfig.java` (new)
- **Commit:** `3d65e1c`

**3. [Rule 1 - Bug] infra-tests Flyway location must use subdirectory path**
- **Found during:** Task 3 (SagaHappyPathE2ETest)
- **Issue:** `flyway.locations = classpath:db/migration` resolves to ALL `db/migration` directories on classpath — both payment-service JAR and inventory-service JAR have V1+V2 migrations, causing `FlywayException: Found more than one migration with version 2`.
- **Fix:** Added payment-service migrations to `infra-tests/src/test/resources/db/migration/payment/` and set `flyway.locations = classpath:db/migration/payment` in infra-tests `application.yml`.
- **Files modified:** infra-tests `application.yml` + 2 migration files (copied from payment-service)
- **Commit:** `3d65e1c`

**4. [Rule 1 - Bug] Sniffer queue must NOT be autoDelete**
- **Found during:** Task 3 (SagaHappyPathE2ETest) - plan specified `.autoDelete()`
- **Issue:** `QueueBuilder.nonDurable(name).autoDelete().build()` causes the sniffer queue to be deleted when `RabbitTemplate.receive(sniffer, 100ms)` terminates its internal consumer after the 100ms timeout. The queue disappears between Awaitility poll iterations, causing `404 NOT_FOUND` on subsequent polls.
- **Fix:** Removed `.autoDelete()` from the sniffer queue builder. Non-durable, non-autodelete survives between Awaitility polls for the duration of the test and is cleaned up when RabbitMQ container stops.
- **Files modified:** `infra-tests/src/test/java/com/n11/infratests/saga/SagaHappyPathE2ETest.java`
- **Commit:** `3d65e1c`

---

**Total deviations:** 4 auto-fixed (2 missing critical multi-service classpath isolation, 2 test bugs)
**Impact on plan:** All were necessary for correctness. The bean disambiguation pattern is a Phase 5 structural find that must apply to Phase 6+ whenever infra-tests loads additional services. Documented under patterns-established.

## Patterns Established

- **Multi-service classpath in tests:** When infra-tests includes multiple services, all shared bean/entity names must be disambiguated with unique names via `@Entity(name=...)`, `@RestController(beanName)`, `@Component(beanName)`. Apply to any new service module before adding it to infra-tests dependencies.
- **Isolated @SpringBootApplication for E2E tests:** Use a test-config class with `excludeFilters = @ComponentScan.Filter(type=ANNOTATION, classes=SpringBootApplication.class)` to prevent other services' application classes from expanding scan scope.
- **Flyway isolation:** Service migrations in `src/test/resources/db/migration/<schema>/` allow selective loading without conflicting with other services' migrations at the same version numbers.

## QUAL-03 Integration Test Coverage

| Service | Test | Type |
|---------|------|------|
| cart-service | CartItemUpsertRepositoryTest | Testcontainers Postgres |
| cart-service | OrderConfirmedConsumerIdempotencyTest | Testcontainers Postgres |
| order-service | OrderCreationFlowTest | Testcontainers Postgres |
| order-service | SagaConsumerIdempotencyTest | Testcontainers Postgres |
| order-service | OrderControllerPriceDriftMvcTest | Testcontainers Postgres |
| inventory-service | InventoryCompensationTest | Testcontainers Postgres |
| payment-service | StockReservedConsumerIntegrationTest | Testcontainers Postgres |
| infra-tests | SagaHappyPathE2ETest | Testcontainers Postgres + RabbitMQ (real AMQP) |

## Known Stubs

None. payment-service V1 deliberately has no Iyzico integration (D-06 design). The `iyzico_payment_id` column is populated with `"mock-<paymentId>"` in Phase 5 — this is documented and expected, not a stub. Phase 6 replaces `PaymentTransactionalService.persistAndPublish` internals with real Iyzico calls.

## Threat Flags

None beyond what was documented in Phase 5 Plan 03 (X-User-Id trust boundary). payment-service has no public REST endpoints in v1.

## Self-Check: PASSED

All 3 task commits (b0d1cf8, 22114b0, 3d65e1c) verified in git log. Key files verified present.
