# Phase 3: Identity + Gateway Auth - Research

**Researched:** 2026-04-29
**Domain:** Spring Security 6.x JWT issuance + RS256 JWKS + reactive gateway resource server
**Confidence:** HIGH on Spring Security API shapes (Context7 + official docs); LOW on CONTEXT.md's `refreshTtl=1h` claim (not verified in Spring Security source — see Assumptions Log)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- D-01: 24h access token, no refresh-token endpoint. On 401 client clears token + re-logs in.
- D-02: Private key from `JWT_PRIVATE_KEY` env var (PEM multi-line). Public key derived at boot. Stable `kid` from `JWT_KEY_ID` env var.
- D-03: Gateway uses ONLY `jwk-set-uri` (not `issuer-uri`). `http://identity-service:8081/.well-known/jwks.json`. Default `NimbusReactiveJwtDecoder` caching.
- D-04: Clock skew = 30 seconds via `JwtTimestampValidator(Duration.ofSeconds(30))`.
- D-05: Two roles: `ROLE_USER` (every signup), `ROLE_ADMIN` (seeded). `roles` claim = JSON array with ROLE_ prefix.
- D-06: Admin seeded via Flyway `V2__seed_admin.sql` using placeholders `${adminSeedEmail}` / `${adminSeedPasswordHash}`.
- D-07: `POST /auth/register` always assigns `ROLE_USER` only.
- D-08: `addresses` table in `identity` schema. No future split.
- D-09: Address fields: `id` UUID PK, `user_id` UUID FK, `title` VARCHAR(50), `recipient_name` VARCHAR(120), `phone` VARCHAR(20), `il` VARCHAR(50), `ilce` VARCHAR(80), `mahalle` VARCHAR(120), `street_line` VARCHAR(255), `postal_code` CHAR(5)` (validated `^\d{5}$`), `is_default` BOOLEAN DEFAULT false, `created_at` TIMESTAMPTZ. No `country`.
- D-10: Address-snapshot pattern for Phase 5 — addresses table stays mutable; order-service copies fields at order-creation time.
- D-11: Partial unique index on `addresses(user_id) WHERE is_default` to enforce at-most-one default per user.
- D-12: `user.registered` event published from identity-service via transactional outbox. New exchange `identity.tx` (topic, durable).
- D-13: Outbox demo lives in Phase 3. Poller code in identity-service; Phase 5 order-service copies-and-edits.
- D-14: Login response: `{ accessToken, tokenType: "Bearer", expiresIn: 86400, user: { id, email, fullName, roles: [...] } }`. Register returns same shape.
- D-15: `/auth/me` reads `X-User-Id` from gateway header. Zero JWT decoding in identity-service.
- D-16: Smoke unit test = `PasswordEncoderTest` (BCrypt cost 10, encode + matches + distinct hashes).
- D-17: Optional integration test with WebTestClient (planner discretion).
- D-18: Replace `SecurityConfig.java` wholesale. New chain: `oauth2ResourceServer().jwt()`, public allowlist, `anyExchange().authenticated()`, CORS preserved.
- D-19: Replace `GatewayHeaderInjectionFilter.java` wholesale. Strip inbound headers + read JWT principal from reactive SecurityContext + inject `X-User-Id`/`X-User-Roles` + strip `Authorization`.
- D-20: Add `oauth2.resource-server.jwt.jwk-set-uri` block to `config-server/.../config/api-gateway.yml`. Replace the existing comment at the footer.
- D-21: identity-service clones `service-template/skeleton/`. Port 8081. Entries in `settings.gradle.kts` and `docker-compose.yml`.
- D-22: DB user = `identity_user` (already created by `infra/postgres/init.sh`). Config file `config/identity-service.yml`. Flyway migrations: `V1__init_users_addresses.sql`, `V2__seed_admin.sql`, `V3__init_outbox.sql`.

### Claude's Discretion
- CD-01: Password complexity (minimum 8 chars + 1 letter + 1 digit recommended). Validation messages in Turkish.
- CD-02: Email validation via `@Email` + uniqueness check (pre-insert or DB constraint + 409 mapping).
- CD-03: DDL for `users` table and `roles`/`user_roles` join. Seed `roles` rows in V1.
- CD-04: Outbox poller cadence = 5s, batch size = 100. No outbox-level DLQ.
- CD-05: `ApiErrorCode` enum gains 4-5 new entries for auth errors.
- CD-06: Skip login rate-limiting (out of scope for v1).
- CD-07: Wave plan: W0 = scaffold + migrations; W1 = business code; W2 = gateway replacement.

### Deferred Ideas (OUT OF SCOPE)
- Refresh-token endpoint, password reset, email verification, OAuth login, TC kimlik validation.
- `POST /agents/exchange` (Phase 9).
- Frontend auth pages (Phase 10).
- Rate-limiting on `/auth/login`.
- `il`/`ilçe` reference dataset. Phone E.164 enforcement.
- JWKS rotation automation. Full OIDC issuer (`issuer-uri`).
- Extracting outbox poller into `common-outbox` shared module.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| AUTH-01 | User can register with email and password | identity-service `POST /auth/register` endpoint wired to `UserService.register()` + BCrypt + Flyway `users` table |
| AUTH-02 | User can log in and receive JWT | identity-service `POST /auth/login` wired to `JwtIssuerService.issue()` using `NimbusJwtEncoder` + RS256 private key from env var |
| AUTH-03 | Session persists (token stored client-side, "refreshed" on 401 = re-login) | D-01: 24h token + client clears on 401. No server-side refresh endpoint. |
| AUTH-04 | User can log out from any page | Client-side only: remove token from localStorage. No server-side session/blacklist. |
| AUTH-05 | identity-service issues JWTs with user-id and roles claims | `JwtClaimsSet.builder().subject(userId).claim("roles", roles)` via `NimbusJwtEncoder` |
| AUTH-06 | api-gateway validates every JWT on protected routes; downstream trust `X-User-Id`/`X-User-Roles` | New `SecurityConfig` (`oauth2ResourceServer().jwt()`) + new `GatewayHeaderInjectionFilter` reading `JwtAuthenticationToken` from reactive SecurityContext |
| AUTH-07 | Passwords hashed with BCrypt cost 10 before persistence | `BCryptPasswordEncoder(10)` in identity-service; D-16 smoke test proves it |
| AUTH-08 | User can manage multiple delivery addresses | `addresses` table (D-09); `GET /addresses` and `POST /addresses` endpoints behind gateway auth |
| QUAL-02 | Each service has at least one smoke unit test | `PasswordEncoderTest` per D-16; pure JUnit, no Spring context |
</phase_requirements>

---

## Summary

- **What CONTEXT.md already decided and does not need research:** All 22 locked decisions (JWT lifetime, RS256 keypair, JWKS URI, clock skew, roles model, address schema, outbox, login response shape, gateway surgery) are locked. Research does not re-open these.
- **What research fills in:** Exact API shapes for the bean classes the planner will prescribe (`NimbusJwtEncoder`, `NimbusReactiveJwtDecoder`, `JwtTimestampValidator`, `RsaKeyConverters`, `RSAKey`/`JWKSet` Nimbus builder, reactive `SecurityContext` extraction in a `GlobalFilter`); JWK cache behavior caveat (the "refreshTtl=1h" claim from CONTEXT.md is not verified as a distinct property in Spring Security 6.x — it is a Nimbus library internal); Flyway placeholder exact YAML syntax; `lb://` URI scheme incompatibility with `NimbusReactiveJwtDecoder` (must use docker-compose hostname); concrete outbox DDL and poller skeleton; test layers needed for Nyquist gate.
- **Primary recommendation:** Use docker-compose service hostname (`http://identity-service:8081/.well-known/jwks.json`) for `jwk-set-uri` — `lb://` URIs are NOT natively supported by `NimbusReactiveJwtDecoder`'s internal `WebClient`. The WebClient can be customized with a `ReactorLoadBalancerExchangeFilterFunction` but this adds fragile wiring with zero benefit on a single-host deploy. Stick with the docker-compose hostname. [VERIFIED: Spring Security GitHub issues #7474, #10686]
- **JWK caching reality check:** The Nimbus library's `DefaultJWKSetCache` defaults to 5-minute TTL. Spring Security 6.x reactive decoder triggers a JWKS re-fetch on unknown `kid` (rotation signal). The CONTEXT.md "refreshTtl=1h" is an approximation — it may reflect a 1-hour Nimbus internal refresh timer, but the exact knob is not a Spring Security configuration property. For v1 (single non-rotating key), this does not matter: the default behavior is sufficient and correct. [ASSUMED — see Assumptions Log A1]
- **Default clock skew:** `JwtTimestampValidator()` default is **60 seconds**. D-04 sets it to 30 seconds explicitly. The planner must wire `new JwtTimestampValidator(Duration.ofSeconds(30))` with `DelegatingOAuth2TokenValidator` — it does NOT apply automatically when using `jwk-set-uri` without a custom validator. [VERIFIED: Context7 / docs.spring.io]

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| JWT signing (token issuance) | identity-service (servlet) | — | Owns the private key; the only signer |
| JWT validation | api-gateway (reactive/WebFlux) | — | Single chokepoint; no downstream JWT awareness |
| JWKS public-key serving | identity-service (servlet) | — | Exposes `/.well-known/jwks.json` to the gateway |
| Header injection (X-User-Id / X-User-Roles) | api-gateway (GlobalFilter) | — | After auth filter, before forwarding |
| Authorization-header strip | api-gateway (GlobalFilter) | — | Same filter as injection; prevents raw JWT reaching downstreams |
| User registration + BCrypt hashing | identity-service (servlet) | — | Domain responsibility of the identity bounded context |
| Address book CRUD | identity-service (servlet) | — | Part of the identity bounded context |
| Outbox publishing (user.registered) | identity-service (servlet) | — | Co-located with the signup TX |
| Flyway schema migrations | identity-service (at startup) | — | Per-service migration pattern |
| Per-service config injection | config-server (native) | identity-service.yml | Supplies DB creds + Flyway schema name |

