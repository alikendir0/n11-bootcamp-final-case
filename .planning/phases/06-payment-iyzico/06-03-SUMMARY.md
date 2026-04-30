---
phase: 06-payment-iyzico
plan: 03
subsystem: payments
tags: [iyzico, checkout-form, saga, rabbitmq, postgres, tdd]

requires:
  - phase: 06-payment-iyzico
    provides: Iyzico SDK configuration, checkout lifecycle columns, and internal order payment context endpoint
provides:
  - Concrete Iyzico Checkout Form initialize adapter with SDK request mapping and signature validation
  - stock.reserved-driven checkout initialization that persists one reusable PENDING payment link
  - Redelivery/idempotency tests proving no duplicate checkout sessions and no premature payment.completed event
affects: [06-payment-iyzico, payment-service, order-service, frontend-checkout, agent-toolset]

tech-stack:
  added: []
  patterns:
    - External SDK call outside transaction with transactional pending-link persistence
    - Active PENDING checkout reuse before SDK initialization to prevent duplicate sessions
    - Internal payment context carries order createdAt for Iyzico buyer sample parity

key-files:
  created:
    - payment-service/src/main/java/com/n11/payment/iyzico/DefaultIyzicoCheckoutClient.java
    - payment-service/src/test/java/com/n11/payment/iyzico/IyzicoCheckoutRequestMapperTest.java
  modified:
    - payment-service/src/main/java/com/n11/payment/messaging/PaymentSagaService.java
    - payment-service/src/main/java/com/n11/payment/messaging/PaymentTransactionalService.java
    - payment-service/src/main/java/com/n11/payment/payment/PaymentRepository.java
    - payment-service/src/main/java/com/n11/payment/order/OrderPaymentContext.java
    - order-service/src/main/java/com/n11/order/order/dto/PaymentContextDto.java
    - order-service/src/main/java/com/n11/order/order/PaymentContextService.java
    - payment-service/src/test/java/com/n11/payment/messaging/StockReservedConsumerIntegrationTest.java

key-decisions:
  - "Checkout initialization is driven only by stock.reserved; clients will fetch the persisted paymentPageUrl later instead of creating sessions directly."
  - "A second stock.reserved event for an order with an active PENDING payment records the event as processed and reuses the existing checkout without another SDK call."
  - "The internal order payment context includes createdAt so Iyzico buyer registrationDate/lastLoginDate are deterministic and sourced from the order snapshot."

patterns-established:
  - "Iyzico mapper normalizes every basket price to scale 2 HALF_UP and fails fast with BASKET_TOTAL_MISMATCH before hitting the SDK."
  - "Payment saga external calls stay outside @Transactional; PaymentTransactionalService owns processed_events plus payment row writes."

requirements-completed: [PAY-01, PAY-02, PAY-03]

duration: 11min
completed: 2026-04-30
---

# Phase 06 Plan 03: Iyzico Checkout Initialization Summary

**stock.reserved now creates one signed, reusable Iyzico Checkout Form PENDING payment link without publishing payment.completed before callback**

## Performance

- **Duration:** 11 min
- **Started:** 2026-04-30T12:04:01Z
- **Completed:** 2026-04-30T12:15:00Z
- **Tasks:** 2
- **Files modified:** 13

## Accomplishments

- Added `DefaultIyzicoCheckoutClient` using `CheckoutFormInitialize.create(request, options)` with `Locale.TR`, `Currency.TRY`, `PaymentGroup.PRODUCT`, enabled installments `2,3,6,9`, debit-card support, signature verification, and success-response validation.
- Enforced the Iyzico 5062 basket invariant by deriving `request.price` from scaled basket item prices and rejecting mismatched order totals with `BASKET_TOTAL_MISMATCH` before the SDK call.
- Replaced the Phase 5 mock payment completion path with stock-reserved checkout initialization that persists `PaymentStatus.PENDING`, `iyzicoToken`, `paymentPageUrl`, and `expiresAt`.
- Updated stock-reserved integration coverage so same-event redelivery and same-order repeated events call Iyzico initialize once, create one payment row, record processed events, and write zero `payment.completed` outbox rows.

## Task Commits

Each TDD task was committed atomically:

1. **task 1 RED:** `7099bc4` (`test`) add failing Iyzico checkout mapper tests.
2. **task 1 GREEN:** `9a19f16` (`feat`) implement Checkout Form initialize mapper.
3. **task 2 RED:** `0976a56` (`test`) add failing pending checkout saga tests.
4. **task 2 GREEN:** `8907202` (`feat`) initialize pending checkout from stock reservation.
5. **task 2 REFACTOR:** `7f2e913` (`refactor`) clean up stale mock completion comments and dependencies.

**Plan metadata:** pending final docs commit.

## Files Created/Modified

- `payment-service/src/main/java/com/n11/payment/iyzico/DefaultIyzicoCheckoutClient.java` — concrete Checkout Form initialize adapter and SDK request mapper.
- `payment-service/src/test/java/com/n11/payment/iyzico/IyzicoCheckoutRequestMapperTest.java` — request mapping, signature validation, basket total, and buyer-field tests.
- `payment-service/src/main/java/com/n11/payment/iyzico/IyzicoCheckoutResult.java` — checkout initialization command now carries `createdAt` for Iyzico buyer date fields.
- `payment-service/src/main/java/com/n11/payment/messaging/PaymentSagaService.java` — fetches order context, builds neutral checkout command, calls Iyzico, and delegates persistence.
- `payment-service/src/main/java/com/n11/payment/messaging/PaymentTransactionalService.java` — records processed events, reuses active PENDING checkout rows, and persists new pending checkout links.
- `payment-service/src/main/java/com/n11/payment/payment/PaymentRepository.java` — active PENDING lookup by order.
- `payment-service/src/main/java/com/n11/payment/order/OrderPaymentContext.java` — includes order `createdAt` from the internal context endpoint.
- `order-service/src/main/java/com/n11/order/order/dto/PaymentContextDto.java` and `PaymentContextService.java` — expose order `createdAt` on the internal payment context snapshot.
- `payment-service/src/test/java/com/n11/payment/messaging/StockReservedConsumerIntegrationTest.java` — proves pending checkout persistence and duplicate-session prevention.
- `payment-service/src/test/java/com/n11/payment/order/OrderPaymentContextClientTest.java` and `order-service/src/test/java/com/n11/order/order/PaymentContextControllerTest.java` — updated context contract coverage for `createdAt`.

