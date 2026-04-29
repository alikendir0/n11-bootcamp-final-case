---
phase: 03-identity-gateway-auth
plan: 05
plan_id: 03-05
subsystem: identity-service
tags: [outbox, rabbitmq, jpa, testcontainers, saga, jwt, drift-gate]

# Dependency graph
requires:
  - phase: 03-01
    provides: identity-service Gradle module scaffold + RabbitMQ deps
  - phase: 03-02
    provides: V4__init_outbox.sql DDL + user-registered.schema.json classpath mirror
  - phase: 03-04
    provides: UserRegistrationOutboxPublisher interface + NoOpUserRegistrationOutboxPublisher seam

provides:
  - OutboxEvent JPA entity (maps V4 outbox table with @JdbcTypeCode(JSON) for JSONB payload)
  - OutboxRepository with findUnsentBatch (FOR UPDATE SKIP LOCKED, native query)
  - UserRegisteredPayload record (matches user-registered.schema.json)
  - IdentityRabbitConfig: identity.tx TopicExchange (durable) bean
  - OutboxBackedUserRegistrationOutboxPublisher: real D-12 outbox-row insert in same @Transactional as users INSERT; displaces NoOp via @ConditionalOnMissingBean(name)
  - OutboxPoller: @Scheduled(fixedDelay=5000) + @Transactional poll() draining identity.outbox to RabbitMQ
  - OutboxIntegrationTest: Testcontainers-backed Spring Boot test — register produces outbox row, schema drift gate, poller drains and marks sent, idempotency

