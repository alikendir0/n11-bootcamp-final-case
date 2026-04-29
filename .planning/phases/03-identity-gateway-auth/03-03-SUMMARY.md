---
phase: 03-identity-gateway-auth
plan: 03
subsystem: auth
tags: [jwt, rs256, jwks, bcrypt, spring-security, nimbus-jose]

# Dependency graph
requires:
  - phase: 03-01
    provides: identity-service module scaffold (build.gradle.kts, settings.gradle.kts, IdentityServiceApplication.java)
  - phase: 03-02
    provides: config-server identity-service.yml with jwt.private-key and jwt.key-id env-var pass-through
provides:
  - RS256 JWT keypair bean cluster (JwtConfig: RSAPrivateKey, RSAPublicKey, RSAKey, JWKSource, NimbusJwtEncoder)
  - JWKS endpoint at /.well-known/jwks.json serving public-only JWK set
  - Permit-all servlet SecurityFilterChain + BCryptPasswordEncoder(10) bean
  - JwtIssuerService.issue() — mints RS256 JWTs with sub/roles/email/fullName claims, 24h expiry
  - PasswordEncoderTest — QUAL-02 smoke unit test pattern established
affects:
  - 03-04 (AuthController/UserService — calls JwtIssuerService.issue() from login)
  - 03-06 (api-gateway — fetches /.well-known/jwks.json for JWT validation)
  - All Phase 4+ services (PasswordEncoderTest pattern replicated per QUAL-02)

# Tech tracking
tech-stack:
  added:
    - spring-boot-starter-oauth2-resource-server (NimbusJwtEncoder, JwtClaimsSet, JwsHeader)
    - spring-boot-starter-security (BCryptPasswordEncoder, SecurityFilterChain)
    - com.nimbusds:nimbus-jose-jwt (RSAKey, JWKSet, JWSAlgorithm — transitive)
  patterns:
    - RS256 keypair: private from env var, public derived at boot (single-source-of-truth, no key-mismatch drift)
    - JWKS endpoint via rsaJwk.toPublicJWK() — critical private-key strip before serialization
    - Permit-all SecurityFilterChain (identity-service does NOT validate JWTs — gateway is sole validator)
    - QUAL-02 smoke unit test: pure JUnit, no Spring context, < 5s, cost-10 BCrypt assertions

