# Phase 9 — Pattern Map

> Maps every new/modified file to its closest existing analog.
> Downstream planners and executors copy these patterns rather than invent new ones.

**Mapped:** 2026-05-01
**Files analyzed:** 22 new/modified files
**Analogs found:** 18 / 22 (4 have no codebase analog — documented in final section)

---

## File Classification

| New / Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---------------------|------|-----------|----------------|---------------|
| `mcp-server/build.gradle.kts` | config | — | `ai-service/build.gradle.kts` | exact (same plugin set; different deps) |
| `mcp-server/src/main/java/com/n11/mcp/McpServerApplication.java` | service-boot | — | `ai-service/src/main/java/com/n11/ai/AiServiceApplication.java` | exact |
| `mcp-server/src/main/java/com/n11/mcp/config/AgentToolMcpRegistration.java` | config/adapter | transform | no codebase analog — Spring AI API only |
| `mcp-server/src/main/java/com/n11/mcp/auth/AgentJwtCache.java` | service | request-response (lazy) | no codebase analog — `@PostConstruct`+`@Scheduled` is framework pattern |
| `mcp-server/src/main/java/com/n11/mcp/auth/AgentJwtClient.java` | service | request-response | `agent-toolset/src/main/java/com/n11/agent/http/ToolHttpClients.java` | partial (RestClient.Builder pattern) |
| `mcp-server/src/main/java/com/n11/mcp/auth/JwtBearerInterceptor.java` | middleware | request-response | `agent-toolset/src/main/java/com/n11/agent/http/ToolHttpClients.java` | partial (builder wiring) |
| `mcp-server/src/main/resources/application.yml` | config | — | `ai-service/src/main/resources/application.yml` + `service-template/skeleton/src-main/resources/application.yml.template` | exact |
| `mcp-server/src/test/java/com/n11/mcp/McpServerContextTest.java` | test | — | `infra-tests/.../ai/AiServiceClasspathTest.java` | role-match |
| `mcp-server/src/test/java/com/n11/mcp/AgentToolMcpRegistrationTest.java` | test | — | `agent-toolset/src/test/java/com/n11/agent/AgentToolRegistryTest.java` | role-match |
| `identity-service/.../agent/AgentExchangeController.java` | controller | request-response | `identity-service/.../auth/AuthController.java` | exact (same "verify cred → mint JWT → return DTO" shape) |
| `identity-service/.../agent/AgentExchangeService.java` | service | request-response | `identity-service/.../user/UserService.java` (login path) | role-match |
| `identity-service/.../agent/AgentApiKey.java` | model/entity | CRUD | `identity-service/.../address/Address.java` | exact (same UUID PK, Instant fields, no eager joins) |
| `identity-service/.../agent/AgentApiKeyRepository.java` | repository | CRUD | `identity-service/.../address/AddressRepository.java` | exact |
| `identity-service/.../auth/IdentitySecurityConfig.java` | config | — | same file (modify/verify only — already `anyRequest().permitAll()`) | exact |
| `identity-service/.../db/migration/V5__agent_api_keys.sql` | migration | — | `identity-service/.../db/migration/V4__init_outbox.sql` | exact |
| `identity-service/.../db/migration/R__seed_agent_api_keys.sql` | migration/seed | — | `identity-service/.../db/migration/V3__seed_admin.sql` | role-match |
| `agent-toolset/.../http/ToolHttpClients.java` | config (modify) | — | self (existing file — minimal interceptor registration addition) | self |
| `config-server/.../config/api-gateway.yml` | config (modify) | — | same file, `ai-service-chat-stream` route block (lines 108-117) | exact |
| `docker-compose.yml` | infra (modify) | — | `ai-service:` block (lines 465-492) | exact (no DB, no AMQP) |
| `settings.gradle.kts` | build (modify) | — | same file, `"ai-service"` and `"search-service"` lines | exact |
| `build.gradle.kts` (root) | build (modify) | — | same file, existing `mavenBom(...)` lines in `subprojects` block | exact |
| `infra-tests/.../mcp/McpServerTestConfig.java` | test-config | — | `infra-tests/.../saga/AiServiceTestConfig.java` | exact |
| `infra-tests/.../mcp/McpServerToolsListEqualityTest.java` | test | — | `infra-tests/.../ai/AiServiceClasspathTest.java` | role-match |

