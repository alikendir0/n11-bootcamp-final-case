# Phase 9: MCP Server - Research

**Researched:** 2026-05-01
**Domain:** Spring AI MCP Server (WebMVC), MCP spec 2025-06-18 Streamable HTTP, identity-service agent key exchange, agent-toolset DRY registration
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** One Boot app, both transports. Same Jib image. `MCP_TRANSPORT=http` for compose; `MCP_TRANSPORT=stdio` for Claude Desktop `docker run -e`. (Corrected below: stdio activated via `spring.ai.mcp.server.stdio=true`, not a protocol property — see Standard Stack section.)
- **D-02:** Streamable HTTP only — no deprecated SSE. `spring.ai.mcp.server.protocol=STREAMABLE`.
- **D-03:** Share gateway tunnel under `/mcp/**`. Resolves Open Question 2.
- **D-04:** Claude Desktop demo path uses `docker run -i` against Jib image.
- **D-05:** DB-backed API keys: `agent_api_keys(api_key_hash CHAR(64) PK, agent_label VARCHAR, user_id UUID FK, created_at TIMESTAMPTZ, last_used_at TIMESTAMPTZ NULL, revoked_at TIMESTAMPTZ NULL)`. SHA-256 base16. New Flyway migration in identity-service (next: V5).
- **D-06:** JWT sub = real user_id. ROLE_USER. 24h. Same RS256 path as login.
- **D-07:** AgentJwtCache: lazy exchange on first tool call; scheduled refresh every minute, re-exchange when expiresAt - now < 5 min.
- **D-08:** MCP_API_KEY via env var. mcp-server reads at boot via `@Value("${mcp.api-key}")`.
- **D-09:** Adapter at startup — NOT `@McpTool` annotations. `AgentToolMcpRegistration @Configuration` wraps each `AgentTool` as `ToolCallbackProvider`. Zero `@McpTool` in mcp-server.
- **D-10:** `ToolContext` per MCP tool call: userId from cached JWT.sub, correlationId from `X-Correlation-Id` or generated UUID, seenIds = `Set.of()`.
- **D-11:** Tool dispatch via Eureka direct (`lb://`). No new HTTP client classes.
- **D-12:** `infra-tests` integration test: `McpServerToolsListEqualityTest`. Asserts 10 tools, names + schemas identical to `ToolRegistry.all()`.
- **D-13:** No seenIds tracking in mcp-server (live-lookup-only provenance). D-15 verification required.
- **D-14:** Backing-service errors → `ToolResult.Err` with semantic codes.
- **D-15 (research item):** Verify zero `seenIds` reads in tool implementations. **Result: CONFIRMED ZERO** — grep of `agent-toolset/src/main/java/com/n11/agent/tools/` returns no matches.

### Claude's Discretion

- Spring AI `ToolCallback` vs `ToolDefinition` API surface — resolved below.
- Streamable HTTP route metadata for gateway — resolved below.
- Hash algorithm — SHA-256 base16 confirmed per D-05.
- MCP capability negotiation flags — resolved below.
- `MCP_EAGER_EXCHANGE` switch — recommend lazy (default D-07).
- Springdoc surface for mcp-server — skip (no REST surface).
- Bean disambiguation in infra-tests — preemptive pattern documented.
- `/agents/exchange` request body shape — resolved below (JSON body preferred).

### Deferred Ideas (OUT OF SCOPE)

- Per-agent role scoping (ROLE_AGENT_READONLY).
- Multi-key per mcp-server instance.
- MCP resources and prompts capabilities.
- Stronger key hashing (HMAC + server pepper).
- API key rotation endpoint.
- ROLE_AGENT audit claim.
- MCP_EAGER_EXCHANGE=true flag.
- Streamable HTTP keep-alive tuning beyond defaults.
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| AI-11 | mcp-server registers the SAME agent-toolset (single source of truth) via `spring-ai-starter-mcp-server-webmvc 1.1.5` | D-09 adapter pattern: `ToolCallbackProvider.from(List<ToolCallback>)` wraps ToolRegistry beans |
| AI-12 | mcp-server supports stdio transport (Claude Desktop) AND Streamable HTTP (network); both wired | `spring.ai.mcp.server.stdio=true` activates stdio alongside `protocol=STREAMABLE`; single webmvc starter |
| AI-13 | Auth bridge: `MCP_API_KEY` → identity-service `/agents/exchange` → JWT propagates like normal user | `agent_api_keys` table + V5 migration; `AgentJwtCache`; userId in ToolContext |
</phase_requirements>

---

## Summary

Phase 9 stands up a new `mcp-server` Spring Boot service (port 8090) that re-exposes the Phase 8 `agent-toolset` via the MCP wire protocol. The service has zero local tool definitions — `AgentToolMcpRegistration` wraps every `AgentTool` bean from `ToolRegistry` into a `ToolCallbackProvider` that Spring AI's MCP starter auto-publishes. This structural DRY proof (SC-4) is the grading wedge: a grep or integration test trivially proves no tool definitions live in mcp-server.

Transport architecture is simpler than initially assumed. `spring-ai-starter-mcp-server-webmvc 1.1.5` supports both Streamable HTTP (via `spring.ai.mcp.server.protocol=STREAMABLE`) and stdio (via `spring.ai.mcp.server.stdio=true`) from a single dependency. D-01's one-image-two-transports plan is implementable with a single starter. The MCP endpoint for Streamable HTTP is `/mcp` by default (configurable via `spring.ai.mcp.server.streamable-http.mcp-endpoint`). The older SSE transport used `/mcp/messages`; Streamable HTTP uses `/mcp`.

