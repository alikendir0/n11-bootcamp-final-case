---
phase: 03-identity-gateway-auth
verified: 2026-04-29T15:30:00Z
status: human_needed
score: 5/5 must-haves verified
overrides_applied: 0
human_verification:
  - test: "AUTH-03 browser-refresh survival: store the JWT from POST /auth/register in localStorage, reload the page, and confirm the frontend (Phase 10) reads the token and calls GET /auth/me returning 200 without re-login"
    expected: "User remains logged in after browser refresh; /auth/me returns the same UserProfileResponse"
    why_human: "The backend contract is fully wired (24h token, /auth/me accepts X-User-Id), but 'survives browser refresh' is a frontend storage behavior (localStorage/sessionStorage). Phase 10 has not been built yet. The backend half is verified; the user-facing claim in SC-1 requires a browser."
  - test: "AUTH-04 logout from any page: navigate to a page, click logout, confirm the token is cleared and a subsequent GET /api/v1/identity/auth/me returns 401"
    expected: "After clicking logout the browser clears the JWT; next /auth/me returns 401 from the gateway"
    why_human: "AUTH-04 is a client-side operation (clear token from storage). The backend is stateless — there is no revocation endpoint and none is needed (confirmed in RESEARCH.md). Verification requires a browser and the Phase 10 frontend, which is not yet built."
  - test: "SC-4 keypair rotation without gateway restart: change JWT_PRIVATE_KEY + bump JWT_KEY_ID, restart identity-service only (not api-gateway), then confirm a token signed with the new key is accepted within 1 hour"
    expected: "New JWT signed with rotated keypair validates through the gateway without restarting api-gateway (NimbusReactiveJwtDecoder refreshes JWKS within its 1h TTL)"
    why_human: "The code is correctly wired (NimbusReactiveJwtDecoder with default 1h refresh, no explicit cache override found), but rotation is a live operational test requiring key regeneration, identity-service restart, and waiting/forcing a JWKS cache refresh cycle. Cannot test programmatically without running infrastructure."
---

# Phase 3: Identity + Gateway Auth Verification Report

**Phase Goal:** Stand up identity-service (signup, login, JWT issuance, address book) and wire api-gateway to validate every JWT and inject `X-User-Id` / `X-User-Roles` headers, so every later service trusts the gateway-injected mesh and never sees the raw JWT.
**Verified:** 2026-04-29T15:30:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | User can register with email + password (BCrypt cost 10), log in, receive an RS256-signed JWT | VERIFIED | `IdentitySecurityConfig` declares `BCryptPasswordEncoder(10)`. `JwtIssuerService.issue()` produces RS256 tokens via `JwsHeader.with(SignatureAlgorithm.RS256)`. `AuthController` POST /auth/register returns 201 with `AuthResponse(accessToken, "Bearer", 86400, user)`. E2E smoke test 2 confirmed HTTP 201 + RS256 JWT with kid=n11-jwt-2026-04 and expiresIn=86400. |
| 2 | Token survives browser refresh + 401 retry against /auth/me (SC-1 backend half) | VERIFIED | Backend: `/auth/me` endpoint is wired in `AuthController`; reads `X-User-Id` header injected by gateway; returns `UserProfileResponse`. Token lifetime is 24h (86400s). E2E smoke test 4 confirmed `/auth/me` returns 200 with valid profile. Frontend/browser half flagged for human verification. |
| 3 | Protected routes without Bearer token return 401 from gateway; downstream sees only X-User-Id and X-User-Roles (Authorization stripped) | VERIFIED | `SecurityConfig.anyExchange().authenticated()` enforces 401 for unauthenticated requests. `GatewayHeaderInjectionFilter` strips `Authorization` unconditionally and injects `X-User-Id`/`X-User-Roles` from JWT claims. `GatewayHeaderInjectionFilterTest` passes. E2E smoke test 3 confirmed 401 without token; smoke test 4 confirmed downstream receives X-User-Id. |
| 4 | User can save and list multiple Türkiye delivery addresses — visible via GET /addresses | VERIFIED | `AddressController` at `/addresses` with GET (list) and POST (create). `AddressService.listForUser` delegates to `AddressRepository.findByUserIdOrderByCreatedAtDesc`. D-11 partial unique index on `is_default` enforced. E2E smoke test 5 confirmed POST 201 + GET 200 with Turkish address fields (il, ilce, mahalle, postalCode). |
| 5 | identity-service exposes /.well-known/jwks.json serving only public material; gateway validates against it | VERIFIED | `JwksController` at `/.well-known/jwks.json` calls `rsaJwk.toPublicJWK()` (private material stripped). `SecurityConfig` configures `NimbusReactiveJwtDecoder.withJwkSetUri("http://identity-service:8081/.well-known/jwks.json")` with no cache override (uses Nimbus defaults: 5m TTL, 1h refresh). E2E smoke test 1 confirmed HTTP 200, kty=RSA, alg=RS256, no `d` field. |