---

## Pattern Excerpts

### Pattern: Boot service application class

**Used by:** `mcp-server/src/main/java/com/n11/mcp/McpServerApplication.java`
**Analog:** `ai-service/src/main/java/com/n11/ai/AiServiceApplication.java` (lines 1-21)

```java
package com.n11.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = "com.n11")
@EntityScan(basePackages = "com.n11")   // mcp-server has NO entities — omit @EntityScan
@EnableDiscoveryClient
public class AiServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiServiceApplication.class, args);
    }
}
```

**Adaptation for mcp-server:** Drop `@EntityScan` (mcp-server is stateless, no JPA). Add `@EnableScheduling` because `AgentJwtCache` uses `@PostConstruct`-scheduled refresh. Keep `scanBasePackages = "com.n11"` so agent-toolset beans (`com.n11.agent.*`) are picked up.

---

### Pattern: Build module — service build.gradle.kts

**Used by:** `mcp-server/build.gradle.kts`
**Analog:** `ai-service/build.gradle.kts` (lines 1-65)

```kotlin
plugins {
    id("org.springframework.boot")
    id("com.google.cloud.tools.jib")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // NO spring-boot-starter-data-jpa  (ai-service has it; mcp-server does NOT)
    // NO springdoc  (mcp-server has no REST surface)
    // NO flyway     (mcp-server has no DB)
    // NO spring-amqp (mcp-server has no AMQP)

    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")
    implementation("org.springframework.cloud:spring-cloud-starter-config")

    implementation(project(":agent-toolset"))
    implementation(project(":common-error"))
    implementation(project(":common-logging"))

    runtimeOnly("net.logstash.logback:logstash-logback-encoder:8.0")
    // common-logging's CorrelationIdMessagePostProcessor implements spring-amqp
    // MessagePostProcessor; include spring-amqp (core only, NOT spring-rabbit) as
    // runtimeOnly to avoid RabbitAutoConfiguration — same fix ai-service uses (line 34).
    runtimeOnly("org.springframework.amqp:spring-amqp")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveBaseName.set("mcp-server")   // adapt from "ai-service"
}

jib {
    from { image = "eclipse-temurin:21-jre" }
    to { image = "n11/mcp-server:dev" }
    container {
        ports = listOf("8090")          // port 8090 per RESEARCH.md §Summary
        jvmFlags = listOf("-XX:+UseG1GC", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0")
    }
}
```

**Key diff vs ai-service:** Add `"org.springframework.ai:spring-ai-starter-mcp-server-webmvc"` (version managed by the Spring AI BOM added to root). Remove `spring-boot-starter-data-jpa`, `flyway-*`, `postgresql`, `springdoc-*`, `google-genai`, `ai-port` project dep.

---

### Pattern: Root BOM import addition

**Used by:** `build.gradle.kts` (root — modify)
**Analog:** `build.gradle.kts` lines 21-26 (existing BOM block)

```kotlin
subprojects {
    // ...
    extensions.configure<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension> {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:3.5.14")
            mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.0.0")
            mavenBom("org.testcontainers:testcontainers-bom:2.0.5")
            // ADD THIS LINE (Wave 0 blocker — Spring AI BOM not currently present):
            mavenBom("org.springframework.ai:spring-ai-bom:1.1.5")
        }
    }
}
```

---

### Pattern: settings.gradle.kts module addition

**Used by:** `settings.gradle.kts` (modify)
**Analog:** same file, lines 3-24 — the existing `include(...)` block

```kotlin
include(
    // ... existing entries ...
    "ai-service",
    "search-service",
    "mcp-server"      // ADD at end of list
)
```

---

### Pattern: service application.yml (config-server client bootstrap)

**Used by:** `mcp-server/src/main/resources/application.yml`
**Analogs:** `ai-service/src/main/resources/application.yml` (line 1-5) AND `service-template/skeleton/src-main/resources/application.yml.template` (lines 1-5)

```yaml
spring:
  application:
    name: mcp-server
  config:
    import: optional:configserver:${CONFIG_SERVER_URL:http://localhost:8888}
```