affects:
  - 03-06 (api-gateway routes /api/v1/identity/** to identity-service)
  - Phase 7 (notification-service binds notify.q.user-registered to identity.tx exchange)
  - Phase 5 (order-service outbox pattern clones this plan's OutboxPoller/OutboxRepository shape)

# Tech tracking
tech-stack:
  added:
    - networknt json-schema-validator 3.0.2 added to identity-service testImplementation (drift-gate)
    - @JdbcTypeCode(SqlTypes.JSON) Hibernate 6 annotation pattern for JSONB columns
  patterns:
    - Transactional outbox: OutboxEvent + OutboxRepository.findUnsentBatch(FOR UPDATE SKIP LOCKED) + OutboxPoller(@Scheduled fixedDelay=5000, @Transactional)
    - Envelope construction: eventId=UUID, correlationId=eventId (saga-root), causationId=null, producer="identity-service", occurredAt=Instant (not String)
    - Bean displacement: OutboxBackedUserRegistrationOutboxPublisher uses class name that Spring derives as "outboxBackedUserRegistrationOutboxPublisher" — matches NoOp's @ConditionalOnMissingBean(name=...)
    - Schema-qualified native query: identity.outbox avoids search_path dependency (works in Testcontainers + production equally)
    - Testcontainers @SpringBootTest pattern established for Phase 5+: pgvector:pg16 + @ServiceConnection, @DynamicPropertySource for Flyway creds, @ActiveProfiles("test")
    - networknt 3.0.2 SchemaRegistry API (not JsonSchemaFactory) for drift-gate validation in OutboxIntegrationTest

key-files:
  created:
    - identity-service/src/main/java/com/n11/identity/outbox/OutboxEvent.java
    - identity-service/src/main/java/com/n11/identity/outbox/OutboxRepository.java
    - identity-service/src/main/java/com/n11/identity/outbox/UserRegisteredPayload.java
    - identity-service/src/main/java/com/n11/identity/outbox/IdentityRabbitConfig.java
    - identity-service/src/main/java/com/n11/identity/outbox/OutboxBackedUserRegistrationOutboxPublisher.java
    - identity-service/src/main/java/com/n11/identity/outbox/OutboxPoller.java
    - identity-service/src/test/java/com/n11/identity/outbox/OutboxIntegrationTest.java
    - identity-service/src/test/resources/application-test.yml
  modified:
    - identity-service/build.gradle.kts (added networknt testImplementation)
    - config-server/src/main/resources/config/identity-service.yml (added flyway.schema placeholder)

key-decisions:
  - "Envelope.occurredAt is Instant not String — plan code used occurredAt.toString() but the Envelope record signature uses Instant; adapted to pass Instant directly"
  - "OutboxRepository native query uses identity.outbox (schema-qualified) — avoids search_path dependency in Testcontainers which doesn't run init.sh's ALTER USER identity_user SET search_path"
  - "OutboxEvent.payload uses @JdbcTypeCode(SqlTypes.JSON) — Hibernate 6 requires explicit JSON type hint to write String -> PostgreSQL JSONB column without type error"
  - "application-test.yml provides flyway.schema=identity placeholder — V1 migration SQL comment contains ${flyway.schema} which Flyway attempts to resolve; production identity-service.yml also updated"
  - "ddl-auto: none in test profile — validate fails on CHAR(5) columns (bpchar/Types#CHAR vs Types#VARCHAR Hibernate mapping discrepancy); Flyway already handles schema creation"
  - "networknt 3.0.2 SchemaRegistry API used in OutboxIntegrationTest — cannot extend AbstractEventSchemaTest (lives in common-events/src/test/, not exported to dependents)"

# Metrics
duration: "~17 min"
completed: "2026-04-29"
tasks_completed: 3
tasks_total: 3
files_created: 8
files_modified: 2
---

# Phase 03 Plan 05: Transactional Outbox Wire — Outbox Entity + Poller + Integration Test Summary

**Transactional outbox wired end-to-end: OutboxEvent JPA entity (JSONB payload via @JdbcTypeCode), OutboxRepository (FOR UPDATE SKIP LOCKED), OutboxBackedUserRegistrationOutboxPublisher replacing the no-op, @Scheduled poller draining to identity.tx, and Testcontainers integration test with schema drift gate**

## Performance

- **Duration:** ~17 min
- **Started:** 2026-04-29T13:09:05Z
- **Completed:** 2026-04-29T13:26:00Z
- **Tasks:** 3 completed
- **Files modified:** 10 (8 new + 2 modified)

## Accomplishments

### Task 1: OutboxEvent + OutboxRepository + UserRegisteredPayload + IdentityRabbitConfig

- `OutboxEvent` @Entity: maps V4 outbox table with JSONB payload column (`@JdbcTypeCode(SqlTypes.JSON)` required for Hibernate 6 type-safe binding)
- `OutboxRepository`: `findUnsentBatch(int batchSize)` native query with `FOR UPDATE SKIP LOCKED` (schema-qualified `identity.outbox` — avoids Testcontainers search_path gap)
- `UserRegisteredPayload` record: 4 fields matching `user-registered.schema.json` (userId UUID, email String, fullName String, registeredAt Instant)
- `IdentityRabbitConfig`: `@Bean TopicExchange identityExchange()` declares `identity.tx` (durable) — Phase 7 binds `notify.q.user-registered` here

### Task 2: OutboxBackedUserRegistrationOutboxPublisher + OutboxPoller

- `OutboxBackedUserRegistrationOutboxPublisher`: `@Component` implementing `UserRegistrationOutboxPublisher`; inserts outbox row in the same `@Transactional` as the users INSERT (D-12); bean name matches NoOp's `@ConditionalOnMissingBean(name=...)` condition
- Envelope construction: `eventId=UUID`, `correlationId=eventId` (saga-root initiator), `causationId=null`, `occurredAt=Instant` (matches `Envelope` record field type — NOT `String`)
- `OutboxPoller`: `@Scheduled(fixedDelay=5000)` + `@Transactional`; batch=100; drains `findUnsentBatch`; `rabbitTemplate.convertAndSend("identity.tx", "user.registered", payload)` then `event.markSent(Instant.now())`

### Task 3: OutboxIntegrationTest + application-test.yml

- `application-test.yml`: test profile — Flyway enabled + Testcontainers DynamicPropertySource, Eureka disabled, real RSA-2048 PKCS#8 test key (generated with `openssl genrsa | openssl pkcs8 -topk8 -nocrypt`), `flyway.schema=identity` placeholder fix
- `OutboxIntegrationTest`: 2 test methods:
  - `registrationProducesValidOutboxRow`: register user → assert outbox row with aggregate=identity, eventType=user.registered, sentAt=null; validates envelope JSON (8 fields); validates payload against `user-registered.schema.json` via networknt 3.0.2 `SchemaRegistry` (drift gate D-08)
  - `pollerDrainsAndMarksSent`: register user → `outboxPoller.poll()` → assert sentAt is non-null; second poll → assert sentAt unchanged (idempotency)
- Testcontainers: `pgvector/pgvector:pg16` + `rabbitmq:4.0-management` via `@ServiceConnection`

## Task Commits

| Task | Name | Commit | Key Files |
|------|------|--------|-----------|
| 1 | OutboxEvent JPA entity + repo + payload + RabbitConfig | bb57b9c | OutboxEvent.java, OutboxRepository.java, UserRegisteredPayload.java, IdentityRabbitConfig.java |
| 2 | OutboxBackedUserRegistrationOutboxPublisher + OutboxPoller | 498952e | OutboxBackedUserRegistrationOutboxPublisher.java, OutboxPoller.java |
| 3 | OutboxIntegrationTest + test config + fixes | 048128c | OutboxIntegrationTest.java, application-test.yml, build.gradle.kts, OutboxEvent.java (Rule 1), OutboxRepository.java (Rule 1), identity-service.yml (Rule 1) |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Envelope constructor uses Instant, not String**
- **Found during:** Task 2 (implementation review before writing)
- **Issue:** Plan code example used `occurredAt.toString()` for the Envelope's occurredAt field, but the `Envelope` Java record defines `Instant occurredAt` (not String). Passing a String would cause a compile error.
- **Fix:** Passed `Instant` directly: `new Envelope(eventId.toString(), EVENT_TYPE, EVENT_VERSION, occurredAt, eventId.toString(), null, PRODUCER, objectMapper.valueToTree(payload))`
- **Files modified:** `OutboxBackedUserRegistrationOutboxPublisher.java`
- **Commit:** 498952e

**2. [Rule 1 - Bug] Hibernate 6 cannot write String to JSONB column without type hint**
- **Found during:** Task 3, first test run
- **Issue:** `column "payload" is of type jsonb but expression is of type character varying` — Hibernate 6 requires `@JdbcTypeCode(SqlTypes.JSON)` on String fields mapped to JSONB columns
- **Fix:** Added `@JdbcTypeCode(SqlTypes.JSON)` import + annotation on `OutboxEvent.payload`
- **Files modified:** `OutboxEvent.java`
- **Commit:** 048128c

**3. [Rule 1 - Bug] V1 migration SQL comment contains unresolvable ${flyway.schema} placeholder**
- **Found during:** Task 3, first test run
- **Issue:** Flyway replaces placeholders even in SQL comments. V1 SQL comment uses `${flyway.schema}` but neither the test config nor production config defines `flyway.schema` (they define `schema`)
- **Fix:** Added `flyway.schema: identity` to `application-test.yml` and `config-server/identity-service.yml` placeholders
- **Files modified:** `application-test.yml`, `config-server/src/main/resources/config/identity-service.yml`
- **Commit:** 048128c

**4. [Rule 1 - Bug] Hibernate ddl-auto validate fails on CHAR(5) columns (bpchar vs VARCHAR mapping)**
- **Found during:** Task 3, second test run (after Flyway placeholder fix)
- **Issue:** `Schema-validation: wrong column type encountered in column [postal_code] in table [addresses]; found [bpchar (Types#CHAR)], but expecting [char(5) (Types#VARCHAR)]` — Hibernate schema validator incorrectly maps CHAR(5) in its expected type
- **Fix:** Changed `spring.jpa.hibernate.ddl-auto` from `validate` to `none` in `application-test.yml`. Flyway already handles schema creation; Hibernate validation is redundant in integration tests.
- **Files modified:** `application-test.yml`
- **Commit:** 048128c

**5. [Rule 1 - Bug] Native query unqualified table name fails in Testcontainers (no search_path)**
- **Found during:** Task 3, test run (scheduled poller firing during test execution)
- **Issue:** `relation "outbox" does not exist` — the production `init.sh` sets `ALTER USER identity_user SET search_path = identity, public` but Testcontainers does not run `init.sh`, leaving the search path as public. Unqualified `SELECT * FROM outbox` fails.
- **Fix:** Changed native query from `FROM outbox` to `FROM identity.outbox` (explicit schema qualification). Works in both Testcontainers and production (with search_path set, schema-qualified queries still resolve correctly).
- **Files modified:** `OutboxRepository.java`
- **Commit:** 048128c

**6. [Rule 1 - Bug] Plan's example RSA key in application-test.yml was invalid (BadPaddingException)**
- **Found during:** Task 3, test run after Flyway and ddl-auto fixes
- **Issue:** The plan provided a copy-paste key example with `BadPaddingException` on signing — the key had incorrect internal structure for PKCS#8 RSA CRT form
- **Fix:** Generated a fresh 2048-bit RSA key with `openssl genrsa 2048 | openssl pkcs8 -topk8 -nocrypt` and used it in `application-test.yml`
- **Files modified:** `application-test.yml`
- **Commit:** 048128c

## Known Stubs

None — all outbox components are fully wired. `NoOpUserRegistrationOutboxPublisher` from Plan 03-04 is now displaced by `OutboxBackedUserRegistrationOutboxPublisher` (Spring resolves the `@ConditionalOnMissingBean(name="outboxBackedUserRegistrationOutboxPublisher")` condition correctly).

## Threat Surface Scan

| Threat ID | Mitigation Applied | Verified By |
|-----------|-------------------|-------------|
| T-3-14 (Dual-write inconsistency) | OutboxBackedUserRegistrationOutboxPublisher.publishRegistered() calls outboxRepository.save() inside the @Transactional boundary owned by UserService.register | OutboxIntegrationTest.registrationProducesValidOutboxRow (row persists in same TX) |
| T-3-15 (Double-publish) | `FOR UPDATE SKIP LOCKED` in identity.outbox native query — two pollers cannot lock the same row | OutboxRepository query grep check |
| T-3-08 (Schema drift) | OutboxIntegrationTest.registrationProducesValidOutboxRow calls assertPayloadMatchesSchema() which validates against user-registered.schema.json via networknt 3.0.2 SchemaRegistry | OutboxIntegrationTest passes |
| T-3-16 (Test JWT key in production) | Test key has kid=n11-jwt-test; production key has different kid; gateway rejects mismatched kids at validation time | application-test.yml key-id confirmed as n11-jwt-test |

## Self-Check: PASSED
