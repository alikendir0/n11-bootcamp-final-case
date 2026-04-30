# Phase 6: Payment (Iyzico) - Research

**Researched:** 2026-04-30
**Status:** Ready for planning
**Confidence:** HIGH for Checkout Form flow and tunnel posture; MEDIUM for exact sandbox account/card dashboard behavior because it requires live Iyzico credentials.

## Executive Summary

Phase 6 should replace the Phase 5 mock payment path with Iyzico Checkout Form, not direct card capture or direct 3DS. The official Java sample uses `CheckoutFormInitialize.create(request, options)` to get `paymentPageUrl`, `checkoutFormContent`, and `token`, then `CheckoutForm.retrieve(request, options)` when Iyzico posts the callback token. This aligns with the project decision to expose only `paymentPageUrl` in Phase 6 and defer embedding via `checkoutFormContent`.

The hard implementation constraint is not the SDK call itself; it is constructing a complete Checkout Form request from existing saga data. The current `stock.reserved` payload contains `orderId`, `reservationId`, `reservedItems`, and `totalAmount`, but Iyzico initialization requires buyer, billing/shipping address, and basket item details. The plan must therefore add an internal order-service payment context endpoint that returns the order snapshot payment-service needs without cross-schema joins. This keeps service boundaries intact and avoids enriching every saga event with PII.

## Official Docs Findings

### Iyzico Checkout Form Initialize

Source: `https://docs.iyzico.com/en/payment-methods/checkoutform/cf-implementation/cf-initialize.md` and `iyzipay-java/src/test/java/com/iyzipay/sample/CheckoutFormSample.java`.

Required request fields:
- `locale`: use `Locale.TR.getValue()`.
- `conversationId`: use the saga correlation ID or order ID.
- `price` and `paidPrice`: use the order total amount; `paidPrice` may equal `price` for the demo.
- `currency`: `Currency.TRY.name()`.
- `paymentGroup`: `PaymentGroup.PRODUCT.name()`.
- `callbackUrl`: `${PUBLIC_BASE_URL}/api/v1/payments/iyzico/callback` per D-02.
- `buyer`: id, name, surname, gsm, email, identityNumber, registrationAddress, city, country.
- `shippingAddress` and `billingAddress`: use the order shipping snapshot.
- `basketItems`: item id, name, category1, itemType, price.

Initialize response fields used by this project:
- `status == "success"`
- `token`
- `paymentPageUrl`
- `signature` verified with `verifySignature(options.getSecretKey())`

### Iyzico Checkout Form Retrieve

Source: `https://docs.iyzico.com/en/payment-methods/checkoutform/cf-implementation/cf-retrieve.md` and Java sample.

When the user completes the hosted form, Iyzico redirects/posts to the configured `callbackUrl` with a `token`. The merchant must make a second server-side call using `RetrieveCheckoutFormRequest` and `CheckoutForm.retrieve(request, options)` to get final status, `paymentId`, `paymentStatus`, `fraudStatus`, card summary, and item transaction details. The retrieve response must also pass `verifySignature(options.getSecretKey())` before mutating saga state.

### Tunnel / Public Callback

Cloudflare Tunnel is the primary path per CONTEXT D-01. Official Cloudflare docs require:
- `cloudflared tunnel login`
- `cloudflared tunnel create <NAME>`
- route DNS to a hostname with `cloudflared tunnel route dns <UUID or NAME> <hostname>`
- `config.yml` pointing the hostname to `http://localhost:8080` (gateway)
- run with `cloudflared tunnel run <UUID or NAME>`

ngrok fallback remains valid for recovery: install ngrok, `ngrok config add-authtoken $NGROK_AUTHTOKEN`, then `ngrok http 8080`; use the HTTPS forwarding URL as `PUBLIC_BASE_URL`.

## Recommended Architecture

### Payment initialization flow

1. `payment-service` consumes `stock.reserved` as it does in Phase 5.
2. Consumer delegates to a non-transactional service.
3. Non-transactional service calls order-service internal payment context endpoint for order items/address/userId.
4. Non-transactional service initializes Iyzico Checkout Form through an `IyzicoCheckoutClient` adapter.
5. Transactional service persists a `PENDING` payment row with `iyzico_token`, `payment_page_url`, `expires_at`, and writes `processed_events` in the same transaction.
6. Re-delivered `stock.reserved` event reuses the active payment row and does not call Iyzico again.

### Client handoff flow

