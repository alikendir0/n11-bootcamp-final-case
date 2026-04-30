---
phase: 06-payment-iyzico
plan: 06
subsystem: payments
tags: [iyzico, cloudflare-tunnel, ngrok, runbook, sandbox, documentation, operator-verification]

# Dependency graph
requires:
  - phase: 06-payment-iyzico
    provides: Iyzico Checkout Form integration (Plans 06-01..06-03), callback finalization + public payment API (06-04), timeout compensation + failure E2E (06-05)
  - phase: 05-cart-order-skeleton
    provides: order placement, cart-service, idempotency, saga topology (Phase 5 runbook pattern)
provides:
  - payment-service/README.md with Cloudflare Tunnel primary + ngrok fallback setup + full env var matrix
  - .env.example updated with explicit Iyzico and PUBLIC_BASE_URL placeholders
  - .planning/phases/06-payment-iyzico/06-06-SMOKE-RUNBOOK.md — deterministic 10-step live sandbox verification script
  - Iyzico Sandbox Test Card Matrix (7 paths) with PASS/FAIL sign-off checklist
  - Human verification checkpoint for PAY-07 (pending operator approval)
affects: [phase-07-notification, phase-08-ai-service, phase-09-mcp-server, phase-10-frontend, phase-11-devops-deploy]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Operator runbook structure: preflight tests, Jib rebuild, stack boot, env verify, user/cart/order setup, paymentPageUrl polling, test-card matrix, compensation verification, idempotency replay, sign-off checklist"
    - "PUBLIC_BASE_URL as the single derivation source for Iyzico callbackUrl — documented in both README and .env.example"

key-files:
  created:
    - payment-service/README.md
    - .planning/phases/06-payment-iyzico/06-06-SMOKE-RUNBOOK.md
  modified:
    - .env.example

key-decisions:
  - "Cloudflare Tunnel is documented as the primary path (D-01/D-03); docker-compose cloudflared sidecar deferred to Phase 11 per D-03."
  - "ngrok fallback documented so sandbox testing or demo recovery is not blocked by Cloudflare setup failure (D-04)."
  - "PUBLIC_BASE_URL is the single env var for callback URL derivation; README explicitly warns against introducing a separate IYZICO_CALLBACK_URL (D-02)."
  - "PAY-07 operator must exercise at minimum: row 1 (happy path), one decline row (any of rows 2-3), and row 7 (timeout) — not just the success card."
  - "Timeout runbook sets PAYMENT_TIMEOUT_MINUTES=1 + PAYMENT_TIMEOUT_SCAN_DELAY_MS=15000 for the smoke test to avoid a 15-minute wait."
  - "Task 3 (live sandbox verification) is a blocking human-verify checkpoint — not executable by the AI agent; documented as pending human operator approval."

patterns-established:
  - "Phase runbook pattern for external payment provider: preflight automated tests, rebuild images, verify public URL, saga setup, paymentPageUrl poll, hosted form completion, compensation path, idempotency replay, sign-off table."

requirements-completed: [PAY-05, PAY-07]

# Metrics
duration: ~5min (tasks 1+2; task 3 pending human verification)
completed: 2026-04-30
---

# Phase 06 Plan 06: Sandbox Runbook & Human Verification Checkpoint Summary

**Cloudflare Tunnel primary + ngrok fallback runbooks documented in payment-service/README.md; Phase 6 live sandbox smoke script created with 7-path Iyzico test card matrix and operator sign-off checklist; PAY-07 human verification checkpoint pending operator approval.**

## Performance

- **Duration:** ~5 min (tasks 1 and 2 complete; task 3 is a human-verify gate)
- **Started:** 2026-04-30T13:30:00Z
- **Completed:** 2026-04-30T13:32:00Z (tasks 1+2); task 3 awaiting operator
- **Tasks:** 2 of 3 complete (task 3 is a blocking human-verify checkpoint)
- **Files modified:** 3 (2 created + 1 modified)

## Accomplishments

- Created `payment-service/README.md` with complete Cloudflare Tunnel primary setup (cloudflared login/create/route/run) and ngrok fallback (`ngrok config add-authtoken` + `ngrok http 8080`), full env var matrix (IYZICO_API_KEY, IYZICO_SECRET_KEY, PUBLIC_BASE_URL, PAYMENT_TIMEOUT_MINUTES, PAYMENT_TIMEOUT_SCAN_DELAY_MS, IYZICO_DEMO_BUYER_IDENTITY_NUMBER), sandbox test card table, and callback troubleshooting section (including Iyzico error code 5062 fix in DefaultIyzicoCheckoutClient basket-line-price reconciliation).
- Updated `.env.example` with explicit Iyzico/PUBLIC_BASE_URL placeholders (no real secrets; sections organized by phase for discoverability).
- Created `06-06-SMOKE-RUNBOOK.md` with 11-step deterministic sandbox script: preflight test run, Jib rebuild, stack boot, PUBLIC_BASE_URL verification, register/login/address/cart/order flow, paymentPageUrl polling, Iyzico Sandbox Test Card Matrix (7 paths), compensation verification for both decline and timeout paths, callback idempotency replay, Springdoc surface check, and a sign-off checklist requiring PASS for rows 1 (happy), 7 (timeout), and at least one decline row.

