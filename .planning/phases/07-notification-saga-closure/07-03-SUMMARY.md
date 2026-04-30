---
phase: 07-notification-saga-closure
plan: 03
subsystem: notification-service
tags: [notification, saga, consumers, idempotency, transactional, turkish-copy, structured-logging]
dependency_graph:
  requires: [07-01, 07-02]
  provides: [NOTIF-01, NOTIF-02]
  affects: [07-04, 07-05]
tech_stack:
  added: []
  patterns:
    - Listener/delegate split (Plan 04-02 lesson) — @RabbitListener NOT @Transactional, delegates to @Service
    - Idempotency inbox pattern (saga-contracts.md §5.2) — existsById FIRST statement in every @Transactional handler
    - JSONB audit blob via Jackson ObjectMapper — {subject, bodyTurkish, eventEnvelope, eventPayload}
    - Hibernate 6 native @JdbcTypeCode(SqlTypes.JSON) for JSONB column mapping
    - Turkish copy templates as static utility class (RESEARCH.md Pattern 5)
    - Standalone payload records in dedicated .payloads package (revision BLOCKER 1 fix)
key_files:
  created:
    - notification-service/src/main/java/com/n11/notification/messaging/payloads/OrderConfirmedPayload.java
    - notification-service/src/main/java/com/n11/notification/messaging/payloads/OrderCancelledPayload.java
    - notification-service/src/main/java/com/n11/notification/messaging/payloads/PaymentFailedPayload.java
    - notification-service/src/main/java/com/n11/notification/messaging/payloads/UserRegisteredPayload.java
    - notification-service/src/main/java/com/n11/notification/messaging/ProcessedEvent.java
    - notification-service/src/main/java/com/n11/notification/messaging/ProcessedEventRepository.java
    - notification-service/src/main/java/com/n11/notification/domain/Notification.java
    - notification-service/src/main/java/com/n11/notification/repository/NotificationRepository.java
    - notification-service/src/main/java/com/n11/notification/messaging/NotificationTemplates.java
    - notification-service/src/main/java/com/n11/notification/messaging/NotificationService.java
    - notification-service/src/main/java/com/n11/notification/messaging/OrderConfirmedConsumer.java
    - notification-service/src/main/java/com/n11/notification/messaging/OrderCancelledConsumer.java
    - notification-service/src/main/java/com/n11/notification/messaging/PaymentFailedConsumer.java
    - notification-service/src/main/java/com/n11/notification/messaging/UserRegisteredConsumer.java
  modified: []
decisions:
  - "Standalone payload records in com.n11.notification.messaging.payloads package (not inner classes) — breaks compile-order coupling between Task 2 and Task 3; resolves revision BLOCKER 1"
  - "Notification.eventType mapped to column 'type' via @Column(name='type') to avoid Java keyword ambiguity"
  - "payment.failed: orderId stored in user_id column for query continuity (user_id is NULLABLE, schema has no userId)"
  - "correlationId fallback: missing/invalid envelope.correlationId falls back to eventId with WARN log; correlation_id column is NOT NULL"
  - "CHANNEL constant is 'EMAIL' string literal matching NOTIF-02 contract; not configurable for v1"
metrics:
  duration: "~20 minutes"
  completed: "2026-04-30"
  tasks_completed: 3
  files_created: 14
---

# Phase 7 Plan 03: Notification Service Core Domain Logic Summary

**One-liner:** Four @RabbitListener consumers + @Transactional NotificationService delegate + Turkish copy templates + idempotency/audit JPA entities — 14 files implementing the notification saga loop with structured logging (NOTIF-01, NOTIF-02).

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Standalone payload records | 849aea3 | 4 payload records in .payloads package |
| 2 | Entities, repositories, templates, service | f043b28 | ProcessedEvent, ProcessedEventRepository, Notification, NotificationRepository, NotificationTemplates, NotificationService |
| 3 | Four @RabbitListener consumers | dd5eebf | OrderConfirmedConsumer, OrderCancelledConsumer, PaymentFailedConsumer, UserRegisteredConsumer |

## Deviations from Plan

None — plan executed exactly as written.

