---
phase: 4
slug: catalog-inventory
status: draft
nyquist_compliant: true
wave_0_complete: true
created: 2026-04-29
---

# Phase 4 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (`org.junit.jupiter`) + Spring Boot Test 3.5.14 + Testcontainers 1.20.x (Postgres + RabbitMQ) + Awaitility 4.2.x |
| **Config file** | `product-service/build.gradle.kts`, `inventory-service/build.gradle.kts` (test deps inherited from `service-template/skeleton/build.gradle.kts` already configured in Phase 1) |
| **Quick run command** | `./gradlew :product-service:test :inventory-service:test --rerun` |
| **Full suite command** | `./gradlew clean check` |
| **Estimated runtime** | ~90 seconds (Testcontainers cold start ~30s, tests ~30s, build ~30s) |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :<module>:test` for the affected module
- **After every plan wave:** Run `./gradlew :product-service:test :inventory-service:test`
- **Before `/gsd-verify-work`:** Full suite (`./gradlew clean check`) must be green
- **Max feedback latency:** 60 seconds for per-module tests, 120 seconds for full suite

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 4-01-01 | 01 | 1 | PROD-01, PROD-03, PROD-09 | — | N/A | unit | `./gradlew :product-service:test --tests *FlywayMigrationsTest` | ✅ Planned (04-01 Task 2) | ⬜ pending |
| 4-01-02 | 01 | 1 | PROD-01, PROD-02, PROD-05 | — | N/A | unit | `./gradlew :product-service:test --tests *ProductRepositoryTest` | ✅ Planned (04-01 Task 5) | ⬜ pending |
| 4-01-03 | 01 | 1 | PROD-04 | — | N/A | integration | `./gradlew :product-service:test --tests *ProductSearchIntegrationTest` | ✅ Planned (04-01 Task 5) | ⬜ pending |
| 4-01-04 | 01 | 2 | PROD-01, PROD-02, PROD-05, PROD-07 | — | ROLE_ADMIN required for writes | integration | `./gradlew :product-service:test --tests *ProductControllerIntegrationTest` | ✅ Planned (04-01 Task 5) | ⬜ pending |
| 4-02-01 | 02 | 1 | PROD-08 | — | N/A | unit | `./gradlew :inventory-service:test --tests *StockEntityVersionTest` | ✅ Planned (04-02 Task 2) | ⬜ pending |
| 4-02-02 | 02 | 1 | PROD-06, PROD-08 | — | N/A | unit | `./gradlew :inventory-service:test --tests *StockStateComputationTest` | ✅ Planned (04-02 Task 2) | ⬜ pending |
| 4-02-03 | 02 | 2 | PROD-08 | — | Idempotent saga consumer | integration | `./gradlew :inventory-service:test --tests *OrderCreatedConsumerIntegrationTest` | ✅ Planned (04-02 Task 4) | ⬜ pending |
| 4-02-04 | 02 | 2 | PROD-06, PROD-07, PROD-08 | — | N/A | integration | `./gradlew :inventory-service:test --tests *InventoryControllerIntegrationTest` | ✅ Planned (04-02 Task 2) | ⬜ pending |
| 4-03-01 | 03 | 3 | PROD-07 | — | N/A | manual | `curl -fsS http://localhost:8080/swagger-ui.html \| grep -E 'product-service\|inventory-service'` | ❌ W0 | ⬜ pending |
| 4-03-02 | 03 | 3 | PROD-09 | — | N/A | unit | `./gradlew :product-service:test --tests *SeedDataAssertionTest` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

*Plan IDs (01/02/03) and task IDs are placeholders — will be reconciled against actual PLAN.md files after planner output. The planner is required to keep test class names and `--tests` filters stable so this map remains valid.*

---

## Wave 0 Requirements

- [x] `product-service/src/test/java/.../FlywayMigrationsTest.java` — planned in 04-01 Task 2 (B-03 fix)
- [x] `product-service/src/test/java/.../ProductRepositoryTest.java` — planned in 04-01 Task 5
- [x] `product-service/src/test/java/.../ProductSearchIntegrationTest.java` — planned in 04-01 Task 5
- [x] `product-service/src/test/java/.../ProductControllerIntegrationTest.java` — planned in 04-01 Task 5
- [x] `product-service/src/test/java/.../SeedDataAssertionTest.java` — planned in 04-01 Task 5
- [x] `inventory-service/src/test/java/.../StockEntityVersionTest.java` — planned in 04-02 Task 2
- [x] `inventory-service/src/test/java/.../StockStateComputationTest.java` — planned in 04-02 Task 2
- [x] `inventory-service/src/test/java/.../OrderCreatedConsumerIntegrationTest.java` — planned in 04-02 Task 4
- [x] `inventory-service/src/test/java/.../InventoryControllerIntegrationTest.java` — planned in 04-02 Task 2

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Springdoc gateway aggregator surfaces both services | PROD-07 | Browser-side Swagger UI rendering can't be asserted via Java; the aggregated dropdown is verified by curl + visual inspection | `docker compose up -d` then open http://localhost:8080/swagger-ui.html — confirm dropdown lists "product-service" and "inventory-service"; clicking each loads OpenAPI JSON without 404 |
| Turkish ILIKE search matches dotted-i variants | PROD-04 (LOC-01) | `İ`/`i`/`I`/`ı` collation behavior is environment-sensitive; manual sanity check required even with automated test | Hit `GET /products?search=Iphone` and `GET /products?search=iPhone` against seed data, expect equivalent matches |

*Stock-state copy strings ("Stokta" / "Tükendi" / "Son N ürün!") are validated by automated unit tests; visual rendering moves to Phase 10.*

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s per-module / 120s full suite
- [x] `nyquist_compliant: true` set in frontmatter (W-02 fix — all Wave 0 test files now planned)

**Approval:** pending
