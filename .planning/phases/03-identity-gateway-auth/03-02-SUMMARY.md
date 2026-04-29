---
phase: 03-identity-gateway-auth
plan: 02
plan_id: 03-02
subsystem: identity-service
tags: [flyway, migrations, schema, saga-contracts, config-server]
dependency_graph:
  requires:
    - 01-03 (infra/postgres/init.sh created identity schema + identity_user)
    - 01-04 (common-events with AbstractEventSchemaTest drift gate)
  provides:
    - identity-service Flyway migration chain V1-V4 (tables: processed_events, users, roles, user_roles, addresses, outbox)
    - config-server identity-service.yml (port 8081, Flyway placeholders, JWT env-var slots)
    - user.registered saga schema locked at three loci (canonical, classpath mirror, saga-contracts.md)
  affects:
    - 03-04 (JPA entities + repositories build on these tables)
    - 03-05 (OutboxIntegrationTest extends AbstractEventSchemaTest against user-registered.schema.json)
    - 07 (notification-service adds consumer for notify.q.user-registered)
tech_stack:
  added: []
  patterns:
    - Flyway migration per-service schema (default-schema=identity, create-schemas=false)
    - Flyway placeholder substitution for admin seed (D-06 pattern)
    - Transactional outbox DDL per saga-contracts.md §5.1
    - Classpath mirror pattern for AbstractEventSchemaTest drift gate (§8)
key_files:
  created:
    - identity-service/src/main/resources/db/migration/V1__init_processed_events.sql
    - identity-service/src/main/resources/db/migration/V2__init_users_addresses.sql
    - identity-service/src/main/resources/db/migration/V3__seed_admin.sql
    - identity-service/src/main/resources/db/migration/V4__init_outbox.sql
    - config-server/src/main/resources/config/identity-service.yml
    - .planning/saga-contracts/user-registered.schema.json
    - common-events/src/main/resources/saga-schemas/user-registered.schema.json
  modified:
    - .planning/saga-contracts.md
decisions:
  - "D-22 numbering shift confirmed: V1=processed_events (skeleton-inherited), V2=users+addresses, V3=admin seed, V4=outbox"
  - "Flyway placeholders adminSeedEmail/adminSeedPasswordHash flow via config-server YAML from .env — no plaintext in committed files"
  - "JWT_PRIVATE_KEY env-var pass-through only in identity-service.yml — T-3-01 threat mitigated"
  - "user.registered saga schema locked at three loci: canonical .planning/saga-contracts/, classpath mirror in common-events, catalog+topology in saga-contracts.md"
metrics:
  duration: "~2 min"
  completed: "2026-04-29"
  tasks_completed: 3
  tasks_total: 3
  files_created: 7
  files_modified: 1
---

# Phase 3 Plan 2: Identity Schema Migrations + Saga Schema Lock Summary

**One-liner:** Flyway V1-V4 migrations for `identity` schema (processed_events + users/roles/addresses + admin seed + outbox) with Flyway-placeholder admin seed, per-service config-server YAML, and `user.registered` saga schema locked at three canonical loci.

## Tasks Completed

| Task | Name | Commit | Key Files |
|------|------|--------|-----------|
| 1 | V2/V3/V4 Flyway migrations for identity schema | e00f75b | V1-V4 migration SQL files |
| 2 | config-server/identity-service.yml | 4c61106 | config-server/src/main/resources/config/identity-service.yml |
| 3 | user.registered saga schema + saga-contracts.md | 26bc500 | saga-contracts/user-registered.schema.json, common-events mirror, saga-contracts.md |

## What Was Built

### Task 1: Flyway Migrations V1-V4

Four Flyway migrations in `identity-service/src/main/resources/db/migration/`:

