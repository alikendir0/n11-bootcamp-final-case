---
phase: 06-payment-iyzico
plan: 05
subsystem: payments
tags: [iyzico, scheduled-job, saga, compensation, rabbitmq, postgres, testcontainers, awaitility, tdd]

# Dependency graph
requires:
  - phase: 06-payment-iyzico
    provides: PaymentTransactionalService.timeoutFromScheduler (PENDING-only guard, payment.failed outbox), PaymentRepository.findExpiredPendingPayments, payment row expires_at column
  - phase: 05-cart-order-skeleton
    provides: inventory-service PaymentFailedConsumer (CD-08), order-service PaymentFailedConsumer + OrderSagaService.processPaymentFailed, infra-tests Testcontainers + Awaitility scaffold
provides:
  - Scheduled timeout sweep (PaymentTimeoutJob) emitting payment.failed on expired PENDING checkouts (PAY-06)
  - Cross-service compensation E2E proof: payment.failed releases inventory stock and cancels orders (QUAL-05)
  - Idempotent timeout-vs-callback race protection (T-06-13: status guard PENDING-only, first writer wins)
  - infra-tests order-side multi-service classpath isolation (OrderPaymentFailureTestConfig)
affects: [06-payment-iyzico, agent-toolset, mcp-server, frontend-checkout, notification-service]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Scheduled saga compensation: sweep expired rows + delegate to status-guarded transactional method"
    - "Per-row try/catch in scheduled sweep so one bad payment never breaks the whole tick"
    - "Cross-service E2E with separate Spring contexts when both services own a `processed_events` inbox table"
    - "Schema-aligned timeout shape: reason=TIMEOUT, errorCode=PAYMENT_TIMEOUT — same downstream compensation as Iyzico decline"

key-files:
  created:
    - payment-service/src/main/java/com/n11/payment/payment/PaymentTimeoutJob.java
    - payment-service/src/test/java/com/n11/payment/payment/PaymentTimeoutJobTest.java
    - infra-tests/src/test/java/com/n11/infratests/saga/PaymentFailureCompensationE2ETest.java
    - infra-tests/src/test/java/com/n11/infratests/saga/InventoryCompensationTestConfig.java
    - infra-tests/src/test/java/com/n11/infratests/saga/OrderPaymentFailureCompensationE2ETest.java
    - infra-tests/src/test/java/com/n11/infratests/saga/OrderPaymentFailureTestConfig.java
    - infra-tests/src/test/resources/db/migration/payment/V3__iyzico_checkout_fields.sql
    - infra-tests/src/test/resources/db/migration/inventory/V1__init_processed_events.sql
    - infra-tests/src/test/resources/db/migration/inventory/V2__init_inventory.sql
    - infra-tests/src/test/resources/db/migration/orders/V1__init_processed_events.sql
    - infra-tests/src/test/resources/db/migration/orders/V2__init_orders.sql
  modified:
    - infra-tests/build.gradle.kts
    - .planning/phases/06-payment-iyzico/deferred-items.md

key-decisions:
  - "Timeout sweep cadence is configurable via payment.timeout.scan-delay-ms (default 60000 ms): coarse enough to avoid DB load, fine enough to release stock within ~1 min of expiration."
  - "PaymentTimeoutJob iterates expired rows with per-row try/catch so a single bad row cannot break the whole sweep tick."
  - "Order-side and inventory-side compensation E2Es run as separate Spring contexts (OrderPaymentFailureTestConfig + InventoryCompensationTestConfig). A single boot would collide on processed_events.event_id when both consumer beans tried to record the same envelope."
  - "Timeout envelope reuses payment.failed shape (reason=TIMEOUT, errorCode=PAYMENT_TIMEOUT) so order-service.OrderSagaService.processPaymentFailed treats Iyzico decline and timeout identically — verified by an OrderPaymentFailureCompensationE2ETest case that publishes the timeout envelope shape."
  - "For Plan 06-05 Task 2 the production code (PaymentFailedConsumer in inventory + order, Phase 5 CD-08 wiring) pre-existed; this plan adds the cross-service AMQP delivery proof only — committed as `test(06-05)` with no separate `feat` commit because no production change was required."

