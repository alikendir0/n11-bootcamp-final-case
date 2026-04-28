---
phase: 01-foundations-day-1-contracts
plan: 01
subsystem: infra
tags: [gradle, java21, spring-boot, spring-cloud, gitleaks, github-actions, ci]

requires: []
provides:
  - Gradle 8.10 multi-module skeleton with 8 subprojects (eureka-server, config-server, api-gateway, common-error, common-logging, common-events, service-template, infra-tests)
  - Pinned version catalog (gradle/libs.versions.toml — Boot 3.5.14, Cloud 2025.0.0, Springdoc 2.8.17, Flyway 12.5.0, Testcontainers 2.0.5, networknt 3.0.2, logstash-logback 8.0)
  - Java 21 toolchain locked at JavaPluginExtension level (Corretto 21.0.11 installed at $HOME/.jdks/jdk21.0.11_10; Gradle auto-toolchain detects)
  - Secret-leak gate (gitleaks v8 config + GH Actions workflow)
  - CI build/test pipeline (.github/workflows/ci.yml — Corretto 21 + Gradle cache + build → infra-tests two-job flow)
  - .env.example placeholder slots (1 superuser + 10 service DBs + RabbitMQ + Phase 3+ commented stubs)
  - PROJECT.md deploy-target state verified to match D-15 revised
affects: [01-02, 01-03, 01-04, 01-05, 01-06, 01-07, 01-08, all-future-phases]

tech-stack:
  added: [gradle-8.10, java-21, spring-boot-3.5.14, spring-cloud-2025.0.0, gitleaks-action-v2, github-actions]
  patterns:
    - Cross-Cutting #1 — Boot/Jib plugins applied=false at root, applied selectively per service-app subproject (library subprojects remain plain JARs)
    - Version catalog as canonical version source (libs.versions.toml — referenced via libs.* in subproject build.gradle.kts)
    - Two-job CI: build runs full ./gradlew build, then infra-tests is gated on build success

key-files:
  created:
    - settings.gradle.kts
    - build.gradle.kts
    - gradle/libs.versions.toml
    - gradle/wrapper/gradle-wrapper.{jar,properties}
    - gradlew, gradlew.bat
    - {eureka-server,config-server,api-gateway,common-error,common-logging,common-events,service-template,infra-tests}/build.gradle.kts (stubs)
    - {each-subproject}/src/main/java/.gitkeep
    - .gitignore
    - .gitleaks.toml
    - .env.example
    - .github/workflows/security.yml
    - .github/workflows/ci.yml
  modified: []

key-decisions:
  - "settings.gradle.kts omits explicit versionCatalogs{ create(\"libs\") { from(...) } } block — Gradle 8.x auto-imports gradle/libs.versions.toml at the conventional path; explicit declaration causes 'you can only call the from method a single time' (deviation from RESEARCH §4.11 verbatim, semantically equivalent)"
  - ".gitleaks.toml uses gitleaks v8 [[allowlists]] schema with regexes = [...] (list of strings) instead of older [[allowlist.regexes]] array-of-tables (deviation from RESEARCH §4.12 — older schema fails to decode against gitleaks v8.x)"
  - "Java 21 install path: zip distribution to $HOME/.jdks/jdk21.0.11_10 (no MSI elevation required); system PATH still resolves to JDK 23 — Gradle auto-toolchain detects 21 from .jdks per the toolchain spec language and uses it for all compilation"

patterns-established:
  - "Gradle multi-module flat layout (D-12): every subproject is a sibling of root; settings.gradle.kts include() lists them; root build.gradle.kts subprojects{} block applies java + dependency-management to all"
  - "Library-vs-Boot-app plugin discipline (Cross-Cutting #1): root applies Boot+Jib with apply=false; subprojects opt in selectively (eureka/config/gateway/services do; common-* libs do not)"
  - "Version catalog (gradle/libs.versions.toml) is the single source of truth for every pinned dependency version; subproject build files reference libs.* aliases"
  - "CI gate sequence: build (compile + unit tests) → infra-tests (boundary smoke). infra-tests fails fast independently of build."

requirements-completed:
  - ARCH-01
  - QUAL-09
  - DEV-07