The auth bridge requires a new Flyway V5 migration in identity-service adding `agent_api_keys`, a new `AgentExchangeController` with a `POST /agents/exchange` JSON body endpoint, and `AgentJwtCache` in mcp-server that lazily calls the exchange on first tool invocation. Since the tool HTTP clients already pass `X-User-Id` per-call from `ctx.userId()`, no RestClient interceptor for Authorization is needed — only the userId extracted from the cached JWT is required to populate `ToolContext`.

**Primary recommendation:** One webmvc starter + `spring.ai.mcp.server.protocol=STREAMABLE` + `spring.ai.mcp.server.stdio=true` + `ToolCallbackProvider.from(...)` bean. Spring AI BOM 1.1.5 must be added to the root `build.gradle.kts` BOM imports (it is NOT currently imported).

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| MCP wire protocol (tools/list, tools/call) | mcp-server | — | Spring AI starter handles JSON-RPC; mcp-server owns the adapter |
| Tool execution / HTTP calls to backing services | agent-toolset (shared) | mcp-server (ToolContext builder) | Tools live in agent-toolset; mcp-server only builds ToolContext |
| API key → JWT exchange | identity-service | mcp-server (JWT cache) | identity-service is the auth authority; mcp-server holds the cached JWT |
| Gateway auth enforcement on /mcp/** | api-gateway | — | `anyExchange().authenticated()` already covers /mcp/**; no allowlist needed |
| Cart/order/product/payment mutations | cart/order/product/payment services | agent-toolset HTTP clients | Backing services own the data; tools call them via Eureka direct |
| stdio transport for Claude Desktop | mcp-server | — | Same process, different transport mode |

---

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| spring-ai-starter-mcp-server-webmvc | 1.1.5 | MCP wire protocol, tool registration, Streamable HTTP + stdio transports | Single starter for both transports; auto-discovers ToolCallbackProvider beans |
| agent-toolset (project module) | 0.0.1-SNAPSHOT | 10 AgentTool beans + ToolRegistry | Phase 9 imports this unchanged; no edits to the module |
| spring-boot-starter-web | (Boot BOM 3.5.14) | Required by spring-ai-starter-mcp-server-webmvc (Spring MVC runtime) | WebMVC servlet container for MCP HTTP endpoint |
| spring-ai-bom | 1.1.5 | Version management for Spring AI artifacts | Must be added to root build.gradle.kts BOM imports |

[VERIFIED: Maven Central repo1.maven.org — `spring-ai-starter-mcp-server-webmvc:1.1.5` confirmed published]
[VERIFIED: Spring AI docs — `spring-ai-bom:1.1.5` present in BOM artifact]

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| common-logging (project module) | project | CorrelationId MDC propagation | All business services; replaces gateway's GatewayCorrelationIdFilter |
| common-error (project module) | project | RFC-7807 error shape | All business services |
| spring-cloud-starter-netflix-eureka-client | (Cloud BOM 2025.0.0) | lb:// URI resolution for tool HTTP clients | Required by ToolHttpClients RestClient.Builder |
| spring-cloud-starter-config | (Cloud BOM 2025.0.0) | Config-server client | Standard across all business services |
| springdoc-openapi-starter-webmvc-ui | 2.8.17 | OpenAPI docs | SKIP on mcp-server — MCP is JSON-RPC, no REST surface to document |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| spring-ai-starter-mcp-server-webmvc | spring-ai-starter-mcp-server (stdio only) | stdio-only starter cannot serve Streamable HTTP; webmvc starter handles both |
| ToolCallbackProvider.from(List) | @McpTool annotations | Annotations require tool metadata in mcp-server source; breaks DRY (D-09, CLAUDE.md Rule #2) |
| SHA-256 base16 for api_key_hash | BCrypt | BCrypt is for password verification (unknown plaintext); key lookup needs exact hash equality — SHA-256 is correct for this use case |

**Installation (mcp-server/build.gradle.kts):**
```kotlin
dependencies {
    // Spring AI MCP Server
    implementation("org.springframework.ai:spring-ai-starter-mcp-server-webmvc")
    // Shared toolset (no edits to module)
    implementation(project(":agent-toolset"))
    implementation(project(":common-error"))
    implementation(project(":common-logging"))
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")
    implementation("org.springframework.cloud:spring-cloud-starter-config")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // NO springdoc (MCP is not REST)
    // NO spring-amqp / common-events / common-outbox (mcp-server is AMQP-free in v1)
    // NO spring-boot-starter-data-jpa (mcp-server has NO DB)
    runtimeOnly("net.logstash.logback:logstash-logback-encoder:8.0")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
```

**Root build.gradle.kts BOM import addition (REQUIRED — Spring AI BOM is NOT currently in root):**
```kotlin
mavenBom("org.springframework.ai:spring-ai-bom:1.1.5")
```
Add alongside the existing `spring-boot-dependencies` and `spring-cloud-dependencies` imports in the `subprojects { extensions.configure<DependencyManagementExtension> { imports { ... } } }` block.

[VERIFIED: current root build.gradle.kts does NOT include Spring AI BOM — ai-service declares google-genai directly with explicit version, uses NO Spring AI artifacts]

---

## Architecture Patterns

### System Architecture Diagram

```
Claude Desktop (stdio)            External MCP Client (HTTP)
       |                                    |
       | docker run -e MCP_TRANSPORT=stdio  | POST /mcp
       |                                    | Authorization: Bearer <exchanged-JWT>
       +------------+       +--------------+
                    |       |
              [mcp-server :8090]
              spring.ai.mcp.server.protocol=STREAMABLE
              spring.ai.mcp.server.stdio=true
                    |
         AgentToolMcpRegistration
         ToolCallbackProvider (10 tools)
                    |
              AgentJwtCache
              (lazy JWT from /agents/exchange)
                    |
         ToolContext(userId=JWT.sub, correlationId, seenIds=Set.of())
                    |
            agent-toolset ToolRegistry (10 AgentTool beans)
            /    |     |     \
    CartToolClient  OrderToolClient  ProductToolClient  PaymentToolClient
         |              |                |                   |
    lb://CART   lb://ORDER-SERVICE  lb://PRODUCT-SERVICE  lb://PAYMENT-SERVICE
    (Eureka direct — NOT gateway-relayed)

Auth bridge:
    mcp-server  ---POST /agents/exchange---> identity-service :8081
                <---{ accessToken, expiresIn }---
```

### Recommended Project Structure
```
mcp-server/
├── build.gradle.kts              # Spring AI + agent-toolset + common-* deps
├── src/main/java/com/n11/mcp/
│   ├── McpServerApplication.java         # @SpringBootApplication
│   ├── config/
│   │   └── AgentToolMcpRegistration.java # @Configuration: ToolRegistry -> ToolCallbackProvider
│   ├── auth/
│   │   ├── AgentJwtCache.java            # JWT lazy-exchange + scheduled refresh
│   │   └── AgentExchangeClient.java      # RestClient for POST /agents/exchange
│   └── infra/
│       └── McpCorrelationFilter.java     # (optional) X-Correlation-Id on MCP calls
└── src/main/resources/
    └── application.yml                   # spring.ai.mcp.server.* config
config-server/src/main/resources/config/
└── mcp-server.yml                        # externalised config (MCP_API_KEY etc.)
```

identity-service additions:
```
identity-service/
├── src/main/java/com/n11/identity/
│   └── agent/
│       ├── AgentApiKey.java              # @Entity
│       ├── AgentApiKeyRepository.java
│       ├── AgentExchangeService.java     # validates hash, mints JWT
│       └── AgentExchangeController.java  # POST /agents/exchange
└── src/main/resources/db/migration/
    └── V5__agent_api_keys.sql            # CREATE TABLE + seed demo key
```

### Pattern 1: ToolCallbackProvider from AgentTool Beans (D-09)

The canonical adapter pattern — wrap every `AgentTool` from `ToolRegistry` into a `ToolCallback` using `ToolDefinition.builder()` with the existing `parametersJsonSchema()` string.

```java
// Source: https://docs.spring.io/spring-ai/reference/api/tools.html (ToolDefinition.builder + explicit inputSchema)
// Source: https://docs.spring.io/spring-ai/reference/api/mcp/mcp-stateless-server-boot-starter-docs.html (ToolCallbackProvider.from)

@Configuration
public class AgentToolMcpRegistration {

    private final ToolRegistry toolRegistry;
    private final AgentJwtCache jwtCache;

    public AgentToolMcpRegistration(ToolRegistry toolRegistry, AgentJwtCache jwtCache) {
        this.toolRegistry = toolRegistry;
        this.jwtCache = jwtCache;
    }

    @Bean
    public ToolCallbackProvider agentTools() {
        List<ToolCallback> callbacks = toolRegistry.all().stream()
            .map(this::toToolCallback)
            .toList();
        return ToolCallbackProvider.from(callbacks);
    }

    private ToolCallback toToolCallback(AgentTool tool) {
        ToolDefinition definition = ToolDefinition.builder()
            .name(tool.name())
            .description(tool.descriptionTr())
            .inputSchema(tool.parametersJsonSchema())
            .build();

        return FunctionToolCallback.builder(tool.name(), (JsonNode args) -> {
                String userId = jwtCache.userId();              // JWT.sub from cached exchange
                String correlationId = MDC.get("correlationId"); // from common-logging filter
                if (correlationId == null) correlationId = UUID.randomUUID().toString();
                ToolContext ctx = new ToolContext(userId, correlationId, Set.of());
                ToolResult result = tool.execute(ctx, args);
                // Spring AI serialises ToolResult back as JSON; Err surfaces as error message
                return result;
            })
            .toolDefinition(definition)
            .inputType(JsonNode.class)
            .build();
    }
}
```

[CITED: https://docs.spring.io/spring-ai/reference/api/tools.html — `ToolDefinition.builder().inputSchema(String)` confirmed; `FunctionToolCallback.builder()` confirmed]
[CITED: https://docs.spring.io/spring-ai/reference/api/mcp/mcp-stateless-server-boot-starter-docs.html — `ToolCallbackProvider.from(List<ToolCallback>)` confirmed]

**Implementation note:** `FunctionToolCallback` accepts `inputType(JsonNode.class)` to avoid Spring AI generating a schema from reflection; the `inputSchema` is provided explicitly from `tool.parametersJsonSchema()`. The function signature `Function<JsonNode, T>` is the cleanest match for our `execute(ctx, args)` contract.

### Pattern 2: Transport Configuration (D-01 + D-02)

```yaml
# config-server/src/main/resources/config/mcp-server.yml
spring:
  ai:
    mcp:
      server:
        name: n11-storefront
        version: 1.0.0
        type: SYNC
        protocol: STREAMABLE          # D-02: Streamable HTTP (not SSE)
        stdio: true                   # D-01: stdio also active (env-switched at Claude Desktop)
        streamable-http:
          mcp-endpoint: /mcp          # DEFAULT — confirmed in docs; explicit for clarity
        capabilities:
          tool: true
          resource: false             # D-v1: only tools
          prompt: false               # D-v1: only tools
```

Startup log (Pitfall #15 probe):
```
Logging at boot: "MCP transport: STREAMABLE + stdio=true, capabilities: tools only, Spring AI: 1.1.5"
```

[CITED: https://docs.spring.io/spring-ai/reference/api/mcp/mcp-streamable-http-server-boot-starter-docs.html — `protocol: STREAMABLE`, `mcp-endpoint`, `keep-alive-interval`]
[CITED: https://docs.spring.io/spring-ai/reference/api/mcp/mcp-stdio-sse-server-boot-starter-docs.html — `stdio: true` activates optional stdio on the webmvc starter]

### Pattern 3: AgentJwtCache (D-07)

```java
@Component
public class AgentJwtCache {

    private final AgentExchangeClient exchangeClient;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "agent-jwt-refresher"); t.setDaemon(true); return t;
    });
    private volatile String jwt;
    private volatile Instant expiresAt = Instant.EPOCH;

    public AgentJwtCache(AgentExchangeClient exchangeClient) {
        this.exchangeClient = exchangeClient;
    }

    @PostConstruct
    void schedule() {
        scheduler.scheduleAtFixedRate(this::refreshIfNeeded, 1, 1, TimeUnit.MINUTES);
    }

    @PreDestroy
    void shutdown() { scheduler.shutdownNow(); }

    /** userId for ToolContext — triggers lazy exchange on first call */
    public synchronized String userId() {
        ensureValid();
        return JwtUtils.extractSub(jwt);  // parse JWT.sub claim
    }

    public synchronized String bearerToken() {
        ensureValid();
        return jwt;
    }

    private synchronized void ensureValid() {
        if (jwt == null || Instant.now().isAfter(expiresAt.minus(Duration.ofMinutes(5)))) {
            exchange();
        }
    }

    private void refreshIfNeeded() {
        try { ensureValid(); } catch (Exception e) { log.warn("JWT refresh failed", e); }
    }

    private void exchange() {
        ExchangeResponse resp = exchangeClient.exchange();
        this.jwt = resp.accessToken();
        this.expiresAt = Instant.now().plusSeconds(resp.expiresIn());
    }
}
```

[ASSUMED: The synchronized approach is correct for single-key-per-process (D-08). Risk: minor contention on first tool call. Acceptable for demo posture.]

### Pattern 4: identity-service /agents/exchange Endpoint

```java
// POST /agents/exchange  (public — no Spring Security JWT required on identity-service)
// Request body: { "apiKey": "plaintext-key" }
// Response: { "accessToken": "...", "expiresIn": 86400 }
@PostMapping("/agents/exchange")
public ResponseEntity<AgentTokenResponse> exchange(@RequestBody @Valid AgentExchangeRequest req) {
    String hash = Hex.encodeHexString(DigestUtils.sha256(req.apiKey()));
    AgentApiKey key = agentApiKeyRepository.findByHashNotRevoked(hash)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid API key"));
    agentApiKeyRepository.updateLastUsed(key.id(), Instant.now());
    // Reuse Phase 3 JwtIssuerService — identical JWT shape to /auth/login
    String token = jwtIssuerService.issue(key.userId(), key.agentLabel(), "Agent", List.of("ROLE_USER"));
    return ResponseEntity.ok(new AgentTokenResponse(token, jwtIssuerService.tokenLifetimeSeconds()));
}
```

`IdentitySecurityConfig` is already `anyRequest().permitAll()` — NO change needed. The key check happens inside the controller.

[VERIFIED: `IdentitySecurityConfig` current code — `anyRequest().permitAll()` confirmed; no route allowlist needed]
[VERIFIED: `JwtIssuerService.issue(UUID, String, String, List<String>)` signature confirmed]

### Pattern 5: Flyway V5 Migration (identity-service)

Next sequence: V5 (V1–V4 confirmed in `identity-service/src/main/resources/db/migration/`).

```sql
-- V5__agent_api_keys.sql
CREATE TABLE IF NOT EXISTS agent_api_keys (
    api_key_hash   CHAR(64)     PRIMARY KEY,             -- SHA-256 base16, never the plaintext
    agent_label    VARCHAR(100) NOT NULL,
    user_id        UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_used_at   TIMESTAMPTZ,
    revoked_at     TIMESTAMPTZ                           -- NULL = active
);
-- Seed one demo agent bound to the admin user (admin user seeded in V3)
-- Plaintext key logged ONCE at service startup via ApplicationReadyEvent; not stored
INSERT INTO agent_api_keys (api_key_hash, agent_label, user_id)
SELECT 'PLACEHOLDER_HASH_REPLACED_BY_SEED_SCRIPT', 'demo-agent', id
FROM users WHERE email = 'admin@n11demo.com';
```

[VERIFIED: V1–V4 exist in `identity-service/src/main/resources/db/migration/`; V5 is next]
[ASSUMED: The seed hash is populated by a separate seed script at first run, not hardcoded in the migration, to avoid leaking the demo key in source.]

### Pattern 6: api-gateway Route Addition

**Current state:** `/mcp/**` is NOT in `config-server/src/main/resources/config/api-gateway.yml`. The discovery locator would auto-route `lb://MCP-SERVER` under `/mcp-server/**` but NOT `/mcp/**`. An explicit route is required.

**Response-timeout determination:** Streamable HTTP keeps connections open during SSE streaming of tool responses. The same `response-timeout: -1` pattern used for the ai-service SSE route applies.

```yaml
# Add to routes: list in api-gateway.yml
# Phase 9: mcp-server Streamable HTTP.
# response-timeout: -1 required — Streamable HTTP holds SSE stream open while
# the LLM reads tool results. Same pattern as ai-service-chat-stream.
- id: mcp-server
  uri: lb://MCP-SERVER
  predicates:
    - Path=/mcp/**
  metadata:
    response-timeout: -1
    connect-timeout: 5000
  filters:
    - StripPrefix=0     # /mcp is the full path; no prefix to strip
    - PreserveHostHeader=true
```

**SecurityConfig:** `/mcp/**` falls through to `.anyExchange().authenticated()` — no change needed.

[VERIFIED: SecurityConfig current code — no /mcp/** entry in allowlist; anyExchange().authenticated() catches it]
[VERIFIED: api-gateway.yml current content — no /mcp/** route exists; explicit route required]
[CITED: api-gateway.yml SSE route — `response-timeout: -1` pattern confirmed at `ai-service-chat-stream` route]

### Anti-Patterns to Avoid

- **`@McpTool` annotations in mcp-server:** Violates D-09 and CLAUDE.md Rule #2. The adapter wraps ToolRegistry beans at startup — zero Spring AI annotations in mcp-server source.
- **Adding `org.springframework.ai.*` types to agent-toolset:** Violates CLAUDE.md Rule #1. `ToolCallback`, `ToolCallbackProvider`, `ToolDefinition` must only appear in mcp-server's own classes.
- **Adding AgentJwtCache to ai-service:** The JWT cache is mcp-server-only. ai-service receives X-User-Id from the gateway-injected header; it does not exchange API keys.
- **Depending on `spring-ai-starter-mcp-server` (stdio-only) instead of webmvc:** Loses Streamable HTTP capability. Always use `spring-ai-starter-mcp-server-webmvc`.
- **Skipping BOM addition:** `spring-ai-starter-mcp-server-webmvc` is not in the Spring Boot BOM. Without adding `spring-ai-bom:1.1.5` to the root BOM imports, all Spring AI transitive dependencies will need explicit versions.
- **Hardcoding api_key_hash in V5 migration:** Demo key must be generated at runtime and set via `.env`; plaintext must never reach source.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| MCP JSON-RPC wire protocol | Custom HTTP endpoint for tools/list, tools/call | Spring AI `spring-ai-starter-mcp-server-webmvc` | Protocol versioning, capability negotiation, SSE streaming all handled by the starter |
| ToolCallback registration | Manual MCP SDK `McpServer` builder calls | `ToolCallbackProvider` bean + Spring Boot autoconfiguration | Auto-configuration detects and registers the bean; no manual SDK wiring |
| Transport switching | Custom `if (MCP_TRANSPORT.equals("stdio"))` router | `spring.ai.mcp.server.stdio=true` property | Starter handles the stdio/HTTP coexistence |
| SHA-256 hashing | Custom hex-encoding | Apache Commons Codec `DigestUtils.sha256` + `Hex.encodeHexString` | Already on classpath via Spring Security; no new dependency |
| JWT claims parsing (for userId) | Custom JWT parser | `Jwts.parserBuilder()` or `NimbusJwtDecoder` | Only need `sub` claim; use `new JWTParser().parse(jwt).getJWTClaimsSet().getSubject()` (Nimbus SDK already on classpath via spring-security-oauth2-jose) |

**Key insight:** Spring AI's MCP starter eliminates ~500 lines of JSON-RPC boilerplate. The entire MCP protocol surface is handled by autoconfiguration once a `ToolCallbackProvider` bean is present.

---

## Runtime State Inventory

Phase 9 adds new runtime state. This is a greenfield service addition with a DB migration.

| Category | Items Found | Action Required |
|----------|-------------|-----------------|
| Stored data | `agent_api_keys` table does NOT yet exist; V5 migration creates it | Flyway V5 migration in identity-service |
| Live service config | `mcp-server` not yet in docker-compose.yml; no n8n or external config system involved | Add `mcp-server` service entry to docker-compose.yml |
| OS-registered state | None — no Task Scheduler or systemd units for mcp-server | None |
| Secrets/env vars | `MCP_API_KEY` must be added to `.env.example` as a placeholder; actual key generated at first run | Update `.env.example`; never commit plaintext key |
| Build artifacts | `settings.gradle.kts` does not include `mcp-server`; egg-info equivalent is Gradle cache | Add `mcp-server` to `settings.gradle.kts` |

---

## Common Pitfalls

### Pitfall 1: Spring AI BOM Not in Root Build (NEW — Phase 9 specific)
**What goes wrong:** `spring-ai-starter-mcp-server-webmvc` resolution fails with "Could not find org.springframework.ai:spring-ai-*" because Spring Boot's BOM does not manage Spring AI artifacts.
**Why it happens:** Unlike Spring Cloud which is in the root BOM imports, Spring AI is absent from the current root `build.gradle.kts`.
**How to avoid:** Add `mavenBom("org.springframework.ai:spring-ai-bom:1.1.5")` to `subprojects { extensions.configure<DependencyManagementExtension> { imports { } } }` in `build.gradle.kts` as Wave 0.
**Warning signs:** Gradle resolution error mentioning `org.springframework.ai` artifacts during `:mcp-server:dependencies` or `:mcp-server:compileJava`.

### Pitfall 2: Streamable HTTP Endpoint Path Mismatch (Pitfall #15 analog)
**What goes wrong:** MCP Inspector or Claude Desktop can't connect. Error: `405 Method Not Allowed` or `404 Not Found` at the expected endpoint.
**Why it happens:** (a) Using `protocol=SSE` default instead of `protocol=STREAMABLE`; SSE uses `/sse` + `/mcp/messages` endpoints, not `/mcp`. (b) api-gateway route path misconfigured (wrong StripPrefix value). (c) Stale Jib image after editing config-server YAML.
**How to avoid:** Verify endpoint with `curl -X POST http://localhost:8090/mcp -H "Accept: application/json, text/event-stream" -d '{"jsonrpc":"2.0","method":"initialize","id":1,"params":{"protocolVersion":"2025-06-18","clientInfo":{"name":"test","version":"1"},"capabilities":{}}}' -v`. Rebuild Jib image after config-server edits.
**Warning signs:** 405 or 404 at `/mcp`; 200 at `/sse` (wrong transport activated).

### Pitfall 3: ToolCallback.inputType(JsonNode.class) Serialization Issue
**What goes wrong:** `FunctionToolCallback` fails to deserialize MCP tool call arguments into `JsonNode`.
**Why it happens:** Spring AI may try to use its default Jackson mapper to deserialize the argument string; if the MCP client sends a nested JSON object and the inputType is `String`, double-serialization occurs.
**How to avoid:** Set `inputType(JsonNode.class)` explicitly so Spring AI passes the raw JSON node to the function. Test with `npx @modelcontextprotocol/inspector` sending a `tools/call` request.
**Warning signs:** `ClassCastException` or `JsonMappingException` in mcp-server logs on first tool call.

### Pitfall 4: Clock Skew on Cached JWT (Pitfall #18 analog)
**What goes wrong:** mcp-server's cached JWT is rejected by the gateway with 401 even though `expiresAt` check shows it valid.
**Why it happens:** The 30-second `JwtTimestampValidator` at the gateway means a JWT must have `exp > now + 30s`. If the cache refresh fires exactly at the 5-minute window boundary, the token may be valid per cache but rejected by the gateway.
**How to avoid:** Use 10-minute buffer instead of 5-minute: `expiresAt.minus(Duration.ofMinutes(10))` in `ensureValid()`. This is more conservative and eliminates the edge case within the demo window.
**Warning signs:** Intermittent 401 on mutating tool calls; read tools succeed but write tools fail.

### Pitfall 5: Spring AI Capability Defaults (all capabilities enabled)
**What goes wrong:** MCP client sees `resources` and `prompts` capabilities advertised but no resources/prompts registered. Some strict clients (e.g., MCP Inspector) warn about empty capability sets.
**Why it happens:** Spring AI enables all capabilities by default (tools, resources, prompts, completions, logging, progress, ping).
**How to avoid:** Explicitly set `capabilities.resource: false` and `capabilities.prompt: false` in mcp-server.yml to advertise only `tools`.
**Warning signs:** MCP Inspector `initialize` response shows `resources: {}` alongside `tools`; client logs capability mismatch warnings.

### Pitfall 6: infra-tests Classpath Collision (Plan 05-04 lesson)
**What goes wrong:** Adding mcp-server to infra-tests causes `NoUniqueBeanDefinitionException` or entity scan explosion.
**Why it happens:** mcp-server's `@SpringBootApplication` with `scanBasePackages` may overlap with existing service packages; any `@Entity` classes in the same com.n11.* hierarchy cause conflicts.
**How to avoid:** Use `McpServerTestConfig` with `excludeFilters` blocking `@SpringBootApplication` classes (same pattern as `AiServiceTestConfig`). mcp-server has NO `@Entity` classes (stateless), so entity disambiguation is simpler than payment/order services.
**Warning signs:** `NoUniqueBeanDefinitionException` on `ToolRegistry` or `ToolCallback` beans; multiple `McpServerApplication` in component scan.

### Pitfall 7: `MCP_DB_PASSWORD` Declared but mcp-server Has No DB
**What goes wrong:** mcp-server fails to start because `DB_PASSWORD` env var triggers Flyway or DataSource autoconfiguration.
**Why it happens:** `MCP_DB_PASSWORD` is already in `docker-compose.yml` postgres environment (from Phase 1 init.sh); if mcp-server imports it, Spring Boot may try to configure a DataSource.
**How to avoid:** Explicitly set `spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration` in mcp-server's application.yml. mcp-server has NO DB; do not include `spring-boot-starter-data-jpa` or `flyway-core` in its dependencies.
**Warning signs:** `DataSourceProperties$DataSourceBeanCreationException: Failed to determine a suitable driver class` at startup.

---

## Code Examples

### tools/list Verification Curl
```bash
# Get exchanged JWT first
TOKEN=$(curl -s -X POST http://localhost:9090/api/v1/identity/agents/exchange \
  -H "Content-Type: application/json" \
  -d "{\"apiKey\":\"$MCP_API_KEY\"}" | jq -r '.accessToken')

# Streamable HTTP: tools/list
curl -X POST https://<tunnel>/mcp \
  -H "Authorization: Bearer $TOKEN" \
  -H "Accept: application/json, text/event-stream" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"tools/list","id":1}'
# Expected: response with tools array of length 10
```

### MCP Inspector Launch Commands
```bash
# HTTP transport (after docker-compose is up)
npx @modelcontextprotocol/inspector http http://localhost:8090/mcp
# With Authorization header for authenticated requests:
# Inspector UI lets you set custom headers — set Authorization: Bearer <token>

# stdio transport (Claude Desktop simulation)
npx @modelcontextprotocol/inspector \
  docker run -i --rm --network=host \
  -e MCP_TRANSPORT=stdio \
  -e SPRING_AI_MCP_SERVER_STDIO=true \
  -e MCP_API_KEY=${MCP_API_KEY} \
  n11/mcp-server:dev
```

[VERIFIED: `@modelcontextprotocol/inspector` version 0.21.2 confirmed on npm registry]

### claude_desktop_config.json Snippet
```json
{
  "mcpServers": {
    "n11-storefront": {
      "command": "docker",
      "args": [
        "run", "-i", "--rm",
        "--network=host",
        "-e", "MCP_TRANSPORT=stdio",
        "-e", "SPRING_AI_MCP_SERVER_STDIO=true",
        "-e", "SPRING_AI_MCP_SERVER_PROTOCOL=STREAMABLE",
        "-e", "MCP_API_KEY=<demo-key>",
        "n11/mcp-server:dev"
      ]
    }
  }
}
```

Note: `--network=host` allows the stdio process to reach `localhost:8081` (identity-service) and `localhost:876x` (Eureka) for the key exchange and tool dispatch.

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| HTTP+SSE transport (GET /sse + POST /mcp/messages) | Streamable HTTP (POST /mcp) | MCP spec 2025-06-18 | SSE deprecated; Spring AI webmvc starter defaults to SSE but supports STREAMABLE via property |
| `FunctionCallback.builder()` | `FunctionToolCallback.builder()` | Spring AI 1.0→1.1 | API renamed; same concept |
| Per-service Spring AI BOM declaration | Add Spring AI BOM once in root build.gradle.kts | Phase 9 introduces Spring AI as a server-side dep | Single BOM import manages all spring-ai-* transitive versions |
| MCP Java SDK direct dependency | Transitive via spring-ai-starter-mcp-server-webmvc | Spring AI 1.1.x | `io.modelcontextprotocol.sdk:mcp:1.1.2` arrives transitively; do NOT declare directly |

**Deprecated/outdated:**
- HTTP+SSE transport (`/sse` + `/mcp/messages`): deprecated in MCP spec 2025-06-18; Spring AI still supports via `protocol=SSE` but use `protocol=STREAMABLE` per D-02.
- `FunctionCallback` API: replaced by `ToolCallback` / `FunctionToolCallback` in Spring AI 1.1.0; old API may still compile but is deprecated.
- `@McpTool` / `@McpToolParam` annotations: still work in Spring AI 1.1.5 but D-09 explicitly avoids them for DRY reasons. The adapter approach via `ToolCallbackProvider` is preferred in our architecture.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | The demo agent key seed in V5 migration uses a placeholder; actual key generated and logged at first `ApplicationReadyEvent`, not stored in source | Pattern 5 (V5 migration) | If wrong: demo key hardcoded in SQL migration, violating QUAL-09. Mitigation: use a Spring Boot `ApplicationRunner` to generate the seed key at first boot if `agent_api_keys` is empty |
| A2 | `spring.ai.mcp.server.capabilities.resource: false` and `capabilities.prompt: false` are valid config keys that disable those capabilities | Common Pitfall 5 | If wrong: mcp-server still advertises resource/prompt capabilities with no registrations; harmless for demo but inconsistent |
| A3 | `synchronized` on `AgentJwtCache.ensureValid()` is sufficient thread-safety for a single-process single-key scenario | Pattern 3 | If wrong: possible double-exchange on concurrent first-call; idempotent (both get the same JWT), non-harmful |
| A4 | `FunctionToolCallback.builder("name", fn).toolDefinition(def).inputType(JsonNode.class).build()` correctly overrides the auto-generated schema with `def.inputSchema()` | Pattern 1 | If wrong: Spring AI ignores explicit ToolDefinition and generates schema from `JsonNode.class` reflection (likely produces `{}` schema). Mitigation: verify with `tools/list` response and assert schema non-empty in McpServerToolsListEqualityTest |

---

## Open Questions

1. **`FunctionToolCallback` and explicit inputSchema precedence**
   - What we know: `ToolDefinition.builder().inputSchema(String)` accepts an explicit JSON schema string. `FunctionToolCallback.builder("name", fn).toolDefinition(def)` passes a ToolDefinition.
   - What's unclear: Whether Spring AI's `FunctionToolCallback` honours the `inputSchema` from the provided `ToolDefinition` when `inputType` is also set, or regenerates the schema from the type.
   - Recommendation: In Wave 0 of planning, add a unit test: call `AgentToolMcpRegistration.agentTools()`, list the callbacks, assert `callback.getToolDefinition().inputSchema()` equals `tool.parametersJsonSchema()`. If the test fails, switch to a low-level `McpServer` `ToolSpecification` approach instead of `FunctionToolCallback`.

2. **`--network=host` on non-Linux Docker**
   - What we know: `docker run --network=host` is Linux-only. On macOS/Windows Docker Desktop, `--network=host` does not forward host ports.
   - What's unclear: Whether the grader is on Linux or macOS/Windows.
   - Recommendation: README includes both options — `--network=host` for Linux, `--add-host=host.docker.internal:host-gateway` for macOS/Windows.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK 21 | Build | Assumed via toolchain | 21 | — |
| Docker | Jib build + Claude Desktop demo | Confirmed (other services built with Jib) | — | — |
| `npx @modelcontextprotocol/inspector` | Verification | ✓ (0.21.2 on npm registry) | 0.21.2 | curl JSON-RPC directly |
| identity-service port 8081 | /agents/exchange | ✓ (Phase 3 complete) | Phase 3 | — |
| Eureka at port 8761 | Eureka client registration | ✓ (Phase 1 complete) | Phase 1 | — |
| config-server at port 8888 | Spring Cloud Config client | ✓ (Phase 1 complete) | Phase 1 | — |

**Missing dependencies with no fallback:** None.

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 (Spring Boot Test) |
| Config file | Inherited from root `build.gradle.kts` `tasks.withType<Test> { useJUnitPlatform() }` |
| Quick run command | `./gradlew :mcp-server:test :infra-tests:test --tests "*.McpServerToolsListEqualityTest"` |
| Full suite command | `./gradlew :mcp-server:test :identity-service:test :infra-tests:test` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| AI-11 | ToolCallbackProvider exposes exactly 10 tools with names + schemas matching ToolRegistry | integration | `./gradlew :infra-tests:test --tests "*.McpServerToolsListEqualityTest"` | Wave 0 |
| AI-12 | mcp-server boots with both STREAMABLE and stdio active; HTTP endpoint responds to tools/list | integration (Spring context) | `./gradlew :mcp-server:test --tests "*McpServerContextTest*"` | Wave 0 |
| AI-13 | POST /agents/exchange with valid API key returns JWT; JWT.sub equals bound user_id | unit + integration | `./gradlew :identity-service:test --tests "*AgentExchangeControllerTest*"` | Wave 0 |
| AI-11 (SC-4) | Zero tool definitions in mcp-server source | static (grep) | `grep -r 'class.*AgentTool\|implements AgentTool' mcp-server/src/ \|\| echo "PASS"` | Static |
| AI-12 (SC-2) | MCP Inspector connects and lists 10 tools | manual / smoke | `npx @modelcontextprotocol/inspector http http://localhost:8090/mcp` | Manual |
| AI-13 (SC-3) | add_to_cart via MCP client shows in cart-service REST | manual / smoke | see RUNBOOK.md | Manual |

### Sampling Rate
- **Per task commit:** `./gradlew :mcp-server:test`
- **Per wave merge:** `./gradlew :mcp-server:test :identity-service:test :infra-tests:test --tests "*.McpServer*"`
- **Phase gate:** Full suite green before `/gsd-verify-work`

### Wave 0 Gaps
- [ ] `mcp-server/src/test/java/com/n11/mcp/McpServerContextTest.java` — covers AI-12 (context boots, tools count = 10, transport configured)
- [ ] `mcp-server/src/test/java/com/n11/mcp/AgentToolMcpRegistrationTest.java` — covers AI-11 (ToolCallback name/schema equality)
- [ ] `identity-service/src/test/java/com/n11/identity/agent/AgentExchangeControllerTest.java` — covers AI-13 (exchange endpoint unit test)
- [ ] `infra-tests/src/test/java/com/n11/infratests/mcp/McpServerToolsListEqualityTest.java` — covers AI-11 SC-1 (infra-tests integration)
- [ ] `infra-tests/src/test/java/com/n11/infratests/mcp/McpServerTestConfig.java` — Plan 05-04 pattern for classpath disambiguation

---

## Project Constraints (from CLAUDE.md)

| Directive | Impact on Phase 9 |
|-----------|------------------|
| Rule #1 — Zero `org.springframework.ai.*` types in agent-toolset signatures | `ToolCallback`, `ToolCallbackProvider`, `ToolDefinition` live ONLY in mcp-server's `config/` package. agent-toolset has no Spring AI imports. |
| Rule #2 — One toolset, two consumers | D-09 enforced structurally: mcp-server has zero local `AgentTool` implementations. `agent-toolset` is the single source of truth for all 10 tools. |
| Rule #4 — Verify SDK docs before coding | Spring AI `ToolCallback`, `ToolCallbackProvider`, `ToolDefinition` verified against official docs (cited above). MCP spec transport verified against modelcontextprotocol.io. |
| Rule #5 — No secrets in source | `MCP_API_KEY` via `.env` (gitignored). Demo agent hash NOT hardcoded in V5 migration. |
| Rule #3 — Saga consumers idempotent | N/A — mcp-server has no AMQP consumers. |

---

## Sources

### Primary (HIGH confidence)
- [Spring AI Tools Reference](https://docs.spring.io/spring-ai/reference/api/tools.html) — `ToolDefinition.builder().inputSchema(String)`, `FunctionToolCallback.builder()`, `ToolCallbackProvider.from(List)` API surface verified
- [Spring AI MCP Server Boot Starter](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html) — transport list (webmvc, webflux, stdio), capability configuration, auto-discovery of ToolCallbackProvider beans
- [Spring AI Streamable HTTP Starter](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-streamable-http-server-boot-starter-docs.html) — `protocol=STREAMABLE`, `/mcp` default endpoint, `streamable-http.mcp-endpoint` override
- [Spring AI STDIO/SSE Starter](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-stdio-sse-server-boot-starter-docs.html) — `stdio=true` activates optional stdio on webmvc starter; `/mcp/message` SSE endpoint confirmed (NOT used; using STREAMABLE)
- [MCP Spec 2025-06-18 Transports](https://modelcontextprotocol.io/specification/2025-06-18/basic/transports) — Streamable HTTP is current; HTTP+SSE from 2024-11-05 is deprecated; `/mcp` endpoint path convention
- Maven Central — `spring-ai-starter-mcp-server-webmvc:1.1.5` confirmed published; `spring-ai-bom:1.1.5` confirmed

### Secondary (MEDIUM confidence)
- Codebase grep — D-15 confirmed: zero `seenIds` reads in `agent-toolset/src/main/java/com/n11/agent/tools/` (11 tool files scanned)
- Codebase inspection — identity-service V1–V4 migrations confirmed; V5 is next sequence
- Codebase inspection — api-gateway.yml has no `/mcp/**` route; `anyExchange().authenticated()` confirmed
- Codebase inspection — mcp-server NOT in settings.gradle.kts; Spring AI BOM NOT in root build.gradle.kts

### Tertiary (LOW confidence)
- [A1] Demo agent key seed strategy — generation at ApplicationReadyEvent not verified by official docs; ASSUMED pattern

---

## Metadata

**Confidence breakdown:**
- Standard Stack: HIGH — all artifacts version-verified on Maven Central; API surface verified via official Spring AI docs
- Architecture: HIGH — existing codebase constraints verified by code inspection; transport behavior verified via official MCP spec + Spring AI docs
- Pitfalls: HIGH for code-level pitfalls (verified via codebase reading); MEDIUM for runtime behavior pitfalls (partial verification)
- Transport: HIGH — both `protocol=STREAMABLE` and `stdio=true` confirmed via official Spring AI docs

**Research date:** 2026-05-01
**Valid until:** 2026-05-31 (Spring AI 1.1.x is in active maintenance; MCP spec 2025-06-18 stable)