The `optional:` prefix (from service-template) is the production posture for cold-boot tolerance. The docker-compose override uses the full retry URL via `SPRING_CONFIG_IMPORT` env var (see docker-compose pattern below). The externalised MCP-specific config (`spring.ai.mcp.server.*`) lives in `config-server/src/main/resources/config/mcp-server.yml` (not in this file), following the same split as `ai-service.yml` vs `ai-service/src/main/resources/application.yml`.

---

### Pattern: JPA entity (UUID PK, Instant fields, no eager relation joins)

**Used by:** `identity-service/.../agent/AgentApiKey.java`
**Analog:** `identity-service/.../address/Address.java` (lines 1-83)

```java
package com.n11.identity.address;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "addresses")
public class Address {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;              // stored as raw UUID FK — no @ManyToOne

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Address() { /* JPA */ }

    public Address(UUID id, UUID userId, /* ... other fields ... */, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.createdAt = createdAt;
    }

    public UUID getId()       { return id; }
    public UUID getUserId()   { return userId; }
    public Instant getCreatedAt() { return createdAt; }
}
```

**Adaptation for `AgentApiKey`:** PK is `CHAR(64) api_key_hash` (String, not UUID). Add `agentLabel`, `lastUsedAt` (nullable `Instant`), `revokedAt` (nullable `Instant`). No `@ManyToOne` to `User` — store `userId` as raw `UUID` column (same pattern as `Address.userId`). The `@Entity(name = "AgentApiKey")` explicit name prevents infra-tests classpath collisions.

---

### Pattern: Spring Data JPA repository with custom query methods

**Used by:** `identity-service/.../agent/AgentApiKeyRepository.java`
**Analog:** `identity-service/.../address/AddressRepository.java` (lines 1-19)

```java
public interface AddressRepository extends JpaRepository<Address, UUID> {

    List<Address> findByUserIdOrderByCreatedAtDesc(UUID userId);

    @Modifying
    @Query("UPDATE Address a SET a.isDefault = false WHERE a.userId = :userId AND a.isDefault = true")
    int clearDefaultForUser(@Param("userId") UUID userId);
}
```

**Adaptation for `AgentApiKeyRepository`:** PK type is `String` (the hash). Custom finders:
- `Optional<AgentApiKey> findByApiKeyHashAndRevokedAtIsNull(String apiKeyHash)` — exact lookup, active keys only (derived method name, no @Query needed).
- `@Modifying @Query("UPDATE AgentApiKey k SET k.lastUsedAt = :now WHERE k.apiKeyHash = :hash")` — timestamp update on successful exchange.

---

### Pattern: Auth controller — "verify credential → mint JWT → return DTO"

**Used by:** `identity-service/.../agent/AgentExchangeController.java`
**Analog:** `identity-service/.../auth/AuthController.java` (lines 1-66)

```java
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest body) {
        return userService.login(body);   // delegate to service; controller stays thin
    }
}
```

