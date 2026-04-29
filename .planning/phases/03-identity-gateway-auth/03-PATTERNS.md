# Phase 3: Identity + Gateway Auth - Pattern Map

**Mapped:** 2026-04-29
**Files analyzed:** 22 new/modified files
**Analogs found:** 20 / 22 (2 have no codebase analog — use RESEARCH.md shapes)

---

## File Classification

| New / Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---------------------|------|-----------|----------------|---------------|
| `identity-service/build.gradle.kts` | config | CRUD | `service-template/build.gradle.kts` | exact |
| `identity-service/src/main/java/com/n11/identity/IdentityServiceApplication.java` | config | request-response | `service-template/skeleton/src-main/java/com/n11/__SERVICE_PACKAGE__/ServiceApplication.java.template` | exact |
| `identity-service/src/main/resources/application.yml` | config | request-response | `service-template/skeleton/src-main/resources/application.yml.template` | exact |
| `identity-service/src/main/resources/logback-spring.xml` | config | — | `service-template/skeleton/src-main/resources/logback-spring.xml.template` | exact (copy verbatim) |
| `identity-service/src/main/resources/db/migration/V1__init_users_addresses.sql` | migration | CRUD | `service-template/skeleton/src-main/resources/db/migration/V1__init_processed_events.sql.template` | role-match (extend pattern) |
| `identity-service/src/main/resources/db/migration/V2__seed_admin.sql` | migration | CRUD | `service-template/skeleton/src-main/resources/db/migration/V1__init_processed_events.sql.template` | role-match (Flyway convention) |
| `identity-service/src/main/resources/db/migration/V3__init_outbox.sql` | migration | event-driven | `service-template/skeleton/src-main/resources/db/migration/V1__init_processed_events.sql.template` | role-match |
| `identity-service/src/main/java/com/n11/identity/auth/AuthController.java` | controller | request-response | `service-template/skeleton/src-main/java/com/n11/__SERVICE_PACKAGE__/health/SampleHealthController.java.template` | role-match |
| `identity-service/src/main/java/com/n11/identity/auth/JwtIssuerService.java` | service | request-response | no analog — use RESEARCH.md §JWT Token Issuance | no analog |
| `identity-service/src/main/java/com/n11/identity/auth/JwtConfig.java` | config | request-response | no analog — use RESEARCH.md §RS256 Keypair Loading | no analog |
| `identity-service/src/main/java/com/n11/identity/auth/JwksController.java` | controller | request-response | `service-template/skeleton/src-main/java/com/n11/__SERVICE_PACKAGE__/health/SampleHealthController.java.template` | role-match |
| `identity-service/src/main/java/com/n11/identity/auth/IdentitySecurityConfig.java` | config | request-response | `api-gateway/src/main/java/com/n11/gateway/SecurityConfig.java` (servlet variant differs) | partial-match |
| `identity-service/src/main/java/com/n11/identity/address/AddressController.java` | controller | CRUD | `service-template/skeleton/src-main/java/com/n11/__SERVICE_PACKAGE__/health/SampleHealthController.java.template` | role-match |
| `identity-service/src/main/java/com/n11/identity/outbox/OutboxPoller.java` | utility | event-driven | `common-events/src/main/java/com/n11/events/RabbitRetryConfig.java` | partial-match |
| `identity-service/src/test/java/com/n11/identity/PasswordEncoderTest.java` | test | — | `common-error/src/test/java/com/n11/error/ProblemDetailControllerAdviceTest.java` | role-match |
| `config-server/src/main/resources/config/identity-service.yml` | config | — | `config-server/src/main/resources/config/service-template.yml` | exact |
| `.planning/saga-contracts/user-registered.schema.json` | config | event-driven | `.planning/saga-contracts/order-created.schema.json` | exact |
| `api-gateway/src/main/java/com/n11/gateway/SecurityConfig.java` (REPLACE) | config | request-response | `api-gateway/src/main/java/com/n11/gateway/SecurityConfig.java` (current file) | self-analog (structural replacement) |
| `api-gateway/src/main/java/com/n11/gateway/GatewayHeaderInjectionFilter.java` (REPLACE) | middleware | request-response | `api-gateway/src/main/java/com/n11/gateway/GatewayHeaderInjectionFilter.java` (current stub) | self-analog (structural replacement) |
| `config-server/src/main/resources/config/api-gateway.yml` (MODIFY) | config | — | `config-server/src/main/resources/config/api-gateway.yml` (current file) | self-analog (additive) |
| `settings.gradle.kts` (MODIFY) | config | — | `settings.gradle.kts` (current file) | self-analog (additive) |
| `docker-compose.yml` (MODIFY) | config | — | `docker-compose.yml` (current `api-gateway` service block) | self-analog (additive) |

---

## Pattern Assignments

### `identity-service/build.gradle.kts` (config)

**Analog:** `service-template/build.gradle.kts` (lines 1–43)

**Core pattern — copy verbatim then adapt** (lines 1–43):
```kotlin
plugins {
    id("org.springframework.boot")
}

dependencies {
    // Boot core
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-amqp")

    // Spring Cloud — discovery + centralized config
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")
    implementation("org.springframework.cloud:spring-cloud-starter-config")

    // Springdoc per-service (QUAL-01 per-service half)
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.17")

    // Flyway 12.5 (D-11.4, ARCH-10 non-owner half)
    implementation("org.flywaydb:flyway-core:12.5.0")
    implementation("org.flywaydb:flyway-database-postgresql:12.5.0")

    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("net.logstash.logback:logstash-logback-encoder:8.0")

    implementation(project(":common-error"))
    implementation(project(":common-logging"))
    implementation(project(":common-events"))

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveBaseName.set("service-template")  // <-- rename to "identity-service"
}
```

