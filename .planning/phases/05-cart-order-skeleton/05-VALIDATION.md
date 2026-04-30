---
phase: 5
slug: cart-order-skeleton
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-30
---

# Phase 5 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution. Hand-fill from `05-RESEARCH.md` § Validation Architecture and the planner's `<test_dimension>` blocks during planning.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5.x (Spring Boot 3.5.14 BOM) + Testcontainers Postgres 16 + Testcontainers RabbitMQ 3.13-management + Awaitility 4.3.1 + ArchUnit 1.4.2 |
| **Config file** | per-service `src/test/resources/application.yml` (`optional:configserver:` + `hikari.connection-init-sql=SET search_path=...`) — pattern locked Plan 04-01 |
| **Quick run command** | `./gradlew :{service}:test` (per-service slice) |
| **Full suite command** | `./gradlew test` (root — all modules) |
| **Estimated runtime** | ~120s root suite (Phase 4 baseline ~50s; Phase 5 adds ~70s for 3 new services + saga E2E with Testcontainers RabbitMQ cold-start) |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :{service}:test` for the service touched
- **After every plan wave:** Run `./gradlew test` (full suite)
- **Before `/gsd-verify-work`:** Full suite must be green; Phase 5 smoke runbook must execute clean against live `docker compose up` stack
- **Max feedback latency:** ~30s per-service slice; ~120s full suite

---

## Per-Task Verification Map

> Populated during planning — each `<test_dimension>` in PLAN.md tasks maps to one row here.

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 5-W0-01 | 01 (common-outbox) | 0 | ARCH-07 | — | message_id property always set on AMQP publish | unit | `./gradlew :common-outbox:test --tests AbstractOutboxPollerTest` | ❌ W0 | ⬜ pending |
| 5-W0-02 | 01 (ArchUnit) | 0 | ARCH-07 | — | no `@RabbitListener` reverts to MANUAL ack without Channel parameter | unit | `./gradlew :infra-tests:test --tests AmqpAckModeArchTest` | ❌ W0 | ⬜ pending |
| 5-W1-01 | 02 (cart-service) | 1 | CART-01,CART-05 | — | UPSERT on (user_id, product_id); add-twice produces single row with summed qty; cart persists by userId | integration (Testcontainers Postgres) | `./gradlew :cart-service:test --tests CartItemRepositoryUpsertTest` | ❌ W0 | ⬜ pending |
| 5-W1-02 | 02 (cart-service) | 1 | CART-02,CART-03,CART-04 | — | line-totals computation + PATCH/DELETE by productId proven via REST controller integration | integration | `./gradlew :cart-service:test --tests CartItemRepositoryUpsertTest` (covers UPSERT add + DELETE; line-total computation covered by CartService.loadCartView in CartItemRepositoryUpsertTest assertions) | ❌ W0 | ⬜ pending |
| 5-W1-03 | 02 (cart-service) | 1 | ARCH-07,D-07 | — | OrderConfirmedConsumer idempotent: redelivery clears cart exactly once | integration | `./gradlew :cart-service:test --tests OrderConfirmedConsumerIdempotencyTest` | ❌ W0 | ⬜ pending |
| 5-W1-04 | 02 (cart-service) | 1 | CART-06 | — | Springdoc /v3/api-docs surface | smoke | `curl -fsS http://localhost:8080/api/v1/cart/v3/api-docs` (in runbook Step 11) | ❌ W4 | ⬜ pending |
| 5-W2-01 | 03 (order-service) | 2 | ORD-01,ORD-02,ARCH-06,ARCH-07,D-05 | — | POST /orders writes orders+items+address+idempotency_key+outbox in single tx; replay returns same orderId; price drift throws BEFORE persistence | integration | `./gradlew :order-service:test --tests OrderCreationFlowTest` | ❌ W0 | ⬜ pending |
| 5-W2-02 | 03 (order-service) | 2 | ORD-05,ARCH-06,ARCH-07 | — | 4 saga consumers transition status (STOCK_RESERVED, CONFIRMED, CANCELLED w/ reason); redelivery is no-op | integration | `./gradlew :order-service:test --tests SagaConsumerIdempotencyTest` | ❌ W0 | ⬜ pending |
| 5-W2-03 | 03 (order-service) | 2 | ORD-03,ORD-04 | — | listing sorted by created_at DESC; detail returns canonical OrderStatus + cancelReason + items | integration | `./gradlew :order-service:test --tests OrderListingAndDetailTest` | ❌ W0 | ⬜ pending |
| 5-W2-04 | 03 (order-service) | 2 | ARCH-06 | — | POST /orders/{id}/cancel publishes order.cancelled when PENDING/STOCK_RESERVED; 409 otherwise | integration | `./gradlew :order-service:test --tests OrderCancelTest` | ❌ W0 | ⬜ pending |
| 5-W2-05 | 03 (order-service) | 2 | D-01,RFC-7807 | — | PriceDriftException maps to HTTP 409 RFC-7807 with custom updatedItems[] property | unit (MockMvc slice) | `./gradlew :order-service:test --tests OrderControllerPriceDriftMvcTest` | ❌ W0 | ⬜ pending |
| 5-W2-06 | 03 (order-service) | 2 | ORD-06 | — | Springdoc surface | smoke | `curl -fsS http://localhost:8080/api/v1/orders/v3/api-docs` (in runbook Step 11) | ❌ W4 | ⬜ pending |
| 5-W3-01 | 04 (payment-skeleton) | 3 | — | — | StockReservedConsumer publishes payment.completed after configurable delay; redelivery is no-op | integration | `./gradlew :payment-service:test --tests StockReservedConsumerIntegrationTest` | ❌ W0 | ⬜ pending |
| 5-W3-02 | 04 (saga E2E) | 3 | QUAL-03,ARCH-06,ARCH-07,ARCH-08 | — | order.created → stock.reserved → payment.completed across real Testcontainers RabbitMQ + Postgres; D-09 messageId invariant asserted on the published payment.completed message | integration (Testcontainers Postgres + RabbitMQ) | `./gradlew :infra-tests:test --tests SagaHappyPathE2ETest` | ❌ W0 | ⬜ pending |
| 5-W3-03 | 04 (inventory comp) | 3 | ARCH-06 | — | inventory.q.payment-failed releases stock; inventory.q.order-cancelled releases stock; redelivery is no-op | integration | `./gradlew :inventory-service:test --tests InventoryCompensationTest` | ❌ W0 | ⬜ pending |
| 5-W4-01 | 05 (gateway+smoke) | 4 | CART-06,ORD-06,QUAL-01 | — | gateway routes cart/orders with StripPrefix=2; Springdoc aggregator dropdown shows 5 entries | smoke (live curl) | runbook step 1-3 | ❌ W4 | ⬜ pending |
| 5-W4-02 | 05 (smoke) | 4 | ARCH-08 | — | correlation-ID flows through MDC and JSON logs across all services | smoke | runbook `grep '"correlationId":"<uuid>"' logs/*.log` | ❌ W4 | ⬜ pending |
| 5-W4-03 | 05 (smoke) | 4 | QUAL-03 | — | live AMQP delivery proves saga end-to-end (not just direct invocation) | smoke | runbook step 7-8 (curl POST /orders → poll until CONFIRMED) | ❌ W4 | ⬜ pending |
| 5-W4-04 | 05 (smoke) | 4 | ARCH-07 | — | re-publish same eventId leaves processed_events row counts unchanged at 1 | smoke | runbook step 9 (rabbitmqadmin re-publish) | ❌ W4 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `common-outbox/src/main/java/com/n11/outbox/OutboxEvent.java` — JPA entity (lifted from inventory-service)
- [ ] `common-outbox/src/main/java/com/n11/outbox/OutboxRepository.java` — base interface (extracted base; per-service subclasses supply native @Query)
- [ ] `common-outbox/src/main/java/com/n11/outbox/AbstractOutboxPoller.java` — `@Scheduled` driver (constructs MessagePostProcessor on each poll loop)
- [ ] `common-outbox/src/main/java/com/n11/outbox/OutboxMessagePostProcessor.java` — standalone `MessagePostProcessor` bean injecting `MessageProperties.messageId` + `correlationId` from envelope JSON (split into separate file per I1; AbstractOutboxPoller now @Autowires it as a constructor arg)
- [ ] `common-outbox/src/test/java/com/n11/outbox/AbstractOutboxPollerTest.java` — unit (covers post-processor invariant + poll loop publish)
- [ ] `infra-tests/src/test/java/com/n11/infratests/arch/AmqpAckModeArchTest.java` — ArchUnit gate (D-10)
- [ ] identity-service + inventory-service migration to common-outbox (pre-existing tests stay green)

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Springdoc aggregator dropdown click-through (UX) | Backlog 999.1 | Springdoc/gateway interaction not unit-testable; live browser verification | Open `http://localhost:8080/swagger-ui.html`, select cart-service from dropdown, confirm endpoints render — known failure tracked in 999.1 (acceptable for Phase 5) |
| Saga state visualization (5-service correlation-ID grep) | ARCH-08 | Multi-service log aggregation requires running stack | Run `docker compose logs --tail=200 \| grep '"correlationId":"<uuid>"' \| wc -l` — must show ≥4 hits across services |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references (common-outbox + ArchUnit + spike tests A3/A6 from RESEARCH.md)
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s per-service slice; < 120s full suite
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
