---
phase: 3
slug: identity-gateway-auth
status: draft
nyquist_compliant: true
wave_0_complete: false
created: 2026-04-29
updated: 2026-04-29
---

# Phase 3 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (Jupiter) — comes via `spring-boot-starter-test` (Spring Boot 3.5.14) |
| **Config file** | `identity-service/build.gradle.kts` (test dependencies block) |
| **Quick run command** | `./gradlew :identity-service:test --tests "*PasswordEncoderTest"` |
| **Full suite command** | `./gradlew test` (all modules) |
| **Estimated runtime** | ~30 seconds quick · ~3 minutes full (with `infra-tests` Testcontainers if W2 enables it) |

> See 03-RESEARCH.md `## Validation Architecture` for the four-layer test pyramid (smoke unit · slice · contract · boundary integration). Wave 0 installs no new framework — `service-template/` already brings JUnit 5; identity-service inherits via clone.

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :identity-service:test` (filtered to identity-service to keep latency bounded)
- **After every plan wave:** Run `./gradlew :identity-service:test :api-gateway:test` (both touched modules)
- **Before `/gsd-verify-work`:** `./gradlew build` plus the end-to-end smoke (register → login → `GET /auth/me` via curl through gateway) must be green
- **Max feedback latency:** 60 seconds (per-task)

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 03-01-T01 | 03-01 | 0 | AUTH-01, AUTH-05, AUTH-07, QUAL-02 | T-3-01, T-3-04 | Skeleton clone preserves processed_events DDL + correlationId MDC | structural | `test -f identity-service/src/main/java/com/n11/identity/IdentityServiceApplication.java && ! find identity-service/src/main -name '*.template' \| grep -q . && ! grep -r '__SERVICE_PACKAGE__\|<SERVICE_NAME>' identity-service/src/main` | ⬜ | ⬜ pending |
| 03-01-T02 | 03-01 | 0 | AUTH-01, QUAL-02 | T-3-04 | identity-service compiles as a Gradle subproject; oauth2-resource-server starter wired | compile | `./gradlew :identity-service:compileJava --no-daemon && ./gradlew projects --no-daemon \| grep -c 'identity-service'` | ⬜ | ⬜ pending |
| 03-01-T03 | 03-01 | 0 | AUTH-05, AUTH-07 | T-3-01, T-3-04 (Pitfall #14) | docker-compose block has NO host-port mapping; .env documents JWT/admin slots | structural | `docker compose config > /dev/null && grep -c '^  identity-service:' docker-compose.yml \| grep -c 1 && awk '/^  identity-service:/,/^  [a-z]/' docker-compose.yml \| grep -c 'ports:' \| grep -c 0` | ⬜ | ⬜ pending |
| 03-02-T01 | 03-02 | 0 | AUTH-01, AUTH-05, AUTH-08 | T-3-06, T-3-07 | Flyway V2 ships D-09 fields + D-11 partial unique index; V3 admin seed uses placeholders; V4 outbox per saga-contracts §5.1 | structural | `test -f identity-service/src/main/resources/db/migration/V2__init_users_addresses.sql && grep -c 'CREATE UNIQUE INDEX idx_addresses_user_default' identity-service/src/main/resources/db/migration/V2__init_users_addresses.sql && grep -c 'CREATE TABLE outbox' identity-service/src/main/resources/db/migration/V4__init_outbox.sql` | ⬜ | ⬜ pending |
| 03-02-T02 | 03-02 | 0 | AUTH-01, AUTH-05 | T-3-01, T-3-07 | Per-service config-server YAML uses identity_user creds + Flyway placeholders + JWT env-var pass-through; NO PEM in YAML | structural | `test -f config-server/src/main/resources/config/identity-service.yml && grep -c 'username: identity_user' config-server/src/main/resources/config/identity-service.yml && grep -c '\\-----BEGIN' config-server/src/main/resources/config/identity-service.yml \| grep -c 0` | ⬜ | ⬜ pending |
| 03-02-T03 | 03-02 | 0 | AUTH-01 | T-3-08 | user.registered schema locked + classpath mirror identical (drift gate enabler) | structural | `test -f .planning/saga-contracts/user-registered.schema.json && diff .planning/saga-contracts/user-registered.schema.json common-events/src/main/resources/saga-schemas/user-registered.schema.json && jq -e '.additionalProperties == false' .planning/saga-contracts/user-registered.schema.json` | ⬜ | ⬜ pending |
| 03-03-T01 | 03-03 | 1 | AUTH-02, AUTH-03, AUTH-05, AUTH-07 | T-3-01, T-3-02 | RS256 keypair from env-only PEM; permit-all servlet chain (D-15); JWKS serves public-only via toPublicJWK | compile | `./gradlew :identity-service:compileJava --no-daemon` | ⬜ | ⬜ pending |
| 03-03-T02 | 03-03 | 1 | AUTH-02, AUTH-03, AUTH-07, QUAL-02 | T-3-01, T-3-09, T-3-10 | JwtIssuerService produces RS256 JWT with sub/iat/exp/roles/email/fullName, 24h expiry (D-01); BCrypt cost-10 round-trip | unit | `./gradlew :identity-service:test --tests "*PasswordEncoderTest" --no-daemon` | ⬜ | ⬜ pending |
| 03-04-T01 | 03-04 | 2 | AUTH-01, AUTH-03, AUTH-05, AUTH-08 | T-3-06, T-3-11 | JPA entities mirror V2 DDL; @ManyToMany users↔roles; partial-unique on addresses(user_id) WHERE is_default verified at slice layer | slice | `./gradlew :identity-service:test --tests "*EntityTest" --no-daemon` | ⬜ | ⬜ pending |
| 03-04-T02 | 03-04 | 2 | AUTH-01, AUTH-02, AUTH-03, AUTH-05, AUTH-07 | T-3-11, T-3-12 | UserService.register hashes via BCrypt(10), assigns ROLE_USER (D-07), throws 409 on dup email; AuthController.me reads X-User-Id header (D-15) | compile + unit | `./gradlew :identity-service:compileJava --no-daemon && ./gradlew :identity-service:test --tests "*PasswordEncoderTest" --no-daemon` | ⬜ | ⬜ pending |
| 03-04-T03 | 03-04 | 2 | AUTH-08 | T-3-06 | AddressService.create flips existing default in same @Transactional before inserting (D-11); validation messages in Turkish (LOC) | compile + unit | `./gradlew :identity-service:compileJava --no-daemon && ./gradlew :identity-service:test --tests "*PasswordEncoderTest" --no-daemon` | ⬜ | ⬜ pending |
| 03-05-T01 | 03-05 | 3 | AUTH-01, QUAL-02 | T-3-15 | OutboxRepository uses FOR UPDATE SKIP LOCKED (Risk A3); identity.tx exchange declared topic+durable | compile | `./gradlew :identity-service:compileJava --no-daemon` | ⬜ | ⬜ pending |
| 03-05-T02 | 03-05 | 3 | AUTH-01, QUAL-02 | T-3-14 | OutboxBackedUserRegistrationOutboxPublisher writes envelope+payload in same @Transactional as users INSERT (D-12); poller drains every 5s (CD-04) | compile | `./gradlew :identity-service:compileJava --no-daemon` | ⬜ | ⬜ pending |
| 03-05-T03 | 03-05 | 3 | AUTH-01, QUAL-02 | T-3-08, T-3-14, T-3-15 | OutboxIntegrationTest boots Postgres + RabbitMQ via Testcontainers; produced JSON validates against user-registered.schema.json (drift gate) | boundary integration | `./gradlew :identity-service:test --tests "*OutboxIntegrationTest" --no-daemon` | ⬜ | ⬜ pending |
| 03-06-T01 | 03-06 | 4 | AUTH-02, AUTH-03, AUTH-04, AUTH-06 | T-3-01, T-3-02, T-3-03 | NimbusReactiveJwtDecoder + JwtTimestampValidator(30s) (D-04); GatewayHeaderInjectionFilter strips Authorization unconditionally (api-contracts §4); roles claim authority converter prepares hasRole(ADMIN) for Phase 4 | slice + unit | `./gradlew :api-gateway:test --tests "*SecurityConfigTest" --tests "*GatewayHeaderInjectionFilterTest" --no-daemon` | ⬜ | ⬜ pending |
| 03-06-T02 | 03-06 | 4 | AUTH-06 | T-3-18 | Gateway YAML pins jwk-set-uri to docker-compose hostname (NOT lb://, Risk 2); Springdoc aggregator surfaces identity-service; api-gateway depends_on identity-service healthy (Risk 1) | structural | `grep -c 'jwk-set-uri: http://identity-service:8081/.well-known/jwks.json' config-server/src/main/resources/config/api-gateway.yml && docker compose config > /dev/null` | ⬜ | ⬜ pending |
| 03-06-T03 | 03-06 | 4 | AUTH-02, AUTH-03, AUTH-04, AUTH-06 | T-3-01, T-3-02, T-3-03, T-3-17, T-3-18 | End-to-end smoke: register→login→/auth/me round-trip; 401 without token; Authorization stripped downstream; outbox row drained to identity.tx | manual + e2e | human-verify checkpoint (`.planning/phases/03-identity-gateway-auth/03-06-E2E-SMOKE.md`) | ⬜ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `identity-service/src/test/java/com/n11/identity/PasswordEncoderTest.java` — BCrypt cost-10 round-trip + salt verification (D-16, QUAL-02 starter; authored by 03-03 Task 2)
- [ ] `identity-service/src/test/java/com/n11/identity/user/UserEntityTest.java` — JPA slice (`@DataJpaTest`) verifying `password_hash` column, role FK, and partial-unique `is_default` index (authored by 03-04 Task 1)
- [ ] `api-gateway/src/test/java/com/n11/gateway/SecurityConfigTest.java` + `api-gateway/src/test/java/com/n11/gateway/GatewayHeaderInjectionFilterTest.java` — Spring Security reactive slice (`@WebFluxTest` + `SecurityMockServerConfigurers.mockJwt()`) covering 401-on-missing-Bearer, authenticated-passthrough, and unconditional Authorization-strip (authored by 03-06 Task 1)
- [ ] `identity-service/src/test/resources/application-test.yml` — disables Eureka/RabbitMQ-startup for unit slice tests; supplies test RSA keypair (authored by 03-05 Task 3)
- [ ] `identity-service/src/test/java/com/n11/identity/outbox/OutboxIntegrationTest.java` — Testcontainers boundary harness; register → outbox row → schema-validate → poll → sent_at flip (authored by 03-05 Task 3)

*All other infrastructure (JUnit 5, Mockito, AssertJ, Spring Boot Test) inherits from `service-template/skeleton/build.gradle.kts` — no new framework install.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| End-to-end JWT flow through real gateway + identity-service | AUTH-01..04, AUTH-06 | Requires docker-compose stack up; covered by automated boundary test if W2 enables it, otherwise manual via 03-06 Task 3 checkpoint | See `.planning/phases/03-identity-gateway-auth/03-06-E2E-SMOKE.md` (created by 03-06 Task 3) |
| JWKS rotation hot-swap | AUTH-04 success criterion #4 | Requires changing `JWT_PRIVATE_KEY` env, restarting identity-service, observing gateway picks up new `kid` within ~1h via JWKS refresh | Outside automated CI; documented in `identity-service/README.md` rotation runbook |
| Schema isolation deny matrix | ARCH-09/-10 (inherited from Phase 1) | Already covered by `infra-tests/.../SchemaIsolationBoundaryTest` | Re-run as part of `./gradlew :infra-tests:test` |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references (PasswordEncoderTest, UserEntityTest, SecurityConfigTest, GatewayHeaderInjectionFilterTest, OutboxIntegrationTest, application-test.yml)
- [x] No watch-mode flags (Gradle is stateless per invocation)
- [x] Feedback latency < 60s per-task (per-task `<automated>` commands all under 60s; OutboxIntegrationTest is the only Testcontainers-backed task and runs ~45s)
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** pending wave 0 commit
