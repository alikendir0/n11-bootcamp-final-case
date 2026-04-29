---
phase: 03-identity-gateway-auth
plan: 01
subsystem: identity-service
tags: [scaffold, gradle, docker-compose, env-vars, jwt, spring-boot]
dependency_graph:
  requires:
    - service-template/skeleton/ (Phase 1 Plan 01-07)
    - common-error, common-logging, common-events (Phase 1 Plan 01-04)
    - infra/postgres/init.sh identity_user (Phase 1 Plan 01-03)
  provides:
    - identity-service Gradle subproject (compilable skeleton)
    - identity-service docker-compose service block (internal port 8081)
    - JWT_PRIVATE_KEY + JWT_KEY_ID + ADMIN_SEED_* env-var slots in .env.example
    - README operator runbooks for keypair + bcrypt hash generation
  affects:
    - settings.gradle.kts (identity-service added to include block)
    - docker-compose.yml (additive identity-service block)
    - .env.example (4 new placeholder slots, JWT_SIGNING_KEY removed)
tech_stack:
  added:
    - spring-boot-starter-oauth2-resource-server (NimbusJwtEncoder classes)
    - spring-boot-starter-security (BCryptPasswordEncoder)
    - spring-boot-testcontainers, testcontainers:postgresql/:rabbitmq
    - com.google.cloud.tools.jib plugin (n11/identity-service:dev)
  patterns:
    - service-template/skeleton/ clone procedure (4-step bash block per README)
    - Jib plugin with eclipse-temurin:21-jre base + G1GC flags
    - docker-compose env_file + depends_on service_healthy chain pattern
key_files:
  created:
    - identity-service/src/main/java/com/n11/identity/IdentityServiceApplication.java
    - identity-service/src/main/java/com/n11/identity/health/SampleHealthController.java
    - identity-service/src/main/resources/application.yml
    - identity-service/src/main/resources/logback-spring.xml
    - identity-service/src/main/resources/db/migration/V1__init_processed_events.sql
    - identity-service/build.gradle.kts
    - identity-service/README.md
  modified:
    - settings.gradle.kts (identity-service added to include block)
    - docker-compose.yml (identity-service block appended before volumes:)
    - .env.example (Phase 3 JWT + admin seed slots added, JWT_SIGNING_KEY removed)
    - gradlew (executable bit restored, chmod +x)
decisions:
  - "D-21 confirmed: identity-service on port 8081, internal-only (Pitfall #14 — no ports: mapping)"
  - "Production posture: optional: stripped from spring.config.import so missing config-server fails loud at boot"
  - "gradlew executable bit fixed in worktree — required for ./gradlew invocations in subsequent plan tasks"
metrics:
  duration: ~22 min
  completed_date: "2026-04-29"
  tasks_completed: 3
  tasks_total: 3
  files_created: 7
  files_modified: 4
---

# Phase 3 Plan 01: Identity-Service Module Shell Summary

**One-liner:** Identity-service Gradle module cloned from service-template skeleton with oauth2-resource-server + security starters, registered in settings.gradle.kts, wired into docker-compose.yml (internal port 8081, Pitfall #14 compliant), and env-var matrix seeded in .env.example with JWT keypair + admin seed placeholder slots.

## What Was Built

Plan 03-01 stood up the identity-service Gradle module by following the service-template/skeleton/ clone procedure verbatim. The module compiles, is recognized as a Gradle subproject, and has its docker-compose entry ready for `./gradlew :identity-service:jibDockerBuild` + `docker compose up`.

### Task 1: Skeleton Clone
- Executed the 4-step clone bash block from `service-template/skeleton/README.md`
- Renamed `ServiceApplication` → `IdentityServiceApplication`, added `@EnableScheduling` for Plan 03-05's OutboxPoller
- Stripped `optional:` from `spring.config.import` for production posture (ARCH-11)
- `V1__init_processed_events.sql` and `logback-spring.xml` carried verbatim from skeleton

### Task 2: Gradle Module
- Created `identity-service/build.gradle.kts` from `service-template/build.gradle.kts` with identity-specific additions:
  - `spring-boot-starter-oauth2-resource-server` — NimbusJwtEncoder classes for Plan 03-03
  - `spring-boot-starter-security` — BCryptPasswordEncoder for AUTH-07
  - Testcontainers (`postgresql` + `rabbitmq`) for Plan 03-04/03-05 integration tests
  - Jib plugin targeting `n11/identity-service:dev` on port 8081
- Added `"identity-service"` to `settings.gradle.kts` include block
- `./gradlew :identity-service:compileJava` exits 0 (verified)

### Task 3: Docker-Compose + Env Matrix
- Added `identity-service:` block to `docker-compose.yml` after `api-gateway:` block
- **No `ports:` mapping** — Pitfall #14 mitigation (reachable only through gateway on 8080)
- `env_file: .env` injects JWT_PRIVATE_KEY, JWT_KEY_ID, ADMIN_SEED_* at runtime
- `depends_on` with `condition: service_healthy` on postgres, rabbitmq, eureka-server, config-server
- Updated `.env.example` with 4 new placeholder slots; removed obsolete `JWT_SIGNING_KEY` name
- Created `identity-service/README.md` with operator runbooks:
  - `openssl genrsa 2048` → PEM PKCS#8 private key generation
  - `htpasswd -bnBC 10` → bcrypt cost-10 admin password hash

## Deviations from Plan

### Minor Deviations (auto-resolved)

**1. [Rule 3 - Blocking] gradlew not executable in worktree**
- **Found during:** Task 2 (attempt to run `./gradlew :identity-service:compileJava`)
- **Issue:** `gradlew` had mode 100644 (not executable) in the worktree's checkout
- **Fix:** `chmod +x gradlew` applied, mode change committed in Task 3 commit
- **Files modified:** `gradlew`
- **Commit:** 0811f9a

**2. [Rule 3 - Blocking] .env not present for docker compose config validation**
- **Found during:** Task 3 verification
- **Issue:** `docker compose config` fails with "env file .env not found" when `env_file:` references `.env` that is gitignored and absent in the worktree
- **Fix:** Created a temporary `.env` from `.env.example` for validation only; removed after verification
- **Impact:** None — `.env` is intentionally absent in version control (CLAUDE.md Rule #5). The `env_file:` reference is correct docker-compose posture

## Known Stubs

None — this plan creates infrastructure scaffolding only. No business logic, no data rendering paths. The `SampleHealthController.java` smoke endpoint returns a map with service name and timestamp (not a stub in the meaningful sense — it is an intentional smoke endpoint preserved through Phase 3).

## Threat Flags

No new threat surface beyond what the plan's threat model documents:
- JWT private key is env-only (`env_file:` reference, not embedded in YAML)
- Identity-service port 8081 is internal-only (no `ports:` mapping)

## Self-Check: PASSED

All created files exist on disk. All task commits verified in git history:
- `f9bb833` — feat(03-01): clone service-template skeleton into identity-service module
- `ecbac21` — feat(03-01): add identity-service Gradle module and register in settings.gradle.kts
- `0811f9a` — feat(03-01): add identity-service docker-compose block, env-var matrix and README runbook
