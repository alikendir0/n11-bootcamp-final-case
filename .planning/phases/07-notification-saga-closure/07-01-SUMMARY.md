---
phase: 07-notification-saga-closure
plan: 01
subsystem: notification-service
tags: [notification, scaffolding, gradle, flyway, docker-compose, spring-boot]
dependency_graph:
  requires:
    - infra/postgres/init.sh (notification schema + notification_user — provisioned by Phase 1 Plan 03)
    - common-error, common-logging, common-events (Phase 1 Plans 01-04)
    - config-server (Phase 1 Plan 05)
  provides:
    - notification-service Gradle module (buildable skeleton)
    - Flyway V1 processed_events migration
    - Flyway V2 notifications audit table (8-column, extends ARCHITECTURE.md §2.10 with correlation_id)
    - config-server/notification-service.yml (port 8087, notification_user, hikari pool 2)
    - docker-compose notification-service entry
  affects:
    - settings.gradle.kts (notification-service added to include list)
    - docker-compose.yml (notification-service service added)
tech_stack:
  added:
    - notification-service Gradle module (Spring Boot 3.5.14, Jib 3.5.3)
    - spring-boot-starter-amqp (RabbitMQ consumer)
    - flyway-core + flyway-database-postgresql 12.5.0
    - logstash-logback-encoder 8.0 (structured JSON logging)
    - awaitility 4.2.0 (test)
  patterns:
    - Cart-service skeleton clone with leaf-consumer pruning (no common-outbox)
    - Verbatim V1 migration copy (processed_events idempotency inbox)
    - V2 JSONB payload column pattern (extends ARCHITECTURE.md §2.10)
key_files:
  created:
    - notification-service/build.gradle.kts
    - notification-service/src/main/java/com/n11/notification/NotificationServiceApplication.java
    - notification-service/src/main/resources/application.yml
    - notification-service/src/test/resources/application.yml
    - notification-service/src/main/resources/logback-spring.xml
    - notification-service/src/main/resources/db/migration/V1__init_processed_events.sql
    - notification-service/src/main/resources/db/migration/V2__init_notifications.sql
    - config-server/src/main/resources/config/notification-service.yml
  modified:
    - settings.gradle.kts (added "notification-service" between "payment-service" and "service-template")
    - docker-compose.yml (added notification-service service entry after payment-service)
decisions:
  - "V2 DDL uses 8-column shape extending ARCHITECTURE.md §2.10 (7-col baseline) with correlation_id UUID NOT NULL per saga-contracts.md §6 — payload_json JSONB replaces PATTERNS.md draft subject/body TEXT columns"
  - "notification-service is a leaf consumer (no common-outbox dependency) — emits no saga events"
  - "Hikari pool size 2 per RESEARCH.md Planning Input #5 (leaf consumer, no compute pressure)"
metrics:
  duration: "~10 minutes"
  completed_date: "2026-04-30"
  tasks: 3
  files_created: 8
  files_modified: 2
---

# Phase 7 Plan 01: notification-service Scaffold Summary

Bootstrap the notification-service Gradle module with Flyway migrations (processed_events idempotency inbox + 8-column notifications audit table extending ARCHITECTURE.md §2.10 with correlation_id per saga-contracts.md §6), config-server YAML, and docker-compose entry on port 8087.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Create notification-service Gradle module skeleton | 499bd59 | build.gradle.kts, NotificationServiceApplication.java, application.yml (main+test), logback-spring.xml, settings.gradle.kts |
| 2 | Write Flyway migrations V1/V2 + config-server YAML | f394d8e | V1__init_processed_events.sql, V2__init_notifications.sql, config-server/notification-service.yml |
| 3 | Add notification-service entry to docker-compose.yml | 21b0ba4 | docker-compose.yml |

## Verification