**Score:** 5/5 truths verified (3 items require human verification per flagged sub-behaviors)

### Deferred Items

None — all must-haves are either verified or pending human testing.

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `identity-service/src/main/java/com/n11/identity/auth/JwtConfig.java` | RSAPrivateKey + RSAPublicKey + RSAKey + JWKSource + NimbusJwtEncoder beans | VERIFIED | 5 `@Bean` methods confirmed. `RsaKeyConverters.pkcs8()` loads private key. `RSAPrivateCrtKey` cast derives public key. |
| `identity-service/src/main/java/com/n11/identity/auth/JwtIssuerService.java` | issue(UUID,String,String,List) -> RS256 JWT, 24h expiry, roles claim | VERIFIED | `expiresAt(now.plusSeconds(86400))` confirmed. Claims: sub, iat, exp, roles, email, fullName. RS256 header. |
| `identity-service/src/main/java/com/n11/identity/auth/JwksController.java` | GET /.well-known/jwks.json returning public JWK only | VERIFIED | `rsaJwk.toPublicJWK()` called before serialization. Map response confirmed. |
| `identity-service/src/main/java/com/n11/identity/auth/IdentitySecurityConfig.java` | Permit-all SecurityFilterChain + BCryptPasswordEncoder(10) | VERIFIED | `anyRequest().permitAll()` confirmed. `new BCryptPasswordEncoder(10)` bean confirmed. |
| `identity-service/src/test/java/com/n11/identity/PasswordEncoderTest.java` | 4 pure-JUnit smoke tests, cost-10 BCrypt | VERIFIED | 4 `@Test` methods, no `@SpringBootTest`, `$2a$10$` prefix assertion present. `./gradlew :identity-service:test --tests "*PasswordEncoderTest"` exits 0 (BUILD SUCCESSFUL). |
| `identity-service/src/main/resources/db/migration/V2__init_users_addresses.sql` | users/roles/user_roles/addresses DDL + D-11 partial unique index | VERIFIED | All 4 tables confirmed. `CREATE UNIQUE INDEX idx_addresses_user_default ON addresses (user_id) WHERE is_default` confirmed. `ROLE_USER` + `ROLE_ADMIN` pre-seeded. |
| `identity-service/src/main/resources/db/migration/V3__seed_admin.sql` | Admin seed via Flyway placeholders, idempotent | VERIFIED | `${adminSeedEmail}` x2 and `${adminSeedPasswordHash}` x1. Two `ON CONFLICT DO NOTHING` clauses confirmed. |
| `identity-service/src/main/resources/db/migration/V4__init_outbox.sql` | Transactional outbox table per saga-contracts.md §5.1 | VERIFIED | `CREATE TABLE outbox` + `outbox_unsent_idx` confirmed. |
| `config-server/src/main/resources/config/identity-service.yml` | Port 8081, identity_user creds, Flyway schema=identity, JWT env pass-through | VERIFIED | `port: 8081`, `username: identity_user`, `password: ${IDENTITY_DB_PASSWORD}`, `schemas: identity`, `private-key: ${JWT_PRIVATE_KEY}`, `adminSeedEmail: ${ADMIN_SEED_EMAIL}`, no PEM content (grep `BEGIN` returns 0). |
| `api-gateway/src/main/java/com/n11/gateway/SecurityConfig.java` | OAuth2 resource server JWT chain, public allowlist, 30s clock skew | VERIFIED | `oauth2ResourceServer().jwt()` with `NimbusReactiveJwtDecoder`. `JwtTimestampValidator(Duration.ofSeconds(30))`. Public allowlist includes login, register, JWKS, products, search, chat, iyzico callback. `anyExchange().authenticated()`. |
| `api-gateway/src/main/java/com/n11/gateway/GatewayHeaderInjectionFilter.java` | Strips Authorization, strips spoofed X-User-*, injects X-User-Id/X-User-Roles | VERIFIED | `h.remove(HEADER_AUTH)` unconditional. `h.remove(HEADER_USER_ID)` + `h.remove(HEADER_USER_ROLES)` strip inbound spoofed headers. `h.set(HEADER_USER_ID, userId)` from JWT sub. `switchIfEmpty` on `Mono<ServerHttpRequest>` (not `Mono<Void>`) — known reactive pitfall avoided. |
| `config-server/src/main/resources/config/api-gateway.yml` | jwk-set-uri + explicit identity-service route with StripPrefix=3 | VERIFIED | `jwk-set-uri: http://identity-service:8081/.well-known/jwks.json` confirmed. Route `id: identity-service`, `uri: lb://IDENTITY-SERVICE`, `Path=/api/v1/identity/**`, `StripPrefix=3` confirmed. |
| `.planning/saga-contracts/user-registered.schema.json` | JSON-Schema 2020-12, locked payload shape | VERIFIED | `$id`, `required: [userId, email, fullName, registeredAt]`, `additionalProperties: false` confirmed. |
| `common-events/src/main/resources/saga-schemas/user-registered.schema.json` | Classpath mirror, identical to canonical | VERIFIED | `diff` of both files exits 0. |
| `identity-service/src/main/java/com/n11/identity/outbox/OutboxPoller.java` | @Scheduled(fixedDelay=5000) + @Transactional, drains to identity.tx | VERIFIED | `@Scheduled(fixedDelay = 5000)` + `@Transactional` confirmed. `rabbitTemplate.convertAndSend("identity.tx", "user.registered", ...)` in poll(). |
| `identity-service/src/test/java/com/n11/identity/outbox/OutboxIntegrationTest.java` | Testcontainers-backed, schema drift gate, idempotency check | VERIFIED | `SchemaRegistry` networknt 3.0.2 drift gate present. 2 test methods (outbox row + poller drain). Confirmed via commit 048128c. |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `JwtConfig.java` | `JwtIssuerService.java` | `JwtEncoder` bean injection | WIRED | `JwtIssuerService` constructor takes `JwtEncoder jwtEncoder`; `JwtConfig` produces `NimbusJwtEncoder` bean. |
| `JwtConfig.java` | `JwksController.java` | `RSAKey` bean injection | WIRED | `JwksController` constructor takes `RSAKey rsaJwk`; `JwtConfig` produces `RSAKey` bean. |
| `UserService.java` | `JwtIssuerService.java` | `buildAuthResponse` call | WIRED | `jwtIssuerService.issue(user.getId(), user.getEmail(), user.getFullName(), roles)` confirmed in `UserService.buildAuthResponse`. |
| `AuthController.java` | `UserService.java` | POST /auth/register + POST /auth/login | WIRED | Controller delegates to `userService.register()` and `userService.login()`. `@RequestMapping("/auth")` + individual `@PostMapping`. |
| `AddressController.java` | `AddressService.java` | GET /addresses + POST /addresses | WIRED | Controller reads `X-User-Id` header and passes `userId` to `addressService.listForUser()` and `addressService.create()`. |
| `api-gateway.yml` jwk-set-uri | `JwksController.java` | JWKS fetch at decode time | WIRED | `http://identity-service:8081/.well-known/jwks.json` matches `@GetMapping("/.well-known/jwks.json")`. Route `StripPrefix=3` correctly strips `/api/v1/identity` prefix. |
| `GatewayHeaderInjectionFilter.java` | `SecurityConfig.java` | JWT claims read from `ReactiveSecurityContextHolder` | WIRED | `SecurityConfig` populates reactive security context via `oauth2ResourceServer().jwt()`. `GatewayHeaderInjectionFilter` reads `JwtAuthenticationToken` from `ReactiveSecurityContextHolder.getContext()`. |
| `OutboxBackedUserRegistrationOutboxPublisher.java` | `UserService.java` | `UserRegistrationOutboxPublisher` interface | WIRED | `@Component` displaces `NoOpUserRegistrationOutboxPublisher` via `@ConditionalOnMissingBean(name="outboxBackedUserRegistrationOutboxPublisher")`. `UserService.register()` calls `outboxPublisher.publishRegistered(saved)` inside `@Transactional`. |
| `config-server/identity-service.yml` | `V3__seed_admin.sql` | Flyway placeholder substitution | WIRED | `spring.flyway.placeholders.adminSeedEmail: ${ADMIN_SEED_EMAIL}` and `adminSeedPasswordHash: ${ADMIN_SEED_PASSWORD_HASH}` confirmed in config YAML. V3 migration uses `${adminSeedEmail}` and `${adminSeedPasswordHash}`. |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `AuthController` /auth/register | `AuthResponse` | `UserService.register()` → `BCrypt.encode` + `JwtIssuerService.issue()` + `userRepository.save()` | Yes — real DB write + real JWT mint | FLOWING |
| `AuthController` /auth/me | `UserProfileResponse` | `UserService.getProfile(userId)` → `userRepository.findById(uuid)` | Yes — real DB read from `identity.users` | FLOWING |
| `AddressController` GET /addresses | `List<AddressResponse>` | `AddressService.listForUser(userId)` → `addressRepository.findByUserIdOrderByCreatedAtDesc(userId)` | Yes — real DB query from `identity.addresses` | FLOWING |
| `JwksController` /.well-known/jwks.json | `Map<String, Object>` | `rsaJwk.toPublicJWK()` → RSA public key from Spring bean loaded at boot from `JWT_PRIVATE_KEY` env var | Yes — derived from real env-supplied RSA key | FLOWING |
| `OutboxPoller.poll()` | `List<OutboxEvent>` | `outboxRepository.findUnsentBatch(100)` native query `FROM identity.outbox WHERE sent_at IS NULL FOR UPDATE SKIP LOCKED` | Yes — real DB query, real RabbitMQ publish | FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| PasswordEncoderTest passes | `./gradlew :identity-service:test --tests "*PasswordEncoderTest" --no-daemon` | BUILD SUCCESSFUL | PASS |
| SecurityConfigTest + GatewayHeaderInjectionFilterTest pass | `./gradlew :api-gateway:test --tests "*SecurityConfigTest" --tests "*GatewayHeaderInjectionFilterTest" --no-daemon` | BUILD SUCCESSFUL | PASS |
| identity-service compileJava | `./gradlew :identity-service:compileJava --no-daemon` | BUILD SUCCESSFUL | PASS |
| api-gateway compileJava | `./gradlew :api-gateway:compileJava --no-daemon` | BUILD SUCCESSFUL | PASS |
| E2E smoke (manual, conducted 2026-04-29) | 6 smoke probes via running stack on localhost:8090 | All 6 PASS (documented in 03-06-E2E-SMOKE.md) | PASS |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| AUTH-01 | 03-01, 03-02, 03-04 | User can register with email and password | SATISFIED | `AuthController` POST /auth/register; `UserService.register()` persists to `identity.users` via JPA; BCrypt encode; 201 response. |
| AUTH-02 | 03-03, 03-04 | User can log in and receive a JWT | SATISFIED | `AuthController` POST /auth/login; `UserService.login()` calls `BCrypt.matches()`; `JwtIssuerService.issue()` returns RS256 token; `AuthResponse(accessToken, "Bearer", 86400, user)`. |
| AUTH-03 | 03-04, 03-06 | Token survives browser refresh; refreshed on 401 | PARTIAL (NEEDS HUMAN) | Backend: 24h token, /auth/me endpoint wired. Client-side storage/retry is Phase 10 frontend work. See human verification item 1. |
| AUTH-04 | 03-06 | User can log out from any page | PARTIAL (NEEDS HUMAN) | Confirmed client-side only per RESEARCH.md. Backend is stateless. No revocation endpoint (per D-01 design decision). See human verification item 2. |
| AUTH-05 | 03-03, 03-04 | JWT contains user-id and roles claims | SATISFIED | `JwtIssuerService` issues tokens with `sub=userId`, `roles=["ROLE_USER"]` (D-05 claim shape). |
| AUTH-06 | 03-06 | Gateway validates JWT; downstream trusts X-User-Id / X-User-Roles | SATISFIED | `SecurityConfig` oauth2ResourceServer + `GatewayHeaderInjectionFilter` strips Authorization and injects X-User-Id/X-User-Roles. Verified by E2E smoke test and unit tests. |
| AUTH-07 | 03-03 | BCrypt cost 10 | SATISFIED | `IdentitySecurityConfig` bean: `new BCryptPasswordEncoder(10)`. `PasswordEncoderTest` asserts `$2a$10$` prefix. |
| AUTH-08 | 03-02, 03-04 | Multiple delivery addresses (Mahalle / İlçe / İl) | SATISFIED | `AddressController` GET/POST `/addresses`. V2 migration has full Turkish address fields. D-11 partial unique index. E2E smoke test 5 confirmed. |
| QUAL-02 | 03-03 | Each service has at least one smoke unit test | SATISFIED | `PasswordEncoderTest`: 4 pure-JUnit tests, no Spring context, passes in < 15s. Establishes per-service pattern for Phase 4+. |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `identity-service/src/main/java/com/n11/identity/user/NoOpUserRegistrationOutboxPublisher.java` | N/A | No-op `publishRegistered()` — intentional outbox fallback | INFO | Displaced by `OutboxBackedUserRegistrationOutboxPublisher` via `@ConditionalOnMissingBean`. Not a stub — design pattern. |