---

## Spring Security 6.x Implementation Details

### Gateway Side (Reactive — WebFlux)

The gateway is a Spring Cloud Gateway reactive app. All Spring Security types must be the **reactive** variants from `org.springframework.security.config.web.server.*` and `org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder`. Do NOT use `HttpSecurity`, `JwtDecoder`, or `NimbusJwtDecoder` here — those are servlet-stack types.

#### SecurityWebFilterChain (replaces current `SecurityConfig`)

[VERIFIED: Context7 / docs.spring.io/spring-security/reference/6.5/reactive/oauth2/resource-server/jwt.html]

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
                // Public allowlist (D-18) — reserve all even if services don't exist yet
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

        // D-04: 30s clock skew. JwtTimestampValidator default is 60s — override explicitly.
        // No issuer-uri means no JwtIssuerValidator; skip it for v1.
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(Duration.ofSeconds(30))
        ));

        return decoder;
    }

    // CORS preserved verbatim from Phase 1 SecurityConfig
    private CorsConfigurationSource corsConfigurationSource() { ... }
}
```

Key imports (reactive stack):
- `org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity`
- `org.springframework.security.config.web.server.ServerHttpSecurity`
- `org.springframework.security.web.server.SecurityWebFilterChain`
- `org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder`
- `org.springframework.security.oauth2.jwt.ReactiveJwtDecoder`
- `org.springframework.security.oauth2.jwt.JwtTimestampValidator`
- `org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator`

#### GatewayHeaderInjectionFilter (replaces current stub)

The reactive `SecurityContext` holds a `JwtAuthenticationToken` after the resource server filter chain runs. In a `GlobalFilter`, access it via `ReactiveSecurityContextHolder`. [VERIFIED: Context7]

```java
@Component
public class GatewayHeaderInjectionFilter implements GlobalFilter, Ordered {

