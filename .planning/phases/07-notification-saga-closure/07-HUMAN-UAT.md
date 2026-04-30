---
status: partial
phase: 07-notification-saga-closure
source: [07-VERIFICATION.md, 07-06-SMOKE-RUNBOOK.md]
started: 2026-04-30T22:00:00Z
updated: 2026-04-30T22:00:00Z
---

## Current Test

[awaiting human testing]

## Tests

### 1. Eureka registration of NOTIFICATION-SERVICE
expected: After `./gradlew :notification-service:jibDockerBuild && docker compose --env-file .env up -d`, the Eureka dashboard at `http://localhost:8761` lists `NOTIFICATION-SERVICE` with status UP.
result: [pending]

### 2. CorrelationId cross-service trace in flat docker-compose logs
expected: Following Smoke Runbook Step 9, picking a `correlationId` from the `notification.notifications` audit table and grepping `docker compose logs` shows that ID appearing in log lines from at least two distinct services (e.g., order-service + notification-service).
result: [pending]

### 3. Smoke runbook full sign-off
expected: Operator runs Steps 0–9 of `07-06-SMOKE-RUNBOOK.md` on a live docker-compose stack and ticks the Step 10 sign-off table to PASS for at least the OrderConfirmed, OrderCancelled, and UserRegistered rows. PaymentFailed row may be SKIPPED if the Cloudflare Tunnel / Iyzico sandbox is not set up.
result: [pending]

## Summary

total: 3
passed: 0
issues: 0
pending: 3
skipped: 0
blocked: 0

## Gaps
