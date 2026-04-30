---
phase: 05-cart-order-skeleton
plan: 01
subsystem: infra
tags: [rabbitmq, outbox, archunit, spring-amqp, jpa, gradle]

# Dependency graph
requires:
  - phase: 04-catalog-inventory
    provides: OutboxPoller pattern in inventory-service + identity-service (now lifted to shared module)

provides:
  - common-outbox Gradle library module (OutboxEvent, OutboxRepository base, AbstractOutboxPoller, OutboxMessagePostProcessor)
  - D-09 structural fix: every outbox publisher automatically sets MessageProperties.messageId from envelope JSON
  - D-10 structural fix: ArchUnit gate in infra-tests fails build if @RabbitListener uses Channel (MANUAL ack) or omits Message parameter

affects:
  - 05-02 (cart-service): imports :common-outbox — no outbox in cart-service (consumer only) but gets D-09 type safety
  - 05-03 (order-service): imports :common-outbox; OrderOutboxPoller extends AbstractOutboxPoller
  - 05-04 (payment-service skeleton): imports :common-outbox; PaymentOutboxPoller extends AbstractOutboxPoller
  - All future saga producers in phases 06-08

# Tech tracking
tech-stack:
  added:
    - "common-outbox Gradle java-library module (new)"
    - "archunit-junit5:1.4.2 in infra-tests (new)"
  patterns:
    - "AbstractOutboxPoller: extend + ctor-only concrete subclass pattern for outbox publishers"
    - "OutboxMessagePostProcessor as @Component injected per-publish (not installed globally on RabbitTemplate)"
    - "@EntityScan(\"com.n11\") required on @SpringBootApplication classes when shared JPA entities live in cross-package library modules"

key-files:
  created:
    - common-outbox/build.gradle.kts
    - common-outbox/src/main/java/com/n11/outbox/OutboxEvent.java
    - common-outbox/src/main/java/com/n11/outbox/OutboxRepository.java
    - common-outbox/src/main/java/com/n11/outbox/OutboxMessagePostProcessor.java
    - common-outbox/src/main/java/com/n11/outbox/AbstractOutboxPoller.java
    - common-outbox/src/test/java/com/n11/outbox/OutboxMessagePostProcessorTest.java
    - common-outbox/src/test/java/com/n11/outbox/AbstractOutboxPollerTest.java
    - infra-tests/src/test/java/com/n11/infra/arch/AmqpAckModeArchTest.java
  modified:
    - settings.gradle.kts (added common-outbox to include block)
    - identity-service/build.gradle.kts (added :common-outbox dependency)
    - identity-service/src/main/java/com/n11/identity/IdentityServiceApplication.java (added @EntityScan)
    - identity-service/src/main/java/com/n11/identity/outbox/OutboxPoller.java (shrunk to ctor-only subclass)
    - identity-service/src/main/java/com/n11/identity/outbox/OutboxRepository.java (rebased to extend shared base)
    - identity-service/src/main/java/com/n11/identity/outbox/OutboxBackedUserRegistrationOutboxPublisher.java (import fix)
    - identity-service/src/test/java/com/n11/identity/outbox/OutboxIntegrationTest.java (import fix)
    - inventory-service/build.gradle.kts (added :common-outbox dependency)
    - inventory-service/src/main/java/com/n11/inventory/InventoryServiceApplication.java (added @EntityScan)
    - inventory-service/src/main/java/com/n11/inventory/outbox/OutboxPoller.java (shrunk to ctor-only subclass)
    - inventory-service/src/main/java/com/n11/inventory/outbox/OutboxRepository.java (rebased to extend shared base)
    - inventory-service/src/main/java/com/n11/inventory/messaging/InventoryOrderService.java (import fix)
    - infra-tests/build.gradle.kts (added archunit + service project dependencies)
  deleted:
    - identity-service/src/main/java/com/n11/identity/outbox/OutboxEvent.java (superseded by common-outbox)
    - inventory-service/src/main/java/com/n11/inventory/outbox/OutboxEvent.java (superseded by common-outbox)

