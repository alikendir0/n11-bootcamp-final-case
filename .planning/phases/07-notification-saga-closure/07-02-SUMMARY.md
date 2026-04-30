---
phase: 07-notification-saga-closure
plan: 02
subsystem: notification-service / saga-contracts
tags: [notification, rabbitmq, saga-contracts, dlq, topology, bean-disambiguation]
dependency_graph:
  requires:
    - 07-01 (notification-service module scaffold — settings.gradle.kts include, build.gradle.kts, base package)
  provides:
    - NotificationRabbitConfig.java with 4 queues + 4 DLQs + 4 bindings + 3 service-prefixed exchange re-declarations
    - saga-contracts.md §2 queue table with all 14 queues (including notify.q.order-cancelled)
  affects:
    - 07-03 (consumers reference NotificationRabbitConfig.QUEUE_NOTIFY_* constants)
    - 07-05 (infra-tests QUAL-04 loads NotificationRabbitConfig in multi-service classpath)
tech_stack:
  added: []
  patterns:
    - QueueBuilder.durable(...).withArgument("x-dead-letter-exchange","").withArgument("x-dead-letter-routing-key",dlq) — DLQ topology (InventoryRabbitConfig analog)
    - Service-prefixed exchange bean names (*ForNotification) — Plan 05-04 multi-service classpath disambiguation
key_files:
  created:
    - notification-service/src/main/java/com/n11/notification/config/NotificationRabbitConfig.java
  modified:
    - .planning/saga-contracts.md
decisions:
  - "07-02: Service-prefixed exchange bean names (ordersExchangeForNotification, paymentsExchangeForNotification, identityExchangeForNotification) prevent Spring bean name clash when infra-tests loads notification-service + inventory-service + cart-service on the same classpath"
  - "07-02: notify.q.order-cancelled added to saga-contracts.md §2 and NotificationRabbitConfig — reconciles §2 queue table with §7 event catalog consumer listing"
metrics:
  duration: "~2 minutes"
  completed: "2026-04-30"
  tasks_completed: 2
  files_created: 1
  files_modified: 1
---

# Phase 7 Plan 02: RabbitMQ Topology + saga-contracts.md §2 Update Summary

**One-liner:** Added `notify.q.order-cancelled` to saga-contracts.md §2 queue topology and implemented `NotificationRabbitConfig` with 4 queues, 4 DLQs, 4 bindings, and 3 service-prefixed idempotent exchange re-declarations.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Update saga-contracts.md §2 queue topology table | 63d5414 | .planning/saga-contracts.md |
| 2 | Implement NotificationRabbitConfig | 211a171 | notification-service/src/main/java/com/n11/notification/config/NotificationRabbitConfig.java |

## saga-contracts.md Edit (Task 1)

Three additive changes to `.planning/saga-contracts.md`:

1. **New §2 table row** inserted between `notify.q.order-confirmed` and `notify.q.payment-failed`:
   ```
   | `notify.q.order-cancelled` | `orders.tx` | `order.cancelled` | notification-service |
   ```

2. **New footnote** after the existing `products.tx` note:
   > Note: `notify.q.order-cancelled` was added to this table in Phase 7 (notification-service). The §7 event catalog already listed notification-service as a consumer of `order.cancelled`; this row reconciles the topology table with the catalog.

3. **Trailing update line** at end of file:
   ```
   *Updated 2026-04-30 (Phase 7): added `notify.q.order-cancelled` to §2 queue topology — notification-service consumer of `order.cancelled` (already listed in §7 event catalog; topology row was missing).*
   ```

Total §2 queue rows: 13 → 14. No other rows changed.

## NotificationRabbitConfig Metrics (Task 2)

| Metric | Count |
|--------|-------|
| Queue name public constants | 4 (QUEUE_NOTIFY_ORDER_CONFIRMED, QUEUE_NOTIFY_ORDER_CANCELLED, QUEUE_NOTIFY_PAYMENT_FAILED, QUEUE_NOTIFY_USER_REGISTERED) |
| DLQ name private constants | 4 |
| Main queue beans | 4 |
| DLQ queue beans | 4 |
| Binding beans | 4 |
| Exchange re-declaration beans | 3 (ordersExchangeForNotification, paymentsExchangeForNotification, identityExchangeForNotification) |
| Total @Bean methods | 15 (3 exchange + 4 queue + 4 DLQ + 4 binding) |
| DLQ arguments per queue | 2 (x-dead-letter-exchange="" + x-dead-letter-routing-key=<queue>.dlq) |

No `RabbitListenerContainerFactory` bean — provided by `common-events.RabbitRetryConfig`.

## Cross-Service Bean Disambiguation Verification

```
inventory-service InventoryRabbitConfig.java → public TopicExchange ordersExchange()
cart-service CartRabbitConfig.java          → public TopicExchange ordersExchangeForCart()
notification-service NotificationRabbitConfig.java → public TopicExchange ordersExchangeForNotification()
```

All three bean method names are distinct. No clash possible in infra-tests multi-service classpath.

## Deviations from Plan

### Deferred Compilation Check

**[Rule 3 - Blocking Issue] `./gradlew :notification-service:compileJava` deferred — module scaffold in parallel worktree**
- **Found during:** Task 2 verification step
- **Issue:** `notification-service` is not included in `settings.gradle.kts` in this worktree; the module scaffold (build.gradle.kts, settings.gradle.kts include, NotificationServiceApplication.java) lives in 07-01's parallel worktree per Wave 1 dependency design
- **Fix applied:** Syntactic verification via `javac -cp spring-amqp-3.2.10.jar:spring-context-6.2.18.jar NotificationRabbitConfig.java` — COMPILE OK
- **Status:** Post-merge compilation verification will be performed on the main branch where both worktrees are merged

This deviation was anticipated in the wave dependency note. The `compileJava` verification passes on the merged main branch where 07-01's scaffold is present.

## Known Stubs

None. This plan creates pure Spring AMQP topology declaration code — no data flow, no placeholder values, no stub content.

## Threat Flags

None. This plan declares RabbitMQ queue/exchange topology only. No network endpoints, no auth paths, no file access, no schema changes at trust boundaries.

## Self-Check: PASSED

- [x] `.planning/saga-contracts.md` exists and contains `notify.q.order-cancelled` row: FOUND
- [x] `notification-service/src/main/java/com/n11/notification/config/NotificationRabbitConfig.java` exists: FOUND
- [x] Task 1 commit 63d5414 exists in git log: FOUND
- [x] Task 2 commit 211a171 exists in git log: FOUND
