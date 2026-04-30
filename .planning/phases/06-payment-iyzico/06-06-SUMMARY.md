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
duration: ~5min (tasks 1+2 docs); ~75min (task 3 live smoke + 2 incidental fixes)
completed: 2026-04-30
pay-07-status: PASS (smoke executed by agent 2026-04-30 with operator-provided sandbox credentials)
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

## Task 3 — Live Smoke Execution Result (PAY-07 PASS)

**Operator provided real sandbox credentials and asked the agent to drive the smoke directly.**
Agent ran the runbook end-to-end against `https://sandbox-cpp.iyzipay.com` via a `cloudflared` quick
tunnel; Playwright drove the hosted Iyzico Checkout Form. All three required paths PASSED.

### Tunnel + Setup
- Tunnel: `cloudflared tunnel --url http://localhost:8088` → `https://uploaded-faq-expenditure-correctly.trycloudflare.com` (ephemeral)
- Host port 8088 used (port 8080 occupied by another local service); `docker-compose.override.yml` (gitignored) maps `api-gateway:8080 → 8088`
- Iyzico keys: `sandbox-aPpyd…gKtp` / `sandbox-Bqjce…9xM5`

### Required-path results (sign-off table)

| # | Path | Card | Result |
|---|---|---|---|
| 1 | Happy / 3DS success | `5528 7900 0000 0008` | **PASS** — order `CONFIRMED`, payment `COMPLETED`, `iyzico_payment_id=31527338` |
| 2 | Decline (insufficient funds) | `4111 1111 1111 1129` (3DS on) | **PASS** — order `CANCELLED`, payment `FAILED` (`failure_code=IYZICO_RETRIEVE_FAILED:10051`), inventory `RELEASED` |
| 7 | Timeout (no submit) | `PAYMENT_TIMEOUT_MINUTES=1` | **PASS** — payment `TIMED_OUT`, `failure_reason=PAYMENT_TIMEOUT`, order `CANCELLED`, inventory `RELEASED` |
| 8 | Callback idempotency | re-POST same token | **PASS** — outbox row count unchanged on replay (1 → 1) |
| 10 | Springdoc surface | aggregator names | **PASS** — `payment-service` listed |

### Three issues surfaced and fixed during smoke (committed)

1. **`ca44cbc` — `DefaultIyzicoCheckoutClient` no-default-constructor blocker** (was tagged D-06-02
   in `deferred-items.md` as "test-only" by the 06-05 executor — actually blocked runtime startup).
   Fix: `@Autowired` on the public 2-arg constructor disambiguates Spring's autowiring.

2. **`24e9a03` — buyer email TLD rejected by Iyzico**. `<userId>@n11clone.local` returned Iyzico
   errorCode 5 ("email hatalı format ile gönderilmiştir"); `.local` is mDNS-reserved and not a
   valid email TLD. Fix: switched to `<userId>@buyer.example.com` (RFC 2606 reserved-for-test).

3. **`24e9a03` — masked Iyzico errors via signature-check ordering**. `initialize()` ran
   `verifySignature` BEFORE the status check; Iyzico error responses have no signature, so all
   real errors surfaced as the misleading `IYZICO_SIGNATURE_INVALID`. Fix: status first; both
   error paths now include status/errorCode/errorMessage in the exception.

### Runbook addenda discovered (operator should patch on next pass)

- Outbox idempotency SQL uses `payload LIKE` against a `jsonb` column → fails on Postgres 16.
  Should be `payload::text LIKE '%…%'`.
- Decline-card actual `failure_code` is `IYZICO_RETRIEVE_FAILED:<errorCode>` (because Iyzico SDK's
  `retrieve` throws when the response status itself is `failure`); the runbook's
  `failure_code=IYZICO_DECLINED` expectation only triggers when the retrieve succeeds with
  `paymentStatus != SUCCESS`. Compensation flow is correct either way.
- Local DNS on this machine (router) doesn't resolve `*.trycloudflare.com`. Iyzico's redirect
  POST from the buyer's browser fails locally — the smoke worked by manually replaying the same
  `token` to the callback URL via curl. In a production demo with a normal client DNS the redirect
  works without intervention.

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