key-decisions:
  - "@EntityScan(\"com.n11\") required on both @SpringBootApplication classes: Spring Boot's entity scan only covers the application class's own package hierarchy by default; @SpringBootApplication(scanBasePackages=\"com.n11\") sets component scan but NOT entity scan. Adding @EntityScan(\"com.n11\") is the correct fix."
  - "AmqpAckModeArchTest placed in com.n11.infra.arch package to match the existing infra-tests package structure (CrossSchemaDenyTest is in com.n11.infra), NOT com.n11.infratests.arch as the plan originally suggested."
  - "OutboxMessagePostProcessor test fix: assertThat(Object) overload cast required because MessageProperties.getHeader() returns Object which is ambiguous with IntPredicate and Predicate<T> overloads in AssertJ."

patterns-established:
  - "Shared JPA library entities: use @EntityScan on all consuming @SpringBootApplication classes to widen scan scope"
  - "AbstractOutboxPoller subclass: 3-arg constructor injection only; @Scheduled/@Transactional inherited from abstract base"
  - "Per-service OutboxRepository: extends com.n11.outbox.OutboxRepository and provides schema-qualified @Override of findUnsentBatch; keeps service-specific methods (e.g., findByEventType for tests)"
  - "ArchUnit gates in infra-tests: scan com.n11 package WITHOUT tests (DO_NOT_INCLUDE_TESTS option) so test-only listeners don't trigger false positives"

requirements-completed: [ARCH-07, ARCH-08]

# Metrics
duration: ~30min
completed: 2026-04-30
---

# Phase 5 Plan 01: common-outbox Library + D-09 Producer Fix + D-10 ArchUnit Gate Summary

**common-outbox Gradle library unifies the outbox-poller pattern across identity-service and inventory-service; OutboxMessagePostProcessor structurally prevents the messageId-property-missing regression; ArchUnit gate enforces AUTO-ack @RabbitListener shape for all future Phase 5+ consumers**

## Performance

- **Duration:** ~30 min
- **Started:** 2026-04-30T07:50:00Z
- **Completed:** 2026-04-30T08:01:43Z
- **Tasks:** 3 of 3
- **Files modified:** 21 (8 created, 11 modified, 2 deleted)

## Accomplishments

- Extracted `common-outbox` Gradle library module with `OutboxEvent`, `OutboxRepository` (base), `AbstractOutboxPoller` (scheduled driver), and `OutboxMessagePostProcessor` (@Component; sets messageId + correlationId per publish)
- Migrated identity-service and inventory-service to consume the shared module; their concrete `OutboxPoller` classes are now ctor-only 3-line subclasses — the 999.2 messageId setter regression (commit 06338b1) is now structurally impossible
- Shipped D-10 ArchUnit gate in `infra-tests/`: `AmqpAckModeArchTest` fails the build if any `@RabbitListener` method declares a `com.rabbitmq.client.Channel` parameter or omits a Spring AMQP `Message` parameter; passes today for `OrderCreatedConsumer` canonical shape

## Task Commits

1. **Task 1: Create common-outbox library module** - `0284247` (feat)
2. **Task 2: Migrate identity-service + inventory-service to common-outbox** - `9ce1591` (feat)
3. **Task 3: Add ArchUnit ack-mode gate in infra-tests** - `46b93b3` (feat)

## Files Created/Modified

- `common-outbox/build.gradle.kts` — java-library module; `api(common-events)` re-export; compileOnly JPA/AMQP/Spring-Context/TX
- `common-outbox/src/main/java/com/n11/outbox/OutboxEvent.java` — JPA entity lifted verbatim from inventory-service (package change only, per CD-05)
- `common-outbox/src/main/java/com/n11/outbox/OutboxRepository.java` — base `JpaRepository<OutboxEvent, UUID>` interface with abstract `findUnsentBatch(int)` contract
- `common-outbox/src/main/java/com/n11/outbox/OutboxMessagePostProcessor.java` — D-09 post-processor: reads envelope JSON from message body, sets `messageId` + `correlationId` + `X-Correlation-Id` header
- `common-outbox/src/main/java/com/n11/outbox/AbstractOutboxPoller.java` — `@Scheduled(fixedDelay=5000)` `@Transactional` poll driver; passes `messagePostProcessor` as 4th arg to `convertAndSend`
- `infra-tests/src/test/java/com/n11/infra/arch/AmqpAckModeArchTest.java` — D-10 ArchUnit gate
- Per-service files: OutboxPoller (ctor-only), OutboxRepository (rebased), Application classes (EntityScan added)

## Decisions Made