    private static final String HEADER_USER_ID    = "X-User-Id";
    private static final String HEADER_USER_ROLES = "X-User-Roles";
    private static final String HEADER_AUTH       = "Authorization";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(auth -> auth instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .flatMap(jwtAuth -> {
                    Jwt jwt = jwtAuth.getToken();
                    String userId    = jwt.getSubject();           // "sub" claim
                    List<String> roles = jwt.getClaimAsStringList("roles"); // D-05 custom claim
                    String rolesHeader = (roles != null) ? String.join(",", roles) : "";

                    ServerHttpRequest mutated = exchange.getRequest().mutate()
                            .headers(h -> {
                                h.remove(HEADER_USER_ID);      // strip inbound (defense in depth)
                                h.remove(HEADER_USER_ROLES);
                                h.remove(HEADER_AUTH);         // strip raw JWT (anti-pattern #4)
                                h.set(HEADER_USER_ID, userId);
                                h.set(HEADER_USER_ROLES, rolesHeader);
                            })
                            .build();
                    return chain.filter(exchange.mutate().request(mutated).build());
                })
                // Unauthenticated path (public routes): still strip inbound spoofed headers
                .switchIfEmpty(Mono.defer(() -> {
                    ServerHttpRequest stripped = exchange.getRequest().mutate()
                            .headers(h -> {
                                h.remove(HEADER_USER_ID);
                                h.remove(HEADER_USER_ROLES);
                                // Do NOT strip Authorization on public paths —
                                // the security chain has already rejected it / passed it through
                                // for non-JWT paths. Strip only X-User-* spoofing.
                            })
                            .build();
                    return chain.filter(exchange.mutate().request(stripped).build());
                }));
    }

    @Override
    public int getOrder() {
        // After GatewayCorrelationIdFilter (HIGHEST_PRECEDENCE + 5),
        // before Spring Cloud Gateway's forwarding filter.
        return Ordered.HIGHEST_PRECEDENCE + 10; // same as Phase 1 stub
    }
}
```

Key imports (reactive):
- `org.springframework.security.web.server.context.ReactiveSecurityContextHolder`
- `org.springframework.security.core.context.SecurityContext`
- `org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken`
- `org.springframework.security.oauth2.jwt.Jwt`
- `org.springframework.cloud.gateway.filter.{GlobalFilter, GatewayFilterChain}`

**Note:** `Authorization` header stripping on the public path is intentionally omitted. The security chain for public paths does not validate the header; stripping it would break any downstream call that legitimately passes a bearer token for, say, a future inter-service authenticated path. Only the user-id/roles spoofing headers need stripping on all paths (defense in depth).

#### config-server `api-gateway.yml` change (D-20)

Replace the footer comment with:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: http://identity-service:8081/.well-known/jwks.json
```

