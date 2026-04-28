---
status: complete
phase: 01-foundations-day-1-contracts
source:
  - 01-01-SUMMARY.md
  - 01-02-SUMMARY.md
  - 01-03-SUMMARY.md
  - 01-04-SUMMARY.md
  - 01-05-SUMMARY.md
  - 01-06-SUMMARY.md
  - 01-07-SUMMARY.md
  - 01-08-SUMMARY.md
started: 2026-04-28T23:27:31Z
updated: 2026-04-28T23:36:01Z
executed_by: claude-opus-4-7 (autonomous on user request)
---

## Current Test

[testing complete]

## Tests

### 1. Cold Start Smoke Test
expected: From a clean slate (`docker compose down -v`), `docker compose up -d` brings all 5 services (postgres, rabbitmq, eureka-server, config-server, api-gateway) to `(healthy)` within 60s.
result: pass
evidence: |
  After `docker compose down -v` (volumes removed) followed by `docker compose up -d`,
  all 5 containers reached `(healthy)` in ~24 seconds — well under the 60s SC-1 budget.
  `docker compose ps` final state:
    n11-postgres        Up 23s (healthy)  0.0.0.0:5432->5432
    n11-rabbitmq        Up 23s (healthy)  0.0.0.0:5672, 15672
    n11-eureka-server   Up 23s (healthy)  0.0.0.0:8761
    n11-config-server   Up 23s (healthy)  0.0.0.0:8888
    n11-api-gateway     Up 12s (healthy)  0.0.0.0:8080

### 2. Saga + API Contracts on Disk
expected: `.planning/saga-contracts.md` + 9 schema files + `.planning/api-contracts.md` with 7 sections; all 9 schemas declare draft 2020-12.
result: pass
evidence: |
  ls .planning/saga-contracts/ → 9 files: envelope, order-cancelled, order-confirmed,
    order-created, payment-completed, payment-failed, stock-released, stock-reserve-failed,
    stock-reserved (all .schema.json)
  grep -l 'json-schema.org/draft/2020-12/schema' .planning/saga-contracts/*.schema.json | wc -l → 9
  api-contracts.md sections found: 1 endpoints, 2 routing, 3 allowlist, 4 auth-strip,
    5 correlation, 6 SSE caveat, 7 RFC-7807 — all 7 present.

### 3. Eureka Dashboard + Gateway Self-Registration
expected: Dashboard returns 200; /eureka/apps lists API-GATEWAY.
result: pass
evidence: |
  curl http://localhost:8761 → 200
  curl http://localhost:8761/eureka/apps → XML containing
    <application><name>API-GATEWAY</name><instance>...status>UP</status>...
  Instance ID: 9117dc3ba942:api-gateway:8080, IP 172.19.0.6.

### 4. Config Server Serves Shared Baseline + Gateway Overlay
expected: Shared baseline at /application/default; gateway overlay at /api-gateway/default has BOTH api-gateway.yml AND application.yml in propertySources.
result: pass
evidence: |
  /application/default → propertySources: ['classpath:/config/application.yml']
  /api-gateway/default → propertySources: ['classpath:/config/api-gateway.yml',
    'classpath:/config/application.yml'] — CD-05 overlay confirmed.

### 5. API Gateway Health + Routes Endpoint
expected: /actuator/health → UP; /actuator/gateway/routes → 200 with at least one route.
result: pass
evidence: |
  /actuator/health → {"status":"UP"}
  /actuator/gateway/routes → 1 route: ReactiveCompositeDiscoveryClient_API-GATEWAY
    (Phase 1 D-14 expected single self-route — business services arrive in Phase 3+).

### 6. Correlation-ID Echo at Gateway
expected: Gateway generates UUID when no header sent; echoes provided header verbatim.
result: pass
evidence: |
  Note: gateway local actuator endpoints (e.g. /actuator/health on :8080) do NOT pass
  through GlobalFilters. Filter only fires on FORWARDED routes. Re-tested via the
  discovery-locator self-route /api-gateway/**:
    No header sent → response carried `X-Correlation-Id: 346d73a6-c9c6-4e77-a513-c47dfb854dbe` (fresh UUID)
    Header `my-test-id-123` sent → response echoed `X-Correlation-Id: my-test-id-123`
  GatewayCorrelationIdFilter behavior matches D-09 spec exactly.

### 7. Cross-Schema Deny in Live Postgres
expected: product_user.current_schema() = 'product'; cross-schema query denied.
result: pass
evidence: |
  psql -U product_user -c "SELECT current_schema()" → product
  psql -U product_user -c "SELECT 1 FROM cart.cart_items LIMIT 1"
    → ERROR: permission denied for schema cart
  ARCH-09 boundary enforced at runtime as designed.

### 8. RabbitMQ Default Guest Disabled
expected: guest:guest → 401; configured user → 200 with broker info.
result: pass
evidence: |
  curl -u guest:guest http://localhost:15672/api/overview → HTTP 401
  curl -u $RABBITMQ_DEFAULT_USER:$RABBITMQ_DEFAULT_PASS → HTTP 200,
    rabbitmq_version: 4.3.0, product_name: RabbitMQ.

### 9. Gradle Build Clean Across All Modules
expected: `./gradlew build` exits 0; modules compile and pass tests.
result: pass
evidence: |
  ./gradlew build --no-daemon -q → exit 0 (BUILD SUCCESSFUL)
  Per-module test xml file counts:
    common-error: 1, common-events: 2, common-logging: 0,
    service-template: 0, eureka-server: 0, config-server: 0,
    api-gateway: 0, infra-tests: 1
  (Library modules with no tests is per Phase-1 design — Phase 5+ adds integration tests.)

### 10. Secret Hygiene + Gitleaks Scan
expected: gitleaks reports "no leaks"; .env not git-tracked; .gitignore covers secrets.
result: pass
evidence: |
  git ls-files .env → empty (untracked)
  .gitignore covers: .env, .env.local, .env.*.local, secrets/, **/application-local.yml,
    *.pem, *.key, **/bin/
  gitleaks v8 scan (1.54 MB scanned, 4.09s) → "no leaks found"
  .gitleaks.toml + .github/workflows/security.yml + ci.yml present.