duration: ~30min (incl. 2 retries due to API 500 errors during executor spawn — final completion was inline)
completed: 2026-04-28
---

# Plan 01-01: Foundations Bootstrap Summary

**Gradle 8.10 multi-module project (8 subprojects, Java 21 toolchain, Spring Boot 3.5.14 + Spring Cloud 2025.0.0 BOMs) with gitleaks secret-scanning CI and PROJECT.md deploy-target state verified.**

## Performance

- **Duration:** ~30 min (2 executor agent spawns hit 500 errors mid-execution; completed inline by orchestrator)
- **Started:** 2026-04-28T18:25Z (executor spawn) → final completion 2026-04-28T18:45Z
- **Tasks:** 3 (Java toolchain, Gradle skeleton, secret hygiene + CI + PROJECT.md verify)
- **Files created:** 23 (8 stub `build.gradle.kts` + 8 `.gitkeep` + 7 root config files)

## Accomplishments

- `./gradlew projects --quiet` lists all 8 subprojects cleanly
- `./gradlew help` succeeds (no syntax errors in any build script)
- gitleaks scan reports "no leaks found" against the entire working tree
- Java 21 LTS (Corretto 21.0.11) installed at `$HOME/.jdks/jdk21.0.11_10` — Gradle toolchain auto-detects
- PROJECT.md confirmed to match D-15 revised (local docker-compose deploy; AWS EB+RDS dropped)
- Secret-leak prevention now active: gitleaks-action@v2 runs on every push and PR to main/master/develop
- CI two-job pipeline ready: `build` job runs `./gradlew build`; `infra-tests` job gated on build success

## Task Commits

1. **Task 1: Java 21 toolchain** — no source commit (system-level install at `$HOME/.jdks/jdk21.0.11_10`; Gradle auto-toolchain detects)
2. **Task 2: Gradle multi-module skeleton** — `a921f42` (feat: scaffold Gradle 8.10 multi-module project)
3. **Task 3: Secret hygiene + CI + PROJECT.md verify** — `66d45dd` (feat: lock secret hygiene + CI workflows)

## Files Created/Modified