## Decisions Made

- Kept Checkout Form initialization automatic after `stock.reserved` (D-05); there is no client-side checkout creation path in this plan.
- Reused active `PENDING` checkout rows before calling Iyzico, making duplicate stock events safe without creating duplicate hosted sessions.
- Added `createdAt` to the internal order payment context instead of inventing current timestamps in payment-service; this keeps Iyzico buyer registration dates deterministic and order-sourced.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Added order createdAt to payment context**
- **Found during:** task 2 (initialize checkout from stock.reserved)
- **Issue:** The plan required Iyzico buyer `registrationDate` and `lastLoginDate` to use `order.createdAt`, but the prior Plan 02 payment context contract did not expose that field.
- **Fix:** Added `createdAt` to order-service `PaymentContextDto`, payment-service `OrderPaymentContext`, and both boundary tests.
- **Files modified:** `order-service/src/main/java/com/n11/order/order/dto/PaymentContextDto.java`, `order-service/src/main/java/com/n11/order/order/PaymentContextService.java`, `payment-service/src/main/java/com/n11/payment/order/OrderPaymentContext.java`, related tests.
- **Verification:** `./gradlew :order-service:test --tests '*PaymentContextControllerTest'` and payment-service verification passed.
- **Committed in:** `8907202`

**2. [Rule 3 - Blocking] Added RabbitMQ Testcontainer wiring to stock-reserved integration test**
- **Found during:** task 2 verification
- **Issue:** The existing Spring context attempted RabbitMQ listener startup and failed authentication without a broker, matching the previously deferred Phase 6 test infrastructure gap.
- **Fix:** Added a `rabbitmq:3.13-management` Testcontainer and dynamic RabbitMQ properties while keeping direct consumer invocation for business assertions.
- **Files modified:** `payment-service/src/test/java/com/n11/payment/messaging/StockReservedConsumerIntegrationTest.java`
- **Verification:** `./gradlew :payment-service:test --tests '*StockReservedConsumerIntegrationTest'` passed.
- **Committed in:** `8907202`

---

**Total deviations:** 2 auto-fixed (1 Rule 2 missing critical field, 1 Rule 3 blocking test infrastructure fix)
**Impact on plan:** Both fixes were required for correctness and verification; no user-facing scope expansion.

## Issues Encountered

- Mapper test fixture initially used two half-up-rounded line prices that summed to `10.01` while the valid fixture total was `10.00`; the fixture was corrected during the GREEN implementation commit.
- `@MockBean` is deprecated in Spring Boot 3.5 test output, but it remains functional and was not in scope to replace across existing tests.

## Verification

- `./gradlew :payment-service:test --tests '*IyzicoCheckoutRequestMapperTest'` — passed.
- `./gradlew :payment-service:test --tests '*StockReservedConsumerIntegrationTest'` — passed.
- `./gradlew :payment-service:test --tests '*IyzicoCheckoutRequestMapperTest' --tests '*StockReservedConsumerIntegrationTest' --tests '*OrderPaymentContextClientTest'` — passed.
- `./gradlew :order-service:test --tests '*PaymentContextControllerTest'` — passed.
- Final required gate: `./gradlew :payment-service:test --tests '*IyzicoCheckoutRequestMapperTest' --tests '*StockReservedConsumerIntegrationTest'` — passed.

## TDD Gate Compliance

- RED commits present: `7099bc4`, `0976a56`.
- GREEN commits present after RED: `9a19f16`, `8907202`.
- REFACTOR commit present after GREEN: `7f2e913`.

## Known Stubs

None. Stub scan found only defensive null/default handling and config placeholder references, not UI/data stubs.

## Threat Flags

None. The new Iyzico SDK boundary and RabbitMQ stock-reserved trigger were already covered by the plan threat model (T-06-06 through T-06-08b).

## User Setup Required

No new setup beyond Plan 06-01 Iyzico sandbox values:
- `IYZICO_API_KEY`
- `IYZICO_SECRET_KEY`
- `PUBLIC_BASE_URL`
- optional `PAYMENT_TIMEOUT_MINUTES`, `IYZICO_DEMO_BUYER_IDENTITY_NUMBER`

## Next Phase Readiness

- Plan 06-04 can implement callback retrieval/finalization against persisted `iyzicoToken` and `paymentPageUrl` rows.
- `payment.completed` is intentionally no longer emitted during initialization; callback success should become the only completion producer.

## Self-Check: PASSED

- Required files exist: `DefaultIyzicoCheckoutClient.java`, `IyzicoCheckoutRequestMapperTest.java`, updated saga services, and this summary.
- Task/TDD commits found in git history: `7099bc4`, `9a19f16`, `0976a56`, `8907202`, `7f2e913`.

---
*Phase: 06-payment-iyzico*
*Completed: 2026-04-30*