### 11. Service-Template Archetype + Clone-Ready Skeleton
expected: service-template builds; skeleton has 5 .template files + README.md.
result: pass
evidence: |
  ./gradlew :service-template:build (rolled into Test 9 full build) → exit 0
  service-template/skeleton/ tree: README.md + 5 .template files:
    src-main/java/com/n11/__SERVICE_PACKAGE__/ServiceApplication.java.template
    src-main/java/com/n11/__SERVICE_PACKAGE__/health/SampleHealthController.java.template
    src-main/resources/application.yml.template
    src-main/resources/logback-spring.xml.template
    src-main/resources/db/migration/V1__init_processed_events.sql.template

### 12. infra-tests Cross-Schema Deny Test Class Present
expected: CrossSchemaDenyTest.java present; ./gradlew :infra-tests:compileTestJava exits 0.
result: pass
evidence: |
  Stronger result than expected: CrossSchemaDenyTest actually EXECUTED locally on this
  Windows host as part of `./gradlew build`. Required: `docker context use default`
  (the Testcontainers v2.0.5 discovery chain finds the npipe socket on the `default`
  context but not on the `desktop-linux` context per the 01-08 SUMMARY).
  TEST-com.n11.infra.CrossSchemaDenyTest.xml: tests=3, skipped=0, failures=0, errors=0
    productUserCannotReadCartSchema()  — pass (0.243s)
    productUserCanReadOwnSchema()       — pass (0.030s)
    cartUserCannotReadIdentitySchema() — pass (0.020s)
  Testcontainers booted pgvector/pgvector:pg16 with init.sh mounted, connected via
  npipe:////./pipe/docker_engine, ARCH-09 boundary verified end-to-end.

## Summary

total: 12
passed: 12
issues: 0
pending: 0
skipped: 0
blocked: 0

## Findings (non-blocking observations)

1. **Docker discovery on Windows is solvable, not blocked.** Plan 01-08 SUMMARY
   recorded that local Testcontainers execution was deferred to CI due to the
   `desktop-linux` context. Switching to `docker context use default` (which
   exposes `npipe:////./pipe/docker_engine`) lets Testcontainers v2.0.5 discover
   the daemon and run CrossSchemaDenyTest locally on Windows. Recommend updating
   01-08-SUMMARY notes and skeleton/README.md to document this as the canonical
   local-dev workflow.

2. **Correlation-ID filter scope clarification.** The GatewayCorrelationIdFilter
   only fires on FORWARDED routes (anything passing through the discovery-locator),
   not on the gateway's own /actuator/** endpoints. This is correct Spring Cloud
   Gateway behavior but worth documenting in api-gateway/SecurityConfig.java's
   Javadoc so Phase 3+ devs don't waste cycles debugging "why isn't my correlation
   ID showing up on /actuator/health".

## Gaps

[none]
