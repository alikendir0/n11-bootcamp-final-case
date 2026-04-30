---
phase: 06-payment-iyzico
plan: 02
subsystem: payments
tags: [spring-boot, restclient, order-service, payment-service, iyzico, tdd]

requires:
  - phase: 05-cart-order-skeleton
    provides: order schema with orders, order_items, and order_shipping_addresses snapshots
  - phase: 06-payment-iyzico
    provides: Iyzico checkout foundation and payment-service configuration baseline
provides:
  - Internal order-service payment context endpoint for Iyzico buyer/address/basket data
  - payment-service RestClient adapter for retrieving order payment context over the internal mesh
  - ORDER_SERVICE_BASE_URL config hook for Docker-network service calls
affects: [06-payment-iyzico, payment-service, order-service, iyzico-checkout-initialization]

tech-stack:
  added: []
  patterns:
    - Internal REST snapshot contract instead of cross-schema reads or PII-enriched saga events
    - TDD red/green commits for service boundary contracts
    - Sanitized domain exception for missing upstream payment context

key-files:
  created:
    - order-service/src/main/java/com/n11/order/order/PaymentContextController.java
    - order-service/src/main/java/com/n11/order/order/PaymentContextService.java
    - order-service/src/main/java/com/n11/order/order/dto/PaymentContextDto.java
    - order-service/src/test/java/com/n11/order/order/PaymentContextControllerTest.java
    - payment-service/src/main/java/com/n11/payment/order/OrderPaymentContext.java
    - payment-service/src/main/java/com/n11/payment/order/OrderPaymentContextClient.java
    - payment-service/src/main/java/com/n11/payment/order/PaymentInitializationException.java
    - payment-service/src/test/java/com/n11/payment/order/OrderPaymentContextClientTest.java
  modified:
    - config-server/src/main/resources/config/payment-service.yml

key-decisions:
  - "Payment-service obtains Iyzico buyer/address/item inputs through order-service internal REST, preserving schema ownership and keeping PII out of stock.reserved."
  - "The payment context endpoint remains under /internal/orders and no api-gateway route was added; Docker mesh/gateway-only exposure remains the trust boundary."
  - "404 from order-service is treated as a non-retryable payment initialization failure with a sanitized message."

patterns-established:
  - "Internal snapshot endpoint: order-service owns order/address/item reads and returns only fields required for payment initialization."
  - "Payment client config: app.clients.order.base-url defaults to http://order-service:8085 and can be overridden with ORDER_SERVICE_BASE_URL."

requirements-completed: [PAY-01, PAY-02]

duration: 4min
completed: 2026-04-30
---

# Phase 06 Plan 02: Internal Order Payment Context Summary

**Internal order payment snapshot plus payment-service RestClient bridge for Iyzico Checkout Form initialization without cross-schema access**

## Performance

- **Duration:** 4 min
- **Started:** 2026-04-30T11:46:07Z
- **Completed:** 2026-04-30T11:50:11Z
- **Tasks:** 2
- **Files modified:** 9

## Accomplishments

- Added `GET /internal/orders/{orderId}/payment-context` in order-service to return `orderId`, `userId`, totals, currency, shipping address, and item lines.
- Added payment-service `OrderPaymentContextClient` using Spring `RestClient` against the internal order-service endpoint.
- Configured `app.clients.order.base-url: ${ORDER_SERVICE_BASE_URL:http://order-service:8085}` for environment-specific internal service addressing.
- Preserved the threat-model boundary: no gateway route was added for `payment-context`, and `stock.reserved` remains the trigger without saga PII expansion.

## task Commits

Each task was committed atomically:

1. **task 1 RED:** `e4e4d5b` (`test`) add failing payment context controller tests.
2. **task 1 GREEN:** `1fc484a` (`feat`) expose internal order payment context.
3. **task 2 RED:** `c2a15c1` (`test`) add failing order payment context client tests.
4. **task 2 GREEN:** `def1457` (`feat`) add order payment context client.

**Plan metadata:** pending final docs commit.

## Files Created/Modified

- `order-service/src/main/java/com/n11/order/order/PaymentContextController.java` - Internal-only controller under `/internal/orders`.
- `order-service/src/main/java/com/n11/order/order/PaymentContextService.java` - Reads order, items, and shipping address from order-service repositories only.
- `order-service/src/main/java/com/n11/order/order/dto/PaymentContextDto.java` - Payment snapshot contract with nested `ShippingAddress` and `Item` records.
- `order-service/src/test/java/com/n11/order/order/PaymentContextControllerTest.java` - MVC contract tests for response fields, opaque 404s, and internal path placement.
- `payment-service/src/main/java/com/n11/payment/order/OrderPaymentContext.java` - payment-service local DTO mirroring the internal snapshot contract.
- `payment-service/src/main/java/com/n11/payment/order/OrderPaymentContextClient.java` - RestClient wrapper for `/internal/orders/{orderId}/payment-context`.
- `payment-service/src/main/java/com/n11/payment/order/PaymentInitializationException.java` - Sanitized domain exception for upstream context failures.
- `payment-service/src/test/java/com/n11/payment/order/OrderPaymentContextClientTest.java` - RestClient path and 404 translation tests.
- `config-server/src/main/resources/config/payment-service.yml` - Adds `ORDER_SERVICE_BASE_URL`-backed internal client base URL.

## Decisions Made

- Used an internal REST contract rather than cross-schema SQL, keeping the order schema exclusively owned by order-service.
- Kept the endpoint unauthenticated at the service layer because it is intentionally not externally routed; gateway-only exposure remains the public auth boundary.
- Mapped upstream 4xx responses to `PaymentInitializationException` so Plan 03 can fail initialization cleanly instead of retrying missing order snapshots indefinitely.

## Deviations from Plan

None - plan executed as written.

## Issues Encountered

- The negative path test initially expected framework 404 for `/orders/{id}/payment-context`, but the existing global error handler maps Spring MVC `NoResourceFoundException` to a generic 500. The test was refined to assert the request is handled by `ResourceHttpRequestHandler`, proving it is not mapped to the payment context controller.

## Known Stubs

None. Stub scan found only non-stub config placeholder syntax and a defensive null/blank base URL fallback.

## TDD Gate Compliance

- RED commits present: `e4e4d5b`, `c2a15c1`.
- GREEN commits present after RED: `1fc484a`, `def1457`.
- No refactor commit was needed.

## Verification

- `./gradlew :order-service:test --tests '*PaymentContextControllerTest'` — passed.
- `./gradlew :payment-service:compileJava` — passed.
- `./gradlew :payment-service:test --tests '*OrderPaymentContextClientTest'` — passed during task execution.
- `config-server/src/main/resources/config/api-gateway.yml` was not modified with a payment-context route.

## User Setup Required

None - no external service configuration required for this plan. Operators may override `ORDER_SERVICE_BASE_URL` only if the internal order-service address differs from `http://order-service:8085`.

## Next Phase Readiness

- Plan 03 can construct Iyzico Checkout Form initialization commands from `OrderPaymentContext` without expanding saga event payloads.
- Payment initialization should catch `PaymentInitializationException` and route it into the planned failure/compensation path.

## Self-Check: PASSED

- Verified all 10 expected files exist (9 implementation/test/config files plus this summary).
- Verified task commits exist in git history: `e4e4d5b`, `1fc484a`, `c2a15c1`, `def1457`.

---
*Phase: 06-payment-iyzico*
*Completed: 2026-04-30*