patterns-established:
  - "Scheduled compensation sweep: @Scheduled(fixedDelayString) → repository query for expired PENDING rows → delegate to @Transactional terminal-transition method (status-guarded). Pattern usable by any future timeout-driven saga compensation."
  - "Multi-service classpath isolation in infra-tests: each service that participates in an E2E gets its own *TestConfig with @SpringBootApplication + ComponentScan(excludeFilters = @SpringBootApplication) + EntityScan/EnableJpaRepositories scoped to its package. Required because multiple services on the test classpath each carry @SpringBootApplication(scanBasePackages = com.n11) which would otherwise expand entity scope to the entire codebase."
  - "Flyway test isolation: each schema gets its own classpath subdirectory under infra-tests/src/test/resources/db/migration/<schema>/ to avoid V1+V2 collision across services that all declare those versions in their own JARs."

requirements-completed: [PAY-03, PAY-06, QUAL-05]

# Metrics
duration: ~1h 10min
completed: 2026-04-30
---

# Phase 06 Plan 05: Payment Timeout Compensation & Failure E2E Summary

**Scheduled PaymentTimeoutJob emits payment.failed on expired PENDING checkouts; cross-service compensation E2Es prove inventory release and order cancellation through real RabbitMQ delivery, with the timeout envelope reusing the Iyzico-decline shape so downstream consumers do not branch on sub-cause.**

## Performance

- **Duration:** ~1h 10min (across two worktree sessions)
- **Started:** 2026-04-30T12:39:00Z (Task 1 RED commit `8aca646`)
- **Completed:** 2026-04-30T13:21:00Z (Task 2 final test commit `03664b5`)
- **Tasks:** 2 (both with TDD discipline)
- **Files modified:** 13 (12 created + 1 modified — `infra-tests/build.gradle.kts`)

## Accomplishments

- **PaymentTimeoutJob** (`@Scheduled`, configurable cadence via `payment.timeout.scan-delay-ms`, default 60s) sweeps `PaymentRepository.findExpiredPendingPayments(now)` and delegates each expired row to `PaymentTransactionalService.timeoutFromScheduler(paymentId)`. The transactional method is status-guarded (`PENDING`-only) so a callback racing with the timeout sweep cannot double-emit (T-06-13 first-writer-wins).
- **PaymentTimeoutJobTest** (`@SpringBootTest` + Testcontainers Postgres + RabbitMQ): three assertions — expired PENDING → TIMED_OUT with one `payment.failed` outbox row carrying `reason=TIMEOUT, errorCode=PAYMENT_TIMEOUT`; unexpired PENDING is untouched; already-terminal rows (COMPLETED/FAILED/TIMED_OUT) are filtered out by the repository query and never re-emit.
- **PaymentFailureCompensationE2ETest** (inventory side) — boots inventory-service in isolation, publishes a synthetic `payment.failed` envelope to `payments.tx`, awaits real AMQP delivery to `PaymentFailedConsumer`, asserts `stock_reservations.status = RELEASED` and the same envelope replayed leaves only one release (idempotency invariant).
- **OrderPaymentFailureCompensationE2ETest** (order side) — boots order-service in isolation, publishes `payment.failed`, awaits delivery to order-service `PaymentFailedConsumer`, asserts `orders.status = CANCELLED` with `cancel_reason = PAYMENT_DECLINED`. A second test case publishes the timeout envelope shape (`reason=TIMEOUT, errorCode=PAYMENT_TIMEOUT`) and asserts the same outcome — proof that order-side compensation is identical for Iyzico decline and timeout sweep (QUAL-05).
- Order-side test infrastructure: new `OrderPaymentFailureTestConfig` (mirrors `InventoryCompensationTestConfig` pattern), new `infra-tests/src/test/resources/db/migration/orders/` Flyway subdirectory, and `infra-tests/build.gradle.kts` adds `:order-service` test dependency.
- Migration mirrors: payment V3 (Iyzico checkout fields), inventory V1+V2, orders V1+V2 — all copied into the infra-tests test resources tree so each E2E context can boot its own schema-specific Flyway target without colliding on the V1+V2 base path that every service declares in its own JAR.

## Task Commits

Each task was committed with TDD discipline:

1. **Task 1: scheduled payment timeout job**
   - `8aca646` (`test`) add failing PaymentTimeoutJob test
   - `e1084b7` (`feat`) implement scheduled payment timeout sweep