No blockers, no stubs masking real behavior. The `NoOp` publisher is an explicitly designed fallback per D-12 and is correctly displaced in production by the real publisher.

### Human Verification Required

#### 1. AUTH-03 Browser Refresh Survival (Phase 10 Dependency)

**Test:** In a browser with the Phase 10 frontend running: log in via POST /auth/register or /auth/login, refresh the page, then navigate to a protected page that calls /auth/me.
**Expected:** The user remains authenticated after browser refresh; no re-login prompt is shown; GET /auth/me returns 200 with the saved UserProfileResponse.
**Why human:** The backend contract is fully verified (24h JWT, /auth/me wired and working via X-User-Id header). The "survives browser refresh" behavior depends on the frontend storing the JWT in localStorage/sessionStorage and re-attaching it on reload. Phase 10 frontend has not been built yet.

#### 2. AUTH-04 Logout from Any Page (Phase 10 Dependency)

**Test:** With a logged-in browser session, click a logout button/link. Then attempt to access a protected route (e.g., GET /api/v1/identity/auth/me).
**Expected:** After logout the browser clears the JWT; the next protected-route call returns 401 from the gateway.
**Why human:** AUTH-04 is confirmed as client-side only in RESEARCH.md. The backend is stateless (no revocation endpoint, 24h JWTs, confirmed by D-01 decision). Verification requires a browser and Phase 10 frontend.

