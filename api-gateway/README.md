# api-gateway

> **Phase 1 (shell) · Phase 3 (JWT auth) · Phase 8+ (SSE/MCP routing)**

Spring Cloud Gateway (Northfields, reactive WebFlux) — the sole public entry point for all backend traffic. Validates JWTs, strips `Authorization` headers, injects user identity headers, and routes to business services via Eureka discovery.

## Security Model

1. **JWT Validation** — oauth2-resource-server with Nimbus, JWKS fetched from `identity-service/.well-known/jwks.json` (1h refresh)
2. **Authorization Strip** — `Authorization` header is removed before forwarding; downstream services **never** see the raw JWT
3. **Header Injection** — `X-User-Id`, `X-User-Email`, `X-User-Roles` injected from JWT claims
4. **Clock Skew** — `JwtTimestampValidator(Duration.ofSeconds(30))`

## Public Allowlist (No JWT Required)

| Path | Purpose |
|------|---------|
| `POST /api/v1/identity/auth/login` | Login |
| `POST /api/v1/identity/auth/register` | Registration |
| `GET /api/v1/products/**` | Public catalog browsing |
| `GET /api/v1/categories/**` | Public category listing |
| `GET /api/v1/inventory/**` | Public stock checks |
| `POST /api/v1/payments/iyzico/callback` | Iyzico webhook (signature-verified) |
| `POST /api/v1/chat/**` | AI chat (guest-accessible) |
| `GET /api/v1/identity/.well-known/jwks.json` | JWKS endpoint |

## Routing Table

| Path Prefix | Target Service | Notes |
|-------------|---------------|-------|
| `/api/v1/identity/**` | identity-service | Auth + address book |
| `/api/v1/products/**` | product-service | Catalog + categories |
| `/api/v1/inventory/**` | inventory-service | Stock state |
| `/api/v1/cart/**` | cart-service | Cart CRUD |
| `/api/v1/orders/**` | order-service | Order lifecycle |
| `/api/v1/payments/**` | payment-service | Iyzico checkout |
| `/api/v1/chat/**` | ai-service | SSE streaming (`response-timeout: -1`) |
| `/mcp/**` | mcp-server | MCP Streamable HTTP (`response-timeout: -1`) |

## SSE / Long-Lived Connections

Routes serving SSE (ai-service chat, mcp-server) must have:
- `metadata.response-timeout: -1` (overrides the 60s global default)
- No body-modifying filters (they buffer and break streaming)

## Reactive Classpath Isolation

The gateway runs on **Netty** (not Tomcat). To prevent classpath collisions:

```kotlin
configurations.all {
    exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
    exclude(group = "org.springframework.boot", module = "spring-boot-starter-web")
    exclude(group = "org.springdoc", module = "springdoc-openapi-starter-webmvc-ui")
}
```

## Build & Run

```bash
./gradlew :api-gateway:jibDockerBuild
docker compose up -d api-gateway

# Verify routes
curl http://localhost:9090/actuator/health
curl http://localhost:9090/actuator/gateway/routes
```

## Port Mapping

- **Container port:** 8080
- **Host port:** 9090 (mapped in docker-compose)
- No other service exposes host ports (Pitfall #14 — gateway-only entry)
]]>
