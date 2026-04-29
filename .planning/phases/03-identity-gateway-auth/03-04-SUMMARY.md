---
phase: 03-identity-gateway-auth
plan: 04
subsystem: identity-service
tags: [jpa, spring-data, bcrypt, spring-mvc, validation, turkish-locale, outbox-seam]

# Dependency graph
requires:
  - phase: 03-01
    provides: identity-service Gradle module scaffold
  - phase: 03-02
    provides: V2 DDL (users, roles, user_roles, addresses tables)
  - phase: 03-03
    provides: JwtIssuerService.issue(UUID, String, String, List<String>) + tokenLifetimeSeconds()
provides:
  - User/Role/Address JPA entities mirroring V2 DDL exactly
  - UserRepository (findByEmail, existsByEmail), RoleRepository (findByName)
  - AddressRepository (findByUserIdOrderByCreatedAtDesc, @Modifying clearDefaultForUser)
  - UserEntityTest: 3 Testcontainers-backed @DataJpaTest tests (password_hash column, ManyToMany roles, D-11 partial-unique index enforcement)
  - RegisterRequest/LoginRequest/AuthResponse/UserSummary/UserProfileResponse DTOs with Turkish validation messages
  - UserService: register + login + getProfile orchestration + D-12 outbox seam
  - UserRegistrationOutboxPublisher interface + NoOpUserRegistrationOutboxPublisher fallback
  - AuthController: POST /auth/register, POST /auth/login, GET /auth/me (D-15 X-User-Id pattern)
  - AddressService: listForUser + create with D-11 clearDefaultForUser flip semantics
  - AddressController: GET /addresses, POST /addresses (X-User-Id header resolution)
  - CreateAddressRequest/AddressResponse DTOs with Turkish validation messages
