# Phase 01 — Deferred Items

Out-of-scope discoveries logged during execution. Each item is OUT-OF-SCOPE for the plan
that found it (per executor scope-boundary rule) but should be addressed in a follow-up
plan or as Phase 11 hardening.

---

## D-01: IDE-generated `bin/main/` directories pollute `git status`

**Found during:** Plan 01-03 (Task 2 verification, post-init.sh commit)
**Source plan:** Plan 01-01 (scaffolding)
**Files affected:**
- `api-gateway/bin/`
- `common-error/bin/`
- `common-events/bin/`
- `common-logging/bin/`
- `config-server/bin/`
- `eureka-server/bin/`
- `infra-tests/bin/`
- `service-template/bin/`

**Issue:** Eclipse/IntelliJ Gradle integration emits `bin/main/` per-module on import,
which `.gitignore` (currently covering `build/`, `.gradle/`, `out/`) does not exclude.

**Why deferred:** Plan 01-03's scope is Postgres + RabbitMQ infra, not Gradle hygiene.
This was caused by Plan 01-01's scaffolding running through an IDE and is unrelated to
init.sh / docker-compose.yml.

**Recommended fix:** Add `**/bin/` to `.gitignore` (or `bin/` to a future `.gitattributes`
section). Belongs to a Phase 1 hygiene cleanup plan or Phase 11 DevOps.

**Severity:** Low — purely cosmetic (`git status` noise). Doesn't affect runtime; does
NOT cause accidental commits because nothing in the wave-1 plans touches `bin/` paths.

---
