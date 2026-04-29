# Phase 3: Identity + Gateway Auth - Context

**Gathered:** 2026-04-29
**Status:** Ready for planning

<domain>
## Phase Boundary

Stand up `identity-service` (signup, login, RS256 JWT issuance, JWKS endpoint, `/auth/me`, address book) and flip the api-gateway from its Phase-1 `permitAll()` posture to a JWT-validating chain that fetches JWKS from identity-service and injects trusted `X-User-Id` / `X-User-Roles` headers into downstream requests (stripping the inbound `Authorization` header on the way through).

In scope:
- `identity-service` Spring Boot module cloned from `service-template/`, registered with Eureka, served behind the gateway at `/api/v1/identity/**`.
- REST surface locked in `.planning/api-contracts.md` §1: `POST /auth/register`, `POST /auth/login`, `GET /auth/me`, `GET /addresses`, `POST /addresses`, `GET /.well-known/jwks.json`. (`POST /agents/exchange` is reserved for Phase 9 — gateway route only, no implementation now.)
- RS256 keypair sourced from env vars (PEM-encoded) with public key derived from private at boot; JWKS endpoint serves it.
- Gateway flips from `permitAll()` (Phase 1 D-14) to `oauth2ResourceServer().jwt()` against identity-service JWKS; replaces both `SecurityConfig.java` and `GatewayHeaderInjectionFilter.java` (per the in-file Phase-3 TODOs).
- Gateway public allowlist enforced per `api-contracts.md` §3 (login, register, JWKS, `GET /products/**`, `GET /search/**`, `POST /chat/**`, Iyzico webhook).
- Authorization-strip + `X-User-Id` / `X-User-Roles` injection (defense at the edge + trust the mesh — `api-contracts.md` §4).
- Address book table in `identity` schema; addresses owned by user-id; supports listing + create.
- Welcome-notification saga seam: new `user.registered` event published from identity-service via outbox (consumer added in Phase 7).
- One smoke unit test (BCrypt password-hashing round-trip) — establishes the per-service test pattern QUAL-02 mandates everywhere.

Out of scope (later phases own these):
- Welcome-notification consumer (`notify.q.user-registered` consumer + Turkish welcome body) — Phase 7.
- Refresh tokens, password reset, email verification, OAuth login, TC kimlik validation — out of scope per PROJECT.md.
- `POST /agents/exchange` (MCP API-key → internal JWT bridge) — Phase 9.
- Frontend auth pages (login/register UI) — Phase 10.
- Address validation against an `il`/`ilçe` reference dataset — out of scope (free-form text + 5-digit postal code is enough for v1).

</domain>

<decisions>
## Implementation Decisions

### JWT Lifetime + 401 Refresh Strategy
- **D-01:** **Long-lived 24-hour access token, no refresh-token endpoint.** AUTH-03's "refreshed on 401" is reinterpreted as "client clears token + re-logs-in on 401" — Pitfall #18 explicitly says refresh-token flow is not needed for grading. `expiresIn: 86400` returned in the login body. Frontend (Phase 10) stores the access token in `localStorage` (decision deferred to that phase, but the long-lived shape is what justifies localStorage-friendly storage). On 401 the client clears state and routes to `/giris-yap`. Cuts ~half the auth code; demo survives a 30-min grading walkthrough on a single login.

