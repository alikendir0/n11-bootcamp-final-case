---
phase: 01-foundations-day-1-contracts
plan: 03
subsystem: infra
tags: [postgres, pgvector, rabbitmq, docker-compose, schema-per-service, role-level-deny, init.sh]

requires:
  - phase: 01-01
    provides: ".env.example slot list (10 *_DB_PASSWORD + POSTGRES_SUPERUSER_PASSWORD + RABBITMQ_DEFAULT_USER/PASS) referenced by both init.sh interpolation and docker-compose env_file"
provides:
  - infra/postgres/init.sh (139 lines, executable, env-var-aware bash) — pgvector extension + 10 schemas + 10 distinct users + per-user search_path defaults + 10×9 cross-schema REVOKE deny matrix; mounted at /docker-entrypoint-initdb.d/00-init.sh on first container boot
  - docker-compose.yml (96 lines) — Phase 1 infra-only profile with postgres (pgvector/pgvector:pg16) + rabbitmq (rabbitmq:4.3-management); pg_isready + rabbitmq-diagnostics healthchecks; init.sh mounted read-only; named volumes postgres-data + rabbitmq-data; default n11-net bridge network; default guest/guest creds disabled
  - .gitattributes (NEW — Rule 3 fix) — forces LF line endings for *.sh, *.bash, gradlew, *.yml, *.json, *.sql, Dockerfiles, docker-compose*.yml so the Windows host's core.autocrlf=true does not break the Postgres container's bash shebang
  - Verified .env.example slot list (no drift — init.sh ${VAR} refs match Plan 01-01's slots exactly)
  - Functional smoke: both services healthy in <30s; product_user/orders_user/ai_user resolve to the right schema via server-side search_path; pgvector installed; RabbitMQ rejects guest:guest (401), accepts the configured user; product_user has_schema_privilege('identity','USAGE') = false (cross-schema deny works)
affects: [01-04, 01-05, 01-06, 01-07, 01-08, all-Phase-3-onward]

tech-stack:
  added: [pgvector/pgvector:pg16, rabbitmq:4.3-management, .gitattributes line-ending policy]
  patterns:
    - "init.sh as the SINGLE source of truth for the schema-per-service boundary: schemas, users, search_path defaults, and cross-schema REVOKE matrix all live in one file. Plan 01-08 (CrossSchemaDenyTest) and every Phase 5+ Testcontainers smoke test will mount this same file via withCopyFileToContainer."
    - "Env-var-aware bash bootstrap (.sh not .sql): pure .sql files in /docker-entrypoint-initdb.d/ cannot interpolate env vars. The bash heredoc pattern interpolates *_DB_PASSWORD env vars BEFORE psql sees the SQL stream, allowing D-03 (passwords from env vars, never hardcoded) to be enforced."
    - "Server-side ALTER USER ... SET search_path — every JDBC URL is uniform across all 10 services (jdbc:postgresql://postgres:5432/n11), and Flyway picks up the schema server-side without per-service URL flags. Plan 01-07 will lean on this with `flyway.schemas: \${flyway.schema}` placeholders."
    - "Explicit REVOKE matrix as documentation, not just enforcement: new Postgres users have no cross-schema privileges by default; the 10 REVOKE statements are belt-and-braces + auditable boundary docs."
    - "Compose D-13 infra-only profile: only the two shared infra services. eureka/config/gateway are added in Plans 05-06 (same compose, additional service blocks); business services arrive as Jib images in Phase 11 with a `full` profile flag."

key-files:
  created:
    - infra/postgres/init.sh
    - docker-compose.yml
    - .gitattributes
    - .planning/phases/01-foundations-day-1-contracts/deferred-items.md
  modified: []

key-decisions:
  - "Schema count is 10 (not 13). CONTEXT.md D-01 mentions \"13 schemas\" but eureka-server, config-server, and api-gateway are stateless compute and own no relational state. Phase 1 produces schemas for the 10 stateful services only: identity, product, inventory, cart, orders, payment, notification, search, ai, mcp."
  - "`orders` (plural) everywhere — schema name, user name (`orders_user`), env var (`ORDERS_DB_PASSWORD`). The singular `order` is a SQL reserved word (PITFALLS #7 / PATTERNS Cross-Cutting #7). Saga vocabulary stays singular (`order.created`, `orderId`); only the Postgres schema goes plural."
  - "Added .gitattributes (NOT in original plan) to force LF line endings. The greenfield repo is on Windows with `core.autocrlf=true` and no .gitattributes — without this, init.sh would have been committed with CRLF and broken the Postgres container's `#!/usr/bin/env bash\\r` shebang. Tracked as Rule 3 (auto-fix blocking issue)."
  - "Plan 01-01 already wrote .env.example correctly with all 13 expected slots — no drift detected, no patches needed. Task 2 was a verify-only no-op (no commit produced for Task 2)."
  - "Tear-down posture: ran `docker compose down` (without `-v`) at the end — containers stopped, named volumes (postgres-data, rabbitmq-data) preserved on disk so subsequent `docker compose up -d postgres rabbitmq` is faster and the init.sh DOES NOT re-run (Postgres entrypoint convention: init scripts run only when /var/lib/postgresql/data is empty). To force init.sh re-run, use `docker compose down -v`."

patterns-established:
  - "Boundary-as-code pattern: every relational-isolation concern (schemas, users, ownership, search_path, cross-schema deny) lives in infra/postgres/init.sh. Future phases that need a Postgres user MUST add to this file rather than running ad-hoc DDL."
  - "Healthcheck pattern: every infra service in docker-compose ships a healthcheck (test, interval, timeout, retries, start_period). Phase 5-6 services (eureka/config/gateway) and Phase 11 business services will follow the same shape (Spring Boot services use `/actuator/health` with curl)."
  - "Env-var pass-through pattern: docker-compose `environment:` block declares each env var explicitly (rather than relying solely on env_file: .env), so anyone reading docker-compose.yml sees exactly which env vars the container consumes. .env stays the single source of values; compose stays the single source of structure."

requirements-completed:
  - ARCH-09
  - ARCH-10
  - DEV-07

duration: ~5min
completed: 2026-04-28
---

# Phase 1 Plan 03: Postgres + RabbitMQ Infra Bootstrap Summary

**Schema-per-service Postgres boundary (10 schemas / 10 users / role-level cross-schema deny) + RabbitMQ 4.3 with guest creds disabled, both bootable via `docker compose up -d postgres rabbitmq` and healthy in <30s.**

## Performance

- **Duration:** ~5m 20s (320s wall-clock)
- **Started:** 2026-04-28T19:02:01Z
- **Completed:** 2026-04-28T19:07:21Z
- **Tasks:** 3 (1 commit per file-modifying task; Task 2 was a verify-only no-op)
- **Files created:** 4 (init.sh, docker-compose.yml, .gitattributes, deferred-items.md)
- **Files modified:** 0

## Accomplishments

- **infra/postgres/init.sh** (139 lines): pgvector extension + 10 schemas (identity, product, inventory, cart, orders, payment, notification, search, ai, mcp) + 10 distinct users with per-user `search_path` defaults + full 10×9 cross-schema REVOKE deny matrix. All passwords sourced from env vars with `${VAR:?}` guards (no hardcoded secrets — T-01-01).
- **docker-compose.yml** (96 lines): Phase 1 D-13 infra-only profile. `pgvector/pgvector:pg16` + `rabbitmq:4.3-management`, healthchecks, init.sh mount, named volumes, default `n11-net` bridge ready to host eureka/config/gateway in Plans 05-06.
- **`.gitattributes`** (Rule 3 fix): forces LF for `*.sh`, `*.bash`, `gradlew`, YAML/JSON/SQL/TOML, Dockerfiles, docker-compose. Without it, the Windows host's `core.autocrlf=true` would have broken init.sh's bash shebang in the Linux container.
- **Functional smoke** (executor verified, not just structural grep): both services healthy in <2s on a warm daemon (after image pull); 3 service users connect and `SELECT current_schema()` returns the expected schema (proves `ALTER USER ... SET search_path` ran); pgvector extension installed; RabbitMQ rejects `guest:guest` with HTTP 401; configured `n11` user accepted; `product_user.has_schema_privilege('identity','USAGE') = false` (cross-schema deny works).

## Task Commits

Each task was committed atomically (Task 2 was verification-only — no file changes, no commit):

1. **Task 1: Write infra/postgres/init.sh + .gitattributes** — `fbd18f5` (feat)
2. **Task 2: Verify .env.example slot list matches init.sh refs** — *no commit (verification-only no-op; no drift detected)*
3. **Task 3: Write docker-compose.yml + functional smoke** — `0be0f9d` (feat)

**Plan metadata commit:** *(produced after this SUMMARY is written; updates STATE.md + ROADMAP.md + adds this SUMMARY + deferred-items.md)*

## Files Created/Modified

- `infra/postgres/init.sh` — env-var-aware bash bootstrap; mounted by docker-compose at `/docker-entrypoint-initdb.d/00-init.sh` and run ONCE on first container boot. 139 lines. Executable (mode 100755 in git index).
- `docker-compose.yml` — Phase 1 infra-only profile (postgres + rabbitmq); 96 lines.
- `.gitattributes` — line-ending policy (LF for shell/YAML/SQL/Dockerfiles; CRLF for .bat/.cmd).
- `.planning/phases/01-foundations-day-1-contracts/deferred-items.md` — out-of-scope discoveries log (currently 1 item: IDE-generated `*/bin/main/` directories from Plan 01-01 scaffolding pollute `git status`).

## Decisions Made

- **Schema count = 10 (deviates from CONTEXT.md D-01 wording).** D-01 says "13 schemas" but counts the 3 stateless services (eureka-server, config-server, api-gateway) which own no relational state. Net: 10 schemas, one per stateful service.
- **`orders` plural everywhere** (schema, user `orders_user`, env var `ORDERS_DB_PASSWORD`). Singular `order` is a SQL reserved word (PITFALLS #7). Saga vocabulary stays singular (`order.created`, `orderId`); only Postgres schema goes plural. Verified: `grep` for `order_user` / `CREATE SCHEMA order` returns 0.
- **Tear-down: `docker compose down` (no `-v`)** at end of plan. Containers stopped, volumes preserved. Plan 01-04 onward can `docker compose up -d postgres rabbitmq` without re-running init.sh (Postgres skips `/docker-entrypoint-initdb.d/` if `/var/lib/postgresql/data` is non-empty). To force init.sh re-run, use `docker compose down -v`.
- **`.env` reused, not regenerated.** Host already had `.env` from Plan 01-01 / earlier work — the smoke test simply sourced it. (If a fresh checkout, `.env` would need to be `cp`-ed from `.env.example` first.)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 — Blocking] Added `.gitattributes` for LF line endings**
- **Found during:** Task 1 (post-write `git add` warning: "LF will be replaced by CRLF the next time Git touches it")
- **Issue:** The greenfield Windows repo had `core.autocrlf=true` and no `.gitattributes`. Without correction, `git commit` would have rewritten `infra/postgres/init.sh` with CRLF endings, breaking the Postgres container's bash shebang (`#!/usr/bin/env bash\r` is not a valid shebang on Linux). This would have caused the entire Plan 01-08 cross-schema-deny smoke test (and every Phase 5+ Testcontainers test that mounts init.sh) to fail with `exec format error` or shebang-not-found.
- **Fix:** Added `.gitattributes` declaring `eol=lf` for `*.sh`, `*.bash`, `gradlew`, `*.yml`, `*.yaml`, `*.json`, `*.sql`, `*.toml`, Dockerfiles, and `docker-compose*.yml`; CRLF preserved for `*.bat`/`*.cmd`. Verified via `git check-attr eol infra/postgres/init.sh` → `eol: lf`.
- **Files modified:** `.gitattributes` (new)
- **Verification:** `git check-attr eol infra/postgres/init.sh` returns `eol: lf`; the LF-→-CRLF git warning no longer appears for shell/YAML files.
- **Committed in:** `fbd18f5` (Task 1 commit — bundled with init.sh)

---

**Total deviations:** 1 auto-fixed (1 Rule 3 blocking)
**Impact on plan:** Required for correctness — without `.gitattributes`, init.sh would have failed to execute in the Linux container. No scope creep.

### Out-of-Scope Items Logged (NOT fixed)

**1. IDE-generated `*/bin/main/` directories pollute `git status`** — pre-existing artifact from Plan 01-01's scaffolding through an IDE. Logged in `deferred-items.md`. Recommended fix (Phase 1 hygiene cleanup or Phase 11 DevOps): add `**/bin/` to `.gitignore`. Not addressed here per scope-boundary rule (out-of-scope for the postgres+rabbitmq plan).

## Issues Encountered

- Initial `Write` tool call for init.sh leaked literal `\` characters into a SQL comment line ("orders (plural — \\`order\\` is SQL reserved)") because `\`` was treated as an escape sequence in the heredoc-like string. Fixed via `Edit` to replace the backticks with single quotes inside the comment. The bash heredoc body itself is fine (no other backticks in the script). No functional impact — comments are stripped by `psql` regardless.

## Notes for Downstream Plans

- **Plan 01-04 (common-error / common-logging / common-events):** doesn't depend on this plan's infra. Can run in parallel; STATE.md already flagged Wave-1 parallel-safe.
- **Plan 01-07 (service-template Flyway wiring):** use the `flyway.schema` placeholder. For the orders service, set `flyway.schema: orders` (plural — matches init.sh). Flyway needs `default-schema` AND `schemas` set to the same value, with `create-schemas: false` because init.sh already created them. Sketch: `spring.flyway.{enabled: true, schemas: \${flyway.schema}, default-schema: \${flyway.schema}, create-schemas: false}`.
- **Plan 01-08 (CrossSchemaDenyTest):** copy `infra/postgres/init.sh` to `infra-tests/src/test/resources/init.sh` and mount via `withCopyFileToContainer(MountableFile.forClasspathResource("init.sh"), "/docker-entrypoint-initdb.d/00-init.sh")` (RESEARCH §4.4 lines 466–500 has the exact test class skeleton). Pass each `*_DB_PASSWORD` env var via `withEnv(...)`. The 10×9 = 90 cross-schema deny pairs can be enumerated via `@ParameterizedTest`.
- **Phase 5+ saga services:** can rely on `processed_events` inbox tables existing in their own schema (Plan 01-07's service-template ships `V1__init.sql` for the inbox). Cross-schema joins are structurally impossible — the REVOKE matrix verified at runtime here means a Phase-5 dev mistakenly writing `SELECT * FROM cart.cart_items` from `product_user` will get `permission denied for schema cart` immediately.
- **Phase 11 (full compose profile):** extend this same `docker-compose.yml`. Add the 13 service blocks (Jib images), keep `n11-net`, keep postgres + rabbitmq, add a `cloudflared` or `ngrok` sidecar for the demo URL.

## User Setup Required

None — `.env` already exists locally from Plan 01-01's bootstrap, with valid `changeme-*` placeholder values that init.sh accepts (the `${VAR:?}` guards only fail when the variable is unset/empty, not when it's a placeholder string). For a fresh checkout, the user runs `cp .env.example .env` once before `docker compose up`. Real secrets are NOT required at this stage — placeholders work because the Postgres users they create are scoped to the local docker-compose stack only.

## Threat Flags

None — no new security-relevant surface introduced beyond what the plan's `<threat_model>` already covered (T-01-01 hardcoded creds, T-01-02 cross-schema leak, T-01-03 pgvector privilege escalation, T-01-05 RabbitMQ guest creds). All four mitigated as planned.

## Next Phase Readiness

- **Wave 1 status:** Plans 01-01 ✓, 01-02 ✓, 01-03 ✓ done. Plan 01-04 (common libs) is Wave 1's last remaining sibling and is parallel-safe with this plan's outputs (no file overlap; common libs don't touch infra/ or docker-compose.yml).
- **Wave 2 unblocked:** Plans 01-05 (eureka-server runtime config) and 01-06 (config-server + api-gateway shell) can begin once 01-04 completes; both consume this plan's `n11-net` network and `postgres`/`rabbitmq` services.
- **Plan 01-08 (CrossSchemaDenyTest):** unblocked the moment `infra-tests/build.gradle.kts` is wired (in Plan 01-04's scope). The init.sh this plan produced is the file that test will mount.

## Self-Check: PASSED

- File `infra/postgres/init.sh`: FOUND (139 lines, executable, mode 100755 in git index)
- File `docker-compose.yml`: FOUND (96 lines, parses via `docker compose config --quiet`)
- File `.gitattributes`: FOUND
- File `.planning/phases/01-foundations-day-1-contracts/deferred-items.md`: FOUND
- Commit `fbd18f5`: FOUND (`feat(01-03): postgres init.sh with 10-schema boundary + LF .gitattributes`)
- Commit `0be0f9d`: FOUND (`feat(01-03): docker-compose.yml infra-only profile (postgres + rabbitmq)`)
- All 3 task <verify> automated checks passed
- All Task 1 + Task 3 acceptance criteria passed (Task 2 was no-op, all checks passed)
- Functional smoke: 3/3 service users return correct `current_schema()`; pgvector installed; RabbitMQ rejects guest:guest 401, accepts configured user; cross-schema deny verified

---
*Phase: 01-foundations-day-1-contracts*
*Completed: 2026-04-28*
