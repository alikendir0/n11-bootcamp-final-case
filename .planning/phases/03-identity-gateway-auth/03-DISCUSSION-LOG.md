# Phase 3: Identity + Gateway Auth - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in `03-CONTEXT.md` — this log preserves the alternatives considered.

**Date:** 2026-04-29
**Phase:** 3-identity-gateway-auth
**Areas discussed:** JWT lifetime + 401 refresh strategy, RS256 keypair source + rotation, Roles model + admin user seeding, Address book schema (AUTH-08), `user.registered` saga event timing, Login response shape

---

## JWT Lifetime + 401 Refresh Strategy

| Option | Description | Selected |
|--------|-------------|----------|
| Long-lived access only (24h, no refresh) | Single 24h access JWT, no refresh-token endpoint. AUTH-03's "refreshed on 401" reinterpreted as "client clears + re-logs-in." Pitfall #18 confirms refresh flow is not needed for grading. | ✓ |
| Short access (15min) + refresh-token rotation | Standard production pattern: 15min access JWT + 30d opaque refresh token, rotated on use. Adds `/auth/refresh` + `refresh_tokens` table + revocation logic. SOLID-credible but more surface to ship. | |
| 1h access + silent re-login (saved credentials) | 1h token; on 401, frontend silently re-POSTs cached plaintext credentials. Storing plaintext password client-side is a grading red flag. | |
| 1h access only, no refresh, demo-friendly | 1h token. Grader hits a 401 mid-walkthrough during a 30-min review. | |

**User's choice:** Long-lived 24h access only, no refresh.
**Notes:** Captured as D-01 in CONTEXT.md. `expiresIn: 86400` in login response. Frontend (Phase 10) will store in localStorage and route to `/giris-yap` on 401.

---

## RS256 Keypair Source + Rotation

| Option | Description | Selected |
|--------|-------------|----------|
| PEM env vars (dev) + mounted secret file (compose) | `JWT_PRIVATE_KEY` env var as PEM-encoded multi-line; public key derived from private at boot. Single env var to manage; gateway learns public key only via JWKS. Rotation: change env var + bump `JWT_KEY_ID` + restart identity-service. | ✓ |
| Auto-generate keypair at identity-service boot | Fresh RSA-2048 keypair per `:bootRun`. Convenient for dev but every restart invalidates outstanding tokens — fragile for grading walkthrough. | |
| PKCS#12 keystore file (committed public, env-var private) | Pre-generated `.p12` keystore committed; password from env var. Heavier scaffold for marginal benefit over PEM-in-env. | |
| Config-server-served keypair | Crosses the secret boundary at config-server; muddies Pitfall #6's "secret only in config-server / env" guidance. | |

**User's choice:** PEM env var with public key derived at boot.
**Notes:** Captured as D-02, D-03, D-04 in CONTEXT.md. Spring Security's `NimbusReactiveJwtDecoder` defaults (`cacheTtl=5m`, `refreshTtl=1h`) match success criterion #4 ("refreshes hourly"). Clock skew = 30s (Pitfall #18 mitigation).

---

## Roles Model + Admin User Seeding

| Option | Description | Selected |
|--------|-------------|----------|
| Flyway-seeded admin from env vars + `roles: ["ROLE_USER"]` claim | V2 Flyway migration inserts admin with email + bcrypt hash from env vars. Two roles only: ROLE_USER (every signup), ROLE_ADMIN (seeded). Claim shape is a JSON array. | ✓ |
| CommandLineRunner bootstrap from env vars (no Flyway insert) | On startup, CommandLineRunner creates admin if not exists. Idempotent on restart; cleaner separation between schema migrations and seed data. | |
| Self-promotion endpoint gated by `BOOTSTRAP_TOKEN` | `POST /auth/bootstrap-admin` accepts a one-shot token. Adds a feature whose only purpose is bootstrapping. | |
| No admin in v1 — ROLE_USER only | Skip RBAC entirely. Compromises the security/SOLID signal. | |

**User's choice:** Flyway-seeded admin + array claim shape.
**Notes:** Captured as D-05, D-06, D-07 in CONTEXT.md. `V2__seed_admin.sql` uses Flyway placeholders for `${adminSeedEmail}` and `${adminSeedPasswordHash}`. README documents the env-var pair so the grader can sign in as admin.

---

## Address Book Schema (AUTH-08)

| Option | Description | Selected |
|--------|-------------|----------|
| Pragmatic n11-shape, identity schema | Single `addresses` table in identity schema. Fields: id, user_id, title, recipient_name, phone, il, ilce, mahalle, street_line, postal_code (5-digit), is_default, created_at. | ✓ |
| Minimal v1 (only fields AUTH-08 names) | Just il, ilce, mahalle + free-text street_line. Iyzico (Phase 6) will need recipient_name + phone, forcing a re-touch. | |
| Pragmatic shape but in a separate `address-service` | Same fields in a 14th microservice. Dilutes bounded-context (addresses ARE part of identity), adds REST hop. | |
| Fully normalized Türkiye address (referenced il/ilçe tables) | FK-constrained dropdown from official 81 il + ~973 ilçe seed data. Adds a seed-data import job. | |

