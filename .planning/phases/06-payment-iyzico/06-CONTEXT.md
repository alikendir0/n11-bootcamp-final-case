# Phase 6: Payment (Iyzico) - Context

**Gathered:** 2026-04-30
**Status:** Ready for planning

<domain>
## Phase Boundary

Replace the Phase 5 mock payment skeleton with real Iyzico Checkout Form sandbox integration. This phase owns checkout initialization after `stock.reserved`, public callback reachability, `payment.completed` / `payment.failed` publication, payment timeout compensation, and proof that payment failure releases stock and cancels the order. It does not own frontend checkout UI polish, Cloudflare Tunnel compose hardening, or full deploy documentation beyond the payment-service runbook.

</domain>

<decisions>
## Implementation Decisions

### Public Tunnel And Callback Base URL
- **D-01:** Cloudflare Tunnel is the primary public exposure path for Phase 6 Iyzico callbacks and the eventual demo gateway URL.
- **D-02:** payment-service derives callback URLs from one environment variable: `PUBLIC_BASE_URL`. Do not introduce a separate primary `IYZICO_CALLBACK_URL` unless the planner has a concrete reason and validates it cannot drift from the public gateway URL.
- **D-03:** Phase 6 writes a payment-service runbook for Cloudflare Tunnel setup and `PUBLIC_BASE_URL`; the docker-compose `cloudflared` sidecar belongs to Phase 11 deploy hardening.
- **D-04:** Phase 6 also documents an ngrok fallback runbook so callback testing or demo recovery is not blocked if Cloudflare setup fails.

### Checkout Handoff
- **D-05:** `stock.reserved` auto-initializes Iyzico Checkout Form inside payment-service. The client should not be responsible for starting Iyzico after order creation.
- **D-06:** Clients obtain checkout readiness and the payment link by querying payment status for the order. Future `get_payment_link` tooling should read the same source of truth.
- **D-07:** The client-facing checkout response exposes `paymentPageUrl` only for Phase 6. Do not expose or depend on `checkoutFormContent` in this phase; Phase 10 can decide redirect vs embed at the UI layer if needed.
- **D-08:** If a client asks for the payment link before stock reservation or Iyzico initialization finishes, return a `202 Accepted` style pending status instead of blocking the request or treating normal saga lag as an error.
- **D-09:** Repeated payment-link requests for the same order reuse the active Iyzico checkout link until the payment times out or a callback finalizes the payment. Do not create multiple active checkout sessions for one order.

### OpenCode's Discretion
- Callback result page details, exact polling interval hints, internal DTO names, and timeout duration are planner discretion as long as they satisfy the roadmap success criteria and keep the saga idempotent.
- The planner must explicitly reconcile the current `api-contracts.md` `POST /payments/checkout` entry with D-05/D-06. If keeping that endpoint is useful for compatibility, it must behave as an idempotent status/link fetch or ensure operation, not as a second independent checkout creator.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase Scope And Requirements
- `.planning/ROADMAP.md` Section "Phase 6: Payment (Iyzico)" - goal, success criteria, risks, and research need.
- `.planning/REQUIREMENTS.md` Section "Payment (Iyzico)" - PAY-01 through PAY-07 and QUAL-05 traceability.
- `.planning/PROJECT.md` - Cloudflare Tunnel preference, no-secrets rule, local docker-compose deployment posture, and verify-before-implement policy.
- `.planning/STATE.md` - current handoff from Phase 5 and Phase 6 tunnel decision blocker.

### Contracts
- `.planning/api-contracts.md` Sections 1-3 - payment-service REST surface, gateway route `/api/v1/payments/**`, public callback allowlist, and JWT boundary.
- `.planning/saga-contracts.md` Sections 1-8 - event envelope, exchange/queue topology, retry policy, idempotency, correlation IDs, and event catalog.
- `.planning/saga-contracts/stock-reserved.schema.json` - inbound event that triggers payment-service checkout initialization.
- `.planning/saga-contracts/payment-completed.schema.json` - success event payment-service must publish.
- `.planning/saga-contracts/payment-failed.schema.json` - failure/timeout event payment-service must publish for compensation.

### Research And Known Pitfalls
- `.planning/research/STACK.md` Section "Payment" - Iyzico SDK coordinate, sandbox base URL, Checkout Form flow, and sample location.
- `.planning/research/PITFALLS.md` Pitfall 5 and Integration Gotchas - callback reachability, unauthenticated callback allowlist, Checkout Form preference, sandbox/prod key separation, locale/currency requirements.
- `.planning/phases/05-cart-order-skeleton/05-VERIFICATION.md` - verified Phase 5 payment skeleton, mock `payment.completed` path, and known stub boundary that Phase 6 replaces.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `payment-service/src/main/java/com/n11/payment/messaging/StockReservedConsumer.java` - already consumes `stock.reserved` with the correct Message-only listener pattern and delegates side effects.
- `payment-service/src/main/java/com/n11/payment/messaging/PaymentSagaService.java` - current mock seam where Iyzico initialization should replace the delay/mock behavior before opening the transactional boundary.
- `payment-service/src/main/java/com/n11/payment/messaging/PaymentTransactionalService.java` - current transactional persist + outbox write path for `payment.completed`; Phase 6 should adapt this pattern for pending, completed, failed, and timeout outcomes.
- `payment-service/src/main/java/com/n11/payment/payment/Payment.java` and `PaymentStatus.java` - current entity/status model is minimal and likely needs fields for Iyzico token/payment page URL, timeout tracking, and callback dedup.
- `payment-service/src/main/java/com/n11/payment/outbox/PaymentOutboxPoller.java` - common-outbox publisher already exists for payment events.

### Established Patterns
- Saga consumers must be idempotent with `processed_events` checked inside the transaction before side effects.
- Listener classes deserialize and route only; transactional side effects happen in a separate service bean to preserve Spring proxy boundaries.
- Outbound saga events are written through transactional outbox, not published directly during business transactions.
- Gateway validates JWT and strips `Authorization`; callback path must be public at gateway and verified in payment-service via Iyzico retrieve.

### Integration Points
- `config-server/src/main/resources/config/payment-service.yml` currently has mock payment settings and must become the home for Iyzico sandbox config placeholders and `PUBLIC_BASE_URL` usage.
- `config-server/src/main/resources/config/api-gateway.yml` currently has no payment-service route; Phase 6 adds `/api/v1/payments/**` plus the public callback allowlist behavior.
- `order-service` already consumes `payment.completed` and `payment.failed`; `inventory-service` already consumes `payment.failed` to release stock.
- Future `agent-toolset` `get_payment_link` must query the same payment status/link endpoint chosen here.

</code_context>

<specifics>
## Specific Ideas

- Prioritize a robust hosted-page redirect flow with `paymentPageUrl`; embedding can be revisited during frontend checkout UI implementation.
- Keep the public callback base URL and demo gateway URL unified under `PUBLIC_BASE_URL` to avoid environment drift.

</specifics>

<deferred>
## Deferred Ideas

- Docker-compose Cloudflare Tunnel sidecar hardening is deferred to Phase 11 DevOps deploy.
- Checkout Form embedding via `checkoutFormContent` is deferred to Phase 10 if the storefront needs it.

</deferred>

---

*Phase: 06-payment-iyzico*
*Context gathered: 2026-04-30*