## Task Commits

Each task was committed atomically:

1. **Task 1: write payment-service tunnel and Iyzico runbook** — `5dc4e2c` (docs)
2. **Task 2: create Phase 6 live smoke runbook** — `dbcad2d` (docs)
3. **Task 3: verify live Iyzico sandbox callback flow** — PENDING HUMAN VERIFICATION

**Plan metadata:** commit pending (this SUMMARY).

## Files Created/Modified

- `payment-service/README.md` — Cloudflare Tunnel primary + ngrok fallback setup; env var matrix; Iyzico sandbox checkout flow; sandbox test card table; callback troubleshooting (including 5062 basket-price reconciliation fix).
- `.planning/phases/06-payment-iyzico/06-06-SMOKE-RUNBOOK.md` — Deterministic 10-step live sandbox runbook with 7-path Iyzico test card matrix (success card `5528 7900 0000 0008`, decline cards, mdStatus edge cards, timeout path), compensation verification, callback idempotency replay, and PAY-07 sign-off checklist.
- `.env.example` — Added explicit IYZICO_API_KEY, IYZICO_SECRET_KEY, PUBLIC_BASE_URL, PAYMENT_TIMEOUT_MINUTES, PAYMENT_TIMEOUT_SCAN_DELAY_MS, IYZICO_DEMO_BUYER_IDENTITY_NUMBER placeholders with comments. No real secrets.

## Decisions Made

- **Cloudflare Tunnel is primary; docker-compose sidecar deferred to Phase 11.** D-03 is explicit: the compose `cloudflared` sidecar belongs to Phase 11 deploy hardening. This plan only documents the manual CLI path for Phase 6 sandbox testing.
- **ngrok documented as fallback.** D-04 — if Cloudflare setup fails, the operator can get a working HTTPS callback URL with three commands.
- **Timeout runbook uses PAYMENT_TIMEOUT_MINUTES=1.** The default 15-minute timeout would make the runbook impractical for interactive testing. The runbook overrides to 1 minute via env var override on `docker compose up -d payment-service` and restores the default after.
- **PAY-07 requires minimum 3 paths.** The plan requires at least: row 1 (happy `5528 7900 0000 0008`), one decline row (e.g. `4111 1111 1111 1129`), and row 7 (timeout). This exercises happy, failure, and timeout compensation — all three saga terminal transitions.

## Deviations from Plan

None — plan executed exactly as written. Tasks 1 and 2 are documentation-only plans; no code was written, no deviations triggered.

## Task 3 — Pending Human Verification

**Task 3 is a `checkpoint:human-verify` gate.** The AI agent cannot perform live Iyzico sandbox testing (requires real credentials, a browser, a public tunnel URL, and live Iyzico API calls).

The operator must:
1. Provide `IYZICO_API_KEY`, `IYZICO_SECRET_KEY`, and `PUBLIC_BASE_URL` via Cloudflare Tunnel or ngrok.
2. Follow `.planning/phases/06-payment-iyzico/06-06-SMOKE-RUNBOOK.md`.
3. Confirm `GET /api/v1/payments/{orderId}` returns a `paymentPageUrl`.
4. Complete the Iyzico hosted payment with card `5528 7900 0000 0008` (and OTP `283356`).
5. Confirm Iyzico callback reaches payment-service and order status becomes `CONFIRMED`.
6. Confirm a failure card or timeout emits `payment.failed`, inventory releases stock, and order becomes `CANCELLED`.
7. Type "approved" to signal PAY-07 passed.

## Known Stubs

None. The README and runbook are documentation artifacts — no UI stubs or placeholder data flows.

## Threat Flags

T-06-15 (Information Disclosure — `.env.example`/README) is explicitly mitigated: `.env.example` contains placeholders only with empty values; the README warns "NEVER commit real keys" and directs operators to the gitignored `.env`. T-06-16 (Spoofing — public tunnel) mitigated: gateway callback route is protected by server-side `CheckoutForm.retrieve` + `verifySignature`, not callback body trust alone. T-06-17 (Availability — Cloudflare setup failure) mitigated: ngrok fallback documented per D-04.

## Self-Check: PASSED

- `payment-service/README.md` — FOUND
- `.env.example` updated with IYZICO_API_KEY, IYZICO_SECRET_KEY, PUBLIC_BASE_URL — FOUND
- `.planning/phases/06-payment-iyzico/06-06-SMOKE-RUNBOOK.md` — FOUND
- Task commits in git history: `5dc4e2c`, `dbcad2d` — FOUND
- `grep -c 'PUBLIC_BASE_URL' payment-service/README.md` → 11 (>0) — PASS
- `grep -c 'IYZICO_API_KEY' .env.example` (non-comment) → 1 — PASS
- `grep -c '5528 7900 0000 0008' 06-06-SMOKE-RUNBOOK.md` → 3 (>0) — PASS

---
*Phase: 06-payment-iyzico*
*Completed: 2026-04-30 (tasks 1+2); task 3 pending human verification*