### RS256 Keypair Source + Rotation
- **D-02:** **Private key from `JWT_PRIVATE_KEY` env var (PEM-encoded, multi-line) loaded from `.env` (gitignored).** Public key is *derived from the private key at identity-service boot* — no separate `JWT_PUBLIC_KEY` env var; this prevents key-mismatch drift. Spring Security `RsaKeyConverters.pkcs8` parses the PEM into a `RSAPrivateKey`; identity-service constructs a `JWK` with a stable `kid` (e.g., `n11-jwt-2026-04` derived from a `JWT_KEY_ID` env var, or fingerprint of public key). The gateway never sees the private key.
- **D-03:** **JWKS is the only path the gateway uses to learn the public key.** Gateway boot config: `spring.security.oauth2.resourceserver.jwt.jwk-set-uri: http://identity-service:8081/.well-known/jwks.json`. Spring Security's `NimbusReactiveJwtDecoder` caches the JWKS with a default `cacheTtl=5m` and `refreshTtl=1h` — those defaults match success criterion #4 ("refreshes hourly"). Rotation procedure: change `JWT_PRIVATE_KEY` + bump `JWT_KEY_ID`, restart identity-service; gateway picks up the new `kid` within ~1h via JWKS refresh; old tokens still validate until 24h expiry against the still-served previous `kid` (JWKS publishes BOTH keys during a rotation window — owned in Phase 3 docs but no automation).
- **D-04:** **Clock skew** = `Duration.ofSeconds(30)` on the gateway's `JwtTimestampValidator` (Pitfall #18 mitigation). Token `iat`/`exp` logged at validation in DEBUG.

### Roles Model + Admin Seeding
- **D-05:** **Two roles only in v1: `ROLE_USER` (every signup) and `ROLE_ADMIN` (single seeded user).** Spring Security idiom — claim shape is `roles: ["ROLE_USER"]` (JSON array of strings, prefix-`ROLE_`). The gateway maps the array into a comma-separated `X-User-Roles` header for downstream services (e.g., `X-User-Roles: ROLE_USER,ROLE_ADMIN`).
- **D-06:** **Admin seeded via Flyway migration in `identity` schema.** Migration file `V2__seed_admin.sql` inserts one row with email = `${ADMIN_SEED_EMAIL}` and bcrypt hash = `${ADMIN_SEED_PASSWORD_HASH}` (env-var-substituted via Flyway placeholders — `flyway.placeholders.adminSeedEmail` etc.). README documents the env-var pair + how to generate the bcrypt hash with `htpasswd` or a one-line Spring Boot CLI command. The Flyway approach keeps schema and seed data in version-controlled migrations (auditable; reproducible across environments) instead of putting DML in a CommandLineRunner.
- **D-07:** **`POST /auth/register` always assigns `ROLE_USER` only.** No self-promotion endpoint, no signup-time role parameter.

### Address Book Schema (AUTH-08)
- **D-08:** **Single `addresses` table in `identity` schema** — no future `address-service` split. Addresses are part of the identity bounded context.
- **D-09:** **Field set:** `id` UUID PK, `user_id` UUID FK → `users(id)`, `title` VARCHAR(50) (`Ev` / `İş` — user-visible label), `recipient_name` VARCHAR(120) (`Ad Soyad`), `phone` VARCHAR(20) (free-form for v1; no E.164 enforcement), `il` VARCHAR(50), `ilce` VARCHAR(80), `mahalle` VARCHAR(120), `street_line` VARCHAR(255) (`Sokak / Cadde / No / Daire` — single free-form column), `postal_code` CHAR(5) (5-digit Türkiye, server-validated `^\d{5}$`), `is_default` BOOLEAN DEFAULT false, `created_at` TIMESTAMPTZ. No `country` column — Türkiye-only is implicit (LOC requirement).
- **D-10:** **Address-snapshot pattern (forward-compat for Phase 5):** `POST /orders` will reference an `addressId`, and order-service will COPY the address fields into its own `order_shipping_addresses` row at order-creation time so subsequent `PATCH /addresses/{id}` or deletes don't retro-mutate orders. This decision is announced here so Phase 5's planner picks it up; Phase 3's `addresses` table itself is mutable (no soft-delete).
- **D-11:** **`is_default` semantics:** at most one `is_default = true` per user (enforced by partial unique index `CREATE UNIQUE INDEX ... WHERE is_default`); `POST /addresses` with `is_default: true` flips any existing default to false in a single transaction. No special handling for the first address (the API caller decides whether to mark it default).