**Adaptation notes:**
- Add `implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")` for `NimbusJwtEncoder`, `JwtClaimsSet`, `JwsHeader` (RESEARCH.md §Identity-Service Dependencies).
- Add `implementation("org.springframework.boot:spring-boot-starter-security")` to bring `BCryptPasswordEncoder`.
- Change `archiveBaseName.set("identity-service")`.
- Add Testcontainers for integration tests: `testImplementation("org.springframework.boot:spring-boot-testcontainers")` + `testImplementation("org.testcontainers:postgresql")`.

---

### `identity-service/src/main/java/com/n11/identity/IdentityServiceApplication.java` (config)

**Analog:** `service-template/skeleton/src-main/java/com/n11/__SERVICE_PACKAGE__/ServiceApplication.java.template` (lines 1–14)

**Core pattern — substitute tokens** (lines 1–14):
```java
package com.n11.__SERVICE_PACKAGE__;  // → com.n11.identity

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = "com.n11")
@EnableDiscoveryClient
public class ServiceApplication {   // → IdentityServiceApplication

    public static void main(String[] args) {
        SpringApplication.run(ServiceApplication.class, args);  // → IdentityServiceApplication.class
    }
}
```

**Adaptation notes:**
- Add `@EnableScheduling` on the class (required by `OutboxPoller`'s `@Scheduled` annotation).
- Token substitutions per skeleton README: `__SERVICE_PACKAGE__` → `identity`, `ServiceApplication` → `IdentityServiceApplication`.

---

### `identity-service/src/main/resources/application.yml` (config)

**Analog:** `service-template/skeleton/src-main/resources/application.yml.template` (lines 1–6) + `api-gateway/src/main/resources/application.yml` (lines 1–25) for commentary style

**Core pattern** (template lines 1–6):
```yaml
spring:
  application:
    name: <SERVICE_NAME>   # → identity-service
  config:
    import: optional:configserver:http://config-server:8888?fail-fast=true&max-attempts=10&max-interval=1500&multiplier=1.2&initial-interval=1100
```

**Adaptation notes:**
- Per skeleton README "Production posture" section: strip `optional:` so a missing config-server fails loud at runtime. Keep `optional:` for CI-offline builds if CI does not spin up config-server.
- Substitute `<SERVICE_NAME>` → `identity-service`.
- Do NOT add any other keys here — all runtime config (port, datasource, Flyway, JWT) flows from config-server's `identity-service.yml`.

---

### `identity-service/src/main/resources/logback-spring.xml` (config)

**Analog:** `service-template/skeleton/src-main/resources/logback-spring.xml.template` (lines 1–32)

**Copy verbatim.** No token substitutions needed — `${appName}` reads from `spring.application.name` at runtime.

Key points from the file (lines 7–9):
```xml
<!-- MDC allowlist: only correlationId + userId reach the log line.
     PII fields (email) MUST NOT be added here per T-01-04. -->
<includeMdcKeyName>correlationId</includeMdcKeyName>
<includeMdcKeyName>userId</includeMdcKeyName>
```

---

### `identity-service/src/main/resources/db/migration/V1__init_users_addresses.sql` (migration, CRUD)

**Analog:** `service-template/skeleton/src-main/resources/db/migration/V1__init_processed_events.sql.template` (lines 1–25) for header convention and comment block style

**Header convention to copy** (template lines 1–14):
```sql
-- V1__init_users_addresses.sql
-- Identity schema DDL: users, roles, user_roles, addresses, processed_events.
-- processed_events inbox inherited from service-template pattern (every service carries it).
--
-- Runs against `identity` schema (Flyway default-schema = identity per identity-service.yml).
-- NO `CREATE SCHEMA` — schema already created by infra/postgres/init.sh as superuser.
-- create-schemas: false in Flyway config ensures no permission-denied error here.
```

**`processed_events` table to inherit verbatim** (template lines 16–24):
```sql
CREATE TABLE processed_events (
    event_id      UUID         PRIMARY KEY,
    consumer      VARCHAR(64)  NOT NULL,
    event_type    VARCHAR(128) NOT NULL,
    processed_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_processed_events_consumer_processed_at
    ON processed_events (consumer, processed_at);
```

**New DDL to add after (per D-09 / CD-03):**
```sql
CREATE TABLE users (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email         TEXT         NOT NULL UNIQUE,
    password_hash TEXT         NOT NULL,
    full_name     TEXT         NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_users_email ON users (email);

CREATE TABLE roles (
    id   INT  PRIMARY KEY,
    name TEXT NOT NULL UNIQUE
);
INSERT INTO roles (id, name) VALUES (1, 'ROLE_USER'), (2, 'ROLE_ADMIN');

CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id INT  NOT NULL REFERENCES roles(id),
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE addresses (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title          VARCHAR(50)  NOT NULL,
    recipient_name VARCHAR(120) NOT NULL,
    phone          VARCHAR(20)  NOT NULL,
    il             VARCHAR(50)  NOT NULL,
    ilce           VARCHAR(80)  NOT NULL,
    mahalle        VARCHAR(120),
    street_line    VARCHAR(255) NOT NULL,
    postal_code    CHAR(5)      NOT NULL,
    is_default     BOOLEAN      NOT NULL DEFAULT false,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_addresses_user_id ON addresses (user_id);
-- D-11: partial unique index — at most one default per user
CREATE UNIQUE INDEX idx_addresses_user_default
    ON addresses (user_id) WHERE is_default;
```

---

### `identity-service/src/main/resources/db/migration/V2__seed_admin.sql` (migration)

**Analog:** `service-template/skeleton/src-main/resources/db/migration/V1__init_processed_events.sql.template` for Flyway comment convention

**Core pattern** (RESEARCH.md §Flyway Placeholder Syntax, lines 544–557):
```sql
-- V2__seed_admin.sql
-- Seeds one admin user via Flyway placeholders (D-06).
-- Env vars ADMIN_SEED_EMAIL + ADMIN_SEED_PASSWORD_HASH must be set in .env.
-- Generate hash: htpasswd -bnBC 10 "" "password" | tr -d ':\n'

INSERT INTO users (id, email, password_hash, full_name, created_at)
VALUES (gen_random_uuid(), '${adminSeedEmail}', '${adminSeedPasswordHash}', 'Admin', now())
ON CONFLICT (email) DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
     JOIN roles r ON r.name = 'ROLE_ADMIN'
WHERE u.email = '${adminSeedEmail}'
ON CONFLICT DO NOTHING;
```

**Adaptation notes:**
- Flyway placeholder YAML block belongs in `config-server/src/main/resources/config/identity-service.yml` (not in the SQL file itself):
  ```yaml
  spring.flyway.placeholders.adminSeedEmail: ${ADMIN_SEED_EMAIL}
  spring.flyway.placeholders.adminSeedPasswordHash: ${ADMIN_SEED_PASSWORD_HASH}
  ```

---

### `identity-service/src/main/resources/db/migration/V3__init_outbox.sql` (migration, event-driven)

**Analog:** `service-template/skeleton/src-main/resources/db/migration/V1__init_processed_events.sql.template` for Flyway convention

**Core pattern** (RESEARCH.md §Outbox Pattern DDL, lines 422–434):
```sql
-- V3__init_outbox.sql
-- Transactional outbox table for user.registered event (D-12).
-- Per saga-contracts.md §5.1.

CREATE TABLE outbox (
    id          UUID         PRIMARY KEY,
    aggregate   TEXT         NOT NULL,
    event_type  TEXT         NOT NULL,
    payload     JSONB        NOT NULL,
    occurred_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    sent_at     TIMESTAMPTZ  NULL
);

CREATE INDEX outbox_unsent_idx ON outbox (occurred_at) WHERE sent_at IS NULL;
```

---

### `identity-service/src/main/java/com/n11/identity/auth/AuthController.java` (controller, request-response)

**Analog:** `service-template/skeleton/src-main/java/com/n11/__SERVICE_PACKAGE__/health/SampleHealthController.java.template` (lines 1–25) for controller shape + `common-error/src/main/java/com/n11/error/ProblemDetailControllerAdvice.java` for error handling convention

**Imports pattern from SampleHealthController** (lines 1–9):
```java
package com.n11.identity.auth;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
// No Spring Security imports in AuthController — D-15 means /auth/me reads the
// gateway-injected X-User-Id header, not a SecurityContext.
```

**Core controller pattern** (adapted from SampleHealthController + ProblemDetailControllerAdvice shape):
```java
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return userService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return userService.login(request);
    }

    @GetMapping("/me")
    public UserProfileResponse me(HttpServletRequest request) {
        // D-15: read X-User-Id injected by the gateway — zero JWT decoding here
        String userId = request.getHeader("X-User-Id");
        if (userId == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return userService.getProfile(UUID.fromString(userId));
    }
}
```

**Error handling:** `ProblemDetailControllerAdvice` from `common-error` is auto-applied via Spring Boot autoconfiguration (Plan 01-04). Do NOT add local `@ExceptionHandler` — throw `ResponseStatusException` or `MethodArgumentNotValidException` and let the advice handle it. For `auth/email-taken`, throw `new ResponseStatusException(HttpStatus.CONFLICT, "E-posta adresi zaten kayıtlı")`.

**Validation messages in Turkish** (CD-01 / LOC requirement):
```java
public record RegisterRequest(
    @Email(message = "Geçerli bir e-posta adresi giriniz")
    @NotBlank(message = "E-posta adresi zorunludur")
    String email,

    @NotBlank(message = "Şifre zorunludur")
    @Pattern(regexp = "^(?=.*[a-zA-Z])(?=.*\\d).{8,}$",
             message = "Şifre en az 8 karakter, bir harf ve bir rakam içermelidir")
    String password,

    @NotBlank(message = "Ad Soyad zorunludur")
    String fullName
) {}
```

---

### `identity-service/src/main/java/com/n11/identity/auth/JwtConfig.java` (config, no codebase analog)

**Analog:** RESEARCH.md §RS256 Keypair Loading (lines 264–313)

This is a new pattern in the codebase. Copy the complete `JwtConfig` bean cluster from RESEARCH.md:

```java
// Key beans to define in JwtConfig (servlet stack, NOT reactive):
// 1. RSAPrivateKey rsaPrivateKey(@Value("${jwt.private-key}") String pemKey)
//    — RsaKeyConverters.pkcs8().convert(new ByteArrayInputStream(keyBytes))
// 2. RSAPublicKey rsaPublicKey(RSAPrivateKey privateKey)
//    — derives via RSAPrivateCrtKey cast + RSAPublicKeySpec
// 3. RSAKey rsaJwk(RSAPublicKey, RSAPrivateKey, @Value("${jwt.key-id}") String keyId)
//    — new RSAKey.Builder(publicKey).privateKey(...).keyID(...).algorithm(RS256).keyUse(SIGNATURE)
// 4. JWKSource<SecurityContext> jwkSource(RSAKey rsaJwk)
//    — lambda: (jwkSelector, context) -> jwkSelector.select(jwkSet)
// 5. JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource)
//    — new NimbusJwtEncoder(jwkSource)
```

**Required imports (servlet, non-reactive):**
- `org.springframework.security.converter.RsaKeyConverters`
- `com.nimbusds.jose.jwk.RSAKey`, `JWKSet`, `JWKSource`
- `com.nimbusds.jose.JWSAlgorithm`, `com.nimbusds.jose.jwk.KeyUse`
- `org.springframework.security.oauth2.jwt.NimbusJwtEncoder`
- `com.nimbusds.jose.proc.SecurityContext`
- `java.security.interfaces.RSAPrivateCrtKey`, `RSAPrivateKey`, `RSAPublicKey`
- `java.security.spec.RSAPublicKeySpec`

---

### `identity-service/src/main/java/com/n11/identity/auth/JwtIssuerService.java` (service, no codebase analog)

**Analog:** RESEARCH.md §JWT Token Issuance (lines 320–340)

```java
// Core issuance pattern from RESEARCH.md:
JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
JwtClaimsSet claims = JwtClaimsSet.builder()
        .issuer("n11-identity")
        .subject(user.getId().toString())
        .issuedAt(now)
        .expiresAt(now.plusSeconds(86400))   // D-01: 24h
        .claim("roles", user.getRoles())      // D-05: ["ROLE_USER"]
        .claim("email", user.getEmail())
        .claim("fullName", user.getFullName())
        .build();
return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
```

**Required imports (servlet, non-reactive):**
- `org.springframework.security.oauth2.jwt.JwtEncoder`
- `org.springframework.security.oauth2.jwt.JwtClaimsSet`
- `org.springframework.security.oauth2.jwt.JwtEncoderParameters`
- `org.springframework.security.oauth2.jose.jws.SignatureAlgorithm`
- `org.springframework.security.oauth2.jwt.JwsHeader`

---

### `identity-service/src/main/java/com/n11/identity/auth/JwksController.java` (controller, request-response)

**Analog:** `service-template/skeleton/src-main/java/com/n11/__SERVICE_PACKAGE__/health/SampleHealthController.java.template` (lines 12–25) for `@RestController` + `@GetMapping` shape

**Core pattern** (RESEARCH.md §JWKS Endpoint, lines 349–360):
```java
@RestController
public class JwksController {

    private final RSAKey rsaJwk;   // injected — holds both private + public

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        // toPublicJWK() strips private key material — CRITICAL (never serve raw RSAKey)
        JWKSet publicSet = new JWKSet(rsaJwk.toPublicJWK());
        return publicSet.toJSONObject();
    }
}
```

---

### `identity-service/src/main/java/com/n11/identity/auth/IdentitySecurityConfig.java` (config, servlet)

**Analog:** `api-gateway/src/main/java/com/n11/gateway/SecurityConfig.java` (lines 34–58) — note the gateway uses reactive `ServerHttpSecurity`; identity-service uses **servlet** `HttpSecurity`. The structural shape is the same; the import namespace differs.

**Imports to use (servlet, NOT reactive):**
```java
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
```

**Core pattern** (RESEARCH.md §Identity-Service SecurityConfig, lines 403–413):
```java
@Configuration
@EnableWebSecurity
public class IdentitySecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(ex -> ex.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);   // D-07 cost 10
    }
}
```

**Adaptation notes:**
- This is a minimal permit-all chain. Identity-service does NOT validate JWTs (D-15); all endpoint security is the gateway's job.
- `BCryptPasswordEncoder` bean lives here (cost 10 per AUTH-07).

---

### `identity-service/src/main/java/com/n11/identity/address/AddressController.java` + service + repository (controller, CRUD)

**Analog:** `service-template/skeleton/src-main/java/com/n11/__SERVICE_PACKAGE__/health/SampleHealthController.java.template` for controller shape; `common-error/src/main/java/com/n11/error/ProblemDetailControllerAdvice.java` for error handling

**Imports pattern:**
```java
package com.n11.identity.address;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.UUID;
```

**Core CRUD pattern:**
```java
@RestController
@RequestMapping("/addresses")
public class AddressController {

    private final AddressService addressService;

    @GetMapping
    public List<AddressResponse> list(HttpServletRequest req) {
        UUID userId = resolveUserId(req);
        return addressService.listForUser(userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AddressResponse create(HttpServletRequest req,
                                  @Valid @RequestBody CreateAddressRequest body) {
        UUID userId = resolveUserId(req);
        return addressService.create(userId, body);
    }

    private UUID resolveUserId(HttpServletRequest req) {
        String header = req.getHeader("X-User-Id");
        if (header == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return UUID.fromString(header);
    }
}
```

**D-11 is_default flip — transactional semantics in AddressService:**
```java
@Transactional
public AddressResponse create(UUID userId, CreateAddressRequest req) {
    if (req.isDefault()) {
        // Flip any existing default to false before inserting new default
        addressRepository.clearDefaultForUser(userId);
    }
    Address saved = addressRepository.save(buildEntity(userId, req));
    return toResponse(saved);
}
```

---

### `identity-service/src/main/java/com/n11/identity/outbox/OutboxPoller.java` (utility, event-driven)

**Analog:** `common-events/src/main/java/com/n11/events/RabbitRetryConfig.java` (lines 27–67) for RabbitMQ wiring pattern; `common-logging/src/main/java/com/n11/logging/CorrelationIdFilter.java` for `@Scheduled` + MDC pattern reference

**Imports pattern:**
```java
package com.n11.identity.outbox;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
```

**Core poller pattern** (RESEARCH.md §Outbox Poller, lines 453–475):
```java
@Component
public class OutboxPoller {

    private final OutboxRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;

    @Scheduled(fixedDelay = 5000)   // CD-04: every 5 seconds
    @Transactional
    public void poll() {
        List<OutboxEvent> unsent = outboxRepository.findUnsentBatch(100);  // batch 100
        for (OutboxEvent event : unsent) {
            rabbitTemplate.convertAndSend(
                    event.getAggregate() + ".tx",   // "identity.tx"
                    event.getEventType(),            // "user.registered"
                    event.getPayload()
            );
            event.setSentAt(Instant.now());
            outboxRepository.save(event);           // mark sent in same TX
        }
    }
}
```

**Repository query pattern** (must use `nativeQuery = true` for `FOR UPDATE SKIP LOCKED`):
```java
@Query(value = "SELECT * FROM outbox WHERE sent_at IS NULL ORDER BY occurred_at LIMIT :batchSize FOR UPDATE SKIP LOCKED",
       nativeQuery = true)
List<OutboxEvent> findUnsentBatch(@Param("batchSize") int batchSize);
```

**RabbitMQ exchange declaration bean** (add to a `@Configuration` class, e.g., `RabbitConfig.java`):
```java
@Bean
public TopicExchange identityExchange() {
    // D-12: identity.tx exchange (topic, durable). No queue binding yet — Phase 7 adds it.
    return ExchangeBuilder.topicExchange("identity.tx").durable(true).build();
}
```

**Adaptation notes:**
- `@EnableScheduling` must be on `IdentityServiceApplication` (noted in that file's pattern above).
- The `RabbitRetryConfig` bean from `common-events` provides the `rabbitListenerContainerFactory` and DLX retry interceptor. Identity-service inherits it via `implementation(project(":common-events"))` but does NOT use the listener factory (no consumer in Phase 3).

---

### `identity-service/src/test/java/com/n11/identity/PasswordEncoderTest.java` (test)

**Analog:** `common-error/src/test/java/com/n11/error/ProblemDetailControllerAdviceTest.java` for pure-JUnit test structure (no Spring context, no Testcontainers)

**Core pattern** (D-16 smoke test — pure JUnit, no Spring context):
```java
package com.n11.identity;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordEncoderTest {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

    @Test
    void encodedPasswordMatchesOriginal() {
        String raw = "TestPassword1";
        String hashed = encoder.encode(raw);
        assertThat(encoder.matches(raw, hashed)).isTrue();
    }

    @Test
    void twoEncodesOfSamePasswordAreDistinct() {
        String raw = "TestPassword1";
        assertThat(encoder.encode(raw)).isNotEqualTo(encoder.encode(raw));
    }

    @Test
    void costIsExactlyTen() {
        String hashed = encoder.encode("any");
        // BCrypt hash format: $2a$<cost>$...
        assertThat(hashed).startsWith("$2a$10$");
    }
}
```

**Adaptation notes:**
- Run with: `./gradlew :identity-service:test --tests "*PasswordEncoderTest"` (< 5s).
- No `@SpringBootTest`, no `@ExtendWith(SpringExtension.class)` — pure instantiation.

---

### `config-server/src/main/resources/config/identity-service.yml` (config)

**Analog:** `config-server/src/main/resources/config/service-template.yml` (lines 1–74) — exact shape to copy and override per-service keys

**Full pattern** (copy `service-template.yml` and override):
```yaml
server:
  port: 8081    # D-21: identity-service port

eureka:
  client:
    registry-fetch-interval-seconds: 5
    eureka-server-connect-timeout-seconds: 5
    eureka-server-read-timeout-seconds: 8
    initial-instance-info-replication-interval-seconds: 5
  instance:
    lease-renewal-interval-in-seconds: 10
    lease-expiration-duration-in-seconds: 30
    prefer-ip-address: true

spring:
  datasource:
    url: jdbc:postgresql://postgres:5432/n11
    username: ${db.user:identity_user}
    password: ${db.password:${IDENTITY_DB_PASSWORD}}
  flyway:
    enabled: true
    locations: classpath:db/migration
    schemas: identity
    default-schema: identity
    create-schemas: false
    user: ${spring.datasource.username}
    password: ${spring.datasource.password}
    placeholders:
      schema: identity
      adminSeedEmail: ${ADMIN_SEED_EMAIL}         # D-06: Flyway placeholder for V2 seed
      adminSeedPasswordHash: ${ADMIN_SEED_PASSWORD_HASH}
  jpa:
    open-in-view: false
    properties:
      hibernate:
        default_schema: identity

# D-02 / Pitfall #6: JWT keypair from env vars — never hardcode in YAML committed to git
jwt:
  private-key: ${JWT_PRIVATE_KEY}
  key-id: ${JWT_KEY_ID:n11-jwt-2026-04}

management:
  endpoints:
    web:
      exposure:
        include: health,info

springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
    path: /swagger-ui.html
```

---

### `.planning/saga-contracts/user-registered.schema.json` (config, event-driven)

**Analog:** `.planning/saga-contracts/order-created.schema.json` (lines 1–30) — exact JSON-Schema shape to copy

**Schema pattern** (copy `order-created.schema.json` structure):
```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://n11clone/saga/user-registered.schema.json",
  "title": "user.registered payload",
  "type": "object",
  "required": ["userId", "email", "fullName", "registeredAt"],
  "additionalProperties": false,
  "properties": {
    "userId":       { "type": "string", "format": "uuid" },
    "email":        { "type": "string", "format": "email" },
    "fullName":     { "type": "string", "minLength": 1 },
    "registeredAt": { "type": "string", "format": "date-time" }
  }
}
```

**Copy this schema also into** `common-events/src/main/resources/saga-schemas/user-registered.schema.json` so `AbstractEventSchemaTest` can validate against it in tests (same pattern as all other schemas in `common-events/src/main/resources/saga-schemas/`).

---

### `api-gateway/src/main/java/com/n11/gateway/SecurityConfig.java` (REPLACE, reactive)

**Analog (current file to read before replacing):** `api-gateway/src/main/java/com/n11/gateway/SecurityConfig.java` (lines 1–59)

**CORS block to preserve verbatim** (current file lines 45–57):
```java
private CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration cfg = new CorsConfiguration();
    cfg.setAllowedOrigins(List.of("http://localhost:5173"));
    cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    cfg.setAllowedHeaders(List.of("*"));
    cfg.setExposedHeaders(List.of("X-Correlation-Id"));
    cfg.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
    src.registerCorsConfiguration("/**", cfg);
    return src;
}
```

**New `SecurityWebFilterChain` bean to replace** (RESEARCH.md §SecurityWebFilterChain, lines 113–158):
```java
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityFilterChain(ServerHttpSecurity http,
                                                       ReactiveJwtDecoder jwtDecoder) {
        http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeExchange(ex -> ex
                .pathMatchers(HttpMethod.POST,  "/api/v1/identity/auth/login").permitAll()
                .pathMatchers(HttpMethod.POST,  "/api/v1/identity/auth/register").permitAll()
                .pathMatchers(HttpMethod.GET,   "/api/v1/identity/.well-known/jwks.json").permitAll()
                .pathMatchers(HttpMethod.GET,   "/api/v1/products/**").permitAll()
                .pathMatchers(HttpMethod.GET,   "/api/v1/search/**").permitAll()
                .pathMatchers(HttpMethod.POST,  "/api/v1/chat/**").permitAll()
                .pathMatchers(HttpMethod.GET,   "/api/v1/chat/**").permitAll()
                .pathMatchers(HttpMethod.POST,  "/api/v1/payments/iyzico/callback").permitAll()
                .anyExchange().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtDecoder(jwtDecoder))
            );
        return http.build();
    }

    @Bean
    public ReactiveJwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri) {
        NimbusReactiveJwtDecoder decoder =
                NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri)
                        .jwsAlgorithm(SignatureAlgorithm.RS256)
                        .build();
        // D-04: 30s clock skew; default is 60s — must override explicitly
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(Duration.ofSeconds(30))
        ));
        return decoder;
    }

    // Risk 5 mitigation: custom converter to read "roles" claim (not "scope")
    @Bean
    public ReactiveJwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter rolesConverter = new JwtGrantedAuthoritiesConverter();
        rolesConverter.setAuthoritiesClaimName("roles");
        rolesConverter.setAuthorityPrefix("");  // already ROLE_-prefixed in claim
        ReactiveJwtAuthenticationConverter converter = new ReactiveJwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(
                new ReactiveJwtGrantedAuthoritiesConverterAdapter(rolesConverter));
        return converter;
    }

    private CorsConfigurationSource corsConfigurationSource() {
        // ... verbatim from current file ...
    }
}
```

**Reactive imports (must NOT mix with servlet types):**
- `org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity`
- `org.springframework.security.config.web.server.ServerHttpSecurity`
- `org.springframework.security.web.server.SecurityWebFilterChain`
- `org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder`
- `org.springframework.security.oauth2.jwt.ReactiveJwtDecoder`
- `org.springframework.security.oauth2.jwt.JwtTimestampValidator`
- `org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator`

---

### `api-gateway/src/main/java/com/n11/gateway/GatewayHeaderInjectionFilter.java` (REPLACE, reactive)

**Analog (current file to read before replacing):** `api-gateway/src/main/java/com/n11/gateway/GatewayHeaderInjectionFilter.java` (lines 37–59)

**Preserve from current file:**
- Package declaration (`package com.n11.gateway`)
- Class name (`GatewayHeaderInjectionFilter`)
- Interface declarations (`implements GlobalFilter, Ordered`)
- `getOrder()` return value: `Ordered.HIGHEST_PRECEDENCE + 10` (line 57)
- Header name constants `HEADER_USER_ID`, `HEADER_USER_ROLES`

**New `filter()` body** (RESEARCH.md §GatewayHeaderInjectionFilter, lines 183–226):
```java
@Override
public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    return ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .filter(auth -> auth instanceof JwtAuthenticationToken)
            .cast(JwtAuthenticationToken.class)
            .flatMap(jwtAuth -> {
                Jwt jwt = jwtAuth.getToken();
                String userId = jwt.getSubject();
                List<String> roles = jwt.getClaimAsStringList("roles");
                String rolesHeader = (roles != null) ? String.join(",", roles) : "";

                ServerHttpRequest mutated = exchange.getRequest().mutate()
                        .headers(h -> {
                            h.remove(HEADER_USER_ID);
                            h.remove(HEADER_USER_ROLES);
                            h.remove(HEADER_AUTH);      // strip raw JWT (D-19)
                            h.set(HEADER_USER_ID, userId);
                            h.set(HEADER_USER_ROLES, rolesHeader);
                        })
                        .build();
                return chain.filter(exchange.mutate().request(mutated).build());
            })
            .switchIfEmpty(Mono.defer(() -> {
                // Public routes: strip X-User-* spoofing but do NOT strip Authorization
                ServerHttpRequest stripped = exchange.getRequest().mutate()
                        .headers(h -> {
                            h.remove(HEADER_USER_ID);
                            h.remove(HEADER_USER_ROLES);
                        })
                        .build();
                return chain.filter(exchange.mutate().request(stripped).build());
            }));
}
```

**New reactive imports to add:**
- `org.springframework.security.web.server.context.ReactiveSecurityContextHolder`
- `org.springframework.security.core.context.SecurityContext`
- `org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken`
- `org.springframework.security.oauth2.jwt.Jwt`

---

### `config-server/src/main/resources/config/api-gateway.yml` (MODIFY — additive)

**Analog:** `config-server/src/main/resources/config/api-gateway.yml` (current file, lines 93–95)

Current file footer (line 93–95):
```yaml
# Phase 3 will add an `oauth2.resource-server.jwt.issuer-uri` block here once
# identity-service publishes a JWKS endpoint. NOT in Phase 1.
```

**Replace the comment with** (RESEARCH.md §config-server change, lines 243–249):
```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          # D-20 / D-03: docker-compose hostname — NOT lb:// (NimbusReactiveJwtDecoder
          # does not natively support service-discovery URI scheme per Risk 2).
          jwk-set-uri: http://identity-service:8081/.well-known/jwks.json
```

**Also add Springdoc aggregator entry** (noted as Phase 3 ownership in current file lines 87–91):
```yaml
springdoc:
  swagger-ui:
    urls:
      - name: identity-service
        url: /api/v1/identity/v3/api-docs
```

---

### `settings.gradle.kts` (MODIFY — additive)

**Analog:** `settings.gradle.kts` (current file, lines 3–11)

Current `include` block:
```kotlin
include(
    "eureka-server",
    "config-server",
    "api-gateway",
    "common-error",
    "common-logging",
    "common-events",
    "service-template",
    "infra-tests"
)
```

**Add `"identity-service"` to the list** (anywhere in the include block; alphabetical preferred):
```kotlin
include(
    "eureka-server",
    "config-server",
    "api-gateway",
    "common-error",
    "common-logging",
    "common-events",
    "identity-service",   // ← add this line
    "service-template",
    "infra-tests"
)
```

---

### `docker-compose.yml` (MODIFY — additive)

**Analog:** `docker-compose.yml` — `api-gateway` service block (lines 172–194) for the health check + depends_on pattern; `postgres` service block (lines 24–62) for env_file + environment variable pattern

**New service block to add** (after `api-gateway` block, before `volumes:`):
```yaml
  # identity-service — first business service (Phase 3, D-21).
  # Port 8081 (gateway is 8080; 808x convention per ARCHITECTURE.md).
  # Image built by: ./gradlew :identity-service:jibDockerBuild
  identity-service:
    image: n11/identity-service:dev
    container_name: n11-identity-service
    env_file:
      - .env
    environment:
      SPRING_CONFIG_IMPORT: configserver:http://config-server:8888?fail-fast=true&max-attempts=10&max-interval=1500&multiplier=1.2&initial-interval=1100
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka-server:8761/eureka/
      SPRING_PROFILES_ACTIVE: docker
    depends_on:
      postgres:
        condition: service_healthy
      rabbitmq:
        condition: service_healthy
      eureka-server:
        condition: service_healthy
      config-server:
        condition: service_healthy
    healthcheck:
      test: ["CMD-SHELL", "wget -q -O- http://localhost:8081/actuator/health | grep -q '\"status\":\"UP\"'"]
      interval: 5s
      timeout: 3s
      retries: 6
      start_period: 20s    # Flyway migration adds ~5s vs infra services
    restart: unless-stopped
    networks:
      - n11-net
```

**Note:** Do NOT publish port 8081 to the host (no `ports:` mapping) — Pitfall #14 mitigation. Identity-service is accessible only through the gateway on port 8080 from outside the Docker network.

---

## Shared Patterns

### Correlation-ID MDC (Cross-cutting)

**Source:** `common-logging/src/main/java/com/n11/logging/CorrelationIdFilter.java` (lines 25–45)
**Apply to:** All identity-service controllers (auto-applied via Spring Boot autoconfiguration registered in `common-logging/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`)

Identity-service does NOT need to wire this manually. It inherits via `implementation(project(":common-logging"))`. The filter runs at `Ordered.HIGHEST_PRECEDENCE`, so all controller method MDC reads of `"correlationId"` will succeed.

Key usage pattern in controllers (matches `SampleHealthController` lines 8–9):
```java
import org.slf4j.MDC;
// Access correlation ID in any service layer:
String cid = MDC.get("correlationId");
```

### Error Handling (RFC-7807)

**Source:** `common-error/src/main/java/com/n11/error/ProblemDetailControllerAdvice.java` (lines 28–90)
**Apply to:** All identity-service controllers and services

`ProblemDetailControllerAdvice` is auto-registered via Spring Boot autoconfiguration. Identity-service controllers must:
1. Throw `ResponseStatusException` for runtime errors (404, 401, 409).
2. Use Jakarta validation annotations on request records for `MethodArgumentNotValidException`.
3. NEVER add local `@ExceptionHandler` methods — the advice handles everything.

**ApiErrorCode enum** (`common-error/src/main/java/com/n11/error/ApiErrorCode.java` lines 14–19) — current entries (`VALIDATION`, `NOT_FOUND`, `CONFLICT`, `UNAUTHORIZED`, `INTERNAL`) are sufficient for Phase 3 auth errors. CD-05 recommends adding auth-specific entries, but CD says "planner may"; the 5 existing codes map correctly:
- `auth/email-taken` → throw `ResponseStatusException(CONFLICT)` → maps to `ApiErrorCode.CONFLICT`
- `auth/invalid-credentials` → throw `ResponseStatusException(UNAUTHORIZED)` → maps to `ApiErrorCode.UNAUTHORIZED`
- `auth/missing-token` → throw `ResponseStatusException(UNAUTHORIZED)` → same

### Saga Envelope DTO

**Source:** `common-events/src/main/java/com/n11/events/Envelope.java` (lines 23–33)
**Apply to:** `OutboxPoller.java` when constructing the `user.registered` event payload

The outbox `payload` column stores the full `Envelope` JSON (envelope wrapping the user-registered payload). Construct via:
```java
Envelope env = new Envelope(
    UUID.randomUUID().toString(),    // eventId
    "user.registered",               // eventType
    1,                               // eventVersion
    Instant.now(),                   // occurredAt
    eventId,                         // correlationId (equals eventId for saga roots)
    null,                            // causationId
    "identity-service",              // producer
    objectMapper.valueToTree(userRegisteredPayload)  // payload as JsonNode
);
outbox.setPayload(objectMapper.writeValueAsString(env));
```

### Schema Drift Gate

**Source:** `common-events/src/test/java/com/n11/events/AbstractEventSchemaTest.java` (lines 46–115) + `common-events/src/test/java/com/n11/events/EnvelopeSchemaSelfTest.java` (lines 13–34)
**Apply to:** `identity-service/src/test/java/com/n11/identity/OutboxIntegrationTest.java`

The integration test for D-12 (outbox writes correct event schema) should extend `AbstractEventSchemaTest` and call `assertEventValid("user-registered.schema.json", producedJson)`. This requires the schema to be on the classpath (add `user-registered.schema.json` to `common-events/src/main/resources/saga-schemas/`).

### Testcontainers Database Test Pattern

**Source:** `infra-tests/src/test/java/com/n11/infra/CrossSchemaDenyTest.java` (lines 42–65) for `@Testcontainers` + singleton container pattern

**Apply to:** `identity-service/src/test/java/com/n11/identity/AuthControllerTest.java` and `AddressControllerTest.java`

```java
@Testcontainers
class AuthControllerTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>(
                DockerImageName.parse("pgvector/pgvector:pg16")
                    .asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("n11")
            .withUsername("identity_user")
            .withPassword("test-password");
    // ... DynamicPropertySource to override spring.datasource.*
}
```

---

## Wave 0 Prerequisites (must exist before clone proceeds)

| Prerequisite | Current Status | Risk if Missing |
|--------------|---------------|-----------------|
| `service-template/skeleton/` directory with `.template` files | EXISTS — verified at all 5 paths | Clone step 1 fails |
| `infra/postgres/init.sh` creates `identity` schema + `identity_user` | EXISTS — `identity_user` confirmed in `CrossSchemaDenyTest` `withEnv` map | Flyway migration fails at boot |
| `common-error`, `common-logging`, `common-events` modules built | EXISTS — all 3 confirmed with source files | `identity-service/build.gradle.kts` project dependencies unresolved |
| `config-server` running with `identity-service.yml` | PHASE 3 creates this file | Identity-service fails to boot (config-server fetch returns 404) |
| `.env` with `JWT_PRIVATE_KEY`, `JWT_KEY_ID`, `ADMIN_SEED_EMAIL`, `ADMIN_SEED_PASSWORD_HASH`, `IDENTITY_DB_PASSWORD` | NOT YET — planner Wave 0 task | BCrypt seed migration fails; JWT bean creation fails |
| Gateway `build.gradle.kts` includes `spring-boot-starter-oauth2-resource-server` | Verify at task time — may already be present from Phase 1 | `NimbusReactiveJwtDecoder` class not found at runtime |

---

## Risks

| Risk | Severity | Mitigation |
|------|----------|-----------|
| Gateway `spring-boot-starter-oauth2-resource-server` may not be in `api-gateway/build.gradle.kts` | HIGH | Verify before replacing `SecurityConfig.java`; add if missing |
| `RSAPrivateCrtKey` cast may fail if key not generated by `openssl genrsa` | MEDIUM | Document `openssl genrsa 2048` as only supported method; add startup assertion |
| `NimbusReactiveJwtDecoder` cold-start race (gateway boots before identity-service healthy) | MEDIUM | docker-compose `depends_on.condition: service_healthy` on `api-gateway` pointing to `identity-service` healthcheck |
| `FOR UPDATE SKIP LOCKED` in Spring Data `@Query` requires `nativeQuery = true` | MEDIUM | Use `nativeQuery = true` in `OutboxRepository.findUnsentBatch` |
| `service-template/skeleton/` exact path verified — it is `src-main/` (not `src/main/`) | LOW | Executor must use `cp -r service-template/skeleton/src-main ${SERVICE_NAME}/src/main` (from skeleton README) |
| Gateway `roles` claim authority converter defaults to reading `scope`/`scp`, not `roles` | LOW for Phase 3 (no role checks), HIGH for Phase 4 | Wire `ReactiveJwtAuthenticationConverter` in Phase 3 SecurityConfig (included in pattern above) |
| Flyway placeholder YAML key must be `spring.flyway.placeholders.*` (not `flyway.placeholders.*`) | LOW | Verified: `service-template.yml` line 53 uses `flyway.placeholders.schema` as the Spring Config key |

---

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `identity-service/src/main/java/com/n11/identity/auth/JwtConfig.java` | config | request-response | First JWT issuance bean in the project — no servlet-side signing pattern exists yet |
| `identity-service/src/main/java/com/n11/identity/auth/JwtIssuerService.java` | service | request-response | Same — first JWT issuance service; RESEARCH.md §JWT Token Issuance is the reference |

Both files are fully specified in RESEARCH.md with verified API shapes. Planner should prescribe those shapes directly rather than pointing to a codebase analog.

---

## Metadata

**Analog search scope:** `service-template/`, `api-gateway/src/`, `config-server/src/`, `common-error/src/`, `common-logging/src/`, `common-events/src/`, `infra-tests/src/`, `.planning/saga-contracts/`
**Files scanned:** 28 source files read in full
**Pattern extraction date:** 2026-04-29

---

## PATTERN MAPPING COMPLETE