affects:
  - 03-05 (OutboxPublisher wires the real outbox-row save into UserService.register)
  - 03-06 (api-gateway — routes /api/v1/identity/** to identity-service at port 8081)
  - Phase 5+ (address-snapshot pattern documented in D-10)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - D-15: X-User-Id header resolution — AuthController.me + AddressController both read HttpServletRequest header, throw 401 if absent/blank, 400 if not valid UUID
    - D-11 clearDefaultForUser: @Modifying JPQL UPDATE in AddressRepository + called before insert in AddressService.create(@Transactional)
    - D-12 outbox seam: outboxPublisher.publishRegistered(saved) called inside UserService.register @Transactional; NoOpUserRegistrationOutboxPublisher as @ConditionalOnMissingBean fallback
    - D-07 ROLE_USER hardcode: UserService.register calls roleRepository.findByName(Role.NAME_USER); no role field in RegisterRequest
    - Turkish validation messages: all @NotBlank/@Email/@Pattern/@Size messages in Turkish (LOC requirement)
    - Testcontainers + @AutoConfigureTestDatabase(replace=NONE) for @DataJpaTest when H2 cannot enforce partial-index WHERE

key-files:
  created:
    - identity-service/src/main/java/com/n11/identity/user/User.java
    - identity-service/src/main/java/com/n11/identity/user/Role.java
    - identity-service/src/main/java/com/n11/identity/user/UserRepository.java
    - identity-service/src/main/java/com/n11/identity/user/RoleRepository.java
    - identity-service/src/main/java/com/n11/identity/user/UserService.java
    - identity-service/src/main/java/com/n11/identity/user/UserRegistrationOutboxPublisher.java
    - identity-service/src/main/java/com/n11/identity/user/NoOpUserRegistrationOutboxPublisher.java
    - identity-service/src/main/java/com/n11/identity/auth/AuthController.java
    - identity-service/src/main/java/com/n11/identity/auth/dto/RegisterRequest.java
    - identity-service/src/main/java/com/n11/identity/auth/dto/LoginRequest.java
    - identity-service/src/main/java/com/n11/identity/auth/dto/AuthResponse.java
    - identity-service/src/main/java/com/n11/identity/auth/dto/UserSummary.java
    - identity-service/src/main/java/com/n11/identity/auth/dto/UserProfileResponse.java
    - identity-service/src/main/java/com/n11/identity/address/Address.java
    - identity-service/src/main/java/com/n11/identity/address/AddressRepository.java
    - identity-service/src/main/java/com/n11/identity/address/AddressService.java
    - identity-service/src/main/java/com/n11/identity/address/AddressController.java
    - identity-service/src/main/java/com/n11/identity/address/dto/CreateAddressRequest.java
    - identity-service/src/main/java/com/n11/identity/address/dto/AddressResponse.java
    - identity-service/src/test/java/com/n11/identity/user/UserEntityTest.java
    - identity-service/src/test/resources/application.yml
  modified:
    - identity-service/build.gradle.kts (added H2 + testcontainers:junit-jupiter test deps)

key-decisions:
  - "D-15 enforced: AuthController.me + AddressController both read X-User-Id from HttpServletRequest header, not from JWT claims"
  - "D-11 enforced: AddressService.create calls addressRepository.clearDefaultForUser(userId) BEFORE insert when isDefault=true"
  - "D-07 enforced: UserService.register hardcodes Role.NAME_USER; RegisterRequest has no roles field"
  - "D-12 seam: NoOpUserRegistrationOutboxPublisher is @ConditionalOnMissingBean(name='outboxBackedUserRegistrationOutboxPublisher') — Plan 03-05 wires real impl with that exact bean name"
  - "Test fallback: H2 does not support partial-index WHERE clause; UserEntityTest uses @AutoConfigureTestDatabase(replace=NONE) + Testcontainers pgvector:pg16 for full Flyway-backed schema"

# Metrics
duration: ~13 min
completed: 2026-04-29
---

# Phase 03 Plan 04: User REST Surface (Entities + Controllers + Services) Summary

**JPA entities mirroring V2 DDL, register/login/me/addresses REST endpoints with Turkish validation, D-12 outbox seam, and 3-test Testcontainers-backed entity test suite establishing D-11 partial-unique index enforcement**

## Performance

- **Duration:** ~13 min
- **Started:** 2026-04-29T12:49:43Z
- **Completed:** 2026-04-29T13:03:02Z
- **Tasks:** 3 completed
- **Files modified:** 21 (19 Java files + 1 test resource + build.gradle.kts)

## Accomplishments

### Task 1: JPA Entities + Repositories

- `User` @Entity: id (UUID), email (UNIQUE), password_hash, full_name, created_at + ManyToMany roles via user_roles join table (EAGER fetch — max 2 roles per user)
- `Role` @Entity: id (INT), name (UNIQUE) + `NAME_USER = "ROLE_USER"` and `NAME_ADMIN = "ROLE_ADMIN"` constants
- `Address` @Entity: full D-09 Turkish address field set (il, ilce, mahalle, street_line, postal_code CHAR(5), is_default BOOLEAN)
- `UserRepository`: `findByEmail` + `existsByEmail` derived queries
- `RoleRepository`: `findByName` derived query
- `AddressRepository`: `findByUserIdOrderByCreatedAtDesc` + `@Modifying` JPQL `clearDefaultForUser` (D-11)
- `UserEntityTest`: 3 @DataJpaTest tests via Testcontainers pgvector:pg16 (H2 fallback rejected due to partial-index WHERE syntax limitation)

### Task 2: DTOs + UserService + AuthController

- `RegisterRequest`: Turkish @NotBlank + @Email + @Pattern (8-char, letter+digit floor per CD-01)
- `LoginRequest`: Turkish @NotBlank + @Email
- `AuthResponse` (D-14): accessToken + tokenType="Bearer" + expiresIn=86400 + user (UserSummary)
- `UserProfileResponse`: id/email/fullName/roles/createdAt (password_hash excluded — T-3-11 mitigation)
- `UserService.register`: @Transactional, existsByEmail check (409 CONFLICT), ROLE_USER assignment (D-07), BCrypt encode, outboxPublisher.publishRegistered() call (D-12 seam)
- `UserService.login`: @Transactional readOnly, BCrypt matches, 401 on mismatch
- `UserService.getProfile`: @Transactional readOnly, 404 if not found
- `UserRegistrationOutboxPublisher`: interface for D-12 outbox seam
- `NoOpUserRegistrationOutboxPublisher`: @ConditionalOnMissingBean(name="outboxBackedUserRegistrationOutboxPublisher") fallback with WARN log
- `AuthController`: POST /auth/register (201), POST /auth/login (200), GET /auth/me (D-15 X-User-Id)

### Task 3: Address DTOs + AddressService + AddressController

- `CreateAddressRequest`: Turkish @NotBlank/@Size/@Pattern for all 9 fields; postal code `^\d{5}$` pattern
- `AddressResponse`: read-model record with all address columns
- `AddressService.listForUser`: @Transactional readOnly, delegates to findByUserIdOrderByCreatedAtDesc
- `AddressService.create`: @Transactional, D-11 default-flip (clearDefaultForUser before insert)
- `AddressController`: GET /addresses + POST /addresses — X-User-Id resolution mirrors AuthController pattern (D-15)

## Task Commits

1. **Task 1: JPA entities + repositories (User, Role, Address)** - `9307ba2` (feat)
2. **Task 2: DTOs + UserService + AuthController** - `fb216c6` (feat)
3. **Task 3: Address DTOs + AddressService + AddressController** - `c42705c` (feat)

## Files Created/Modified

- `identity-service/src/main/java/com/n11/identity/user/User.java`
- `identity-service/src/main/java/com/n11/identity/user/Role.java`
- `identity-service/src/main/java/com/n11/identity/user/UserRepository.java`
- `identity-service/src/main/java/com/n11/identity/user/RoleRepository.java`
- `identity-service/src/main/java/com/n11/identity/user/UserService.java`
- `identity-service/src/main/java/com/n11/identity/user/UserRegistrationOutboxPublisher.java`
- `identity-service/src/main/java/com/n11/identity/user/NoOpUserRegistrationOutboxPublisher.java`
- `identity-service/src/main/java/com/n11/identity/auth/AuthController.java`
- `identity-service/src/main/java/com/n11/identity/auth/dto/RegisterRequest.java`
- `identity-service/src/main/java/com/n11/identity/auth/dto/LoginRequest.java`
- `identity-service/src/main/java/com/n11/identity/auth/dto/AuthResponse.java`
- `identity-service/src/main/java/com/n11/identity/auth/dto/UserSummary.java`
- `identity-service/src/main/java/com/n11/identity/auth/dto/UserProfileResponse.java`
- `identity-service/src/main/java/com/n11/identity/address/Address.java`
- `identity-service/src/main/java/com/n11/identity/address/AddressRepository.java`
- `identity-service/src/main/java/com/n11/identity/address/AddressService.java`
- `identity-service/src/main/java/com/n11/identity/address/AddressController.java`
- `identity-service/src/main/java/com/n11/identity/address/dto/CreateAddressRequest.java`
- `identity-service/src/main/java/com/n11/identity/address/dto/AddressResponse.java`
- `identity-service/src/test/java/com/n11/identity/user/UserEntityTest.java`
- `identity-service/src/test/resources/application.yml` (test config: config-server disabled, Flyway disabled for @DataJpaTest)
- `identity-service/build.gradle.kts` (added H2 + testcontainers:junit-jupiter test deps)

## Decisions Made

- D-15 enforced: identity-service does NOT decode JWTs; both AuthController.me and AddressController extract user identity from the gateway-injected X-User-Id header
- D-11 enforced: AddressService.create uses @Transactional and calls clearDefaultForUser before saving new default address
- D-07 enforced: UserService.register hardcodes Role.NAME_USER; RegisterRequest has no roles field at all (T-3-12 mitigation)
- D-12 seam: NoOpUserRegistrationOutboxPublisher with @ConditionalOnMissingBean(name="outboxBackedUserRegistrationOutboxPublisher") — Plan 03-05 registers the real impl with that exact bean name

## Deviations from Plan

### Auto-fixed Issues (Rule 3 — Blocking)

**1. [Rule 3 - Blocking] H2 missing from test dependencies**
- **Found during:** Task 1, test run attempt
- **Issue:** `@DataJpaTest` requires an embedded database; identity-service `build.gradle.kts` had no `com.h2database:h2` test dependency
- **Fix:** Added `testRuntimeOnly("com.h2database:h2")` to `build.gradle.kts`
- **Files modified:** `identity-service/build.gradle.kts`
- **Commit:** 9307ba2

**2. [Rule 3 - Blocking] Config-server import fails in test context**
- **Found during:** Task 1, first test run attempt
- **Issue:** `application.yml` has `spring.config.import: configserver:...?fail-fast=true` which causes `@DataJpaTest` context to fail (config-server not running in test)
- **Fix:** Created `identity-service/src/test/resources/application.yml` with `spring.config.import: "optional:configserver:"` and `spring.flyway.enabled: false`
- **Files modified:** `identity-service/src/test/resources/application.yml` (new file)
- **Commit:** 9307ba2

**3. [Rule 3 - Blocking] H2 does not support partial-index WHERE clause**
- **Found during:** Task 1, `partialUniqueDefaultAddressIsEnforced` test
- **Issue:** H2 in-memory DB does not support `CREATE UNIQUE INDEX ... WHERE is_default = TRUE` (partial indexes with WHERE clause); the plan anticipated this as a possible fallback
- **Fix:** Changed `UserEntityTest` to use `@AutoConfigureTestDatabase(replace = NONE)` + Testcontainers `pgvector/pgvector:pg16` as the plan's specified fallback. Added `testImplementation("org.testcontainers:junit-jupiter")` to build.gradle.kts for `@Testcontainers` + `@Container` JUnit 5 annotations
- **Files modified:** `UserEntityTest.java`, `build.gradle.kts`
- **Commit:** 9307ba2

**4. [Rule 1 - Bug] Postgres transaction abort after constraint violation**
- **Found during:** Task 1, `partialUniqueDefaultAddressIsEnforced` test
- **Issue:** After the expected `DataIntegrityViolationException` from the second `saveAndFlush`, the Postgres transaction is in aborted state and subsequent SQL in the same test transaction fails with "current transaction is aborted, commands ignored until end of transaction block"
- **Fix:** Removed the post-exception "Sanity check" (non-default address save + list assertion) from `partialUniqueDefaultAddressIsEnforced`. The test now solely asserts that a second `is_default=true` address for the same user throws `DataIntegrityViolationException`. This is the correct Postgres behavior and sufficient to validate D-11
- **Files modified:** `UserEntityTest.java`
- **Commit:** 9307ba2

## Known Stubs

- `NoOpUserRegistrationOutboxPublisher.publishRegistered()` — intentional no-op placeholder. Plan 03-05 wires the real `OutboxRepository`-backed implementation. This is NOT a UI-visible stub; the outbox is internal infrastructure. Documented by design: the WARN log message `"outbox.publisher.fallback: skipping user.registered for userId=..."` surfaces in operator logs if Plan 03-05 is not deployed.

## Threat Surface Scan

All threats from the plan's threat model are mitigated:

| Threat ID | Mitigation Applied |
|-----------|--------------------|
| T-3-02 (Spoofed X-User-Id) | Identity-service internal-only (no host-port mapping per Plan 03-01); Plan 03-06's GatewayHeaderInjectionFilter will strip inbound X-User-Id |
| T-3-11 (password_hash in /auth/me) | UserProfileResponse record has 5 fields: id/email/fullName/roles/createdAt — no passwordHash field (compile-time guarantee) |
| T-3-12 (ROLE_ADMIN self-promotion) | UserService.register hardcodes Role.NAME_USER; RegisterRequest has no roles field |

No new threat surface introduced beyond the plan's threat model.

## Self-Check: PASSED

All 19 created Java files exist on disk. All 3 task commits are present in git log (9307ba2, fb216c6, c42705c). `./gradlew :identity-service:compileJava --no-daemon` exits 0. `./gradlew :identity-service:test --tests "*EntityTest" --no-daemon` exits 0 (3 tests pass). `./gradlew :identity-service:test --tests "*PasswordEncoderTest" --no-daemon` exits 0 (4 tests pass).

---
*Phase: 03-identity-gateway-auth*
*Completed: 2026-04-29*