- `settings.gradle.kts` — root project name + 8 subproject includes
- `build.gradle.kts` — Java 21 toolchain, Boot/Cloud/Testcontainers BOM imports, Boot+Jib plugins applied=false at root
- `gradle/libs.versions.toml` — pinned version catalog (Boot 3.5.14, Cloud 2025.0.0, Springdoc 2.8.17, Flyway 12.5.0, Testcontainers 2.0.5, networknt 3.0.2, logstash-logback 8.0)
- `gradle/wrapper/gradle-wrapper.{jar,properties}` + `gradlew{,.bat}` — Gradle 8.10 wrapper
- `{8 subprojects}/build.gradle.kts` + `src/main/java/.gitkeep` — stubs replaced by plans 01-04 through 01-08
- `.gitignore` — secrets exclusions (.env, secrets/, **/application-local.yml, *.pem, *.key) + build outputs + IDE/OS noise
- `.gitleaks.toml` — extends defaults + Spring `${ENV:default}` placeholder allowlist
- `.env.example` — POSTGRES_SUPERUSER + 10 service-DB + RabbitMQ creds + Phase 3+ commented placeholders (note: ORDERS_DB_PASSWORD, not ORDER_DB_PASSWORD, per RESEARCH finding #5)
- `.github/workflows/security.yml` — gitleaks-action@v2 CI gate
- `.github/workflows/ci.yml` — Corretto 21 + Gradle cache + build → infra-tests two-job pipeline

## Decisions Made

- **Gradle version-catalog import path:** Use the convention-based auto-import (Gradle 8.x picks up `gradle/libs.versions.toml` automatically). Explicit `versionCatalogs { create("libs") { from(...) } }` errors with "you can only call the 'from' method a single time" against Gradle 8.10. RESEARCH §4.11's snippet was written for older Gradle. Functionally identical — `libs.*` aliases are still available in subproject build files.
- **Gitleaks config schema:** Use `[[allowlists]]` (plural, list-of-tables) with `regexes = [...]` (list of strings). The older `[[allowlist.regexes]]` (array-of-tables nested under singular `allowlist`) fails to decode against gitleaks v8.x with "expected type 'string', got unconvertible type 'map[string]interface {}'". Deviation from RESEARCH §4.12 verbatim, semantically equivalent.
- **Java install fallback chosen:** Used the zip distribution path because admin elevation was not available in the executor sandbox. Corretto 21.0.11 unzipped to `$HOME/.jdks/jdk21.0.11_10` and discovered by Gradle auto-toolchain. System `java -version` still reports 23 — fine because the Java toolchain spec in `build.gradle.kts` instructs Gradle to use 21 regardless of PATH.

## Deviations from Plan

### Auto-fixed Issues

**1. [Schema-drift] Gradle 8.x version-catalog auto-import**
- **Found during:** Task 2 (settings.gradle.kts authoring)
- **Issue:** RESEARCH §4.11 verbatim block included `versionCatalogs { create("libs") { from(files("gradle/libs.versions.toml")) } }`, which double-registers the catalog under Gradle 8.x and errors at configuration time
- **Fix:** Removed the explicit `versionCatalogs {}` block; relied on Gradle's convention-based auto-import; left an inline comment explaining the omission
- **Files modified:** `settings.gradle.kts`
- **Verification:** `./gradlew projects --quiet` succeeds and lists all 8 subprojects
- **Committed in:** `a921f42`

**2. [Schema-drift] Gitleaks v8 allowlist schema**
- **Found during:** Task 3 (gitleaks local verification run)
- **Issue:** RESEARCH §4.12's `[[allowlist.regexes]]` array-of-tables form fails decode against gitleaks v8.28+ with "expected type 'string', got unconvertible type 'map[string]interface {}'"
- **Fix:** Migrated to `[[allowlists]] / regexes = [...]` schema; same allowlist semantics
- **Files modified:** `.gitleaks.toml`
- **Verification:** `docker run --rm zricethezav/gitleaks:latest detect ... --config /repo/.gitleaks.toml --no-git` → "no leaks found"
- **Committed in:** `66d45dd`

---

**Total deviations:** 2 (both schema-drift fixes between RESEARCH source-snippets and current upstream tool versions). No scope changes; both fixes were necessary for the verify gates to pass.

## Issues Encountered

- **Two consecutive 500 errors during gsd-executor spawn** (agent IDs `ad4f5e393e9914b1b` and `a891330622d9e67b9`). The 2nd agent had already produced the Gradle skeleton on disk (Tasks 1+2) before being terminated, but no commits had landed. Orchestrator continued inline from where the agent stopped: verified the skeleton matches RESEARCH §4.11, committed Task 2, then completed Task 3 and committed. No data was lost; the spawn failures only added orchestration overhead, not rework.
- **Acceptance-regex false-positive on RABBITMQ_DEFAULT_USER=n11:** the plan's `grep -E '=[^c]' .env.example | grep -v changeme | wc -l` check expects 0 lines but flags `n11` as suspicious. `n11` is a placeholder username, not a credential. Gitleaks (the canonical secret scanner) confirmed clean.

## User Setup Required

None for this plan. Java 21 is pre-installed at `$HOME/.jdks/jdk21.0.11_10` and Gradle auto-detects. Plan 01-03 will introduce docker-compose, which the user will need Docker Desktop running for (already verified during gitleaks check).

## Next Phase Readiness

- ✅ `./gradlew projects` works — all 8 subprojects enumerable from any later plan
- ✅ Version catalog (`libs.versions.toml`) is the single source of truth for dependency versions; later plans will add libraries (e.g. `libs.springdoc.webmvc`, `libs.flyway.pg`) here as they need them
- ✅ Secret-leak gate is live; later plans cannot accidentally check in real credentials without CI flagging
- ✅ Stub `build.gradle.kts` files are intentional placeholders — Plans 01-04 (common libs), 01-05 (eureka+config), 01-06 (gateway), 01-07 (service-template), 01-08 (infra-tests) replace them with real configs
- ✅ Wave 1 sequential mode is correct for the next plans: 01-02 (saga schemas — `.planning/saga-contracts/`) and 01-04 (common libs) are independent, but 01-03 (docker-compose) appends to `.env.example` so it must follow 01-01

---
*Phase: 01-foundations-day-1-contracts*
*Plan: 01-01*
*Completed: 2026-04-28*