- `GET /payments/{orderId}` returns:
  - `202 Accepted` with `status=PENDING_INITIALIZATION` when no row exists yet.
  - `200 OK` with `status=PENDING` and `paymentPageUrl` when Checkout Form is ready.
  - `200 OK` with terminal `COMPLETED`, `FAILED`, or `TIMED_OUT` status after callback/timeout.
- Keep `POST /payments/checkout` only as an idempotent ensure/fetch endpoint for contract compatibility; it must not be a second independent checkout creator (CONTEXT D-05/D-06/D-09).

### Callback flow

1. Gateway allows `POST /api/v1/payments/iyzico/callback` without JWT.
2. payment-service receives token, finds the payment row by `iyzico_token`.
3. payment-service calls `CheckoutForm.retrieve` and verifies SDK signature.
4. On success and acceptable fraud status, update row to `COMPLETED`, set `iyzico_payment_id`, write `payment.completed` outbox.
5. On failure/rejection, update row to `FAILED`, write `payment.failed` outbox with reason/error code.
6. Duplicate callback by token/payment id is a no-op and returns the same result.

### Timeout flow

- Scheduled job scans `PENDING` payments with `expires_at < now()`.
- Each expired row transitions to `TIMED_OUT` and writes `payment.failed` with reason `PAYMENT_TIMEOUT`.
- This must reuse the same downstream compensation path as Iyzico decline.

## Architectural Responsibility Map

| Concern | Tier | Owner |
|---------|------|-------|
| Iyzico SDK calls | infrastructure adapter | `payment-service` only |
| Saga state and outbox | application/domain | `PaymentTransactionalService` |
| Order snapshot for payment | internal REST boundary | `order-service`, not cross-schema SQL |
| Public callback authentication | gateway + payment-service | gateway allowlist + server-side retrieve/signature verification |
| Tunnel setup | operator runbook | `payment-service/README.md`; compose sidecar deferred to Phase 11 |

## Validation Architecture

Automated validation should cover these behaviors:

1. **Request mapping:** unit test builds a Checkout Form initialize request with `Locale.TR`, `TRY`, `PaymentGroup.PRODUCT`, `${PUBLIC_BASE_URL}/api/v1/payments/iyzico/callback`, and line-item prices equal to order snapshots.
2. **Initialization idempotency:** direct consumer redelivery creates one payment row, one Iyzico initialize call, and zero duplicate checkout sessions.
3. **Pending status:** querying a payment link before initialization returns 202; after initialization returns the same `paymentPageUrl` on repeat calls.
4. **Callback finalization:** mocked retrieve success writes one `payment.completed` outbox row; duplicate callback does not write another.
5. **Failure compensation:** mocked retrieve failure and timeout both write `payment.failed`; inventory release and order cancellation are asserted in infra-tests.
6. **Security:** callback route is public at gateway but payment-service does not trust callback body alone; retrieve/signature verification is mandatory before terminal state.

Recommended quick commands:
- `./gradlew :payment-service:test --tests '*Iyzico*' --tests '*Payment*'`
- `./gradlew :order-service:test --tests '*PaymentContext*'`
- `./gradlew :infra-tests:test --tests '*PaymentFailureCompensationE2ETest'`

## Risks / Pitfalls

- **Callback unreachable:** mitigated by Cloudflare Tunnel primary runbook and ngrok fallback (D-01/D-03/D-04).
- **Sandbox/prod key mix:** keep `IYZICO_API_KEY`, `IYZICO_SECRET_KEY`, `iyzico.base-url=https://sandbox-api.iyzipay.com`, and `PUBLIC_BASE_URL` in env/config only.
- **Duplicate charges:** do not initialize multiple active sessions for one order; enforce one active `PENDING` payment per `order_id`.
- **PII in saga events:** fetch payment context over internal REST instead of putting buyer/address fields into `stock.reserved`.
- **State transition races:** callback and timeout must update by status guard (`PENDING` only) so the first terminal transition wins.

## Sources

- Iyzico Checkout Form Java sample: `iyzipay-java/src/test/java/com/iyzipay/sample/CheckoutFormSample.java`
- Iyzico CF initialize docs: `https://docs.iyzico.com/en/payment-methods/checkoutform/cf-implementation/cf-initialize.md`
- Iyzico CF retrieve docs: `https://docs.iyzico.com/en/payment-methods/checkoutform/cf-implementation/cf-retrieve.md`
- Cloudflare Tunnel local tunnel docs: `https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/get-started/create-local-tunnel/`
- ngrok Agent CLI quickstart: `https://ngrok.com/docs/getting-started/`