- `./gradlew :notification-service:compileJava` exits 0 — BUILD SUCCESSFUL
- `settings.gradle.kts` contains exactly 1 occurrence of "notification-service"
- Both Flyway migrations present in `src/main/resources/db/migration/`
- V1 byte-identical to cart-service V1 (verified via `diff` — no output)
- V2 extends ARCHITECTURE.md §2.10 (7 columns) with `correlation_id UUID NOT NULL` — 8 columns total: `id, user_id, correlation_id, channel, type, payload_json JSONB, status, sent_at`
- `config-server/notification-service.yml` has port 8087, notification_user, maximum-pool-size: 2
- `docker-compose.yml` has exactly 1 `notification-service:` entry; `docker compose config` exits 0 (verified in main repo — worktree lacks gitignored `.env`)
- `notification` schema and `notification_user` provisioned by `infra/postgres/init.sh` (16 grep hits — pre-existing, not touched by this plan)
- `NOTIFICATION_DB_PASSWORD` exists in `.env.example` (verified)

## V2 DDL Alignment

The `V2__init_notifications.sql` DDL extends ARCHITECTURE.md §2.10 (which specifies 7 columns: `id, user_id, channel, type, payload_json, status, sent_at`) with:
- `correlation_id UUID NOT NULL` (saga-contracts.md §6 — saga traceability requirement)

8-column attestation: `id, user_id, correlation_id, channel, type, payload_json JSONB, status, sent_at`

Two indexes added for query performance:
- `idx_notifications_correlation_id` on `correlation_id` (saga trace lookups)
- `idx_notifications_user_id` on `user_id` (user inbox lookups for future Phase 10 API)

## Deviations from Plan

### Auto-applied Architecture Alignment

**1. [Rule 2 - Architecture Contract] PATTERNS.md V2 draft used `subject TEXT + body TEXT` columns — upgraded to `payload_json JSONB`**
- **Found during:** Task 2 — plan explicitly called this out as a known deviation from PATTERNS.md
- **Issue:** PATTERNS.md lines 494-513 showed a V2 DDL draft with `subject TEXT NOT NULL` and `body TEXT NOT NULL` columns. This contradicts ARCHITECTURE.md §2.10 which specifies `payload_json JSONB` as the column type.
- **Fix:** V2 DDL uses `payload_json JSONB NOT NULL` per ARCHITECTURE.md §2.10 contract. Turkish subject and body live inside the JSONB payload under keys `subject` and `bodyTurkish`. This is explicitly required by the plan (plan-checker revision 1 flagged the PATTERNS.md draft as incorrect).
- **Files modified:** `notification-service/src/main/resources/db/migration/V2__init_notifications.sql`
- **Commit:** f394d8e

No other deviations — plan executed cleanly.

## Known Stubs

None — this plan delivers infrastructure (DDL + config) only. No domain logic stubs exist. The notification-service will start without any AMQP topology or consumers declared (Plans 07-02/07-03 add those).

## Threat Flags

No new security-relevant surface introduced. All new files are:
- Flyway SQL migrations (DDL only, no data, no auth paths)
- Spring Boot configuration YAML (no new endpoints beyond Spring Boot Actuator `/actuator/health`)
- docker-compose service entry (internal-only, no `ports:` mapping per Pitfall #14)

## Self-Check: PASSED

Files created exist:
- notification-service/build.gradle.kts: EXISTS
- notification-service/src/main/java/com/n11/notification/NotificationServiceApplication.java: EXISTS
- notification-service/src/main/resources/application.yml: EXISTS
- notification-service/src/test/resources/application.yml: EXISTS
- notification-service/src/main/resources/logback-spring.xml: EXISTS
- notification-service/src/main/resources/db/migration/V1__init_processed_events.sql: EXISTS
- notification-service/src/main/resources/db/migration/V2__init_notifications.sql: EXISTS
- config-server/src/main/resources/config/notification-service.yml: EXISTS

Commits exist:
- 499bd59: Task 1 — scaffold notification-service Gradle module skeleton
- f394d8e: Task 2 — add Flyway migrations V1/V2 and config-server YAML
- 21b0ba4: Task 3 — add notification-service entry to docker-compose.yml
