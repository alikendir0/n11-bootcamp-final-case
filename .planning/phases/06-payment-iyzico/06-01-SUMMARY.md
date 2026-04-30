---
phase: 06-payment-iyzico
plan: 01
subsystem: payments
tags: [iyzico, checkout-form, spring-configuration-properties, postgres, flyway, tdd]

requires:
  - phase: 05-cart-order-skeleton
    provides: payment-service skeleton, stock.reserved consumer, payment saga/outbox baseline
provides:
  - Env-backed Iyzico Checkout Form configuration with sandbox SDK dependency
  - Durable payment checkout lifecycle columns and repository lookup methods
  - Neutral Iyzico checkout adapter contract for initialize/retrieve flows
affects: [06-payment-iyzico, payment-service, order-saga, frontend-checkout, agent-toolset]

tech-stack:
  added: [com.iyzipay:iyzipay-java:2.0.141]
  patterns: [env-backed external SDK config, neutral adapter contract, checkout lifecycle persistence]

key-files:
  created:
    - payment-service/src/main/java/com/n11/payment/iyzico/IyzicoProperties.java
    - payment-service/src/main/java/com/n11/payment/iyzico/IyzicoConfig.java
    - payment-service/src/main/java/com/n11/payment/iyzico/IyzicoCheckoutClient.java
    - payment-service/src/main/java/com/n11/payment/iyzico/IyzicoCheckoutResult.java
    - payment-service/src/main/resources/db/migration/V3__iyzico_checkout_fields.sql
    - payment-service/src/test/java/com/n11/payment/iyzico/IyzicoPropertiesTest.java
    - payment-service/src/test/java/com/n11/payment/iyzico/IyzicoCheckoutClientContractTest.java
    - payment-service/src/test/java/com/n11/payment/payment/PaymentRepositoryIntegrationTest.java
  modified:
    - payment-service/build.gradle.kts
    - config-server/src/main/resources/config/payment-service.yml
    - payment-service/src/main/java/com/n11/payment/payment/Payment.java
    - payment-service/src/main/java/com/n11/payment/payment/PaymentStatus.java
    - payment-service/src/main/java/com/n11/payment/payment/PaymentRepository.java

key-decisions:
  - "Use PUBLIC_BASE_URL as the single source for the Iyzico callback URL and normalize trailing slashes in code."
  - "Expose only neutral paymentPageUrl/token/status/paymentId fields through the checkout contract; SDK embedded-form HTML remains behind the future adapter."

patterns-established:
  - "Iyzico SDK integration starts at an infrastructure boundary; domain/application code consumes neutral records."
  - "Payment lifecycle state is persisted before callback plans: PENDING_INITIALIZATION, PENDING, COMPLETED, FAILED, TIMED_OUT."

requirements-completed: [PAY-01, PAY-04, PAY-05]

duration: 8min
completed: 2026-04-30
---

# Phase 06 Plan 01: Iyzico Checkout Foundation Summary

**Iyzico Checkout Form foundation with env-backed sandbox config, durable payment link/token state, and a neutral adapter contract.**

## Performance

- **Duration:** 8 min
- **Started:** 2026-04-30T11:35:27Z
- **Completed:** 2026-04-30T11:43:17Z
- **Tasks:** 3
- **Files modified:** 15 (including tests and deferred-items log)

## Accomplishments

- Added `com.iyzipay:iyzipay-java:2.0.141`, `IyzicoProperties`, and `IyzicoConfig` with no literal secret defaults and callback derivation from `PUBLIC_BASE_URL`.
- Added `V3__iyzico_checkout_fields.sql` plus entity/repository lifecycle support for checkout token, hosted payment URL, terminal timestamps, failures, and timeout scanning.
- Defined `IyzicoCheckoutClient` and neutral command/result records so later plans can implement SDK mapping without leaking Iyzico SDK or embedded-form details.

## Task Commits

Each TDD task was committed atomically:

1. **task 1: add Iyzico SDK dependency and env-backed properties**
   - `f172b68` test: failing Iyzico properties tests
   - `17deeb6` feat: Iyzico SDK/config/properties implementation
2. **task 2: extend payment persistence for checkout state**
   - `b3fd6aa` test: failing payment checkout persistence tests
   - `fe7873e` feat: payment checkout migration/entity/repository implementation
3. **task 3: define narrow Checkout Form adapter contract**
   - `83e340e` test: failing checkout contract tests
   - `886be9b` feat: neutral checkout adapter contract
   - `36666c3` fix: remove embedded-form field name from public contract docs

**Plan metadata:** `d184a40` (docs: complete plan)

## Files Created/Modified