### `user.registered` Saga Event (early outbox demo)
- **D-12:** **Lock `user.registered` in saga-contracts now and publish from Phase 3 via the outbox pattern.** Adds `.planning/saga-contracts/user-registered.schema.json` with payload `{userId: uuid, email: string, fullName: string, registeredAt: rfc3339}`. New exchange `identity.tx` (topic, durable) added to `saga-contracts.md` §2; queue `notify.q.user-registered` bound to `identity.tx` with routing key `user.registered`. Identity-service writes the user row + an `outbox` row in the same transaction; an outbox poller (`@Scheduled` every 5s, batch publish) drains rows to RabbitMQ. Phase 7 just adds the `notification-service` consumer.
- **D-13:** **Why now (not Phase 5):** demonstrates the outbox + at-least-once + neutral-DTO pattern in the FIRST business service, not the FIFTH. Strengthens the SOLID story two phases earlier and gives Phase 5's saga skeleton a working reference. Cost: one extra plan-task (+ ~30 LOC outbox poller + 1 schema file). The poller code lives in identity-service in Phase 3 and gets re-used (or copy-paste-and-edit'd) in Phase 5's order-service.

### Login Response Shape
- **D-14:** **`POST /auth/login` returns `{ accessToken, tokenType: "Bearer", expiresIn: 86400, user: { id, email, fullName, roles: [...] } }`.** Frontend stores both; avoids a synchronous `/auth/me` round-trip on every page load. `GET /auth/me` exists for re-fetching profile state (e.g., if cached profile is stale or after registration). `POST /auth/register` returns the same shape so the frontend can auto-login the user post-registration without a second round-trip.

### Identity-Service `/auth/me` Posture
- **D-15:** **`GET /auth/me` reads `X-User-Id` from the gateway-injected header — does NOT decode JWT itself.** Identity-service ships zero JWT-decoding code; only JWT *signing* (issuance) lives there. Aligns with the locked architecture: gateway is the single JWT verification chokepoint. Lookup is `users.findById(UUID.fromString(request.getHeader("X-User-Id")))`; 401 if header missing (defense-in-depth — should never happen because the gateway gates `/auth/me`).

### Test Pattern (QUAL-02 starter)
- **D-16:** **Smoke unit test** = `PasswordEncoderTest` — verifies BCrypt cost = 10, encode + matches round-trip works, two encodes of the same password produce different hashes (salt). Pure JUnit + `BCryptPasswordEncoder` instantiation, no Spring context, no Testcontainers. Sets the per-service-smoke-test-pattern QUAL-02 mandates everywhere.
- **D-17:** **Optional integration test** (planner discretion): `WebTestClient` POST `/auth/register` → POST `/auth/login` → assert JWT decodes with the published JWKS. Not required by Phase 3 success criteria but a strong signal — recommend the planner include it if scope allows.