**Why `http://identity-service:8081` and not `lb://identity-service`?**
`NimbusReactiveJwtDecoder`'s internal `WebClient` does not understand the `lb://` URI scheme. Spring Cloud LoadBalancer's `ReactorLoadBalancerExchangeFilterFunction` would need to be injected into a custom `WebClient` that is then passed to the decoder builder via `.webClient(...)`. This is possible but adds unnecessary complexity for a single-host docker-compose deploy where `identity-service` resolves correctly as a Docker network hostname. Use the docker-compose hostname directly. [MEDIUM confidence — not tested; derived from Spring Security GitHub issues #7474 and #10686 showing custom WebClient path]

---

### Identity-Service Side (Servlet — Spring MVC)

identity-service is a standard Spring Boot MVC service (cloned from `service-template/skeleton/`). All JWT types are the **non-reactive** variants: `JwtEncoder`, `NimbusJwtEncoder`, etc.

#### RS256 Keypair Loading (D-02)

`RsaKeyConverters.pkcs8()` is the Spring Security built-in converter for PKCS#8 PEM-encoded private keys. [VERIFIED: Context7 / docs.spring.io/spring-security/reference/6.5/api]

```java
@Configuration
public class JwtConfig {

    // JWT_PRIVATE_KEY env var — multiline PEM. Spring replaces \n with actual newlines
    // in environment-variable-sourced properties when the value is a literal PEM block.
    // Safer: read directly from env at boot using System.getenv and wrap in InputStream.
    @Bean
    public RSAPrivateKey rsaPrivateKey(
            @Value("${jwt.private-key}") String pemKey) throws Exception {
        // RsaKeyConverters.pkcs8() expects InputStream containing PEM header + base64 + footer
        byte[] keyBytes = pemKey.replace("\\n", "\n").getBytes(StandardCharsets.UTF_8);
        return RsaKeyConverters.pkcs8().convert(new ByteArrayInputStream(keyBytes));
    }

    @Bean
    public RSAPublicKey rsaPublicKey(RSAPrivateKey privateKey) throws NoSuchAlgorithmException {
        // Derive public key from private at boot — single source of truth (D-02)
        KeyFactory kf = KeyFactory.getInstance("RSA");
        RSAPrivateCrtKey crtKey = (RSAPrivateCrtKey) privateKey;
        RSAPublicKeySpec spec = new RSAPublicKeySpec(
                crtKey.getModulus(), crtKey.getPublicExponent());
        return (RSAPublicKey) kf.generatePublic(spec);
    }

    @Bean
    public RSAKey rsaJwk(RSAPublicKey publicKey, RSAPrivateKey privateKey,
                          @Value("${jwt.key-id}") String keyId) {
        // Nimbus RSAKey used for both signing (NimbusJwtEncoder) and JWKS serving
        return new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(keyId)           // stable kid — e.g. "n11-jwt-2026-04" from JWT_KEY_ID env
                .algorithm(JWSAlgorithm.RS256)
                .keyUse(KeyUse.SIGNATURE)
                .build();
    }

    @Bean
    public JWKSource<SecurityContext> jwkSource(RSAKey rsaJwk) {
        JWKSet jwkSet = new JWKSet(rsaJwk);
        return (jwkSelector, context) -> jwkSelector.select(jwkSet);
    }

    @Bean
    public JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
        return new NimbusJwtEncoder(jwkSource);
    }
}
```

[VERIFIED: Context7 — `NimbusJwtEncoder` constructor signature, `RSAKey.Builder` shape, `JWKSource` lambda pattern from DPoP example]

**Note on `RSAPrivateCrtKey`:** A PKCS#8 RSA private key loaded via `RsaKeyConverters.pkcs8()` gives a `RSAPrivateCrtKey` (CRT = Chinese Remainder Theorem) which exposes `getPublicExponent()` and `getModulus()` needed to reconstruct the public key. If the key was NOT generated in CRT form (rare), use `KeyPairGenerator` instead and log a startup warning. [ASSUMED A2]

#### JWT Token Issuance

```java
@Service
public class JwtIssuerService {

    private final JwtEncoder jwtEncoder;

    public String issue(User user) {
        Instant now = Instant.now();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("n11-identity")          // optional for v1 (no issuer-uri validation)
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(86400)) // D-01: 24h
                .claim("roles", user.getRoles())   // D-05: ["ROLE_USER"] or ["ROLE_USER","ROLE_ADMIN"]
                .claim("email", user.getEmail())   // convenience — avoids /auth/me call
                .claim("fullName", user.getFullName())
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
```

#### JWKS Endpoint

The JWKS endpoint must return the public key only (never the private). [VERIFIED: Nimbus `RSAKey.toPublicJWK()`]

```java
@RestController
public class JwksController {

    private final RSAKey rsaJwk;

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        // toPublicJWK() strips the private key material — critical
        JWKSet publicSet = new JWKSet(rsaJwk.toPublicJWK());
        return publicSet.toJSONObject();
    }
}
```

**JWKS JSON shape** (Nimbus `JWKSet.toJSONObject()` output for an RSA key):

```json
{
  "keys": [
    {
      "kty": "RSA",
      "use": "sig",
      "alg": "RS256",
      "kid": "n11-jwt-2026-04",
      "n": "<base64url-encoded-modulus>",
      "e": "<base64url-encoded-exponent>"
    }
  ]
}
```

`kid`, `alg`, `use` are set automatically from the `RSAKey.Builder` configuration. [VERIFIED: RFC 7517 §4 field names]

#### identity-service Dependencies

The following must be added to `identity-service/build.gradle.kts`:

```kotlin
// JWT signing (NimbusJwtEncoder, JwtClaimsSet, JwsHeader)
implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

// Nimbus JOSE+JWT (RSAKey, JWKSet, JWSAlgorithm, KeyUse) — pulled transitively, but
// explicitly listing for clarity:
// com.nimbusds:nimbus-jose-jwt (version managed by Spring Security BOM)
```

**Important:** `spring-boot-starter-oauth2-resource-server` is needed on identity-service for `NimbusJwtEncoder` and `JwtClaimsSet` even though identity-service does NOT validate JWTs. The starter brings in `spring-security-oauth2-jose` which contains these classes. It does NOT configure a resource server on identity-service (no `@EnableResourceServer` or `oauth2ResourceServer()` call in identity-service's SecurityConfig — which should be a minimal `permitAll()` chain or absent entirely since identity-service has no protected endpoints of its own other than those gated by the gateway).

#### identity-service SecurityConfig (minimal)

identity-service does not validate JWTs itself (D-15). Its `/auth/**` and `/.well-known/jwks.json` are all public. A simple permit-all chain prevents Spring Security from adding default form-login:

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
}
```

---

## Outbox Pattern Concrete Shape

### DDL (V3__init_outbox.sql)

Matches `saga-contracts.md §5.1` exactly. Place in `identity-service/src/main/resources/db/migration/`:

```sql
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

### Transactional Write Semantics

The `user.registered` event row is written in the **same transaction** as the `users` INSERT:

```java
@Transactional
public AuthResponse register(RegisterRequest request) {
    User user = userRepository.save(buildUser(request));
    outboxRepository.save(buildOutboxEvent(user));  // same TX
    return buildAuthResponse(user, issueToken(user));
}
```

The `outboxRepository.save()` and `userRepository.save()` share the Spring-managed JDBC transaction. If the commit fails, neither persists. If the commit succeeds, the outbox row is guaranteed to exist before the poller runs.

### Outbox Poller (CD-04)

```java
@Component
public class OutboxPoller {

    private final OutboxRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;

    @Scheduled(fixedDelay = 5000)  // every 5 seconds
    @Transactional
    public void poll() {
        List<OutboxEvent> unsent = outboxRepository.findUnsentBatch(100); // batch of 100
        for (OutboxEvent event : unsent) {
            rabbitTemplate.convertAndSend(
                    event.getAggregate() + ".tx",  // e.g., "identity.tx"
                    event.getEventType(),           // routing key e.g., "user.registered"
                    event.getPayload()              // JSONB as String
            );
            event.setSentAt(Instant.now());
            outboxRepository.save(event);           // mark sent in same TX
        }
    }
}
```

`findUnsentBatch` query:

```sql
SELECT * FROM outbox WHERE sent_at IS NULL ORDER BY occurred_at LIMIT :batchSize FOR UPDATE SKIP LOCKED
```

`FOR UPDATE SKIP LOCKED` prevents two poller instances (if ever scaled) from double-publishing the same row. [ASSUMED A3 — standard pattern; verify at impl time that the Spring Data `@Query` annotation and dialect support this clause]

### `user.registered` Event Payload (D-12)

Envelope from `saga-contracts.md §1` + payload:

```json
{
  "eventId":       "uuid",
  "eventType":     "user.registered",
  "eventVersion":  1,
  "occurredAt":    "2026-04-29T...",
  "correlationId": "<same as eventId for initiating events>",
  "causationId":   null,
  "producer":      "identity-service",
  "payload": {
    "userId":       "uuid",
    "email":        "user@example.com",
    "fullName":     "Ad Soyad",
    "registeredAt": "2026-04-29T..."
  }
}
```

Lock this in `.planning/saga-contracts/user-registered.schema.json` as a Phase 3 deliverable.

### RabbitMQ Exchange Declaration

New `identity.tx` exchange must be declared as a Spring `@Bean` in identity-service:

```java
@Bean
public TopicExchange identityExchange() {
    return ExchangeBuilder.topicExchange("identity.tx").durable(true).build();
}
```

No queue binding needed in identity-service (phase 3 has no consumer). Phase 7 adds the queue + binding in notification-service.

### Consumer Side (Phase 7 — documented here as shape reference)

`processed_events` table (already in `service-template/V1__init_processed_events.sql`) is the inbox for Phase 7's notification-service consumer. Identity-service V1 migration inherits it unchanged.

---

## Flyway Placeholder Syntax for Admin Seed (D-06)

[VERIFIED: flyway/flyway library docs]

**YAML (in `config/identity-service.yml` or `application.yml`):**

```yaml
spring:
  flyway:
    placeholders:
      adminSeedEmail: ${ADMIN_SEED_EMAIL}
      adminSeedPasswordHash: ${ADMIN_SEED_PASSWORD_HASH}
```

**V2__seed_admin.sql:**

```sql
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

Flyway replaces `${adminSeedEmail}` and `${adminSeedPasswordHash}` before executing. Default placeholder prefix/suffix is `${` / `}`. The env vars `ADMIN_SEED_EMAIL` and `ADMIN_SEED_PASSWORD_HASH` must be set in `.env` (gitignored) and loaded by docker-compose.

**Generating the bcrypt hash:**

```bash
# One-liner using Spring CLI or htpasswd:
htpasswd -bnBC 10 "" "your-password" | tr -d ':\n'
# Or: use an online bcrypt generator for one-time bootstrap
```

Document the command in the project README.

---

## Validation Architecture

> `nyquist_validation: true` in `.planning/config.json` — this section is MANDATORY.

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit Jupiter 5.11.x (transitive from Spring Boot 3.5.14 BOM) |
| Config file | none — Gradle uses JUnit Platform auto-detection |
| Quick run | `./gradlew :identity-service:test --tests "com.n11.identity.*"` |
| Full suite | `./gradlew test` (all modules) |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| AUTH-07 / QUAL-02 | BCrypt cost 10 round-trip; two encodes differ | unit (pure JUnit) | `./gradlew :identity-service:test --tests "*PasswordEncoderTest"` | ❌ Wave 0 |
| AUTH-01 | Registration stores user with hashed password; 409 on duplicate email | integration (WebMvcTest + TestContainers Postgres) | `./gradlew :identity-service:test --tests "*AuthControllerTest"` | ❌ Wave 0 |
| AUTH-02 | Login issues RS256 JWT; JWT decodes with JWKS from the same service | integration (WebMvcTest + TestContainers Postgres) | `./gradlew :identity-service:test --tests "*AuthControllerTest"` | ❌ Wave 0 |
| AUTH-05 | JWT contains `sub`=userId, `roles`=["ROLE_USER"] claims | unit or integration sub-test | part of `AuthControllerTest` | ❌ Wave 0 |
| AUTH-06 | Gateway rejects missing token with 401; passes valid token and injects X-User-Id | integration (gateway integration test OR manual curl smoke) | manual curl smoke | n/a |
| AUTH-08 | Address `POST` persists row; `GET` lists only caller's addresses | integration (WebMvcTest slice) | `./gradlew :identity-service:test --tests "*AddressControllerTest"` | ❌ Wave 0 |
| D-11 | Two addresses marked `is_default=true` for same user → constraint violation | unit (SQL DDL test) | part of `AddressControllerTest` | ❌ Wave 0 |
| D-12 | Registration writes outbox row in same TX; outbox payload matches schema | integration | `./gradlew :identity-service:test --tests "*OutboxIntegrationTest"` | ❌ Wave 0 |

### Sampling Rate

- **Per task commit:** `./gradlew :identity-service:test --tests "*PasswordEncoderTest"` (< 5s, pure unit)
- **Per wave merge:** `./gradlew :identity-service:test` (full identity-service suite)
- **Phase gate:** Full suite green before `/gsd-verify-work`

### Wave 0 Gaps

- [ ] `identity-service/src/test/java/com/n11/identity/PasswordEncoderTest.java` — covers AUTH-07, QUAL-02
- [ ] `identity-service/src/test/java/com/n11/identity/AuthControllerTest.java` — covers AUTH-01, AUTH-02, AUTH-05
- [ ] `identity-service/src/test/java/com/n11/identity/AddressControllerTest.java` — covers AUTH-08, D-11
- [ ] `identity-service/src/test/java/com/n11/identity/OutboxIntegrationTest.java` — covers D-12
- [ ] `identity-service/src/test/resources/application-test.yml` — Testcontainers datasource override
- [ ] Testcontainers Postgres singleton setup (base class or `@TestConfiguration`)

**Optional (D-17):** `WebTestClient` end-to-end: `POST /auth/register` → `POST /auth/login` → call `/.well-known/jwks.json` → validate JWT with public key. Planner should include if scope permits; recommended for grading signal.

**Gateway auth test:** AUTH-06 is most efficiently validated by a manual curl smoke test after gateway replacement (register → login → `GET /api/v1/identity/auth/me` with bearer token → 200; same without token → 401). Automated gateway integration tests are out of scope for Phase 3.

---

## Pitfall Mitigations

### Pitfall #6 — No JWT secret in source

**This phase's surface:** `JWT_PRIVATE_KEY` and `JWT_KEY_ID` env vars. Both must flow from `.env` (gitignored) → docker-compose `env_file: .env` → identity-service container. The `config/identity-service.yml` in config-server maps:

```yaml
jwt:
  private-key: ${JWT_PRIVATE_KEY}
  key-id: ${JWT_KEY_ID:n11-jwt-2026-04}
```

Never put the PEM content in YAML committed to git. Pre-commit hook (already in Phase 1 scope via QUAL-09) catches it.

**Admin seed env vars:** `ADMIN_SEED_EMAIL` and `ADMIN_SEED_PASSWORD_HASH` are also in `.env`. Document both in README with placeholder values:

```
ADMIN_SEED_EMAIL=admin@n11-demo.local
ADMIN_SEED_PASSWORD_HASH=$2a$10$...  # generate with htpasswd
```

### Pitfall #14 — Gateway path validation / direct-service bypass

Phase 3 does not yet set `server.address: 127.0.0.1` on identity-service (that is a Phase 11 hardening step). However, the gateway does validate JWT on all non-allowlisted paths. The risk is low on a single docker-compose host network: backing service ports are not mapped to the host by default unless explicitly declared. Verify that docker-compose does NOT publish identity-service port 8081 to the host (`ports:` mapping absent or limited to internal network) — this is the minimal mitigation for Phase 3.

### Pitfall #18 — Clock skew + non-rotated dev key footgun

**Clock skew:** wired via `JwtTimestampValidator(Duration.ofSeconds(30))` in the gateway's `ReactiveJwtDecoder` bean (D-04). The default would be 60s — D-04 halves it deliberately (safe for a demo with one host).

**Non-rotated dev key footgun:** the `JWT_PRIVATE_KEY` generated for local dev should be a real RSA key pair, not a shared demo key committed to a gist or blog post. The planner's task for Wave 0 should include a `openssl genrsa 2048 > private.pem` command in the README and in `.env.example`.

**WSL2 clock drift:** if demo runs on WSL2, add to demo runbook: "before starting `docker compose up`, run `wsl --shutdown` and restart to reset the clock drift from long laptop sleep."

---

## Open Risks for Planner

### Risk 1: JWK cold-start race (gateway boots before identity-service is healthy)

`NimbusReactiveJwtDecoder.withJwkSetUri(...)` does NOT eagerly fetch the JWKS at Spring context startup. It fetches on the **first incoming authenticated request**. Therefore:

- If identity-service is down at gateway startup: fine, no error at boot.
- If identity-service is down when the **first protected request arrives**: the JWKS fetch fails, the request is rejected with 401 (not 503). This is the correct behavior.
- Mitigation: docker-compose `depends_on.condition: service_healthy` ensures identity-service is healthy before gateway starts. The current `healthcheck` in docker-compose uses `wget` pattern (per Phase 1 decisions). The planner must add an identity-service healthcheck entry in docker-compose (Wave 0 task).

**Note:** There is a `Preload JwkSet on Application Startup` Spring Security issue (#10471) documenting that the JWKS is NOT preloaded. This is expected behavior and safe for v1 — just ensure the healthcheck chain is correct.

### Risk 2: `lb://` URI scheme not supported by NimbusReactiveJwtDecoder

As documented in the Spring Security implementation details above: use `http://identity-service:8081/.well-known/jwks.json` (docker-compose hostname). Do not use `lb://identity-service/.well-known/jwks.json` without injecting a load-balancer-aware `WebClient`. [MEDIUM confidence — derived from issue pattern analysis, not explicit doc statement]

### Risk 3: JWK cache TTL / refresh behavior

The CONTEXT.md states `cacheTtl=5m` and `refreshTtl=1h`. The Nimbus library's `DefaultJWKSetCache` does cache for 5 minutes. The "refreshTtl=1h" claim is not a configurable Spring Security property — it may refer to the Nimbus library's internal lifespan timer (distinct from TTL). For v1 with a single non-rotating key, this is academically irrelevant: once the JWKS is cached, every token with the same `kid` validates from cache, and there is no rotation during the demo window. **No action required for Phase 3** — document in Phase 3's implementation notes that key rotation procedure is manual (per D-03). [See Assumptions Log A1]

### Risk 4: `RSAPrivateCrtKey` assumption for public key derivation

Deriving the public key from a PKCS#8 private key via `RSAPrivateCrtKey.getPublicExponent()` assumes the key was generated in CRT form. Keys generated with `openssl genrsa` are always in CRT form. If an unusual key source is used, this may fail. Mitigation: add a startup-time assertion log that prints the derived public key fingerprint; if the derivation throws `ClassCastException`, fall back to providing `JWT_PUBLIC_KEY` as a second env var. For v1, `openssl genrsa 2048` is the documented generation method — CRT is guaranteed. [ASSUMED A2]

### Risk 5: Roles claim converter mismatch between identity-service and gateway

The `roles` JWT claim is a `List<String>` (`["ROLE_USER"]`). The gateway's `ReactiveJwtAuthenticationConverter` by default looks for `scope` or `scp` claim to create authorities, not `roles`. If the planner does not configure a custom converter, the gateway's `JwtAuthenticationToken` will have no authorities from the `roles` claim, meaning `hasRole()` checks would fail.

For Phase 3, the gateway only checks `.authenticated()` (not role-based), so this is not immediately blocking. But Phase 4 will need `hasRole("ADMIN")` for `POST /products`. The planner should note this as a Wave 2 task: configure `ReactiveJwtAuthenticationConverter` with `setJwtGrantedAuthoritiesConverter` that reads the `roles` claim.

**Sketch:**

```java
@Bean
public ReactiveJwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter rolesConverter = new JwtGrantedAuthoritiesConverter();
    rolesConverter.setAuthoritiesClaimName("roles");
    rolesConverter.setAuthorityPrefix(""); // already prefixed ROLE_ in the claim

    ReactiveJwtAuthenticationConverter converter = new ReactiveJwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(
            new ReactiveJwtGrantedAuthoritiesConverterAdapter(rolesConverter));
    return converter;
}
```

[VERIFIED: Context7 — `DelegatingJwtGrantedAuthoritiesConverter` pattern with `roles` claim name]

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | CONTEXT.md's "refreshTtl=1h" is an approximation of Nimbus internal lifespan timer, not a distinct Spring Security property. Default `NimbusReactiveJwtDecoder` JWKS caching is 5 minutes (Nimbus `DefaultJWKSetCache`). | JWK cache risk | No action required for v1 single-key non-rotation. Low risk. |
| A2 | RSA keys generated with `openssl genrsa` are always PKCS#8 CRT form, so `(RSAPrivateCrtKey)` cast and public-key derivation via `getPublicExponent()` will succeed. | RS256 keypair loading | If wrong: `ClassCastException` at startup. Mitigation: document `openssl genrsa` as the only supported key generation method. |
| A3 | `FOR UPDATE SKIP LOCKED` is supported by the Postgres JDBC driver and Hibernate 6.6.x in a Spring Data `@Query` annotation. | Outbox poller | If SKIP LOCKED is unsupported via @Query, use a native query (`nativeQuery=true`). PostgreSQL 16 definitely supports it. |

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| PostgreSQL 16 | identity-service Flyway + JPA | ✓ (docker-compose) | 16 (pgvector image) | — |
| RabbitMQ | outbox poller → `identity.tx` exchange | ✓ (docker-compose) | 4.3 | — |
| Java 21 | identity-service compilation + runtime | ✓ | 21 (Corretto, per Phase 1) | — |
| Gradle 8.10 | `:identity-service` subproject build | ✓ | 8.10 (locked in Phase 1) | — |
| Docker | Jib build + compose | ✓ | (Phase 1 verified) | — |

All dependencies available. No missing dependencies with no fallback.

---

## Verified External Refs

### Primary (HIGH confidence — verified via Context7 / official docs)

- [Spring Security 6.5 Reactive Resource Server JWT](https://docs.spring.io/spring-security/reference/6.5/reactive/oauth2/resource-server/jwt.html) — `NimbusReactiveJwtDecoder.withJwkSetUri()`, `JwtTimestampValidator` reactive usage, clock-skew wiring
- [Spring Security 6.5 `JwtTimestampValidator` API](https://docs.spring.io/spring-security/reference/6.5/api/java/org/springframework/security/oauth2/jwt/JwtTimestampValidator.html) — default clock skew is 60s; `JwtTimestampValidator(Duration)` constructor
- [Spring Security 6.5 `NimbusJwtEncoder` API](https://docs.spring.io/spring-security/reference/6.5/api/java/org/springframework/security/oauth2/jwt/NimbusJwtEncoder.html) — constructor takes `JWKSource<SecurityContext>`
- [Spring Security 6.5 `DelegatingJwtGrantedAuthoritiesConverter` API](https://docs.spring.io/spring-security/reference/6.5/api/java/org/springframework/security/oauth2/server/resource/authentication/DelegatingJwtGrantedAuthoritiesConverter.html) — roles claim converter pattern
- [RFC 7517 — JWK](https://datatracker.ietf.org/doc/html/rfc7517) — JWKS JSON field names (`kty`, `use`, `alg`, `kid`, `n`, `e`)
- [Flyway placeholders documentation](https://github.com/flyway/flyway/blob/main/documentation/Reference/Configuration/Flyway%20Namespace/Flyway%20Placeholders%20Namespace.md) — `spring.flyway.placeholders.*` YAML key and `${key}` SQL syntax

### Secondary (MEDIUM confidence — derived from issue analysis)

- [Spring Security issue #7474 — ReactiveRemoteJWKSource cache invalidation](https://github.com/spring-projects/spring-security/issues/7474) — confirms no configure-able refresh TTL in reactive decoder; 5-min Nimbus default
- [Spring Security issue #10686 — NimbusReactiveJwtDecoder custom WebClient](https://github.com/spring-projects/spring-security/issues/10686) — confirms custom WebClient injection path for `lb://` support (not recommended for v1)
- [Nimbus JOSE+JWT enhanced JWK retrieval](https://connect2id.com/products/nimbus-jose-jwt/examples/enhanced-jwk-retrieval) — `DefaultJWKSetCache` TTL defaults

---

## RESEARCH COMPLETE

Spring Security 6.x API shapes fully documented for both sides of the auth flow. The five key planner-facing findings are:

1. **Use docker-compose hostname, not `lb://`** for `jwk-set-uri` — reactive decoder does not natively support service-discovery URI schemes.
2. **Wire `JwtTimestampValidator(Duration.ofSeconds(30))` explicitly** — the default (60s) applies only if a custom validator is NOT set; `jwk-set-uri` config without issuer validation does not auto-wire the timestamp validator with a 30s skew.
3. **Gateway needs a custom `ReactiveJwtAuthenticationConverter`** for `roles` claim authority extraction — default converter reads `scope`/`scp`, not `roles`. Not blocking for Phase 3 (no role-based checks yet) but needed for Phase 4.
4. **`toPublicJWK()` on the JWKS endpoint** — must call `rsaJwk.toPublicJWK()` before serializing to JSON; the `RSAKey` bean holds both private and public material and must not be served raw.
5. **Identity-service also needs `spring-boot-starter-oauth2-resource-server`** to get `NimbusJwtEncoder`, `JwtClaimsSet`, and `JwsHeader` — even though it does not validate JWTs.