- `@EntityScan("com.n11")` added to `IdentityServiceApplication` and `InventoryServiceApplication`: `@SpringBootApplication(scanBasePackages="com.n11")` only configures the component scan; JPA entity scanning defaults to the application class's own package hierarchy. Without this, Spring Data JPA throws "Not a managed type: class com.n11.outbox.OutboxEvent". This pattern applies to ALL future services that consume `common-outbox`.
- ArchUnit test placed in `com.n11.infra.arch` (not `com.n11.infratests.arch` as draft plan suggested) to match the existing `com.n11.infra` package convention established by `CrossSchemaDenyTest`.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Added @EntityScan("com.n11") to both service Application classes**
- **Found during:** Task 2 (migrate identity-service + inventory-service)
- **Issue:** After deleting per-service `OutboxEvent.java` and switching imports to `com.n11.outbox.OutboxEvent`, Spring Data JPA could not discover the entity — "Not a managed type: class com.n11.outbox.OutboxEvent". This is because `@SpringBootApplication(scanBasePackages="com.n11")` only covers component scan; JPA entity scanning requires explicit `@EntityScan`.
- **Fix:** Added `@EntityScan("com.n11")` + import to `IdentityServiceApplication.java` and `InventoryServiceApplication.java`.
- **Files modified:** identity-service/.../IdentityServiceApplication.java, inventory-service/.../InventoryServiceApplication.java
- **Verification:** All identity-service and inventory-service tests pass after fix.
- **Committed in:** `9ce1591` (Task 2 commit)

**2. [Rule 1 - Bug] AssertJ type ambiguity for getHeader() Object return in OutboxMessagePostProcessorTest**
- **Found during:** Task 1 (unit test compilation)
- **Issue:** `assertThat(after.getMessageProperties().getHeader("X-Correlation-Id"))` causes "reference to assertThat is ambiguous" — both `assertThat(IntPredicate)` and `assertThat(Predicate<T>)` match when the argument is `Object`.
- **Fix:** Cast to `(Object)` explicitly: `assertThat((Object) after.getMessageProperties().getHeader("X-Correlation-Id"))`.
- **Files modified:** common-outbox/src/test/java/com/n11/outbox/OutboxMessagePostProcessorTest.java
- **Verification:** Test compiles and passes.
- **Committed in:** `0284247` (Task 1 commit, fix was applied before commit)

---

**Total deviations:** 2 auto-fixed (1 missing critical entity scan, 1 compile bug)
**Impact on plan:** Both auto-fixes necessary for correctness. The @EntityScan pattern is a required step for ALL future services that consume common-outbox — documented under patterns-established.

## Issues Encountered

- Testcontainers DB connection-refused errors appear in test logs after DB container shuts down (OutboxPoller @Scheduled fires during test teardown). These are not failures — the build exits 0. Pre-existing behavior from Phase 4.

## User Setup Required

None - no external service configuration required. All changes are build-time and test-time.

## Note for Plans 02-05: How to Add a Per-Service Outbox Publisher

1. **Flyway V2 (or V3):** Create the `outbox` table in the service's schema (same DDL as inventory-service `V2__init_inventory.sql`'s outbox block).
2. **Write `<Service>OutboxRepository extends com.n11.outbox.OutboxRepository`:** Override `findUnsentBatch(@Param("batchSize") int batchSize)` with a native `@Query` that schema-qualifies the `SELECT` (e.g., `FROM orders.outbox WHERE sent_at IS NULL...`).
3. **Write `<Service>OutboxPoller extends AbstractOutboxPoller`:** 3-arg constructor only; wires `<Service>OutboxRepository`, `RabbitTemplate`, `OutboxMessagePostProcessor`.
4. **Add `@EntityScan("com.n11")` to the service's `@SpringBootApplication` class** (see deviation 1 above).
5. **Add `implementation(project(":common-outbox"))` to `build.gradle.kts`.**
6. **Ensure `@EnableScheduling` is on the Application class** (already true for identity/inventory; order-service and payment-service will need it).

## Next Phase Readiness

- Wave 0 complete: `common-outbox` module is on the classpath for all services that depend on it
- Plans 02 (cart-service), 03 (order-service), and 04 (payment-service skeleton) can import `:common-outbox` and extend `AbstractOutboxPoller` directly
- D-09 invariant is uniformly enforced — no future poller can forget `setMessageId`
- D-10 ArchUnit gate is live — any Phase 5+ consumer that reverts to MANUAL ack fails the build immediately

---
*Phase: 05-cart-order-skeleton*
*Completed: 2026-04-30*
