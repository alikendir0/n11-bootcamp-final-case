---
phase: 06
slug: payment-iyzico
status: draft
nyquist_compliant: true
wave_0_complete: false
created: 2026-04-30
---

# Phase 06 — Validation Strategy

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit Jupiter + Spring Boot Test + Testcontainers + WireMock-style fake adapters |
| **Config file** | Gradle multi-module `build.gradle.kts` files |
| **Quick run command** | `./gradlew :payment-service:test --tests '*Payment*' --tests '*Iyzico*'` |
| **Full suite command** | `./gradlew :payment-service:test :order-service:test :infra-tests:test` |
| **Estimated runtime** | ~120-360 seconds depending on Testcontainers warm state |

## Sampling Rate

- **After every task commit:** run the plan's targeted Gradle test command.
- **After every wave:** run all tests for modules touched in that wave.
- **Before verify-work:** run `./gradlew :payment-service:test :order-service:test :infra-tests:test`.
- **Max feedback latency:** one plan wave.

## Per-task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 06-01-01 | 01 | 1 | PAY-01 | T-06-01 | Secrets loaded from env/config only | config/unit | `./gradlew :payment-service:test --tests '*IyzicoPropertiesTest'` | ❌ W0 | ⬜ pending |
| 06-01-02 | 01 | 1 | PAY-01 | T-06-02 | Payment schema supports token/link/terminal dedup | data | `./gradlew :payment-service:test --tests '*PaymentRepository*'` | ❌ W0 | ⬜ pending |
| 06-02-01 | 02 | 1 | PAY-01 | T-06-03 | Payment context stays behind internal service boundary | unit/mvc | `./gradlew :order-service:test --tests '*PaymentContext*'` | ❌ W0 | ⬜ pending |
| 06-03-01 | 03 | 2 | PAY-02, PAY-03 | T-06-04 | One active checkout per order, no duplicate initialize | integration | `./gradlew :payment-service:test --tests '*StockReserved*'` | ✅ | ⬜ pending |
| 06-04-01 | 04 | 3 | PAY-04, PAY-05 | T-06-05 | Public callback verified via retrieve/signature before mutation | mvc/integration | `./gradlew :payment-service:test --tests '*Callback*'` | ❌ W0 | ⬜ pending |
| 06-05-01 | 05 | 4 | PAY-06, QUAL-05 | T-06-06 | Timeout emits same `payment.failed` compensation event | integration | `./gradlew :payment-service:test --tests '*Timeout*'` | ❌ W0 | ⬜ pending |
| 06-05-02 | 05 | 4 | QUAL-05 | T-06-07 | Payment failure releases stock and cancels order | e2e | `./gradlew :infra-tests:test --tests '*PaymentFailureCompensationE2ETest'` | ❌ W0 | ⬜ pending |
| 06-06-01 | 06 | 5 | PAY-07 | T-06-08 | Real sandbox callback reaches gateway and finalizes order | manual smoke | `test -f .planning/phases/06-payment-iyzico/06-06-SMOKE-RUNBOOK.md` | ❌ W0 | ⬜ pending |

## Wave 0 Requirements

- Existing JUnit/Testcontainers infrastructure covers this phase.
- New tests are created inside the plans before/with the production code they verify.

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Full Iyzico sandbox hosted payment with card `5528 7900 0000 0008` | PAY-07 | Requires live Iyzico sandbox credentials, browser hosted form, and a public tunnel callback | Follow `06-06-SMOKE-RUNBOOK.md`; record callback URL, token, final order status, and compensation proof if failure path is tested |

## Validation Sign-Off

- [x] All tasks have automated verification or an explicit human smoke gate.
- [x] Sampling continuity: no 3 consecutive tasks without automated verify.
- [x] No watch-mode flags.
- [x] Feedback latency bounded by per-plan Gradle test commands.

**Approval:** draft 2026-04-30