key-files:
  created:
    - identity-service/src/main/java/com/n11/identity/auth/JwtConfig.java
    - identity-service/src/main/java/com/n11/identity/auth/JwtIssuerService.java
    - identity-service/src/main/java/com/n11/identity/auth/JwksController.java
    - identity-service/src/main/java/com/n11/identity/auth/IdentitySecurityConfig.java
    - identity-service/src/test/java/com/n11/identity/PasswordEncoderTest.java
  modified:
    - settings.gradle.kts (identity-service include added)
    - identity-service/build.gradle.kts (created as part of module setup in this plan's worktree)

key-decisions:
  - "D-02: RSAPublicKey derived from RSAPrivateCrtKey at boot — eliminates two-env-var mismatch trap"
  - "D-15: IdentitySecurityConfig is permit-all — identity-service is JWT signer only, never validator"
  - "AUTH-07: BCryptPasswordEncoder(10) bean in IdentitySecurityConfig, used by UserService (Plan 03-04)"
  - "QUAL-02: PasswordEncoderTest is the per-service smoke test pattern — 4 pure-JUnit tests, no Spring context"

patterns-established:
  - "JWT signing: JwsHeader.with(SignatureAlgorithm.RS256) + JwtClaimsSet.builder() + JwtEncoderParameters.from(header, claims)"
  - "QUAL-02 smoke test: class in com.n11.<service> package, BCryptPasswordEncoder(10) constructor, 4 @Test methods, no @SpringBootTest"
  - "JWKS serving: JWKSet(rsaJwk.toPublicJWK()).toJSONObject() — always strip private material before return"

requirements-completed: [AUTH-02, AUTH-03, AUTH-05, AUTH-07, QUAL-02]

# Metrics
duration: 15min
completed: 2026-04-29
---

# Phase 03 Plan 03: JWT Keypair + JWKS + PasswordEncoderTest Summary

**RS256 keypair wired as Spring beans (JwtConfig), JWKS endpoint serving public-only JWK set, permit-all SecurityFilterChain with BCryptPasswordEncoder(10), JwtIssuerService minting 24h tokens, and PasswordEncoderTest establishing QUAL-02 smoke pattern**

## Performance

- **Duration:** ~15 min
- **Started:** 2026-04-29T09:26:00Z
- **Completed:** 2026-04-29T09:41:19Z
- **Tasks:** 2 completed
- **Files modified:** 7 (5 auth/test Java files + build.gradle.kts + settings.gradle.kts)

## Accomplishments

- JwtConfig wires 5 beans: RSAPrivateKey (from JWT_PRIVATE_KEY env var via RsaKeyConverters.pkcs8()), RSAPublicKey (derived at boot via RSAPrivateCrtKey cast), RSAKey, JWKSource<SecurityContext>, NimbusJwtEncoder — single private key env var eliminates key-mismatch drift
- JwksController serves GET /.well-known/jwks.json using rsaJwk.toPublicJWK() — private material never reaches the wire (T-3-01 mitigation)
- IdentitySecurityConfig: permit-all SecurityFilterChain (D-15: identity-service does NOT validate JWTs) + BCryptPasswordEncoder(10) bean (AUTH-07)
- JwtIssuerService.issue() produces RS256-signed JWTs with sub=userId, roles=[ROLE_*], email, fullName claims and 24h expiry (86400s)
- PasswordEncoderTest: 4 pure-JUnit tests pass — cost-10 round-trip, wrong-password rejection, distinct salts, $2a$10$ prefix assertion (D-16, QUAL-02 starter)

## Task Commits

1. **Task 1: JwtConfig + IdentitySecurityConfig + JwksController** - `4587c62` (feat)
2. **Task 2: JwtIssuerService + PasswordEncoderTest** - `b7cf947` (feat)

## Files Created/Modified

- `identity-service/src/main/java/com/n11/identity/auth/JwtConfig.java` - RS256 keypair bean cluster (5 @Bean methods)
- `identity-service/src/main/java/com/n11/identity/auth/IdentitySecurityConfig.java` - permit-all SecurityFilterChain + BCryptPasswordEncoder(10)
- `identity-service/src/main/java/com/n11/identity/auth/JwksController.java` - GET /.well-known/jwks.json with public-only JWKS
- `identity-service/src/main/java/com/n11/identity/auth/JwtIssuerService.java` - RS256 JWT issuance with 24h expiry
- `identity-service/src/test/java/com/n11/identity/PasswordEncoderTest.java` - 4 pure-JUnit smoke tests (QUAL-02 pattern)
- `identity-service/build.gradle.kts` - module build with oauth2-resource-server + security starters + Jib
- `settings.gradle.kts` - identity-service added to include block

## Decisions Made

- D-02 enforced: public key derived from private at boot (RSAPrivateCrtKey cast, documented as openssl genrsa CRT assumption — Risk 4)
- D-15 enforced: no oauth2ResourceServer() block in IdentitySecurityConfig — identity-service is signer only
- QUAL-02 pattern: PasswordEncoderTest placed in com.n11.identity package (not com.n11.identity.auth) per CONTEXT D-16 specification; package-private class, no @SpringBootTest
- JwtIssuerService.issue() uses UUID/email/fullName parameters (not a User entity) to keep the method decoupled from JPA entity availability — Plan 03-04 wraps this in UserService

## Deviations from Plan

None — plan executed exactly as written. All 5 files match the exact content prescribed in the plan's `<action>` blocks. Module scaffold (build.gradle.kts, IdentityServiceApplication.java, application.yml) was created in this worktree to enable compilation verification since 03-01 executes in parallel.

## Issues Encountered

- `gradlew` was not executable in the fresh worktree (permission 644 instead of 755). Applied `chmod +x` before running Gradle commands. This is a minor worktree initialization issue, not a code defect.

## User Setup Required

None — no external service configuration required for this plan. The JWT keypair env vars (JWT_PRIVATE_KEY, JWT_KEY_ID) are documented in identity-service/README.md and .env.example (Plan 03-01 deliverable).

## Next Phase Readiness

- Plan 03-04 (AuthController + UserService) can now call `jwtIssuerService.issue(userId, email, fullName, roles)` to get a signed JWT
- Plan 03-06 (gateway JWT validation) can configure `jwk-set-uri: http://identity-service:8081/.well-known/jwks.json`
- Phase 4+ services replicate PasswordEncoderTest as their QUAL-02 smoke test (established pattern)
- No blockers

## Self-Check: PASSED

All created files exist on disk. Both commits (4587c62, b7cf947) are present in git log. `./gradlew :identity-service:compileJava` exits 0. `./gradlew :identity-service:test --tests "*PasswordEncoderTest"` exits 0 with 4 tests passing.

---
*Phase: 03-identity-gateway-auth*
*Completed: 2026-04-29*