2. **Task 2: payment failure compensation E2E**
   - `771a9d3` (`test`) add payment failure compensation E2E test (inventory side)
   - `03664b5` (`test`) add payment failure compensation E2E test (order side)

**Plan metadata:** pending final docs commit (this SUMMARY).

## Files Created/Modified

- `payment-service/src/main/java/com/n11/payment/payment/PaymentTimeoutJob.java` — `@Scheduled` sweep + per-row try/catch + delegate to `timeoutFromScheduler`.
- `payment-service/src/test/java/com/n11/payment/payment/PaymentTimeoutJobTest.java` — `@SpringBootTest` + Testcontainers covering expired/unexpired/terminal-row paths.
- `infra-tests/src/test/java/com/n11/infratests/saga/PaymentFailureCompensationE2ETest.java` — inventory-side compensation proof (Tests 1+2: release-on-payment-failed, idempotent redelivery).
- `infra-tests/src/test/java/com/n11/infratests/saga/InventoryCompensationTestConfig.java` — minimal `@SpringBootApplication` for inventory-side E2E (Phase 5 multi-service classpath isolation pattern).
- `infra-tests/src/test/java/com/n11/infratests/saga/OrderPaymentFailureCompensationE2ETest.java` — order-side compensation proof (Test 3: cancel-on-payment-failed, plus a timeout-envelope variant proving QUAL-05 same downstream path).
- `infra-tests/src/test/java/com/n11/infratests/saga/OrderPaymentFailureTestConfig.java` — minimal `@SpringBootApplication` for order-side E2E.
- `infra-tests/src/test/resources/db/migration/payment/V3__iyzico_checkout_fields.sql` — mirror of payment-service V3 so SagaHappyPathE2ETest boots against the Iyzico-aware payments schema.
- `infra-tests/src/test/resources/db/migration/inventory/V1+V2.sql` — mirrors of inventory-service V1+V2 for `PaymentFailureCompensationE2ETest`.
- `infra-tests/src/test/resources/db/migration/orders/V1+V2.sql` — mirrors of order-service V1+V2 for `OrderPaymentFailureCompensationE2ETest`.
- `infra-tests/build.gradle.kts` — adds `testImplementation(project(":order-service"))` so order-service classes resolve on the test classpath.
- `.planning/phases/06-payment-iyzico/deferred-items.md` — logs new deferred item D-06-02 (pre-existing SagaHappyPathE2ETest regression unrelated to Plan 06-05).

## Decisions Made

- **Configurable timeout cadence.** `payment.timeout.scan-delay-ms` (default 60000) was already in `config-server/src/main/resources/config/payment-service.yml` from earlier plan groundwork; PaymentTimeoutJob reads it via `@Scheduled(fixedDelayString = "${payment.timeout.scan-delay-ms:60000}")`. Default chosen as the natural balance between DB load and stock-release latency for an abandoned hosted form.
- **Per-row try/catch in the sweep.** A scheduled sweep that throws halts the whole tick. The job logs each failure and continues so a single corrupt row cannot block stock release for unrelated orders.
- **Separate Spring contexts for inventory vs order compensation E2Es.** Both services maintain their own `processed_events` table; co-booting them in one context would force a single consumer to record the envelope and the other to fail on the duplicate primary key. Splitting into two test classes mirrors the production reality (each service has its own DB user + schema).
- **Timeout envelope reuses Iyzico-decline shape.** Order-side compensation does not branch on `errorCode` — `OrderSagaService.processPaymentFailed` cancels regardless of `reason` value. The schema-aligned `reason=TIMEOUT` slots into the saga schema enum, while the granular `errorCode=PAYMENT_TIMEOUT` lets notification-service / observability tooling distinguish the two surfaces if/when needed.
- **Task 2 framing as test-only.** The production compensation code (PaymentFailedConsumer in inventory + order, OrderSagaService.processPaymentFailed) was wired in Phase 5 CD-08/CD-09. Plan 06-05 Task 2 adds the cross-service AMQP delivery proof on top of pre-existing impl. The TDD gate is satisfied by two `test(06-05)` commits — no separate `feat` commit is needed because no production code was changed for Task 2.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Added order-side compensation E2E + order-service test classpath wiring**
- **Found during:** Task 2 plan-vs-state reconciliation (the existing untracked `PaymentFailureCompensationE2ETest` covered Tests 1+2 from the plan, but Test 3 — order-service consumes `payment.failed` and order moves to CANCELLED — had no test artifact yet, despite being a stated plan behavior).
- **Issue:** Plan 06-05 Task 2 lists three behaviors (Tests 1, 2, 3). Behaviors 1 and 2 (inventory release + idempotency) were covered by `PaymentFailureCompensationE2ETest`. Behavior 3 (order CANCELLED) was missing — its absence would silently leave QUAL-05's order-side half unproven.
- **Fix:** Added `OrderPaymentFailureCompensationE2ETest` as a sibling test class (same pattern as inventory side — separate Spring context to avoid `processed_events.event_id` PK collision when both consumer beans record the same envelope). Added `OrderPaymentFailureTestConfig` with the standard `excludeFilters` against `@SpringBootApplication`. Added orders V1+V2 migration mirrors. Added `:order-service` to `infra-tests/build.gradle.kts` so `OrderRepository`/`OrderStatus`/etc. resolve on the test classpath.
- **Files modified:** `infra-tests/src/test/java/com/n11/infratests/saga/OrderPaymentFailureCompensationE2ETest.java`, `OrderPaymentFailureTestConfig.java`, `infra-tests/src/test/resources/db/migration/orders/V1__init_processed_events.sql`, `V2__init_orders.sql`, `infra-tests/build.gradle.kts`.
- **Verification:** `./gradlew :infra-tests:test --tests '*OrderPaymentFailureCompensationE2ETest'` — passes (3 tests: paymentFailed→CANCELLED, timeout-envelope→CANCELLED with same reason, duplicate→idempotent).
- **Committed in:** `03664b5` (Task 2 order-side test commit).