### Gateway Surgery — Replace, Don't Patch
- **D-18:** **Replace `api-gateway/src/main/java/com/n11/gateway/SecurityConfig.java` and `GatewayHeaderInjectionFilter.java` wholesale** (per the in-file Phase-3 TODO notes). New `SecurityConfig`:
  - `oauth2ResourceServer().jwt(jwt -> jwt.jwkSetUri(...))` against identity-service JWKS.
  - Public allowlist (`pathMatchers().permitAll()`) for: `POST /api/v1/identity/auth/login`, `POST /api/v1/identity/auth/register`, `GET /api/v1/identity/.well-known/jwks.json`, `GET /api/v1/products/**`, `GET /api/v1/search/**`, `POST /api/v1/chat/**`, `POST /api/v1/payments/iyzico/callback`. (Some of those route-bases don't exist yet but reserving them in the allowlist now means later phases just add the service — no security-config edit per phase.)
  - Everything else: `.anyExchange().authenticated()`.
  - CORS preserved verbatim from Phase 1.
- **D-19:** **New `GatewayHeaderInjectionFilter`:** keeps the inbound `X-User-Id` / `X-User-Roles` strip, adds: read validated `JwtAuthenticationToken` from reactive `SecurityContext`, extract `sub` claim → `X-User-Id`, extract `roles` claim → `X-User-Roles` (comma-joined), strip `Authorization` header before forwarding. Order: AFTER `GatewayCorrelationIdFilter`, BEFORE Spring Cloud Gateway's forwarding filter (`Ordered.HIGHEST_PRECEDENCE + 10` already set; verify this still works post-replacement).
- **D-20:** **Add `oauth2.resource-server.jwt.jwk-set-uri` to `config-server/src/main/resources/config/api-gateway.yml`** (the file's footer already says "Phase 3 will add an `oauth2.resource-server.jwt.issuer-uri` block here" — replace the comment with the actual block, JWK-set-uri form not issuer-uri form because identity-service is not a full OIDC issuer for v1).

### Identity-Service Bootstrap Posture
- **D-21:** **identity-service is the FIRST business service** — it clones `service-template/skeleton/` (per Plan 01-07 scaffolding decision in Phase 1). New entries in: `settings.gradle.kts` (include `identity-service`), `docker-compose.yml` (additive merge — wget-based healthcheck on port 8081, depends on postgres + config-server + eureka-server). Port = 8081 (gateway is 8080; per ARCHITECTURE.md the convention is 808x for business services).
- **D-22:** **DB user** = `identity_user` already created by Plan 01-03 `infra/postgres/init.sh`; per-service yaml `config/identity-service.yml` injects `db.user=identity_user` + `db.password=${IDENTITY_DB_PASSWORD}` + `flyway.schema=identity`. Flyway migrations live in `identity-service/src/main/resources/db/migration/` — `V1__init_users_addresses.sql` (users + roles + user_roles + addresses tables; **also** the `processed_events` inbox table inherited from service-template's `V1__init_processed_events.sql` which gets renamed/merged), `V2__seed_admin.sql` (admin seed), `V3__init_outbox.sql` (outbox table for D-12 — alternatively merged into V1).

### Claude's Discretion
- **CD-01:** Concrete password complexity rules — minimum 8 chars + at least one letter + one digit is the recommended floor; planner may pick a stricter or laxer regex. Validation messages in Turkish per LOC; error response uses RFC-7807 shape with `errors[]` field (`api-contracts.md` §7 example).
- **CD-02:** Email validation rule — Spring's `@Email` annotation (Jakarta validation) is the default; pick whether to also enforce uniqueness via a pre-insert `findByEmail` check or rely solely on the DB unique constraint + 409 mapping.
- **CD-03:** Concrete SQL DDL for `users(id UUID PK, email TEXT UNIQUE, password_hash TEXT, full_name TEXT, created_at TIMESTAMPTZ)` and `roles(id INT PK, name TEXT UNIQUE)` + `user_roles(user_id, role_id PK)`. Pre-seed `roles` table with rows `(1, 'ROLE_USER')`, `(2, 'ROLE_ADMIN')` in V1 migration. Indexes: `idx_users_email_unique`, `idx_addresses_user_id`, partial unique on `addresses(user_id) WHERE is_default`.
- **CD-04:** Outbox poller cadence (5s recommended) + batch size (100 rows recommended) + failure handling (mark `sent_at = null` and let the next run retry; no explicit DLQ at the outbox level — RabbitMQ's DLX handles consumer-side failures). Spring `@Scheduled(fixedDelay = 5000)`.
- **CD-05:** Whether `ApiErrorCode` enum (from Plan 01-04 `common-error`) gains new entries for `auth/invalid-credentials`, `auth/email-taken`, `auth/missing-token`, `auth/invalid-token`, etc. Recommendation: yes, add 4-5 entries; map RFC-7807 `type` URIs accordingly.
- **CD-06:** Whether `/auth/login` rate-limits failed attempts (Spring's `RequestRateLimiter` filter or in-app counter). Recommend: skip for v1 — out of bootcamp brief, low grading payoff.
- **CD-07:** Plan/wave breakdown — planner territory. Likely 3 waves: (W0) module scaffold + Flyway migrations + DB seed; (W1) identity-service business code (controllers, services, JWT issuer, JWKS endpoint, outbox poller); (W2) gateway replacement (SecurityConfig + HeaderInjectionFilter + config-server YAML) + smoke test of end-to-end JWT flow.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents (researcher, planner, executor) MUST read these before any code is written.**

### Project Context (always)
- `.planning/PROJECT.md` — Key Decisions table (locked architectural choices, JWT-at-gateway, BCrypt cost, no AWS), Open Questions (Phase 6/9 tunnel — irrelevant here), Out of Scope (no email verification, no password reset, no OAuth)
- `.planning/REQUIREMENTS.md` — Phase 3 reqs: AUTH-01..AUTH-08, QUAL-02 (smoke unit test starter pattern); ARCH-09/-10 (schema isolation already in place from Phase 1)
- `.planning/ROADMAP.md` §"Phase 3: Identity + Gateway Auth" — goal, success criteria (5), risks (Pitfall #6, #14, #18), research need = LOW
- `.planning/STATE.md` — current position; Phase 2 closed; Phase 3 next
- `CLAUDE.md` — non-negotiable rules; rule 5 (no secrets in source) directly governs JWT keypair handling; UI Turkish / code English (validation messages in Turkish)

### Architecture (the spine — research output)
- `.planning/research/ARCHITECTURE.md` §2.3 — Gateway responsibilities (JWT validation, header injection, Authorization-strip, public allowlist, CORS) — direct input to D-18, D-19
- `.planning/research/ARCHITECTURE.md` §2.4 — identity-service per-service contract (REST surface, owned data, `user.registered` event marked optional → D-12 elects to lock)
- `.planning/research/ARCHITECTURE.md` §5 — Gateway responsibilities (CORS, JWT, header injection, SSE, rate limit) — supports D-18
- `.planning/research/ARCHITECTURE.md` §10 — Anti-patterns (don't forward raw JWT downstream — strip at gateway) → D-19
- `.planning/research/STACK.md` — Java 21, Spring Boot 3.5.14, Spring Security 6.x for resource server config (gateway side), Spring Security crypto (BCrypt) on identity side
- `.planning/research/PITFALLS.md` #6 (JWT secret committed / duplicated — drives D-02 PEM env var posture; Pitfall recommends RS256 over HS256), #14 (gateway path validation — services bind 127.0.0.1 in production profile per Phase 11; not Phase 3's concern but cross-ref), #18 (clock skew — D-04 30s leeway; refresh-token flow not needed for grading → D-01)
- `.planning/research/PITFALLS.md` #7 — leaky `ChatProvider` — irrelevant to Phase 3 but cited because the same SOLID/abstraction discipline applies to identity-service controllers (no Spring Security types in repository signatures)

### Day-1 Locked Contracts (authoritative — never re-decide)
- `.planning/api-contracts.md` §1 (identity-service Day-1 endpoint table — locks 7 endpoints), §2 (gateway routing table: `/api/v1/identity/**` → identity-service via Eureka discovery-locator), §3 (public allowlist: login, register, JWKS, products, search, chat, payment-callback), §4 (Authorization-strip + `X-User-Id` / `X-User-Roles` injection), §5 (correlation-ID propagation — already wired, just inherits), §7 (RFC-7807 error shape with example for `unauthorized` + `validation`)
- `.planning/saga-contracts.md` §1 (envelope: `eventId, eventType, eventVersion, occurredAt, correlationId, causationId, producer, payload`), §3 (DLX naming `<exchange>.dlx`), §4 (retry policy: 3 attempts, 1s/5s backoff, max 30s cap), §5 (idempotency: transactional outbox + processed_events inbox)
- `.planning/saga-contracts/envelope.schema.json` — outbox payload format for `user.registered` (D-12)

### Existing Phase 1 Artifacts (read before touching)
- `service-template/src/main/java/com/n11/template/ServiceTemplateApplication.java` + `application.yml` + `logback-spring.xml` + `db/migration/V1__init_processed_events.sql` — clone-and-rename source for identity-service (per Phase 1 D-10 / CD-02 "clone-and-edit" choice)
- `service-template/skeleton/` (Phase 1 Plan 01-07 archetype directory — verify exact path during planning) — the canonical archetype for spawning a new business service module
- `api-gateway/src/main/java/com/n11/gateway/SecurityConfig.java` — REPLACE wholesale per in-file Phase-3 TODO; current file is `permitAll()` + CORS only
- `api-gateway/src/main/java/com/n11/gateway/GatewayHeaderInjectionFilter.java` — REPLACE wholesale per in-file Phase-3 TODO; current file strips inbound `X-User-Id` / `X-User-Roles` defensively but does NOT yet inject from JWT
- `api-gateway/src/main/java/com/n11/gateway/GatewayCorrelationIdFilter.java` — DO NOT TOUCH (correlation-ID propagation already complete from Phase 1)
- `config-server/src/main/resources/config/api-gateway.yml` — add `oauth2.resource-server.jwt.jwk-set-uri` per D-20 (footer comment already reserves the spot)
- `config-server/src/main/resources/config/application.yml` — shared baseline (datasource template uses `db.user` / `db.password` placeholders that per-service yaml fills); identity-service.yml just sets those vars
- `infra/postgres/init.sh` — already creates `identity` schema + `identity_user` with role-deny matrix; verify env-var name convention for `IDENTITY_DB_PASSWORD`
- `common-error/...` — RFC-7807 `ProblemDetailControllerAdvice` reused; CD-05 may add new `ApiErrorCode` entries
- `common-logging/...` — correlation-ID MDC propagation reused; identity-service inherits via dependency
- `common-events/...` — saga envelope abstractions + JSON-Schema validator; identity-service uses these for the `user.registered` outbox (D-12)
- `infra-tests/` — Testcontainers boundary smoke pattern; if Phase 3 adds an integration test (CD-07 W2), follow this pattern

### External docs (verify-before-implement policy — read at impl time)
- https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/index.html — BCrypt password encoder configuration (cost 10)
- https://docs.spring.io/spring-security/reference/reactive/oauth2/resource-server/jwt.html — Reactive resource server JWT setup, JWK-set-uri caching (default `cacheTtl=5m`, `refreshTtl=1h` — matches success criterion #4)
- https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html#oauth2resourceserver-jwt-decoder-public-key-boot — JWK serialization, kid management
- https://datatracker.ietf.org/doc/html/rfc7517 — JWK / JWKS normative spec for the `/.well-known/jwks.json` payload shape
- https://datatracker.ietf.org/doc/html/rfc7519 — JWT spec; standard claim names (`sub`, `iat`, `exp`, `iss`); custom claim `roles` is non-standard but conventional
- https://github.com/Nimbus-OAuth/nimbus-jose-jwt — Nimbus JWT library used under the hood by Spring Security; `RSAKey` builder + `JWKSet` for the `/jwks.json` endpoint
- https://flywaydb.org/documentation/concepts/placeholders — Flyway placeholders for `${adminSeedEmail}` substitution in V2 migration (D-06)
- https://www.postgresql.org/docs/16/indexes-partial.html — partial unique index for D-11 (`is_default` constraint)
- (Read at impl time — confirm version-specific syntax for Spring Boot 3.5.14 + Spring Security 6.x)

### Phase 3 deliverables (will become refs for later phases)
- `.planning/saga-contracts/user-registered.schema.json` — locked event payload shape (D-12)
- Updated `.planning/saga-contracts.md` §2 with new `identity.tx` exchange + `notify.q.user-registered` queue row
- `identity-service/` module — first business service; clone source for Phase 4 (product, inventory)
- Replaced `api-gateway/.../SecurityConfig.java` + `GatewayHeaderInjectionFilter.java` — final-form gateway auth posture for v1
- `config-server/src/main/resources/config/identity-service.yml` (new) — per-service yaml shape that Phase 4+ services replicate

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`service-template/`** — the entire archetype is reusable. `ServiceTemplateApplication.java` becomes `IdentityServiceApplication.java`; `application.yml` is reused near-verbatim with `spring.application.name: identity-service`; `logback-spring.xml` reused as-is (correlation-ID JSON encoder already wired); `db/migration/V1__init_processed_events.sql` is **kept** (every saga consumer needs this inbox; identity-service may not consume saga events in v1, but inheriting the table costs ~5 lines and keeps the per-service migration shape uniform).
- **`common-error/`** — `ProblemDetailControllerAdvice` + `ApiErrorCode` enum produce RFC-7807 problem+json errors automatically once identity-service depends on it.
- **`common-logging/`** — `CorrelationIdFilter` (servlet) auto-applied via Spring Boot autoconfiguration (Plan 01-04 registered the AutoConfiguration). Identity-service inherits MDC propagation.
- **`common-events/`** — saga envelope DTOs + `AbstractEventSchemaTest` base class. Identity-service uses these for the `user.registered` outbox (D-12).
- **`api-gateway/.../GatewayCorrelationIdFilter.java`** — DO NOT replace; only the SecurityConfig + HeaderInjectionFilter need surgery. CorrelationId filter precedes both per `getOrder()` ordering — verify post-replacement.

### Established Patterns
- **Schema-per-service is structural.** `identity_user` cannot SELECT from any other schema (Plan 01-08 boundary smoke test). Identity-service code that needs another service's data MUST do it via REST (no cross-DB joins).
- **`processed_events` inbox lives in every service** (Plan 01-07 service-template archetype). Identity-service inherits it even if no consumer exists in v1 — the cost is one migration line, the benefit is "every service has the same idempotency surface."
- **Transactional outbox** is the locked publisher pattern (`saga-contracts.md` §5). Identity-service introduces the FIRST instance of it via D-12; planner should treat the outbox poller code as a candidate for extraction into a `common-outbox` shared module IF Phase 5's order-service implementation reveals enough overlap. Phase 3 does NOT pre-extract — it ships in-service first, refactors when there's a second consumer.
- **`spring.config.import` (NOT bootstrap.yml)** — Boot 3.x convention; identity-service inherits this from the cloned `service-template/application.yml` shape (Cross-Cutting #2 in Plan 01-06 PATTERNS).
- **Per-service Flyway migration** — Phase 1's pattern: `db/migration/V1__init_<domain>.sql`, `V2__...`, etc. Identity-service follows this with `V1__init_users_addresses.sql`, `V2__seed_admin.sql`, `V3__init_outbox.sql` (or all merged into V1 — planner discretion).
- **Pre-compose Jib build** — Plan 01-05 / 01-06 lock the pattern: `./gradlew :identity-service:jibDockerBuild` BEFORE `docker compose up -d` for the new service. README the rule.
- **docker-compose additive merge** — Plan 01-05 / 01-06 lock the idiom: read the existing `docker-compose.yml`, ADD `identity-service` under `services:`, never re-write the file. Verify with `grep -c '^  identity-service:'` returning 1.

### Integration Points
- **identity-service ↔ Postgres** — via `identity_user` (server-side `search_path = identity, public` per Plan 01-03 D-04). JDBC URL is uniform across services.
- **identity-service ↔ Eureka** — registers as `identity-service`; gateway picks it up via `discovery.locator.enabled=true`.
- **identity-service ↔ config-server** — fetches `identity-service.yml` (new file Phase 3 owns) merged with `application.yml` (shared baseline).
- **identity-service ↔ RabbitMQ** — via `identity.tx` exchange (D-12). New exchange — saga-contracts.md §2 gets a new row.
- **api-gateway ↔ identity-service JWKS** — gateway boot config `jwk-set-uri: http://identity-service:8081/.well-known/jwks.json`. Service-discovery aware? — investigate if Spring Security's `NimbusReactiveJwtDecoder` accepts `lb://identity-service/.well-known/jwks.json` (Eureka URI scheme); if not, hardcode the docker-compose hostname (acceptable for v1 single-host deploy).
- **api-gateway ↔ identity-service via JWT** — gateway decodes JWT signed by identity-service; mints `X-User-Id` from `sub` claim, `X-User-Roles` from `roles` claim. End-to-end smoke: register → login → curl `/api/v1/identity/auth/me` with `Authorization: Bearer <token>` → 200 with user JSON.

</code_context>

<specifics>
## Specific Ideas

- **Single 24h JWT, no refresh-token endpoint** (D-01) — biggest scope-saver in Phase 3; keeps the auth surface boring and grading-credible.
- **PEM private key from env var, public key derived at boot** (D-02) — single secret to manage; eliminates the "two env vars that have to match" trap. JWKS endpoint is the single sharing path.
- **Flyway-seeded admin** (D-06) — the env-var pair (`ADMIN_SEED_EMAIL`, `ADMIN_SEED_PASSWORD_HASH`) is documented in the README's env-var matrix; grader can sign in as admin to test `POST /products` in Phase 4.
- **Pragmatic n11-shape address book** (D-08, D-09) — the field set was tuned to (a) match what an Iyzico checkout call expects in Phase 6 (recipient_name + phone are non-negotiable there) and (b) what n11.com itself shows in account → adres-defterim (Phase 2 recon report has the screenshot). No `il`/`ilçe` reference dataset; free-form-text + 5-digit postal code is enough.
- **`user.registered` published from Phase 3, consumed in Phase 7** (D-12) — locks the outbox demo two phases earlier than Phase 5. Treats identity-service as the first SOLID-flavored example of the saga-contracts.md pattern (envelope + transactional outbox + idempotency-key payload), not just the auth service.
- **Replace, don't patch, the gateway auth files** (D-18, D-19) — the in-file TODO comments in Phase 1 explicitly say "REPLACE wholesale; do NOT patch." Honor that. The Phase 1 stubs were intentionally minimal.
- **Smoke test = `PasswordEncoderTest`** (D-16) — the establishing-pattern role for QUAL-02 across all services; Phase 4+ services follow this exact shape (one pure-JUnit smoke in `src/test/java/.../<Domain>EncoderTest.java` style).
- **Identity-service is the FIRST clone** of the service-template (D-21) — Phase 3's planner is the first to walk through the clone procedure end-to-end. Document any rough edges in a per-phase PATTERNS.md so Phase 4 onwards has a frictionless path.

</specifics>

<deferred>
## Deferred Ideas

- **Refresh-token endpoint + rotation** — Pitfall #18 says not needed for grading; revisit only if the storefront UX demands it (Phase 10/11). Out of scope here.
- **Password reset (email link) / email verification at signup** — Out of scope per PROJECT.md (no SMTP). Revisit in v2.
- **OAuth login (Google, GitHub)** — Out of scope per PROJECT.md.
- **TC kimlik validation** — Out of scope; complexity unjustified for v1.
- **`POST /agents/exchange`** (MCP API-key bridge — `api-contracts.md` §1) — Reserved Phase 9 (MCP server) territory. Phase 3 may want to STUB the route in the gateway routing table only (no implementation), or fully defer; recommend defer.
- **Rate-limiting on `/auth/login`** (CD-06) — out of bootcamp brief, low grading payoff. Revisit if a security-grading thread emerges.
- **Extracting outbox poller into `common-outbox` module** — wait until Phase 5's order-service introduces a second use-case before refactoring; ship in-service first.
- **`il`/`ilçe` reference dataset** — would enable cascading dropdowns in the address form. Out of scope; UI uses free-form text inputs.
- **Phone number E.164 / Türkiye-format validation** (D-09) — free-form-text is enough for v1; no SMS sending exists, so the field is decorative.
- **JWKS rotation automation** (zero-downtime key rotation) — D-03 documents the manual procedure; full automation (publish OLD + NEW keys during a rotation window with overlap) is out of scope for v1.
- **`oauth2.resource-server.jwt.issuer-uri`** (full OIDC issuer) — using `jwk-set-uri` only is enough for v1 (D-20). Full issuer + `.well-known/openid-configuration` discovery is over-engineered for the n11 demo.

</deferred>

---

*Phase: 3-identity-gateway-auth*
*Context gathered: 2026-04-29*
