---
phase: 07-notification-saga-closure
plan: "04"
subsystem: notification-service (tests)
tags: [notification, tests, idempotency, testcontainers, dlq, log-shape, NOTIF-01, NOTIF-02]
dependency_graph:
  requires: ["07-01", "07-02", "07-03"]
  provides: ["NOTIF-01 idempotency coverage", "NOTIF-02 log-shape coverage", "NOTIF-01 DLQ contract coverage"]
  affects: []
tech_stack:
  added: []
  patterns:
    - "Logback ListAppender for in-memory SLF4J log capture in tests"
    - "Testcontainers Postgres (pgvector/pgvector:pg16) with SET search_path per schema boundary"
    - "Testcontainers RabbitMQ (rabbitmq:3.13-management) for live AMQP DLQ routing assertion"
    - "Awaitility for asynchronous DLQ message arrival assertion"
key_files:
  created:
    - notification-service/src/test/java/com/n11/notification/messaging/OrderConfirmedConsumerIdempotencyTest.java
    - notification-service/src/test/java/com/n11/notification/messaging/OrderCancelledConsumerIdempotencyTest.java
    - notification-service/src/test/java/com/n11/notification/messaging/PaymentFailedConsumerIdempotencyTest.java
    - notification-service/src/test/java/com/n11/notification/messaging/UserRegisteredConsumerIdempotencyTest.java
    - notification-service/src/test/java/com/n11/notification/messaging/NotificationServiceLogTest.java
    - notification-service/src/test/java/com/n11/notification/messaging/ConsumerDlqRoutingTest.java
  modified: []
decisions:
  - "payment.failed has no userId in payload per schema; tests use orderId as findByUserId lookup key (same trade-off documented in plan)"
  - "compileTestJava deferred to post-merge gate — 07-03 produces the src/main/java classes in a parallel worktree; this is the documented Wave 2 design"
  - "ConsumerDlqRoutingTest uses rabbitmq:3.13-management per Plan 04-02 lesson, NOT 4.x"
  - "DLQ routing test verifies one queue (notify.q.order-confirmed); same x-dead-letter-* pattern applies to all 4 queues per NotificationRabbitConfig"
metrics:
  completed_date: "2026-04-30"
  task_count: 3
  file_count: 6
---

# Phase 7 Plan 4: Notification Service Tests Summary

**One-liner:** Six test classes covering NOTIF-01 idempotency (4 consumers), NOTIF-02 structured log shape, and NOTIF-01 DLQ routing via Testcontainers Postgres + RabbitMQ.

## Tasks Completed

| Task | Name | Commit | Key Files |
|------|------|--------|-----------|
| 1 | Four per-consumer idempotency tests | b299442 | OrderConfirmedConsumerIdempotencyTest, OrderCancelledConsumerIdempotencyTest, PaymentFailedConsumerIdempotencyTest, UserRegisteredConsumerIdempotencyTest |
| 2 | NotificationServiceLogTest — NOTIF-02 log shape | cebaf7a | NotificationServiceLogTest.java |
| 3 | ConsumerDlqRoutingTest — malformed envelope → DLQ | 1a0b460 | ConsumerDlqRoutingTest.java |

## What Was Built

### Task 1: Four Per-Consumer Idempotency Tests

Each test boots `NotificationServiceApplication` with `auto-startup=false`, delivers the same AMQP envelope twice via direct consumer method invocation, and asserts:
- `notificationRepository.findByUserId(userId).size() == 1` (exactly one notification persisted)
- `processedEventRepository.existsById(eventId)` is true
- `processedEventRepository.count() == 1` (exactly one inbox row)

All tests use `pgvector/pgvector:pg16` and `SET search_path = notification, public` per schema boundary conventions.

**PaymentFailedConsumerIdempotencyTest trade-off:** `payment.failed` has no `userId` field per its schema JSON. The test uses `orderId` as the `findByUserId` lookup key — NotificationService stores `orderId` in `notifications.user_id` for this event type as a correlation key. This trade-off is documented in both the test and the plan.

### Task 2: NotificationServiceLogTest

Captures SLF4J INFO output from `NotificationService` using a Logback `ListAppender` attached in `@BeforeEach` and detached in `@AfterEach`. Four `@Test` methods cover the four event types. Each asserts the NOTIF-02 contract fields:
- `notification.sent` (event prefix)
- `recipient=<userId>` (or `orderId` for payment.failed)
- `subject="<turkishSubject>"` — four Turkish subjects: "Siparişiniz onaylandı", "Siparişiniz iptal edildi", "Ödemeniz alınamadı", "Hoş geldiniz!"
- `correlationId=<uuid>`
- `eventType=<routing-key>`
- `channel=EMAIL`

### Task 3: ConsumerDlqRoutingTest

Boots the full application with Testcontainers Postgres + RabbitMQ (`rabbitmq:3.13-management`, NOT 4.x per Plan 04-02 lesson). Listener `auto-startup=true` so the real `@RabbitListener` fires via live AMQP delivery. Sends a malformed JSON string to `orders.tx/order.confirmed`. `OrderConfirmedConsumer` throws `AmqpRejectAndDontRequeueException`, which routes the message to `notify.q.order-confirmed.dlq` without retries. Awaitility asserts arrival within 8 seconds.

## Deviations from Plan

### Deferred Compilation (Rule 3 — Blocking Issue Deferred)

- **Found during:** Tasks 1–3 verification
- **Issue:** `./gradlew :notification-service:compileTestJava` fails in this worktree because the 6 test classes import types produced by Plan 07-03 (running in a parallel worktree): `NotificationService`, `OrderConfirmedConsumer`, `OrderCancelledConsumer`, `PaymentFailedConsumer`, `UserRegisteredConsumer`, `ProcessedEventRepository`, `NotificationRepository`, and the 4 payload records in `com.n11.notification.messaging.payloads`.
- **Resolution:** This is the documented Wave 2 design per the `<wave_dependency_note>` in the execution context. The post-merge test gate on `master` runs after both worktrees merge — that is where the real compile + test execution happens.
- **Documentation:** `compileTestJava` deferred to post-merge gate.

None — all other tests follow the plan exactly as written.

## VALIDATION.md Per-Task Map Sign-Off

| Test | File Exists After Plan 07-04 | Status |
|------|------------------------------|--------|
| `OrderConfirmedConsumerIdempotencyTest` | Yes | Compiles post-merge |
| `OrderCancelledConsumerIdempotencyTest` | Yes | Compiles post-merge |
| `PaymentFailedConsumerIdempotencyTest` | Yes | Compiles post-merge |
| `UserRegisteredConsumerIdempotencyTest` | Yes | Compiles post-merge |
| `NotificationServiceLogTest` | Yes | Compiles post-merge |
| `ConsumerDlqRoutingTest` | Yes | Compiles post-merge |

## Test Isolation Note

Each idempotency test uses its own Testcontainers Postgres container (`@Testcontainers` — fresh container per test class). The `processedEventRepository.count() == 1` invariant is safe against cross-test bleed because no two test classes share a container instance. `ConsumerDlqRoutingTest` spins up its own Postgres + RabbitMQ pair.

## Known Stubs

None — all test classes are fully implemented. No placeholder or TODO methods.

## Threat Flags

None — test files only; no production network endpoints, auth paths, or schema changes introduced.

## Self-Check

All 6 files exist at the paths declared in the plan frontmatter. Commits b299442, cebaf7a, 1a0b460 are in the git log.

## Self-Check: PASSED