---

**Total deviations:** 1 auto-fixed (Rule 2 — missing critical test coverage for plan behavior 3).
**Impact on plan:** No scope creep. The deviation completes coverage of a behavior the plan already specified; without it, QUAL-05's order-side compensation half would be unverified. The new test pattern (separate Spring contexts per service for cross-service compensation E2Es) is documented under Patterns Established for any future plan that needs to assert cross-service saga side effects.

## Issues Encountered

- **Pre-existing SagaHappyPathE2ETest regression (out of scope).** Running the full `:infra-tests:test` task surfaces a `BeanInstantiationException: DefaultIyzicoCheckoutClient — No default constructor found` when SagaHappyPathE2ETest boots payment-service. Root cause: Plans 06-03/06-04 added `DefaultIyzicoCheckoutClient` as a `@Component` with a non-default constructor; SagaHappyPathE2ETest's `PaymentServiceTestConfig` does not provide the required `IyzicoProperties`/`Options`/initializer/retriever beans. This is unrelated to Plan 06-05's scope (timeout + compensation E2E). Logged as deferred item D-06-02 in `.planning/phases/06-payment-iyzico/deferred-items.md` for a future plan to either inject `@MockBean IyzicoCheckoutClient` into SagaHappyPathE2ETest or restructure the saga seam so payment.completed publishing does not require the SDK client beans.

## Verification

- `./gradlew :payment-service:test --tests '*PaymentTimeoutJobTest'` — passes (3 tests).
- `./gradlew :infra-tests:test --tests '*PaymentFailureCompensationE2ETest'` — passes (2 tests).
- `./gradlew :infra-tests:test --tests '*OrderPaymentFailureCompensationE2ETest'` — passes (3 tests).
- Combined gate: `./gradlew :payment-service:test --tests '*PaymentTimeoutJobTest' :infra-tests:test --tests '*PaymentFailureCompensationE2ETest' --tests '*OrderPaymentFailureCompensationE2ETest'` — all pass.
- `grep "@Scheduled" payment-service/src/main/java/com/n11/payment/payment/PaymentTimeoutJob.java` — present.
- `grep "PAYMENT_TIMEOUT" payment-service/src/main/java/com/n11/payment/messaging/PaymentTransactionalService.java` — present (errorCode wired).
- `grep "payment.failed\|Awaitility" infra-tests/src/test/java/com/n11/infratests/saga/PaymentFailureCompensationE2ETest.java` — both present.

## TDD Gate Compliance

