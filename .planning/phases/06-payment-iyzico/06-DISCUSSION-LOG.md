# Phase 6: Payment (Iyzico) - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md - this log preserves the alternatives considered.

**Date:** 2026-04-30
**Phase:** 06-payment-iyzico
**Areas discussed:** Tunnel choice, Checkout handoff

---

## Tunnel choice

| Question | Option | Description | Selected |
|----------|--------|-------------|----------|
| Primary tunnel | Cloudflare primary | Stable hostname if a Cloudflare-managed domain is available; aligns with PROJECT.md preference and Phase 11 deploy posture. | Yes |
| Primary tunnel | ngrok primary | Fastest setup; free tier gives a random hostname unless paid. | |
| Primary tunnel | Cloudflare + ngrok fallback | Cloudflare target plus ngrok fallback for demo resilience. | |
| Callback base URL | Single PUBLIC_BASE_URL | One env var such as `PUBLIC_BASE_URL=https://demo.example.com`; payment-service derives callbackUrl from it. | Yes |
| Callback base URL | Dedicated IYZICO_CALLBACK_URL | More explicit, but risks drift from the gateway demo URL. | |
| Callback base URL | Both with validation | Allow override but fail startup if it conflicts unexpectedly with `PUBLIC_BASE_URL`. | |
| Phase 6 wiring depth | Runbook now, sidecar later | Phase 6 documents cloudflared setup and uses `PUBLIC_BASE_URL`; Phase 11 owns compose hardening. | Yes |
| Phase 6 wiring depth | Compose sidecar now | Add cloudflared service in Phase 6; overlaps with Phase 11 DevOps scope. | |
| Phase 6 wiring depth | Local CLI only | Fast for payment testing, weaker as reusable demo posture. | |
| Fallback docs | Yes, fallback runbook | Keep Cloudflare primary but include ngrok emergency path. | Yes |
| Fallback docs | No fallback | Simpler docs; assumes Cloudflare setup is reliable. | |
| Fallback docs | Only mention briefly | One paragraph pointer, not full commands/env examples. | |

**User's choices:** Cloudflare primary; Single `PUBLIC_BASE_URL`; Runbook now, sidecar later; Yes, fallback runbook.
**Notes:** Cloudflare is locked as primary for Phase 6. ngrok remains a documented fallback only.

---

## Checkout handoff

| Question | Option | Description | Selected |
|----------|--------|-------------|----------|
| Link availability | Auto-init + query | StockReservedConsumer initializes Iyzico; clients query payment status/link. | Yes |
| Link availability | Explicit POST starts it | Frontend calls POST /payments/checkout to initialize Iyzico; weakens PAY-02. | |
| Link availability | Hybrid ensure endpoint | StockReserved auto-inits; POST is idempotent and returns/ensures the same link. | |
| Response artifact | paymentPageUrl only | Safest hosted-page redirect; avoids embedding complexity until frontend work. | Yes |
| Response artifact | Form content only | Matches embedded wording but is more fragile and UI-specific. | |
| Response artifact | Both if available | Maximum flexibility; frontend decides redirect vs embed later. | |
| Not-ready behavior | 202 pending status | Return status=PENDING/INITIALIZING and let clients poll. | Yes |
| Not-ready behavior | Block briefly | Wait for the link; risks gateway timeout and flaky UX. | |
| Not-ready behavior | 409 not ready | Simple retry signal but treats normal async behavior as an error. | |
| Repeated requests | Reuse active link | Return the same active paymentPageUrl until timeout or final callback. | Yes |
| Repeated requests | Always create new link | Fresh token every time; risks multiple sessions per order. | |
| Repeated requests | Create new after failed attempt | Reuse while pending, generate a new link if failed and retryable. | |

**User's choices:** Auto-init + query; `paymentPageUrl` only; `202` pending status; Reuse active link.
**Notes:** The current `api-contracts.md` `POST /payments/checkout` entry must be reconciled during planning so it cannot create duplicate checkout sessions.

---

## OpenCode's Discretion

- Callback result page details, poll interval hints, internal DTO naming, and timeout duration were not discussed; planner can choose standard approaches within roadmap requirements.

## Deferred Ideas

- Cloudflare Tunnel docker-compose sidecar hardening - Phase 11.
- Checkout Form embedding through `checkoutFormContent` - Phase 10 if needed.