**Adaptation for `AgentExchangeController`:** `@RequestMapping("/agents")`, single `@PostMapping("/exchange")`. Accepts `AgentExchangeRequest(@NotBlank String apiKey)`. Returns `AgentTokenResponse(String accessToken, long expiresIn)` — a trimmed `AuthResponse` without the `UserSummary` nested object (agent callers don't need user profile details).

Note from `IdentitySecurityConfig` (lines 28-34): `anyRequest().permitAll()` is already the rule — `/agents/exchange` requires NO security config change. The API-key check is inside the service layer, not Spring Security.

---

### Pattern: Auth service — credential verification + JWT issuance

**Used by:** `identity-service/.../agent/AgentExchangeService.java`
**Analog:** `identity-service/.../user/UserService.java` login path (lines 80-89) + `buildAuthResponse` (lines 105-110)

```java
@Transactional(readOnly = true)
public AuthResponse login(LoginRequest req) {
    User user = userRepository.findByEmail(req.email())
            .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "E-posta veya şifre hatalı"));
    if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "E-posta veya şifre hatalı");
    }
    return buildAuthResponse(user);
}

private AuthResponse buildAuthResponse(User user) {
    List<String> roles = roleNames(user);
    String token = jwtIssuerService.issue(user.getId(), user.getEmail(), user.getFullName(), roles);
    return AuthResponse.bearer(token, jwtIssuerService.tokenLifetimeSeconds(), summary);
}
```

**Adaptation for `AgentExchangeService`:**
1. Hash the incoming plaintext key: `String hash = Hex.encodeHexString(DigestUtils.sha256(req.apiKey()))` (Apache Commons Codec — on classpath via Spring Security).
2. Look up active key: `agentApiKeyRepository.findByApiKeyHashAndRevokedAtIsNull(hash).orElseThrow(UNAUTHORIZED)`.
3. Update audit timestamp: `agentApiKeyRepository.updateLastUsed(key.apiKeyHash(), Instant.now())`.
4. Mint JWT via `jwtIssuerService.issue(key.userId(), key.agentLabel(), "Agent", List.of("ROLE_USER"))` — identical call signature to `UserService.buildAuthResponse`.
5. Return `AgentTokenResponse(token, jwtIssuerService.tokenLifetimeSeconds())`.

---

### Pattern: Flyway forward migration — table + index

**Used by:** `identity-service/src/main/resources/db/migration/V5__agent_api_keys.sql`
**Analog:** `identity-service/src/main/resources/db/migration/V4__init_outbox.sql` (lines 1-14) — most recent migration, cleanest style

```sql
-- V4__init_outbox.sql
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

**Style conventions extracted:**
- Header comment with filename.
- `CREATE TABLE` with inline column-type comments where needed.
- `TIMESTAMPTZ` for all timestamps.
- `NULL` suffix on nullable columns for explicitness.
- One `CREATE INDEX` per significant lookup pattern.
- No `IF NOT EXISTS` on indexes (Flyway versioned migrations only run once).

**Adaptation for `V5__agent_api_keys.sql`:**
```sql
-- V5__agent_api_keys.sql
CREATE TABLE agent_api_keys (
    api_key_hash  CHAR(64)     PRIMARY KEY,   -- SHA-256 base16, never plaintext
    agent_label   VARCHAR(100) NOT NULL,
    user_id       UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_used_at  TIMESTAMPTZ  NULL,
    revoked_at    TIMESTAMPTZ  NULL            -- NULL = active
);
```
No seed INSERT in V5 — seed is handled by `R__seed_agent_api_keys.sql` (see next pattern).

---

### Pattern: Flyway seed (repeatable) migration

**Used by:** `identity-service/src/main/resources/db/migration/R__seed_agent_api_keys.sql`
**Analog:** `identity-service/src/main/resources/db/migration/V3__seed_admin.sql` (lines 1-15) — closest seed analog in the project (no R__ migrations exist yet; V3 is the pattern to follow for `ON CONFLICT DO NOTHING` idempotent seeding)

```sql
-- V3__seed_admin.sql
INSERT INTO users (id, email, password_hash, full_name, created_at)
VALUES (gen_random_uuid(), '${adminSeedEmail}', '${adminSeedPasswordHash}', 'Admin', now())
ON CONFLICT (email) DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u JOIN roles r ON r.name = 'ROLE_ADMIN'
WHERE u.email = '${adminSeedEmail}'
ON CONFLICT DO NOTHING;
```

**Style conventions extracted:**
- Flyway placeholder substitution `${varName}` for runtime-injected values.
- `ON CONFLICT ... DO NOTHING` for idempotent seeds (safe on repeatable migration re-run).
- SELECT + INSERT instead of hardcoded FK values.

**Adaptation for `R__seed_agent_api_keys.sql`:**
```sql
-- R__seed_agent_api_keys.sql
-- Seeds one demo agent bound to the admin user.
-- MCP_AGENT_SEED_HASH must be SHA-256 base16 of the demo API key.
-- Plaintext key is in .env (never committed). Repeatable: re-runs are idempotent.
INSERT INTO agent_api_keys (api_key_hash, agent_label, user_id)
SELECT '${mcpAgentSeedHash}', 'demo-agent', u.id
FROM users u
WHERE u.email = '${adminSeedEmail}'
ON CONFLICT DO NOTHING;
```

---

### Pattern: docker-compose service entry (no DB, no AMQP)

**Used by:** `docker-compose.yml` — new `mcp-server:` block
**Analog:** `docker-compose.yml` `ai-service:` block (lines 465-492) — closest sibling: Spring Boot service, depends on identity + eureka + config, NO postgres dep (ai-service dropped postgres dep; mcp-server also has none)

```yaml
  ai-service:
    image: n11/ai-service:dev
    container_name: n11-ai-service
    env_file:
      - .env
    environment:
      SPRING_CONFIG_IMPORT: configserver:http://config-server:8888?fail-fast=true&max-attempts=10&max-interval=1500&multiplier=1.2&initial-interval=1100
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka-server:8761/eureka/
      SPRING_PROFILES_ACTIVE: docker
      GEMINI_API_KEY: ${GEMINI_API_KEY:-}    # service-specific env var pattern
    depends_on:
      postgres:
        condition: service_healthy           # ai-service keeps postgres for conversation store
      eureka-server:
        condition: service_healthy
      config-server:
        condition: service_healthy
      identity-service:
        condition: service_healthy
    healthcheck:
      test: ["CMD-SHELL", "wget -q -O- http://localhost:8088/actuator/health | grep -q '\"status\":\"UP\"'"]
      interval: 5s
      timeout: 3s
      retries: 6
      start_period: 25s
    restart: unless-stopped
    networks:
      - n11-net
```

**Adaptation for mcp-server:** Remove `postgres:` depends_on (mcp-server has no DB). Remove `rabbitmq:` depends_on (no AMQP). Add `MCP_TRANSPORT: http` and `MCP_API_KEY: ${MCP_API_KEY}` env entries. Change port in healthcheck to `8090`. No `ports:` mapping (gateway-only entry per Pitfall #14). D-07 lazy exchange means `identity-service: service_healthy` is a nice-to-have but not strictly required at boot — keep it to match the dependency chain.

---

### Pattern: api-gateway route with response-timeout override

**Used by:** `config-server/src/main/resources/config/api-gateway.yml` — new `/mcp/**` route block
**Analog:** same file, `ai-service-chat-stream` block (lines 108-117) — the only route currently using `response-timeout: -1`

```yaml
            - id: ai-service-chat-stream
              uri: lb://AI-SERVICE
              predicates:
                - Path=/api/v1/chat/stream/**
              metadata:
                response-timeout: -1
                connect-timeout: 5000
              filters:
                - StripPrefix=2
                - PreserveHostHeader=true
```

**Adaptation for mcp-server:** Route id `mcp-server`. URI `lb://MCP-SERVER`. Predicate `Path=/mcp/**`. `StripPrefix=0` (the Streamable HTTP endpoint IS `/mcp`, not a subpath to strip). Keep `response-timeout: -1` and `connect-timeout: 5000`. Keep `PreserveHostHeader=true`. The security config already covers this path with `anyExchange().authenticated()` — no allowlist change needed.

---

### Pattern: infra-tests TestConfig (Plan 05-04 classpath disambiguation)

**Used by:** `infra-tests/.../mcp/McpServerTestConfig.java`
**Analog:** `infra-tests/.../saga/AiServiceTestConfig.java` (lines 1-47) — most recent TestConfig, cleanest template

```java
@SpringBootApplication(scanBasePackages = {
    "com.n11.ai",       // ai-service domain + application + infrastructure + interfaces
    "com.n11.agent",    // agent-toolset (10 tools + ToolRegistry)
    "com.n11.error",    // common-error
    "com.n11.logging"   // common-logging
})
@ComponentScan(
    basePackages = { "com.n11.ai", "com.n11.agent", "com.n11.error", "com.n11.logging" },
    excludeFilters = {
        @ComponentScan.Filter(
            type = FilterType.ANNOTATION,
            classes = org.springframework.boot.autoconfigure.SpringBootApplication.class
        )
    }
)
@EntityScan(basePackages = {"com.n11.ai"})
@EnableJpaRepositories(basePackages = {"com.n11.ai"})
public class AiServiceTestConfig { }
```

**Adaptation for `McpServerTestConfig`:**
- `scanBasePackages`: `"com.n11.mcp"`, `"com.n11.agent"`, `"com.n11.error"`, `"com.n11.logging"`.
- Remove `@EntityScan` and `@EnableJpaRepositories` — mcp-server has NO JPA entities.
- Keep the `excludeFilters` block exactly as shown (blocks all `@SpringBootApplication` classes from expanding scan).
- mcp-server has `@EnableScheduling` on its main application class — the TestConfig should NOT enable scheduling in tests (avoids the JWT refresh scheduler firing during test startup). Override with `spring.scheduling.enabled=false` in `@TestPropertySource`.

---

### Pattern: infra-tests integration test (Spring context boot + bean assertions)

**Used by:** `infra-tests/.../mcp/McpServerToolsListEqualityTest.java`
**Analog:** `infra-tests/.../ai/AiServiceClasspathTest.java` (lines 1-51)

```java
@SpringBootTest(classes = AiServiceTestConfig.class,
                webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@TestPropertySource(properties = {
    "ai.provider=echo",
    "spring.cloud.config.enabled=false",
    "spring.cloud.discovery.enabled=false",
    "eureka.client.enabled=false",
    "spring.flyway.locations=classpath:db/migration/ai",
    "spring.flyway.schemas=public",
    "spring.flyway.default-schema=public",
    "spring.jpa.properties.hibernate.default_schema=public",
    "spring.datasource.hikari.connection-init-sql=CREATE EXTENSION IF NOT EXISTS pgcrypto"
})
class AiServiceClasspathTest {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired ChatProvider chatProvider;
    @Autowired ToolRegistry toolRegistry;

    @Test
    void context_boots_with_echo_provider_and_10_tools() {
        assertThat(toolRegistry.all()).hasSize(10);
    }
}
```

**Adaptation for `McpServerToolsListEqualityTest`:**
- `classes = McpServerTestConfig.class`.
- No `@Container` or `PostgreSQLContainer` — mcp-server has no DB; `webEnvironment = NONE`.
- `@TestPropertySource` properties: `spring.cloud.config.enabled=false`, `spring.cloud.discovery.enabled=false`, `eureka.client.enabled=false`, `mcp.api-key=test-key-doesnt-matter`, `spring.ai.mcp.server.stdio=false`, `spring.scheduling.enabled=false`.
- Key assertion: `@Autowired ToolCallbackProvider toolCallbackProvider; @Autowired ToolRegistry toolRegistry;` — then assert `toolCallbackProvider` yields callbacks whose names equal `toolRegistry.all().stream().map(AgentTool::name).collect(toSet())` and count == 10.

---

### Pattern: ToolHttpClients RestClient.Builder (for interceptor registration)

**Used by:** `agent-toolset/.../http/ToolHttpClients.java` (modify) + `mcp-server/auth/JwtBearerInterceptor.java` (new)
**Analog:** `agent-toolset/.../http/ToolHttpClients.java` lines 1-22 (existing — the file to modify)

```java
@Configuration
public class ToolHttpClients {

    @Bean
    @ConditionalOnMissingBean
    RestClient.Builder toolRestClientBuilder() {
        return RestClient.builder();
    }
}
```

The existing `@ConditionalOnMissingBean` pattern means ai-service's `common-logging.RestClientConfig` wins when it provides a pre-wired Builder (and it does — correlation-ID interceptor). For mcp-server, the same mechanism works: mcp-server provides a `RestClient.Builder` bean that includes `JwtBearerInterceptor`, and `@ConditionalOnMissingBean` ensures `ToolHttpClients`' fallback bean is skipped.

**New file `JwtBearerInterceptor.java` — Spring's `ClientHttpRequestInterceptor` interface:**
```java
// No codebase analog — Spring framework interface only
// Spring docs reference: https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/http/client/ClientHttpRequestInterceptor.html

@Component
public class JwtBearerInterceptor implements ClientHttpRequestInterceptor {

    private final AgentJwtCache jwtCache;

    public JwtBearerInterceptor(AgentJwtCache jwtCache) {
        this.jwtCache = jwtCache;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        request.getHeaders().setBearerAuth(jwtCache.bearerToken());
        return execution.execute(request, body);
    }
}
```

Register on the shared builder via a `@Bean RestClient.Builder toolRestClientBuilder(JwtBearerInterceptor interceptor) { return RestClient.builder().requestInterceptor(interceptor); }` in mcp-server's own `@Configuration` class — this wins over `ToolHttpClients`' `@ConditionalOnMissingBean` default.

---

## Files With No Codebase Analog (research-driven)

| File | Role | Data Flow | Reason | Primary Source |
|------|------|-----------|--------|----------------|
| `AgentToolMcpRegistration.java` | config/adapter | transform | No Spring AI MCP adapter exists anywhere in the codebase | RESEARCH.md Pattern 1 + [Spring AI Tools docs](https://docs.spring.io/spring-ai/reference/api/tools.html) + [MCP stateless server docs](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-stateless-server-boot-starter-docs.html). Key API: `ToolDefinition.builder().name().description().inputSchema(String)`, `FunctionToolCallback.builder(name, fn).toolDefinition(def).inputType(JsonNode.class).build()`, `ToolCallbackProvider.from(List<ToolCallback>)`. |
| `AgentJwtCache.java` | service/cache | lazy request-response | No JWT cache or scheduled-refresh component exists in the codebase | RESEARCH.md Pattern 3. Spring framework patterns: `@PostConstruct` schedules a `ScheduledExecutorService` minutely refresh; `synchronized` methods protect the `volatile jwt` field; `@PreDestroy` shuts down executor. |
| `AgentJwtClient.java` | service | request-response | No service-to-service API-key exchange client exists | RESEARCH.md Pattern 4. Uses `RestClient` (same as ToolHttpClients) to POST `{ "apiKey": "..." }` to `lb://IDENTITY-SERVICE/agents/exchange`. Record DTO `ExchangeResponse(String accessToken, long expiresIn)` mirrors `AuthResponse`. |
| `config-server/.../config/mcp-server.yml` | config | — | No Spring AI MCP server config exists in config-server | RESEARCH.md Pattern 2. Spring AI `spring.ai.mcp.server.*` property namespace. |

---

## Cross-cutting Conventions

All of the following are established project-wide patterns (visible in every existing service) that mcp-server and the identity-service additions must also follow:

1. **SLF4J via common-logging** — `implementation(project(":common-logging"))` in build.gradle.kts; logback-spring.xml from service-template; MDC correlation ID propagated automatically via `CorrelationIdRestClientInterceptor`. The `RestClient.Builder` from `common-logging.RestClientConfig` already carries this interceptor — mcp-server's `JwtBearerInterceptor` registration stacks on top of it.

2. **RFC-7807 error shape via common-error** — `implementation(project(":common-error"))` in build.gradle.kts; `ResponseStatusException` used for all HTTP errors (as in `AuthController`, `UserService`, `AddressRepository`). `AgentExchangeController` and `AgentExchangeService` follow the same `throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "...")` pattern.

3. **Java 21 toolchain** — configured globally in `build.gradle.kts` `subprojects { extensions.configure<JavaPluginExtension> { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } } }`. No per-service toolchain declaration needed.

4. **Jib image** — `id("com.google.cloud.tools.jib")` plugin + `from { image = "eclipse-temurin:21-jre" }` + G1GC/ContainerSupport JVM flags. Port listed in `container.ports`.

5. **No ports: in docker-compose** — internal-only services (Pitfall #14). Gateway is the sole public face. mcp-server follows this; no `ports:` mapping.

6. **All env vars via `${VAR}` substitution** — values read from `.env` via `env_file: .env`. Service-specific keys (like `MCP_API_KEY`) are also placed in `.env.example` as empty placeholders.

7. **`spring.config.import` retry URL** — the full retry URL (`fail-fast=true&max-attempts=10&max-interval=1500&multiplier=1.2&initial-interval=1100`) is passed as `SPRING_CONFIG_IMPORT` in docker-compose `environment:`, not baked into `application.yml` (which uses the simple `optional:configserver:` form). This split keeps the service's own `application.yml` minimal.

8. **`@EnableDiscoveryClient`** on application class — all services that register with Eureka use this annotation. mcp-server needs it for `lb://` URI resolution in the tool HTTP clients.

9. **`@Transactional` in service layer, read-only where possible** — `UserService.login` uses `@Transactional(readOnly = true)`; `AgentExchangeService.exchange` follows the same convention (read hash lookup + write last_used_at uses `@Transactional` without readOnly because of the UPDATE).

10. **English identifiers, Turkish user-facing strings** — error messages thrown from identity-service (`"E-posta veya şifre hatalı"`) are Turkish; code identifiers and log messages are English. `AgentExchangeService` error messages follow: Turkish for user-visible errors thrown as `ResponseStatusException`, English for log messages.

---

## Metadata

**Analog search scope:** `ai-service/`, `identity-service/`, `agent-toolset/`, `infra-tests/`, `service-template/skeleton/`, `config-server/src/main/resources/config/`, `docker-compose.yml`, `build.gradle.kts`, `settings.gradle.kts`
**Files read (not re-read):** 28 distinct files
**Pattern extraction date:** 2026-05-01