- **V1** (`V1__init_processed_events.sql`): Processed events inbox, copied verbatim from service-template skeleton. Provides idempotency inbox for future Phase 5+ saga consumers.
- **V2** (`V2__init_users_addresses.sql`): Core identity DDL — `users`, `roles`, `user_roles`, `addresses` tables. Pre-seeds `roles` with `(1, ROLE_USER)` and `(2, ROLE_ADMIN)`. Enforces D-11 partial unique index `idx_addresses_user_default ON addresses(user_id) WHERE is_default` for at-most-one default address per user.
- **V3** (`V3__seed_admin.sql`): Inserts admin user via Flyway placeholders `${adminSeedEmail}` and `${adminSeedPasswordHash}` — both `ON CONFLICT DO NOTHING` for idempotency. No plaintext credentials in SQL.
- **V4** (`V4__init_outbox.sql`): Transactional outbox table verbatim from `saga-contracts.md §5.1`, with partial index `outbox_unsent_idx ON outbox(occurred_at) WHERE sent_at IS NULL`.

All tables land in the `identity` schema (Flyway `default-schema=identity`). No `CREATE SCHEMA` in any migration — schema is pre-created by `infra/postgres/init.sh`.

### Task 2: config-server/identity-service.yml

Per-service config for identity-service shipped to config-server:
- Port 8081 (D-21 808x convention)
- `username: identity_user` with `${IDENTITY_DB_PASSWORD}` (no hardcoded creds)
- Flyway configured for `identity` schema with `create-schemas: false`
- `placeholders.adminSeedEmail: ${ADMIN_SEED_EMAIL}` and `placeholders.adminSeedPasswordHash: ${ADMIN_SEED_PASSWORD_HASH}`
- `jwt.private-key: ${JWT_PRIVATE_KEY}` — env-var only, zero PEM content (T-3-01 mitigated)
- RabbitMQ host/port/credentials from env vars for outbox poller

### Task 3: user.registered Saga Schema Lock

Three-locus lock for the `user.registered` saga event (D-12):
1. **Canonical schema**: `.planning/saga-contracts/user-registered.schema.json` — JSON Schema 2020-12, required fields `[userId, email, fullName, registeredAt]`, `additionalProperties: false`
2. **Classpath mirror**: `common-events/src/main/resources/saga-schemas/user-registered.schema.json` — identical file, enables `AbstractEventSchemaTest` drift gate
3. **Topology catalog**: `saga-contracts.md` updated with:
   - `identity.tx` exchange added (Exchanges count: 4→5)
   - `notify.q.user-registered` queue bound to `identity.tx` with routing key `user.registered` (Queues count: 12→13)
   - §7 catalog row for `user.registered → user-registered.schema.json`

## Deviations from Plan

None — plan executed exactly as written.

The migration directory `identity-service/src/main/resources/db/migration/` did not pre-exist (plan 03-01 runs in the same wave and creates the skeleton). Creating it here is expected behavior for parallel wave execution — each plan owns its own files.

## Threat Surface Scan

| Threat ID | Mitigation Applied | Verified By |
|-----------|-------------------|-------------|
| T-3-01 (JWT private key exposure) | `jwt.private-key: ${JWT_PRIVATE_KEY}` only; `grep -c '\-----BEGIN' identity-service.yml` → 0 | Acceptance criteria check |
| T-3-06 (multiple default addresses) | `CREATE UNIQUE INDEX idx_addresses_user_default ON addresses(user_id) WHERE is_default` | V2 migration grep check |
| T-3-07 (admin password in git) | `${adminSeedPasswordHash}` Flyway placeholder; no literal in V3 SQL | Acceptance criteria check |
| T-3-08 (saga schema drift) | Classpath mirror in common-events enables AbstractEventSchemaTest gate | diff of two schema files → 0 |

No new threat surface introduced beyond what the plan's threat model covers.

## Known Stubs

None — this plan delivers schema + config artifacts only. No application code or UI is modified.

## Self-Check: PASSED

All 7 created files verified present on disk. All 3 task commits verified in git history (e00f75b, 4c61106, 26bc500).