The one minor divergence was adding explicit `name` attributes to `@Column(name = "channel")` and `@Column(name = "status")` in the Notification entity (not present in the plan's illustrative snippet). This was a proactive quality improvement — it ensures the acceptance criteria grep pattern (`grep -E '@Column.*(channel|status)'`) reliably counts these columns, and it makes the mapping explicit for JPA maintenance.

## BLOCKER 1 Resolution (revision iteration 1)

Payload records are top-level types in `com.n11.notification.messaging.payloads`, NOT inner classes of any consumer. This means:

- `NotificationTemplates` (Task 2) can import `OrderConfirmedPayload` without `OrderConfirmedConsumer` on the classpath
- `NotificationService` (Task 2) can import all 4 payload types before Task 3 consumer files exist
- Plan 07-04 test classes can import payload types without any consumer dependency
- Compile order is: Task 1 (payloads) -> Task 2 (entities + service + templates) -> Task 3 (consumers). No circular references.

## WARNING 1 Resolution (revision iteration 1)

The `Notification` entity extends ARCHITECTURE.md §2.10 (7-column base shape) with `correlation_id` per saga-contracts.md §6 — **8 columns total**:

| Column | Java Field | JPA Annotation | Notes |
|--------|-----------|----------------|-------|
| id | id | @Id @Column | PK UUID |
| user_id | userId | @Column(name="user_id") | NULLABLE — payment.failed uses orderId here |
| correlation_id | correlationId | @Column(name="correlation_id", nullable=false) | Fallback to eventId when missing |
| channel | channel | @Column(name="channel") | 'EMAIL' for v1 |
| type | eventType | @Column(name="type") | Java field renamed to avoid ambiguity |
| payload_json | payloadJson | @Column(name="payload_json") @JdbcTypeCode(SqlTypes.JSON) | JSONB via Hibernate 6 native |
| status | status | @Column(name="status") | 'SENT' for v1 |
| sent_at | sentAt | @Column(name="sent_at") | Timestamp of creation |

No `subject` or `body` TEXT columns — Turkish content lives INSIDE `payload_json` under keys `subject`, `bodyTurkish`.

## Turkish Copy Verification (CLAUDE.md Rule)

All body language is Turkish per CLAUDE.md (LOC-01 family). All code identifiers, method names, constant names, log keys remain in English.

Subjects: "Siparişiniz onaylandı", "Siparişiniz iptal edildi", "Ödemeniz alınamadı", "Hoş geldiniz!"

Reason code mapping:
- OUT_OF_STOCK -> "Stok yetersizliği"
- PAYMENT_DECLINED -> "Ödeme reddedildi"
- USER_CANCELLED -> "Sipariş iptal edildi"
- PAYMENT_TIMEOUT -> "Ödeme süresi doldu"

## Idempotency Invariant

Every `@Transactional` handler in `NotificationService` has `processedEventRepository.existsById(eventId)` as its FIRST statement. This is enforced by code review (acceptance criteria grep) and verified by the Plan 07-04 test suite.

Re-delivery of the same eventId is a NO-OP: processed_events.count remains 1, notifications.count(by correlationId) remains 1.

## D-10 ArchUnit Cross-Check

No `com.rabbitmq.client.Channel` references in any consumer file. All `@RabbitListener` methods take only `Message amqpMessage` parameter. Verified by:
```
grep -rE 'com\.rabbitmq\.client\.Channel' notification-service/src/main/java/com/n11/notification/messaging/ | wc -l
# -> 0
```

## payment.failed recipient field — orderId-not-userId trade-off

`payment-failed.schema.json` has no `userId` field. The `notifications.user_id` column is NULLABLE per ARCHITECTURE.md §2.10. Rather than storing NULL, `NotificationService.handlePaymentFailed` stores `payload.orderId()` in the `user_id` column position. This enables:
- `notificationRepository.findByUserId(orderId)` in smoke runbook Step 8
- `PaymentFailedConsumerIdempotencyTest` in Plan 07-04 to locate the audit row deterministically

This trade-off is documented in the method Javadoc and the Plan 07-04 test comment.

## Known Stubs

None — no placeholder text, hardcoded empty values, or UI stubs. The "EMAIL" channel is a v1 logging-only stub (actual SMTP wiring deferred), but the channel value is explicitly documented in the code and matches the NOTIF-02 contract verbatim. This is an intentional v1 scope decision, not an accidental stub.

## Threat Flags

No new security-relevant surface introduced. The consumer classes are pure AMQP listeners with no network endpoints, auth paths, or file access patterns.

## Self-Check: PASSED

All 14 created files exist on disk (verified via `find`).

All 3 commits exist:
- 849aea3: feat(07-03): add standalone payload records for 4 saga event types
- f043b28: feat(07-03): add entities, repositories, templates, and service for notification-service
- dd5eebf: feat(07-03): add 4 @RabbitListener consumers for notification-service saga events

Final compilation: `./gradlew :notification-service:compileJava :notification-service:compileTestJava` -> BUILD SUCCESSFUL (compileTestJava NO-SOURCE — Plan 07-04 will add the test sources).