#### 3. SC-4 Keypair Rotation Without Gateway Restart

**Test:** Generate a new RSA keypair, update JWT_PRIVATE_KEY and bump JWT_KEY_ID in .env, restart identity-service only. Wait for NimbusReactiveJwtDecoder to refresh its JWKS cache (default refresh TTL is 1h; can be forced by clearing the cache or waiting). Register a new user to get a token signed with the new key. Call /auth/me with that token.
**Expected:** The new token signed with the rotated keypair validates through the api-gateway without restarting the gateway. The gateway picks up the new JWKS entry within its 1h refresh window.
**Why human:** The code is correctly wired — no explicit cache override found in `SecurityConfig.java`, meaning NimbusReactiveJwtDecoder uses its default 5m-TTL / 1h-refresh JWKS caching. However, this is an operational rotation test requiring key regeneration, identity-service restart, and waiting/forcing a cache refresh cycle. Cannot be verified programmatically in a CI context.

### Gaps Summary

No gaps blocking the phase goal. All 5 success criteria have codebase evidence. The 3 human verification items are:

- AUTH-03 and AUTH-04 are intentionally deferred to Phase 10 (frontend) per design decision D-01 and confirmed in RESEARCH.md.
- SC-4 keypair rotation is operational infrastructure that passes code review but requires a live rotation test.

The backend contract for the phase goal is fully implemented and verified: identity-service issues RS256 JWTs, the address book is wired, the gateway validates JWTs and strips the Authorization header while injecting X-User-Id/X-User-Roles, the JWKS endpoint serves public-only material, and the PasswordEncoderTest establishes the QUAL-02 pattern.

---

_Verified: 2026-04-29T15:30:00Z_
_Verifier: Claude (gsd-verifier)_