- **Task 1 (PaymentTimeoutJob):** RED `8aca646` (`test(06-05): add failing PaymentTimeoutJob test`) preceded GREEN `e1084b7` (`feat(06-05): implement scheduled payment timeout sweep`). Standard TDD gate satisfied.
- **Task 2 (compensation E2E):** Two `test(06-05)` commits — `771a9d3` (inventory side) and `03664b5` (order side). No separate `feat` commit because the production code under test (PaymentFailedConsumer in inventory + order, OrderSagaService.processPaymentFailed) was wired in Phase 5 CD-08/CD-09. The TDD gate sequence here is "test addition over pre-existing impl" — RED commits exist; GREEN is the pre-existing Phase 5 implementation. Documented up-front so future verifiers do not search for a missing `feat(06-05)` commit on the compensation path.

## Known Stubs

None. Stub scan of all created files found no TODO/FIXME/placeholder text. The two test config classes (`InventoryCompensationTestConfig`, `OrderPaymentFailureTestConfig`) are intentionally minimal — they are pure infrastructure (no production behavior).

## Threat Flags

None beyond the plan's threat model. The new `PaymentTimeoutJob` is internal-only (no public surface); the compensation E2Es exercise existing internal AMQP wiring already covered by Phase 5's threat model. T-06-12 (DoS via stuck pending payments), T-06-13 (timeout-vs-callback race), and T-06-14 (timeout audit trail) are all explicitly mitigated and verified.

## User Setup Required

None - no external service configuration changes from prior Phase 6 plans. Iyzico sandbox values remain optional (the timeout job and compensation E2Es do not call Iyzico).

## Next Phase Readiness

- **Plan 06-06 (expected — Cloudflare Tunnel runbook + sandbox card matrix smoke):** all the surface needed is in place. The timeout sweep covers PAY-06; the compensation E2Es prove QUAL-05; the runbook can document the ngrok / Cloudflare Tunnel posture and the Iyzico test-card matrix without changing payment-service code.
- **Phase 7 (notification-service):** `payment.failed` is a known consumer fan-out target (`notify.q.payment-failed` per saga-contracts.md §2). The schema-aligned envelope shape (`reason ∈ {DECLINED, FRAUD, TIMEOUT, INSUFFICIENT_FUNDS, UNKNOWN}` + granular `errorCode`) gives notification-service a stable contract for templated emails / Slack pings.
- **Phase 8 (ai-service) + Phase 9 (mcp-server):** the agent toolset's `get_order_status` will see CANCELLED orders with `cancel_reason=PAYMENT_DECLINED` whenever a buyer abandons the hosted Iyzico form — covered by `OrderPaymentFailureCompensationE2ETest`'s timeout-envelope case.
- **Deferred D-06-02:** SagaHappyPathE2ETest currently regresses because of Plan 06-03/06-04's `DefaultIyzicoCheckoutClient` constructor change; should be re-enabled in a future infra-tests housekeeping plan (probably Phase 11 deploy hardening).

## Self-Check

- Created files exist:
  - `payment-service/src/main/java/com/n11/payment/payment/PaymentTimeoutJob.java` — FOUND.
  - `payment-service/src/test/java/com/n11/payment/payment/PaymentTimeoutJobTest.java` — FOUND.
  - `infra-tests/src/test/java/com/n11/infratests/saga/PaymentFailureCompensationE2ETest.java` — FOUND.
  - `infra-tests/src/test/java/com/n11/infratests/saga/InventoryCompensationTestConfig.java` — FOUND.
  - `infra-tests/src/test/java/com/n11/infratests/saga/OrderPaymentFailureCompensationE2ETest.java` — FOUND.
  - `infra-tests/src/test/java/com/n11/infratests/saga/OrderPaymentFailureTestConfig.java` — FOUND.
  - `infra-tests/src/test/resources/db/migration/payment/V3__iyzico_checkout_fields.sql` — FOUND.
  - `infra-tests/src/test/resources/db/migration/inventory/V1__init_processed_events.sql` — FOUND.
  - `infra-tests/src/test/resources/db/migration/inventory/V2__init_inventory.sql` — FOUND.
  - `infra-tests/src/test/resources/db/migration/orders/V1__init_processed_events.sql` — FOUND.
  - `infra-tests/src/test/resources/db/migration/orders/V2__init_orders.sql` — FOUND.
- Task/TDD commits found in git history: `8aca646`, `e1084b7`, `771a9d3`, `03664b5`.

## Self-Check: PASSED

---
*Phase: 06-payment-iyzico*
*Completed: 2026-04-30*
