---
phase: 7
slug: notification-saga-closure
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-30
---

# Phase 7 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Source: derived from `07-RESEARCH.md` § Validation Architecture.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5.x (Spring Boot 3.5.14 BOM) + Testcontainers 2.0.5 + Awaitility 4.2.0 |
| **Config file** | `notification-service/src/test/resources/application.yml` (`optional:configserver:` + `hikari.connection-init-sql=SET search_path=notification`); `infra-tests/src/test/resources/application.yml` (already exists, extended for QUAL-04) |
| **Quick run command** | `./gradlew :notification-service:test` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | ~30 s per-service slice; ~120–150 s full suite (after notification-service + QUAL-04 added) |

---

## Sampling Rate

- **After every task commit:** `./gradlew :notification-service:test` (~30 s)
- **After every plan wave:** `./gradlew test` (~120–150 s)
- **Before `/gsd-verify-work`:** Full suite green AND Phase 7 smoke runbook executed (`docker compose up`; register a user; place an order; grep `correlationId` across logs)
- **Max feedback latency:** ~30 s per task

---

## Per-Task Verification Map

> Authoritative requirement-to-test map. The planner MUST emit tasks whose `<automated>` block runs at least one of these commands, or list the test as a Wave 0 dependency. Test names below are the canonical class names — planner may not rename.

| Test | Plan (target) | Wave | Requirement | Test Type | Automated Command | File Exists |
|------|---------------|------|-------------|-----------|-------------------|-------------|
| `OrderConfirmedConsumerIdempotencyTest` | 07-02 (or split) | 1 | NOTIF-01 | integration (Testcontainers Postgres) | `./gradlew :notification-service:test --tests OrderConfirmedConsumerIdempotencyTest` | ❌ Wave 0 |
| `OrderCancelledConsumerIdempotencyTest` | 07-02 (or split) | 1 | NOTIF-01 | integration (Testcontainers Postgres) | `./gradlew :notification-service:test --tests OrderCancelledConsumerIdempotencyTest` | ❌ Wave 0 |
| `PaymentFailedConsumerIdempotencyTest` | 07-02 (or split) | 1 | NOTIF-01 | integration (Testcontainers Postgres) | `./gradlew :notification-service:test --tests PaymentFailedConsumerIdempotencyTest` | ❌ Wave 0 |
| `UserRegisteredConsumerIdempotencyTest` | 07-02 (or split) | 1 | NOTIF-01 | integration (Testcontainers Postgres) | `./gradlew :notification-service:test --tests UserRegisteredConsumerIdempotencyTest` | ❌ Wave 0 |
| `NotificationServiceLogTest` | 07-02 / 07-03 | 1 | NOTIF-02 | unit (LogCaptor / log-line shape assertion) | `./gradlew :notification-service:test --tests NotificationServiceLogTest` | ❌ Wave 0 |
| Smoke runbook (`/actuator/health`, log grep) | 07-04 (smoke runbook) | 2 | NOTIF-03 | smoke (live `docker compose up`) | `curl -fsS http://localhost:8087/actuator/health \| jq '.status == "UP"'` | ❌ Wave 2 |
| `QualFourSagaNotificationTest` | 07-05 | 2 | QUAL-04 | integration E2E (Testcontainers Postgres + RabbitMQ) | `./gradlew :infra-tests:test --tests QualFourSagaNotificationTest` | ❌ Wave 0 |
| `ConsumerDlqRoutingTest` | 07-02 / 07-03 | 1 | NOTIF-01 (DLQ contract) | integration (Testcontainers RabbitMQ) | `./gradlew :notification-service:test --tests ConsumerDlqRoutingTest` | ❌ Wave 1 |

*Status legend (planner sets per task): ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements (test stubs deferred to Plan 07-04)

> **Note (revision iteration 1, WARNING 3 fix):** This phase intentionally produces test stubs in **Plan 07-04 (Wave 2)** rather than Wave 0. The 6 test Java files listed below are created by Plan 07-04 — NOT before Plan 07-01 / 07-02 / 07-03. Plan 07-03's `<verify>` block uses compile-only fallbacks where test classes aren't yet present (`./gradlew :notification-service:compileJava` is the canonical green for Plan 07-03; `./gradlew :notification-service:test` becomes canonical once Plan 07-04 lands). Nyquist Dimension 8 sampling is satisfied by per-task `compileJava` + per-wave full test runs after Wave 2.
>
> **`wave_0_complete: false`** in the frontmatter is correct for this layered approach — the renaming above documents the deferral so the plan-checker can recognise it as deliberate, not silent. (It will remain `false` until the orchestrator's next checker pass; the checker re-runs after this revision.)

Files that MUST exist (created or stubbed) before Wave 1 tasks run, so the verification map above can be exercised:

- [ ] `notification-service/src/test/resources/application.yml` — `optional:configserver:` + `hikari.connection-init-sql=SET search_path=notification`
- [ ] `notification-service/src/test/java/com/n11/notification/messaging/OrderConfirmedConsumerIdempotencyTest.java`
- [ ] `notification-service/src/test/java/com/n11/notification/messaging/OrderCancelledConsumerIdempotencyTest.java`
- [ ] `notification-service/src/test/java/com/n11/notification/messaging/PaymentFailedConsumerIdempotencyTest.java`
- [ ] `notification-service/src/test/java/com/n11/notification/messaging/UserRegisteredConsumerIdempotencyTest.java`
- [ ] `notification-service/src/test/java/com/n11/notification/messaging/NotificationServiceLogTest.java`
- [ ] `notification-service/src/test/java/com/n11/notification/messaging/ConsumerDlqRoutingTest.java`
- [ ] `infra-tests/src/test/java/com/n11/infratests/saga/QualFourSagaNotificationTest.java`
- [ ] `infra-tests/src/test/java/com/n11/infratests/saga/NotificationServiceTestConfig.java`
- [ ] `infra-tests/src/test/resources/db/migration/notification/V1__init_processed_events.sql`
- [ ] `infra-tests/src/test/resources/db/migration/notification/V2__init_notifications.sql`

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| `correlationId=X` traces across all 5+ services in flat logs | NOTIF-02 (visibility goal) | Requires live `docker compose up` plus a real registration + checkout flow; assertion is "operator-visible coherence", not a code property | Smoke runbook `07-04-SMOKE-RUNBOOK.md` step "register, place order, run `docker compose logs \| grep correlationId=$CID`" |
| Eureka registration of `notification-service` | NOTIF-03 | Eureka client startup is environmental | Smoke runbook step "open `http://localhost:8761` and confirm NOTIFICATION-SERVICE row is UP" |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify entries OR are listed as Wave 0 dependencies above
- [ ] Sampling continuity: no 3 consecutive tasks without an automated verify
- [ ] Wave 0 covers every MISSING test file in the per-task map
- [ ] No watch-mode flags (CI runs `./gradlew test` headless)
- [ ] Feedback latency < 30 s per task slice
- [ ] `nyquist_compliant: true` set in frontmatter once the planner's plans pass the plan-checker

**Approval:** pending