- `payment-service/build.gradle.kts` — adds Iyzico Java SDK dependency.
- `config-server/src/main/resources/config/payment-service.yml` — replaces mock payment delay config with env-backed Iyzico sandbox settings.
- `payment-service/src/main/java/com/n11/payment/iyzico/IyzicoProperties.java` — typed callback/base-url/credential/timeout settings.
- `payment-service/src/main/java/com/n11/payment/iyzico/IyzicoConfig.java` — creates SDK `Options` from typed properties.
- `payment-service/src/main/resources/db/migration/V3__iyzico_checkout_fields.sql` — checkout token/link/failure/timestamp columns plus lifecycle status check and indexes.
- `payment-service/src/main/java/com/n11/payment/payment/Payment.java` — checkout fields and domain transition methods.
- `payment-service/src/main/java/com/n11/payment/payment/PaymentStatus.java` — full checkout lifecycle enum.
- `payment-service/src/main/java/com/n11/payment/payment/PaymentRepository.java` — order, token, and expired-pending lookups.
- `payment-service/src/main/java/com/n11/payment/iyzico/IyzicoCheckoutClient.java` — narrow initialize/retrieve boundary.
- `payment-service/src/main/java/com/n11/payment/iyzico/IyzicoCheckoutResult.java` — neutral command/result records.
- `payment-service/src/test/java/com/n11/payment/iyzico/IyzicoPropertiesTest.java` — callback URL and secret-default tests.
- `payment-service/src/test/java/com/n11/payment/payment/PaymentRepositoryIntegrationTest.java` — checkout persistence repository tests.
- `payment-service/src/test/java/com/n11/payment/iyzico/IyzicoCheckoutClientContractTest.java` — contract/leakage guard tests.
- `.planning/phases/06-payment-iyzico/deferred-items.md` — out-of-scope test infrastructure issue discovered during broad verification.

## Decisions Made

- `PUBLIC_BASE_URL` remains the only callback base; `callbackUrl()` strips trailing slashes and appends `/api/v1/payments/iyzico/callback` exactly.
- The adapter contract returns `paymentPageUrl` and provider result fields only; embedded form content is intentionally not surfaced in main payment-service code.
- `PENDING_INITIALIZATION` is the pre-Iyzico row state, while `PENDING` means a reusable hosted checkout token/link exists.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed checked reflection exception in checkout contract test**
- **Found during:** task 3 (define narrow Checkout Form adapter contract)
- **Issue:** The RED test used `Class.getMethod(...)` without declaring `NoSuchMethodException`, causing compilation to fail even after the planned implementation existed.
- **Fix:** Added `throws NoSuchMethodException` to the test method.
- **Files modified:** `payment-service/src/test/java/com/n11/payment/iyzico/IyzicoCheckoutClientContractTest.java`
- **Verification:** `./gradlew :payment-service:compileJava && ./gradlew :payment-service:test --tests '*IyzicoCheckoutClientContractTest'`
- **Committed in:** `886be9b`

**2. [Rule 1 - Bug] Removed embedded-form field literal from public contract docs**
- **Found during:** overall verification grep for `checkoutFormContent`
- **Issue:** The public interface Javadoc named the SDK embedded-form field directly, which weakened the plan acceptance check that public payment-service contracts not contain that field.
- **Fix:** Reworded the Javadoc to describe SDK-only embedded-form HTML without the field literal.
- **Files modified:** `payment-service/src/main/java/com/n11/payment/iyzico/IyzicoCheckoutClient.java`
- **Verification:** grep scan of `payment-service/src/main/java` found no `checkoutFormContent`; `./gradlew :payment-service:compileJava` passed.
- **Committed in:** `36666c3`

---

**Total deviations:** 2 auto-fixed (2 Rule 1 bugs)
**Impact on plan:** Both fixes tightened task verification and contract compliance; no scope expansion.

## Issues Encountered

- Full `./gradlew :payment-service:test` exposed an out-of-scope AMQP test infrastructure issue in the pre-existing `StockReservedConsumerIntegrationTest`: the Spring context attempts RabbitMQ startup and fails with `AmqpAuthenticationException` when no RabbitMQ test container/credentials exist. Logged as `D-06-01` in `deferred-items.md`. Plan-specific tests and compile verification pass.

## Verification

- `./gradlew :payment-service:test --tests '*IyzicoPropertiesTest'` — passed.
- `./gradlew :payment-service:test --tests '*PaymentRepository*'` — passed.
- `./gradlew :payment-service:test --tests '*IyzicoCheckoutClientContractTest'` — passed.
- `./gradlew :payment-service:test --tests '*IyzicoPropertiesTest' --tests '*PaymentRepository*' --tests '*IyzicoCheckoutClientContractTest'` — passed.
- `./gradlew :payment-service:compileJava` — passed.
- `checkoutFormContent` scan of `payment-service/src/main/java` — no matches.

## TDD Gate Compliance

- RED test commits exist before GREEN implementation commits for all three tasks.
- GREEN implementation commits exist after each RED gate.
- No separate refactor commits were required.

## Known Stubs

None. Null checks/defaults in `IyzicoProperties` and existing saga code are defensive logic, not UI/data stubs.

## User Setup Required

External Iyzico sandbox values must be provided before live checkout plans can run:
- `IYZICO_API_KEY`
- `IYZICO_SECRET_KEY`
- `PUBLIC_BASE_URL`
- optional `PAYMENT_TIMEOUT_MINUTES`, `IYZICO_DEMO_BUYER_IDENTITY_NUMBER`

## Next Phase Readiness

- Plan 06-02 can build the order payment-context endpoint and payment-service client against the durable checkout schema.
- Plan 06-03 can implement the concrete Iyzico adapter and stock-reserved initialization flow against `IyzicoCheckoutClient`.
- Deferred AMQP test infrastructure item `D-06-01` should be considered before relying on the full payment-service test task as a phase gate.

## Self-Check: PASSED

- Required created files exist: `IyzicoProperties.java`, `IyzicoConfig.java`, `IyzicoCheckoutClient.java`, `IyzicoCheckoutResult.java`, `V3__iyzico_checkout_fields.sql`, and this summary.
- Task/TDD commits found in git history: `f172b68`, `17deeb6`, `b3fd6aa`, `fe7873e`, `83e340e`, `886be9b`, `36666c3`.

---
*Phase: 06-payment-iyzico*
*Completed: 2026-04-30*
