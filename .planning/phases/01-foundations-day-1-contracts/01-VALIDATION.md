---
phase: 1
slug: foundations-day-1-contracts
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-28
---

# Phase 1 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit Jupiter 5.11.x (Spring Boot 3.5.14 transitive) + Testcontainers Postgres |
| **Config file** | `gradle/libs.versions.toml` (Wave 0 creates it) |
| **Quick run command** | `./gradlew :infra-tests:test --tests CrossSchemaDenyTest` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | ~60 seconds (Testcontainers Postgres pull + boot dominates first run; ~15s warm) |

---

## Sampling Rate

- **After every task commit:** Run quick command (compile + the most recent test)
- **After every plan wave:** Run `./gradlew build` (full compile + all tests)
- **Before `/gsd-verify-work`:** Full suite green AND `docker compose up` succeeds within 60s with `/actuator/gateway/routes` returning 200
- **Max feedback latency:** 60 seconds (Testcontainers boundary smoke is the slowest)

---

## Per-Task Verification Map

> The planner fills this table from PLAN frontmatter `tasks[]`. Refresh after each plan revision.

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| TBD     | TBD  | 0/1/2 | TBD       | TBD        | TBD             | unit/integration/contract | TBD | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `gradle/libs.versions.toml` — version catalog with Spring Boot 3.5.14, Spring Cloud 2025.0.x, Flyway 12.5.0, Springdoc 2.8.17, Testcontainers, networknt JSON Schema validator pinned
- [ ] `settings.gradle.kts` — flat top-level subprojects (eureka-server, config-server, api-gateway, common-error, common-logging, common-events, service-template, infra-tests)
- [ ] Root `build.gradle.kts` — Java 21 toolchain, JUnit 5 conventions, repositories
- [ ] `infra-tests/src/test/java/.../CrossSchemaDenyTest.java` — Testcontainers smoke test stub for D-05
- [ ] `common-events/src/test/java/.../AbstractEventSchemaTest.java` — drift gate base class for D-08
- [ ] `infra/postgres/init.sh` — env-var-aware bootstrap (research finding #2: init.sql cannot interpolate env vars)
- [ ] Java 21 LTS install (Corretto 21 via SDKMan or equivalent — current local is Java 23)

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| `docker compose up` boots all 5 infra services healthy <60s | SC-1, ARCH-10 | Requires Docker daemon + network | `docker compose up -d` then `curl -f http://localhost:8761` (Eureka), `curl -f http://localhost:8888/actuator/health` (Config), `curl -f http://localhost:8080/actuator/gateway/routes` (Gateway). All 200 within 60s. |
| Gateway aggregator UI shows empty Springdoc list | SC-1, ARCH-04 | Visual sanity | Open `http://localhost:8080/swagger-ui.html` — empty `urls=[]` is expected in Phase 1. |
| `gitleaks` clean run in CI | SC-5, QUAL-09 | GH Actions runner | Push branch → `Lint / gitleaks` workflow green. Local manual: `docker run -v $PWD:/repo zricethezav/gitleaks:latest detect --source /repo`. |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies (planner-enforced)
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references (no test framework, no settings.gradle, no init script)
- [ ] No watch-mode flags (Gradle daemon allowed; `--continuous` forbidden)
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