**User's choice:** Pragmatic n11-shape in identity schema.
**Notes:** Captured as D-08, D-09, D-10, D-11 in CONTEXT.md. Field set tuned to match (a) what Iyzico checkout needs in Phase 6 and (b) what n11.com itself shows in adres-defterim (Phase 2 recon screenshot evidence). Address-snapshot pattern: order-service copies fields at order-creation time (D-10) so address edits don't retro-mutate orders. `is_default` enforced via partial unique index (D-11).

---

## `user.registered` Saga Event Timing

| Option | Description | Selected |
|--------|-------------|----------|
| Lock contract + publish from Phase 3 via outbox | Add `user-registered.schema.json`, new `identity.tx` exchange, identity-service writes user + outbox row in same transaction. Phase 7 just adds the consumer. Demonstrates outbox pattern in the FIRST business service. | ✓ |
| Defer entirely — Phase 7 owns it | Phase 3 doesn't touch the saga bus. Phase 7 reaches back to add the publisher. | |
| Skip the welcome notification feature entirely | Drop `user.registered` from scope. Marginally cuts ARCHITECTURE.md coverage. | |

**User's choice:** Lock contract + publish from Phase 3.
**Notes:** Captured as D-12, D-13 in CONTEXT.md. Strengthens SOLID story two phases earlier than the order saga (Phase 5). New saga-contracts.md row + new `.schema.json`. Outbox poller code lives in identity-service first; potential extraction into `common-outbox` shared module deferred until Phase 5 reveals enough overlap to justify.

---

## Login Response Shape

| Option | Description | Selected |
|--------|-------------|----------|
| Token + user profile inline | `{ accessToken, tokenType: "Bearer", expiresIn: 86400, user: { id, email, fullName, roles: [...] } }`. Avoids extra `/auth/me` round-trip on every page load. | ✓ |
| Token-only, frontend calls /auth/me right after | One extra round-trip per login. Cleaner separation of concerns; standard JWT pattern. | |
| Token + minimal user (id+email only) | Compromise. No clear winner. | |

**User's choice:** Token + user profile inline.
**Notes:** Captured as D-14 in CONTEXT.md. `POST /auth/register` returns the same shape so the frontend can auto-login post-registration without a second round-trip. `/auth/me` exists for re-fetch when cached profile is stale.

---

## Claude's Discretion

(Captured as CD-01..CD-07 in CONTEXT.md.)

- **CD-01** Password complexity rule (recommend: ≥8 chars + ≥1 letter + ≥1 digit; Turkish validation messages).
- **CD-02** Email validation: Jakarta `@Email` annotation; uniqueness via DB unique constraint mapped to 409 Conflict.
- **CD-03** SQL DDL specifics: `users(id UUID PK, email TEXT UNIQUE, password_hash TEXT, full_name TEXT, created_at TIMESTAMPTZ)` + `roles` + `user_roles` + `addresses` tables; pre-seed roles `(1, ROLE_USER)`, `(2, ROLE_ADMIN)`.
- **CD-04** Outbox poller cadence: `@Scheduled(fixedDelay=5000)` + batch size 100; failure = leave `sent_at=null` and retry next tick.
- **CD-05** Add `ApiErrorCode` entries for `auth/invalid-credentials`, `auth/email-taken`, `auth/missing-token`, `auth/invalid-token`.
- **CD-06** Skip rate-limiting on `/auth/login` for v1 (out of bootcamp brief).
- **CD-07** Plan/wave breakdown: ~3 waves (W0 module scaffold + Flyway, W1 business code + JWT issuer + JWKS + outbox poller, W2 gateway replacement + smoke test).

## Deferred Ideas

(Captured in CONTEXT.md `<deferred>` section.)

- Refresh-token endpoint + rotation — revisit only if storefront UX demands it (Phase 10/11).
- Password reset, email verification — out of scope (no SMTP).
- OAuth login — out of scope per PROJECT.md.
- TC kimlik validation — out of scope; complexity unjustified.
- `POST /agents/exchange` MCP API-key bridge — Phase 9 territory; Phase 3 may stub the gateway route only.
- Rate-limiting on `/auth/login` — out of bootcamp brief.
- Extracting outbox poller into `common-outbox` module — wait until Phase 5's order-service for the second use-case before refactoring.
- `il`/`ilçe` reference dataset for cascading dropdowns — out of scope; free-form text inputs in the form.
- Phone E.164 / Türkiye-format validation — free-form text is enough for v1 (no SMS sending exists).
- JWKS rotation automation (zero-downtime overlap window) — manual procedure documented (D-03); automation out of scope.
- Full OIDC `issuer-uri` + `.well-known/openid-configuration` discovery — over-engineered for the n11 demo; `jwk-set-uri` is enough.
