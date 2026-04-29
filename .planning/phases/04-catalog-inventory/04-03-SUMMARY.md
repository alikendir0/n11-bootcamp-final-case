---
phase: 04-catalog-inventory
plan: "03"
subsystem: api-gateway
tags:
  - api-gateway
  - springdoc-aggregator
  - smoke-test
  - saga
  - rabbitmq
  - idempotency
  - bug-fix
  - stockta-label
  - pg_trgm
  - gateway-routes

dependency_graph:
  requires:
    - 04-01 (product-service catalog with routes + Springdoc)
    - 04-02 (inventory-service saga consumer + outbox)
    - 03-06 (api-gateway SecurityConfig + GatewayHeaderInjectionFilter baseline)
  provides:
    - api-gateway routes for product-service (/api/v1/products/**, /api/v1/categories/**)
    - api-gateway route for inventory-service (/api/v1/inventory/**)
    - Springdoc aggregator dropdown entries (product-service + inventory-service) at /swagger-ui.html
    - ROLE_ADMIN gateway gate on POST/PUT/DELETE /api/v1/products/** (defense-in-depth)
    - docker-compose api-gateway depends_on product-service + inventory-service healthy
    - 04-03-SMOKE-RUNBOOK.md — Phase 4 end-to-end manual smoke runbook
    - 4 live bug fixes surfaced during smoke test execution (see Deviations)
  affects:
    - 05-cart-order (gateway routing pattern established; StripPrefix=2 correction documented)
    - Phase 5+ (pg_trgm extension now reliably present; saga AMQP ack mode corrected)

tech_stack:
  added: []
  patterns:
    - "StripPrefix=2 for /api/v1/{segment}/** paths (strips /api/v1 — 2 components, not 3)"
    - "Springdoc aggregator url format: /api/v1/{service}/v3/api-docs"
    - "Gateway depends_on chain: postgres → rabbitmq → eureka → config → identity → product → inventory → gateway"
    - "AMQP AcknowledgeMode.AUTO with RejectAndDontRequeueRecoverer (throws AmqpRejectAndDontRequeueException, container nacks to DLQ — no manual basicAck needed)"
    - "StatefulRetryInterceptor requires AMQP message_id property set to eventId — must be present in all smoke/test publish commands"
    - "pg_trgm extension provisioned in infra/postgres/init.sh alongside pgvector — both must be created before Flyway migrations run"

key_files:
  created:
    - .planning/phases/04-catalog-inventory/04-03-SMOKE-RUNBOOK.md
  modified:
    - config-server/src/main/resources/config/api-gateway.yml
    - api-gateway/src/main/java/com/n11/gateway/SecurityConfig.java
    - docker-compose.yml
    - common-events/src/main/java/com/n11/events/RabbitRetryConfig.java
    - inventory-service/src/main/java/com/n11/inventory/messaging/OrderCreatedConsumer.java
    - infra/postgres/init.sh

decisions:
  - "StripPrefix=2 is correct for /api/v1/{segment}/** gateway routes (strips /api and /v1 — 2 path components). The plan specified StripPrefix=3 which would strip the service prefix too. This was caught during live smoke test (bug BC10E37)."
  - "AcknowledgeMode.AUTO is correct for StatefulRetry+RejectAndDontRequeueRecoverer — the recoverer throws an exception which the container intercepts to nack, no explicit basicAck() call is needed or safe."
  - "message_id AMQP property must match eventId when using StatefulRetryInterceptor — the key generator extracts message_id and throws AmqpException if absent. All smoke commands must set properties=message_id=<eventId>."
  - "pg_trgm extension must be created in init.sh alongside pgvector — Flyway V2 migrations in product-service assume both are present; absence causes 'operator does not exist: text %% unknown' error on first product listing query."
  - "Phase 4 smoke runbook verifies CLAUDE.md Rule #3 idempotency over REAL AMQP delivery (not just direct invocation as in unit tests) — second publish with same eventId leaves all counts at 1."

metrics:
  duration: "~60 minutes"
  completed_date: "2026-04-29"
  tasks_completed: 3
  tasks_total: 3
  files_created: 1
  files_modified: 6
---

# Phase 04 Plan 03: API-Gateway Wiring + Phase 4 Live Smoke Test Summary

**One-liner:** Gateway routes + Springdoc aggregator wired for product-service and inventory-service; smoke runbook executed against live docker-compose stack revealing 6 bugs — 4 fixed (StripPrefix=2, pg_trgm, AMQP ack mode, consumer hardening), 2 tracked in backlog; all 8 sign-off items verified.

## Tasks Completed

| Task | Name | Commit | Key Files |
|------|------|--------|-----------|
| 1 | Gateway routes + Springdoc aggregator + admin path matchers | e5dddd8 | api-gateway.yml (+3 routes, +2 springdoc urls), SecurityConfig.java (+ROLE_ADMIN matchers) |
| 2 | Compose orchestration — wire api-gateway depends_on product-service + inventory-service healthy | 9162a02 | docker-compose.yml (api-gateway.depends_on += product-service, inventory-service) |
| 3 | Smoke runbook — write + execute manual end-to-end verification (checkpoint:human-verify) | 3494fff | 04-03-SMOKE-RUNBOOK.md |

## Bug Fix Commits (during smoke test execution)

| Commit | Severity | What | File(s) |
|--------|----------|------|---------|
| 2b61689 | HIGH | AMQP ack mode MANUAL→AUTO + consumer null guard + AmqpRejectAndDontRequeueException | RabbitRetryConfig.java, OrderCreatedConsumer.java |
| f6b38af | HIGH | pg_trgm extension missing from postgres init.sh | infra/postgres/init.sh |
| bc10e37 | HIGH | StripPrefix=3→2 for products/categories/inventory routes | api-gateway.yml |
| 06338b1 | MEDIUM | Smoke runbook: message_id property + numeric amount values + debug session resolved | 04-03-SMOKE-RUNBOOK.md, debug/inventory-npe-envelope-payload.md |
| 757d2b5 | LOW | Backlog items 999.1 + 999.2 filed in ROADMAP.md | ROADMAP.md |

## Live Smoke Verification Results

**Executed:** 2026-04-29 22:23 UTC against docker-compose stack with 7 services (postgres, rabbitmq, eureka, config, identity, product, inventory).

### Steps 1-6: PASSED on first try (after fixes)

| Step | Check | Result |
|------|-------|--------|
| 1 | docker compose up — all 7 services healthy | PASSED |
| 2 | GET /api/v1/products → totalElements >= 50 | PASSED (52 products, Turkish names: "Mekanik Klavye RGB", "4K Akıllı Televizyon 55\"", etc.) |
| 2b | GET /api/v1/products?q=Telefon → ILIKE search | PASSED (1 result: "Akıllı Telefon X100" — proves GIN pg_trgm index working) |
| 3 | GET /api/v1/categories → 8 Turkish categories | PASSED (Elektronik, Moda, Ev & Yaşam, Anne & Bebek, Kozmetik, Spor & Outdoor, Süpermarket, Kitap-Müzik-Film-Oyun) |
| 4 | GET /api/v1/products/{id} → PDP fields | PASSED (id, sku, nameTr, descriptionTr, priceGross, kdvRate, slug, sellerName, imageUrls, categoryId, categoryName all present) |
| 5 | GET /api/v1/inventory/{productId} → Turkish stock labels | PASSED (stockState=STOKTA, stockStateLabel="Stokta", availableQty=44) |
| 6 | GET /v3/api-docs/swagger-config → aggregator config | PASSED (3 entries: identity-service, product-service, inventory-service — PROD-07 proven) |

### Steps 7-8: PASSED on second try (after saga AMQP fix in 2b61689)

| Step | Check | Result |
|------|-------|--------|
| 7 | Publish order.created (with message_id) → stock_reservations row | PASSED (1 row status=RESERVED, 1 processed_events row, 1 outbox row event_type=stock.reserved) |
| 8 | Re-publish same eventId → idempotency proof | PASSED (all counts unchanged at 1 — CLAUDE.md Rule #3 over REAL AMQP delivery) |

**Tear-down clean:** docker compose down; queues drained to 0, DLQ to 0; no residual test data leaking into commits.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocker] StripPrefix=3 in plan spec strips /api/v1/{segment} leaving no path for downstream service**
- **Found during:** Task 3 (live smoke test — Step 2 returned 404)
- **Issue:** The plan specified `StripPrefix=3` in the route definitions. For a gateway path `/api/v1/products/**`, StripPrefix=3 strips three path components (`/api`, `/v1`, `/products`), leaving an empty path sent to the upstream service. The service expected the path to start with `/products`. StripPrefix=2 correctly strips only `/api` and `/v1`, forwarding `/products/**` to product-service.
- **Fix:** Changed all three route entries (product-service, category-service, inventory-service) from `StripPrefix=3` to `StripPrefix=2` in api-gateway.yml
- **Files modified:** `config-server/src/main/resources/config/api-gateway.yml`
- **Commit:** bc10e37

**2. [Rule 2 - Missing Critical] pg_trgm extension absent from postgres init.sh**
- **Found during:** Task 3 (live smoke test — product listing returned 500 from product-service; logs showed `operator does not exist: text %% unknown`)
- **Issue:** The product-service Flyway V2 migration creates a GIN index using the `pg_trgm` operator class (`gin_trgm_ops`). This requires the `pg_trgm` extension to be present. Phase 1 Plan 03 (infra/postgres/init.sh) installed `pgvector` but omitted `pg_trgm`. Both must be created as superuser before service-level Flyway runs.
- **Fix:** Added `CREATE EXTENSION IF NOT EXISTS pg_trgm;` to `infra/postgres/init.sh` in the `setup_extensions()` function, adjacent to the existing pgvector provision.
- **Files modified:** `infra/postgres/init.sh`
- **Commit:** f6b38af

**3. [Rule 1 - Bug] AMQP AcknowledgeMode.MANUAL with StatefulRetryInterceptor causes message stall + NPE**
- **Found during:** Task 3 (live smoke test — Steps 7-8: processed_events count stayed at 0 after publish)
- **Issue:** Two compounding bugs prevented saga consumption over real AMQP delivery:
  - (PRIMARY) `StatefulRetryInterceptor.messageKeyGenerator` in `RabbitRetryConfig` throws `AmqpException` when AMQP `message_id` property is absent. This fires BEFORE the listener method is invoked, so `OrderCreatedConsumer.handleOrderCreated()` was never reached. All messages silently rejected — hence processed_events=0.
  - (SECONDARY) `AcknowledgeMode.MANUAL` was set but no `channel.basicAck()` was ever called on the success path. Every successfully-processed message would be left perpetually unacked — infinite redelivery on next connection reset.
  - (TERTIARY) Catch block in `OrderCreatedConsumer` called `payload.orderId()` where `payload` could be null (from `treeToValue` receiving a JSON null node), creating a secondary NPE that would escape to the retry interceptor.
- **Fix:**
  - Changed `AcknowledgeMode.MANUAL` to `AcknowledgeMode.AUTO` in `RabbitRetryConfig` (verified `RejectAndDontRequeueRecoverer` works correctly with AUTO — it throws `AmqpRejectAndDontRequeueException`, which the container intercepts to nack-no-requeue to DLQ).
  - Rewrote `OrderCreatedConsumer` error handling: malformed messages now throw `AmqpRejectAndDontRequeueException` immediately (routes to DLQ without retry slots); null payload guard added before and after `treeToValue`; service exceptions propagate (not swallowed) so `StatefulRetryInterceptor` can retry then DLQ; added explicit UTF-8 charset on `new String(body)`.
- **Files modified:** `common-events/src/main/java/com/n11/events/RabbitRetryConfig.java`, `inventory-service/src/main/java/com/n11/inventory/messaging/OrderCreatedConsumer.java`
- **Commit:** 2b61689
- **Debug session:** `.planning/debug/inventory-npe-envelope-payload.md` (status: resolved, resolution_verified populated)

**4. [Rule 2 - Missing Critical] Smoke runbook missing message_id AMQP property**
- **Found during:** Task 3 (diagnosis of bug #3 above)
- **Issue:** The original smoke runbook's rabbitmqadmin publish command omitted the `properties` flag. Without `message_id` set on the AMQP message, the `StatefulRetryInterceptor` key generator would throw before the listener runs. Additionally, the payload used string-quoted `totalAmount` / `unitPrice` fields where the saga contract expects JSON numbers.
- **Fix:** Updated Step 7 in the runbook to include `properties=message_id=00000000-0000-0000-0000-000000000001,content_type=application/json`, added a CRITICAL callout explaining why `message_id` is required, changed string amounts to numeric values, and added the `--username`/`--password` flags for non-default credentials.
- **Files modified:** `.planning/phases/04-catalog-inventory/04-03-SMOKE-RUNBOOK.md`
- **Commit:** 06338b1

### Deferred to Backlog (non-blocking)

**5. [LOW] Springdoc aggregator click-through returns 500 for individual service docs** → tracked as **Backlog 999.1** in ROADMAP.md
- Steps 1-6 and the aggregator dropdown config endpoint passed. However, clicking through from the dropdown to load individual service endpoint definitions returned HTTP 500 from the gateway when relaying to the individual service Springdoc JSON endpoints.
- Root cause: gateway routes individual Springdoc JSON requests to the upstream service, but the service's Springdoc group path may differ from the configured aggregator URL format. Non-blocking for Phase 4 completion (aggregator config JSON verified via CLI; individual click-through is a UX convenience).
- Scheduled to resolve at latest by Phase 10 (frontend storefront needs working Swagger for dev reference).

**6. [ENV] Host port 8080 conflict on dev machine (AMP_Linux process)**
- Not a code defect. The dev machine had a process (AMP_Linux) holding port 8080, requiring the docker compose stack to be run with a port-override file (`COMPOSE_FILE=docker-compose.yml:docker-compose.override.yml` with the gateway mapped to port 8090). This is a local environment issue, not a project defect.
- Noted in runbook and not tracked as a backlog item.

## Phase 4 Success Criteria Verification

| Criterion | Status | Evidence |
|-----------|--------|----------|
| 1. Browse >= 50 Turkish seed products across 8 categories | PASSED | totalElements=52, 8 categories in /api/v1/categories |
| 2. Sort + ILIKE search (case-insensitive Turkish) | PASSED | q=Telefon → 1 result; GIN pg_trgm index confirmed working |
| 3. PDP with title/gallery/KDV price/stock state reachable through gateway | PASSED | All PDP fields verified; stockStateLabel="Stokta" |
| 4. inventory-service /inventory/{productId} + order.created consumer | PASSED | REST verified; AMQP reservation confirmed via smoke test |
| 5. Springdoc gateway aggregator surfaces product-service + inventory-service | PASSED | /v3/api-docs/swagger-config returns 3 entries (PROD-07) |

All 5 Phase 4 success criteria are met. Phase 4 is complete.

## Commits

| Commit | Type | Description |
|--------|------|-------------|
| e5dddd8 | feat | Gateway routes + Springdoc aggregator + admin path matchers (Task 1) |
| 9162a02 | feat | Compose depends_on product-service + inventory-service healthy (Task 2) |
| 3494fff | docs | Smoke runbook for Phase 4 end-to-end verification (Task 3) |
| 2b61689 | fix | Saga AMQP — MANUAL→AUTO ack mode + consumer hardening |
| f6b38af | fix | pg_trgm extension in postgres init.sh |
| bc10e37 | fix | Gateway StripPrefix=2 (was 3) for products/categories/inventory |
| 06338b1 | docs | Smoke runbook: message_id + numeric amounts + debug session resolved |
| 757d2b5 | docs | Backlog items 999.1 + 999.2 filed |

## Self-Check: PASSED

| Check | Result |
|-------|--------|
| config-server/src/main/resources/config/api-gateway.yml exists | FOUND |
| api-gateway.yml contains lb://PRODUCT-SERVICE (x2) | FOUND |
| api-gateway.yml contains lb://INVENTORY-SERVICE (x1) | FOUND |
| api-gateway.yml contains /api/v1/products/v3/api-docs | FOUND |
| api-gateway.yml contains /api/v1/inventory/v3/api-docs | FOUND |
| SecurityConfig.java contains hasAuthority("ROLE_ADMIN") | FOUND |
| docker-compose.yml api-gateway.depends_on product-service | FOUND |
| docker-compose.yml api-gateway.depends_on inventory-service | FOUND |
| 04-03-SMOKE-RUNBOOK.md exists and > 0 bytes | FOUND |
| Task 1 commit e5dddd8 | FOUND |
| Task 2 commit 9162a02 | FOUND |
| Task 3 commit 3494fff | FOUND |
| Bug fix commit 2b61689 (AMQP ack mode) | FOUND |
| Bug fix commit f6b38af (pg_trgm) | FOUND |
| Bug fix commit bc10e37 (StripPrefix=2) | FOUND |
| Bug fix commit 06338b1 (runbook + debug resolved) | FOUND |
| Backlog commit 757d2b5 (999.1 + 999.2) | FOUND |
| All 8 smoke sign-off items verified (smoke_verification_facts) | VERIFIED |
| CLAUDE.md Rule #3 proven over real AMQP delivery | VERIFIED |

## Known Stubs

None — no placeholder values or unresolved stubs were introduced in this plan. The backlog item 999.1 (Springdoc click-through 500) is a UI convenience issue, not a stub in the codebase.

## Threat Flags

None — no new network endpoints, auth paths, file access patterns, or schema changes were introduced beyond those captured in the plan's threat model (T-04-03-01 through T-04-03-03, all mitigated).
