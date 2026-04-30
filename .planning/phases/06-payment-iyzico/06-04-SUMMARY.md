---
phase: 06-payment-iyzico
plan: 04
subsystem: payments
tags: [iyzico, checkout-form, callback, spring-mvc, gateway-routing, saga, postgres, tdd]

requires:
  - phase: 06-payment-iyzico
    provides: pending Iyzico checkout link, internal order payment context, gateway public allowlist for Iyzico callback
  - phase: 03-identity-gateway-auth
    provides: api-gateway JWT validation + public allowlist for /api/v1/payments/iyzico/callback
provides:
  - Public payment status/link API (GET /payments/{orderId}, POST /payments/checkout) with 202 PENDING_INITIALIZATION semantics
  - Iyzico Checkout Form callback finalizer (POST /payments/iyzico/callback) that drives payment.completed / payment.failed via server-side retrieve + signature verification
  - Concrete IyzicoCheckoutClient.retrieve adapter with mdStatus / fraud / decline classification onto the saga schema enum
  - Idempotent terminal transitions in PaymentTransactionalService (PENDING-only guard; duplicate callbacks write zero extra outbox events)
  - api-gateway route /api/v1/payments/** → lb://PAYMENT-SERVICE with Springdoc surface and updated depends_on
affects: [06-payment-iyzico, frontend-checkout, agent-toolset, mcp-server, order-saga, notification-service]

tech-stack:
  added: []
  patterns:
    - "Schema-aligned enum mapping with granular Iyzico taxonomy preserved in errorCode"
    - "Hosted-redirect minimal HTML response page on callback exit hop"
    - "Status-guarded terminal transitions (PENDING-only) for idempotency"

key-files:
  created:
    - payment-service/src/main/java/com/n11/payment/api/PaymentController.java
    - payment-service/src/main/java/com/n11/payment/api/PaymentStatusResponse.java
    - payment-service/src/main/java/com/n11/payment/api/IyzicoCallbackRequest.java
    - payment-service/src/main/java/com/n11/payment/api/IyzicoCallbackOutcome.java
    - payment-service/src/test/java/com/n11/payment/api/PaymentControllerTest.java
    - payment-service/src/test/java/com/n11/payment/api/IyzicoCallbackIntegrationTest.java
  modified:
    - payment-service/src/main/java/com/n11/payment/iyzico/DefaultIyzicoCheckoutClient.java
    - payment-service/src/main/java/com/n11/payment/messaging/PaymentTransactionalService.java
    - config-server/src/main/resources/config/api-gateway.yml
    - docker-compose.yml

key-decisions:
  - "POST /payments/iyzico/callback consumes application/x-www-form-urlencoded with @RequestParam(\"token\") (matches Iyzico Checkout Form's hosted-page wire shape; the S2S JSON webhook is out of scope for Phase 6)."
  - "Granular Iyzico taxonomy (IYZICO_DECLINED, IYZICO_FRAUD_REVIEW, IYZICO_RETRIEVE_FAILED, IYZICO_3DS_MDSTATUS_INVALID) lands in the payment.failed envelope's errorCode and the row's failure_code column; reason is mapped onto the saga schema enum (DECLINED/FRAUD/UNKNOWN)."
  - "Duplicate terminal callbacks short-circuit before re-calling Iyzico — already-COMPLETED/FAILED payments return the cached terminal status without a second SDK retrieve or outbox row."
  - "POST /payments/checkout is an idempotent ensure/fetch only — the saga (stock.reserved) remains the sole creator of new Iyzico checkout sessions per D-05."
  - "Callback returns a minimal text/html confirmation page (Turkish) so the buyer's browser renders something readable on the Iyzico hosted-form exit hop; canonical status is fetched via GET /payments/{orderId}."

patterns-established:
  - "Schema-aligned enum mapping: when the saga JSON schema enumerates a small set of reasons (DECLINED/FRAUD/TIMEOUT/INSUFFICIENT_FUNDS/UNKNOWN), provider-specific granular codes ride in the errorCode field instead of widening the enum."
  - "Status-guarded terminal transitions: completeFromCallback / failFromCallback both check status == PENDING inside @Transactional before mutating, so duplicate retrieve responses never produce a second outbox event."
  - "Iyzico boundary: SDK signature verification lives inside DefaultIyzicoCheckoutClient.retrieve; the controller depends only on the neutral RetrievedCheckout record."

requirements-completed: [PAY-03, PAY-04, PAY-05, PAY-07, QUAL-05]

duration: 13min
completed: 2026-04-30
---

# Phase 06 Plan 04: Iyzico Callback Finalization & Public Payment API Summary

**Public payment status/link surface + Iyzico Checkout Form callback finalizer that drives payment.completed/failed through server-side retrieve verification and idempotent PENDING-only transitions, plus the api-gateway route that exposes the lot.**

## Performance

- **Duration:** ~13 min
- **Started:** 2026-04-30T12:25:42Z
- **Completed:** 2026-04-30T12:38:10Z
- **Tasks:** 3
- **Files modified:** 10 (4 main + 2 test + 4 config/build)

## Accomplishments

- Added `PaymentController` with `GET /{orderId}` (202 PENDING_INITIALIZATION when no row, 200 with paymentPageUrl when PENDING) and idempotent `POST /checkout` (D-06/D-08 semantics).
- Implemented the `POST /iyzico/callback` finalizer that consumes form-urlencoded `token`, calls `IyzicoCheckoutClient.retrieve`, and drives terminal `PENDING → COMPLETED/FAILED` transitions while writing exactly one `payment.completed` or `payment.failed` outbox event per callback.
- Implemented `DefaultIyzicoCheckoutClient.retrieve` with `CheckoutForm.retrieve(...)`, `verifySignature(secretKey)`, and adapter-level error classification.
- Added `IyzicoCallbackOutcome` to map Iyzico response classes (SUCCESS / mdStatus-invalid / fraud / decline / retrieve-failure) onto the `payment.failed` schema enum (DECLINED/FRAUD/UNKNOWN) while preserving the granular `IYZICO_*` taxonomy in `errorCode`.
- Routed `/api/v1/payments/**` through api-gateway with `StripPrefix=2`, added the Springdoc URL, and updated `docker-compose.yml` so `api-gateway.depends_on` now waits on `payment-service`.

## Task Commits

Each TDD task was committed atomically:

1. **task 1: status/link & ensure-checkout endpoints**
   - `5edb531` (`test`) add failing PaymentController status/link tests
   - `ed86182` (`feat`) expose payment status/link API
2. **task 2: Iyzico callback retrieve & terminal outbox events**
   - `75c5854` (`test`) add failing Iyzico callback integration tests
   - `2b932c4` (`feat`) finalize Iyzico callback into payment.completed/failed
3. **task 3: gateway route & docker-compose alignment**
   - `010dff9` (`feat`) route payment-service through gateway

**Plan metadata:** pending final docs commit.

## Files Created/Modified

- `payment-service/src/main/java/com/n11/payment/api/PaymentController.java` — public REST surface: GET /payments/{orderId} status, POST /payments/checkout idempotent ensure, POST /payments/iyzico/callback finalizer.
- `payment-service/src/main/java/com/n11/payment/api/PaymentStatusResponse.java` — neutral status payload (orderId, status, paymentPageUrl, failureReason, updatedAt) — no SDK embedded-form HTML per D-07.
- `payment-service/src/main/java/com/n11/payment/api/IyzicoCallbackRequest.java` — typed view of the form-encoded callback body (documentation/wire-shape record).
- `payment-service/src/main/java/com/n11/payment/api/IyzicoCallbackOutcome.java` — package-private classifier that translates a `RetrievedCheckout` into `{COMPLETED|DECLINED|FRAUD_REVIEW|MD_STATUS_INVALID|RETRIEVE_FAILED}` plus schema-aligned `(reason, errorCode)`.
- `payment-service/src/main/java/com/n11/payment/iyzico/DefaultIyzicoCheckoutClient.java` — adds the retrieve implementation: `CheckoutForm.retrieve` + `verifySignature` + adapter-error fail-fast.
- `payment-service/src/main/java/com/n11/payment/messaging/PaymentTransactionalService.java` — adds `completeFromCallback` / `failFromCallback` terminal methods (PENDING-only guarded, write exactly one outbox event each), and an injected `PaymentOutboxRepository` + `ObjectMapper`.
- `payment-service/src/test/java/com/n11/payment/api/PaymentControllerTest.java` — `@WebMvcTest` slice covering all four status/link/checkout cases.
- `payment-service/src/test/java/com/n11/payment/api/IyzicoCallbackIntegrationTest.java` — `@SpringBootTest` + Postgres + RabbitMQ Testcontainers + MockMvc, covering success/failure/duplicate/mdStatus-invalid paths.
- `config-server/src/main/resources/config/api-gateway.yml` — adds the `payment-service` route and Springdoc URL.
- `docker-compose.yml` — adds `payment-service` to `api-gateway.depends_on` and updates the payment-service section comment to reflect Phase 6's public REST surface.

## Decisions Made

- **Form-urlencoded callback only.** Iyzico's hosted Checkout Form posts the buyer's browser back to the merchant with `application/x-www-form-urlencoded` and a single `token` field (verified against the iyzipay-java sample `CheckoutFormSample.java` and the docs at `https://docs.iyzico.com/en/payment-methods/checkoutform/cf-implementation/cf-retrieve.md`). Accepting JSON here would be a non-conformant transport for the hosted-form path; the S2S JSON webhook (different endpoint) is out of scope for Phase 6.
- **Schema-aligned enum mapping.** The `payment.failed` schema constrains `reason` to `{DECLINED, FRAUD, INSUFFICIENT_FUNDS, TIMEOUT, UNKNOWN}`. Widening the enum to fit Iyzico-specific codes would break the saga contract; instead the granular taxonomy (IYZICO_DECLINED, IYZICO_FRAUD_REVIEW, IYZICO_RETRIEVE_FAILED, IYZICO_3DS_MDSTATUS_INVALID) lands in the `errorCode` field and the row's `failure_code` column, leaving downstream consumers (order-service, inventory-service compensation, notification-service) unaffected.
- **Idempotent short-circuit.** Duplicate callback delivery for an already-terminal payment returns the cached terminal status WITHOUT calling Iyzico retrieve again. The plan's "no second outbox event" requirement is satisfied; the side benefit is that we never burn a wasted SDK call when the buyer accidentally double-submits the hosted form.
- **POST /checkout is ensure/fetch only.** D-05 makes `stock.reserved` the sole creator of Iyzico sessions; the controller's POST endpoint exists for `api-contracts.md` compatibility and `agent-toolset.get_payment_link` future tooling, but never triggers a new SDK initialize. The test asserts `paymentRepository.save(any())` is never called for the ensure path.
- **3-arg DefaultIyzicoCheckoutClient constructor preserved.** Plan 06-03's mapper test instantiates the client with `(properties, options, initializer)` for initialize-only coverage. Adding the 4-arg `(properties, options, initializer, retriever)` constructor without preserving the 3-arg form would break Plan 06-03's tests; the 3-arg overload now defaults the retriever to `CheckoutForm::retrieve`.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Preserved 3-arg DefaultIyzicoCheckoutClient constructor**
- **Found during:** task 2 (GREEN compile)
- **Issue:** Adding a `CheckoutRetriever` lambda parameter to the test constructor broke Plan 06-03's `IyzicoCheckoutRequestMapperTest`, which uses the 3-arg `(properties, options, initializer)` form for initialize-only coverage.
- **Fix:** Added a convenience overload `DefaultIyzicoCheckoutClient(properties, options, initializer)` that defaults the retriever to `CheckoutForm::retrieve`. Both the new 4-arg form (used by the callback path) and the legacy 3-arg form coexist.
- **Files modified:** `payment-service/src/main/java/com/n11/payment/iyzico/DefaultIyzicoCheckoutClient.java`
- **Verification:** `./gradlew :payment-service:test --tests '*IyzicoCheckoutRequestMapperTest' --tests '*PaymentControllerTest' --tests '*IyzicoCallbackIntegrationTest'` — all pass.
- **Committed in:** `2b932c4`

**2. [Rule 3 - Blocking] Added RabbitMQ Testcontainer to IyzicoCallbackIntegrationTest**
- **Found during:** task 2 (RED initial run)
- **Issue:** `@SpringBootTest` boot of the full payment-service context attempted to wire RabbitMQ listeners despite `spring.rabbitmq.listener.simple.auto-startup=false`, failing with `AmqpAuthenticationException` because the underlying connection factory still tried to authenticate against `localhost:5672`. This matches the deferred D-06-01 issue noted in Plan 06-01.
- **Fix:** Added a `rabbitmq:3.13-management` Testcontainer and dynamic property registration (host/port/credentials), mirroring the pattern Plan 06-03 introduced for `StockReservedConsumerIntegrationTest`.
- **Files modified:** `payment-service/src/test/java/com/n11/payment/api/IyzicoCallbackIntegrationTest.java`
- **Verification:** Test class now boots and all four scenarios pass against real Testcontainer Postgres + RabbitMQ.
- **Committed in:** `75c5854` (already shipped in the RED commit since the failure mode would otherwise have been a context-load error rather than a missing-implementation assertion error).

**3. [Rule 1 - Bug] Corrected the duplicate-callback assertion to match short-circuit semantics**
- **Found during:** task 2 (GREEN run with implementation in place)
- **Issue:** The duplicate-callback test asserted `verify(iyzicoCheckoutClient, times(2)).retrieve(...)`, but the controller short-circuits the second delivery (the payment is already COMPLETED, so retrieve is skipped). The original assertion would have forced a wasteful second SDK call and weakened the T-06-10 mitigation.
- **Fix:** Updated the assertion to `times(1)` and added a comment explaining why short-circuiting is the correct behavior per the plan's "Duplicate terminal callbacks return the current terminal status without extra outbox rows" criterion.
- **Files modified:** `payment-service/src/test/java/com/n11/payment/api/IyzicoCallbackIntegrationTest.java`
- **Verification:** Test passes; `outboxRepository` count assertion still proves zero second outbox row.
- **Committed in:** `2b932c4` (rolled into the GREEN commit because the GREEN implementation defines the short-circuit behavior).

---

**Total deviations:** 3 auto-fixed (2 Rule 3 blocking, 1 Rule 1 test-fixture bug).
**Impact on plan:** All three were necessary for the verification gate to pass; no scope expansion. The 3-arg constructor preserves backward compatibility with Plan 06-03 tests; the RabbitMQ Testcontainer retires the deferred D-06-01 issue for any future test class that loads the full Spring context; the duplicate-callback assertion correction aligns with the plan's stated idempotency contract.

## Issues Encountered

- **`@MockBean` deprecation warnings.** Spring Boot 3.5 marks `org.springframework.boot.test.mock.mockito.MockBean` as deprecated for removal. The existing tests in payment-service still use it, and a sweep is out of scope for this plan; warnings only, no functional issue.

## Verification

- `./gradlew :payment-service:test --tests '*PaymentControllerTest'` — passed (4 tests).
- `./gradlew :payment-service:test --tests '*IyzicoCallbackIntegrationTest'` — passed (4 tests).
- `./gradlew :payment-service:test --tests '*PaymentControllerTest' --tests '*IyzicoCallbackIntegrationTest'` — passed (8 tests).
- `./gradlew :payment-service:test --tests '*IyzicoCheckoutRequestMapperTest' --tests '*StockReservedConsumerIntegrationTest' --tests '*IyzicoCheckoutClientContractTest' --tests '*OrderPaymentContextClientTest' --tests '*PaymentRepositoryIntegrationTest' --tests '*IyzicoPropertiesTest'` — passed (Plan 06-01..03 regression check).
- `./gradlew :api-gateway:compileJava :payment-service:compileJava` — passed.
- `./gradlew :api-gateway:test` — passed (gateway smoke regression).
- `docker compose config --quiet` — exit 0 (compose syntax valid; only env-var warnings from `.env` not present in worktree).
- `grep "Path=/api/v1/payments/\*\*\|lb://PAYMENT-SERVICE\|StripPrefix=2"` against `api-gateway.yml` — present.
- `grep -c "checkoutFormContent" payment-service/src/main/java` — 0 matches (D-07 invariant preserved).

## TDD Gate Compliance

- RED commits present before GREEN: `5edb531` (Task 1 RED), `75c5854` (Task 2 RED).
- GREEN commits present after RED: `ed86182` (Task 1 GREEN), `2b932c4` (Task 2 GREEN).
- Task 3 is non-TDD per plan (`type="auto"`, no `tdd="true"`).
- No separate refactor commit was required.

## Known Stubs

None. Stub scan of `payment-service/src/main/java/com/n11/payment/api` found no TODO/FIXME/placeholder text. The minimal Turkish HTML confirmation page is intentional (Iyzico hosted-form exit-hop UX); canonical status is fetched via `GET /payments/{orderId}`.

## Threat Flags

None. The new public callback surface (`POST /payments/iyzico/callback`) was already covered by the plan's threat model (T-06-09 Spoofing, T-06-10 Tampering, T-06-11 Information Disclosure). The Phase 3 `SecurityConfig` allowlist already permits this exact path; the new gateway route does not introduce a new trust boundary.

## User Setup Required

No new setup beyond Plan 06-01 Iyzico sandbox values:
- `IYZICO_API_KEY`
- `IYZICO_SECRET_KEY`
- `PUBLIC_BASE_URL` (must point at the Cloudflare Tunnel / ngrok hostname for live callback testing — see Phase 6 runbook)
- optional `PAYMENT_TIMEOUT_MINUTES`, `IYZICO_DEMO_BUYER_IDENTITY_NUMBER`

## Next Phase Readiness

- The full Iyzico Checkout Form happy-path is now wired end-to-end: stock.reserved → Iyzico initialize → PENDING checkout → buyer redirect → callback retrieve → `payment.completed`. The Phase 5 mock-payment skeleton is fully replaced.
- Plan 06-05 (timeout compensation, expected) can scan `findExpiredPendingPayments` and call a new `timeoutFromScheduler` method that mirrors `failFromCallback` with `reason=TIMEOUT, errorCode=PAYMENT_TIMEOUT`. The terminal-state guard pattern is already proven idempotent.
- Plan 06-06 (Cloudflare Tunnel runbook + sandbox card matrix smoke, expected) has all the surface it needs: callback path, payment-service public route, Springdoc aggregator entry.
- The `agent-toolset.get_payment_link` tool can read `GET /api/v1/payments/{orderId}` directly (Phase 9 MCP server + Phase 8 chat assistant).
- The frontend checkout flow (Phase 10) can poll `GET /api/v1/payments/{orderId}` and redirect to `paymentPageUrl` once `status` becomes `PENDING`.

## Self-Check: PASSED

- Created files exist:
  - `payment-service/src/main/java/com/n11/payment/api/PaymentController.java` — FOUND.
  - `payment-service/src/main/java/com/n11/payment/api/PaymentStatusResponse.java` — FOUND.
  - `payment-service/src/main/java/com/n11/payment/api/IyzicoCallbackRequest.java` — FOUND.
  - `payment-service/src/main/java/com/n11/payment/api/IyzicoCallbackOutcome.java` — FOUND.
  - `payment-service/src/test/java/com/n11/payment/api/PaymentControllerTest.java` — FOUND.
  - `payment-service/src/test/java/com/n11/payment/api/IyzicoCallbackIntegrationTest.java` — FOUND.
  - `.planning/phases/06-payment-iyzico/06-04-SUMMARY.md` — FOUND.
- Task/TDD commits found in git history: `5edb531`, `ed86182`, `75c5854`, `2b932c4`, `010dff9`.

---
*Phase: 06-payment-iyzico*
*Completed: 2026-04-30*
