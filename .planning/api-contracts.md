# API Contracts (Day-1 Lock)

**Locked:** 2026-04-28
**Authority:** This document is the canonical contract for all REST surfaces, gateway routing, error responses, and correlation-ID propagation in the n11-clone system. Springdoc OpenAPI generates the full per-service rigor at impl time; this doc locks the cross-service shape so Phase 3+ services don't drift. Cross-references the saga contract in `saga-contracts.md` for the AMQP envelope correlation-ID story.

## 1. Per-Service Endpoint Inventory

Source: ARCHITECTURE.md §2.4–§2.13. Each service section below lists its Day-1-locked endpoints; full request/response shapes come from Springdoc at impl time. Endpoints marked `(Phase N)` are scaffolded later but their gateway routes are reserved now.

### identity-service (Phase 3)

| Verb | Path | Auth | Purpose |
|------|------|------|---------|
| POST | /auth/register | public | Create user (email, password) |
| POST | /auth/login | public | Issue JWT |
| GET  | /auth/me | JWT | Return user profile |
| GET  | /addresses | JWT | List user's saved Türkiye addresses |
| POST | /addresses | JWT | Add address |
| GET  | /.well-known/jwks.json | public | JWKS for JWT validation by gateway |
| POST | /agents/exchange | API-key (Phase 9) | Exchange MCP_API_KEY for internal JWT |

### product-service (Phase 4)

| Verb | Path | Auth | Purpose |
|------|------|------|---------|
| GET  | /products | public | Paginated listing (page index 0-based, default size 20) — query params: `page`, `size`, `sort`, `q` |
| GET  | /products/{id} | public | PDP fields |
| GET  | /categories | public | Top-level + nested categories |
| POST | /products | JWT + ROLE_ADMIN | Create product |

### inventory-service (Phase 4)

| Verb | Path | Auth | Purpose |
|------|------|------|---------|
| GET  | /inventory/{productId} | public | Stock state |

(Inventory's main interface is event-driven — see `saga-contracts.md`.)

### cart-service (Phase 5)

| Verb | Path | Auth | Purpose |
|------|------|------|---------|
| GET    | /cart | JWT | Return current cart |
| POST   | /cart/items | JWT | Add/upsert line item (productId, qty) |
| PATCH  | /cart/items/{id} | JWT | Update quantity |
| DELETE | /cart/items/{id} | JWT | Remove item |

### order-service (Phase 5) — schema-name `orders` per saga-contracts.md §9

| Verb | Path | Auth | Purpose |
|------|------|------|---------|
| POST | /orders | JWT | Create order from cart |
| GET  | /orders | JWT | List user's orders desc by date |
| GET  | /orders/{id} | JWT | Order detail with status timeline |
| POST | /orders/{id}/cancel | JWT | User-initiated cancellation |

### payment-service (Phase 6)

| Verb | Path | Auth | Purpose |
|------|------|------|---------|
| POST | /payments/checkout | JWT | Create Iyzico checkout token, return form URL |
| POST | /payments/iyzico/callback | NONE — Iyzico webhook (signature-verified server-side) | Iyzico 3DS callback |
| GET  | /payments/{orderId} | JWT | Payment status |

### notification-service (Phase 7) — event-only, NO public REST

### search-service (Phase 4 + Phase 8)

| Verb | Path | Auth | Purpose |
|------|------|------|---------|
| GET  | /search?q=... | public | v1: ILIKE on product-service; v2: pgvector |

### ai-service (Phase 8) — SSE caveats apply

| Verb | Path | Auth | Purpose |
|------|------|------|---------|
| POST | /chat | public (per allowlist below) | Non-streaming chat fallback |
| GET  | /chat/stream | public — **SSE; see §6** | Token streaming with mid-stream tool-call indicators |
| GET  | /conversations/{id} | JWT | Replay conversation (key by user-id) |

### mcp-server (Phase 9)

| Verb | Path | Auth | Purpose |
|------|------|------|---------|
| POST | /mcp/messages | API-key → exchanged JWT | MCP HTTP+SSE transport |
| (stdio) | n/a | n/a | Claude Desktop transport |

## 2. Gateway Routing Table

The api-gateway resolves routes via `spring.cloud.gateway.server.webflux.discovery.locator.enabled=true` (Northfields-renamed property — see RESEARCH §4.1). Service-id casing is lower-cased automatically so the prefix matches the Eureka registration name.

| Path prefix | Service-id | Eureka name | Notes |
|-------------|------------|-------------|-------|
| `/api/v1/identity/**` | identity-service | identity-service | Phase 3 |
| `/api/v1/products/**` | product-service | product-service | Phase 4 |
| `/api/v1/inventory/**` | inventory-service | inventory-service | Phase 4 |
| `/api/v1/cart/**` | cart-service | cart-service | Phase 5 |
| `/api/v1/orders/**` | order-service | order-service | Phase 5 (DB schema is `orders`, see saga-contracts.md §9) |
| `/api/v1/payments/**` | payment-service | payment-service | Phase 6 |
| `/api/v1/notifications/**` | notification-service | notification-service | Phase 7 (admin only — optional gateway exposure) |
| `/api/v1/search/**` | search-service | search-service | Phase 4 + Phase 8 |
| `/api/v1/chat/**` | ai-service | ai-service | Phase 8 — see SSE caveat §6 |
| `/mcp/**` | mcp-server | mcp-server | Phase 9 |
| anything else | — | — | gateway returns 503 (D-14) |

In Phase 1 the gateway has `discovery.locator.enabled=true` and zero explicit routes; routes auto-populate as services register with Eureka. Phase 8 MUST add an explicit route for `/api/v1/chat/stream/**` so SSE-specific metadata can override defaults — see §6.

## 3. Public Allowlist (D-09)

The following routes are public (no JWT required). All other paths require a valid JWT (gateway validates from Phase 3 onwards):

- `POST /api/v1/identity/auth/login`
- `POST /api/v1/identity/auth/register`
- `GET /api/v1/products/**` (browsing without login)
- `GET /api/v1/search/**`
- `POST /api/v1/chat/**` (chat assistant works for guests)
- `POST /api/v1/payments/iyzico/callback` (Iyzico webhook — signature-verified, not JWT-gated)
- `GET /api/v1/identity/.well-known/jwks.json` (gateway itself fetches this — public)

In Phase 1 the gateway runs `permitAll()` (D-14) — JWT enforcement flips on in Phase 3.

## 4. Authorization-Strip Rule

The gateway STRIPS the inbound `Authorization` header before forwarding to backing services. Backing services NEVER see the raw JWT. Instead the gateway INJECTS:

- `X-User-Id` (UUID, from JWT `sub` claim)
- `X-User-Roles` (comma-separated, from JWT `roles` claim)
- `X-Correlation-Id` (UUID, generated if absent — see §5)

This is the "defense at the edge + trust the mesh" pattern (ARCHITECTURE.md §10 anti-pattern 4: don't forward raw JWT downstream). It gives:

- A single chokepoint where JWT signature/expiry is verified (gateway ↔ JWKS at identity-service)
- Backing services that can be unit-tested without minting JWTs (just stub the headers)
- A clean blast radius if a backing service is compromised — it cannot mint or replay tokens

## 5. Correlation-ID Propagation (D-09)

| Hop | Mechanism | Code reference (Plan 04 / 05) |
|-----|-----------|-------------------------------|
| Inbound HTTP at gateway | `WebFilter` (`GatewayCorrelationIdFilter`) generates UUID if `X-Correlation-Id` absent; mutates request header; sets response header | api-gateway/src/main/java/.../GatewayCorrelationIdFilter.java |
| Inbound HTTP at service | `OncePerRequestFilter` (`CorrelationIdFilter` from `common-logging`) reads header into MDC | common-logging |
| Outbound HTTP from service | `ClientHttpRequestInterceptor` reads MDC and re-adds header to outgoing `RestClient` calls | common-logging |
| Outbound AMQP from service | `MessagePostProcessor` on `RabbitTemplate` reads MDC and adds `X-Correlation-Id` header to message; saga envelope `correlationId` field also carries it | common-logging |
| Inbound AMQP at consumer | `@Around` aspect on `@RabbitListener` reads message header into MDC | common-logging (Phase 5 activates) |
| Logback | `<includeMdcKeyName>correlationId</includeMdcKeyName>` in `logstash-logback-encoder` JSON encoder | service-template/logback-spring.xml |

The same UUID flows through HTTP, AMQP, and JSON logs — `grep '"correlationId":"<uuid>"' logs/*.log` traces a saga across all 13 services. The AMQP envelope `correlationId` field (see `saga-contracts.md` §1) carries the same UUID at the message-body level so consumers can recover it even if the AMQP header is absent (defensive double-write).

## 6. SSE Caveat (Phase 8 forward-compat) — RESEARCH §4.10

Routes via gateway delivering Server-Sent Events MUST:

1. Set `metadata.response-timeout: -1` at the route level (overrides the gateway's global `httpclient.response-timeout` of 60s, which would kill long-lived streams).
2. NOT have any body-modifying filter (`ModifyResponseBody`, `RetryFilter` configured to retry on streaming responses, etc.) — these buffer the response and break token-by-token streaming.

Phase 1 ships the global default `spring.cloud.gateway.server.webflux.httpclient.response-timeout: 60s` and includes a commented-out SSE-route shape in `config-server/src/main/resources/config/api-gateway.yml` so Phase 8 has a known anchor. Example:

```yaml
spring:
  cloud:
    gateway:
      server:
        webflux:
          # Phase 8 will activate this route:
          # routes:
          #   - id: ai-chat-stream
          #     uri: lb://ai-service
          #     predicates:
          #       - Path=/api/v1/chat/stream/**
          #     metadata:
          #       response-timeout: -1
          #       connect-timeout: 5000
          #     filters:
          #       - PreserveHostHeader=true
```

The `metadata.response-timeout: -1` override is the load-bearing line. Without it the gateway closes the connection at 60s and the AI streaming UX silently breaks.

## 7. Error Response Shape (RFC-7807 problem+json) — D-09 / QUAL-07

All error responses across all services use `application/problem+json` with this locked field set:

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| type | URI | yes | `https://n11clone/errors/<code>` (e.g. `validation`, `not-found`, `conflict`, `internal`, `unauthorized`) |
| title | string | yes | Human-readable, language-agnostic |
| status | integer | yes | HTTP status code |
| detail | string | yes | Sanitized — NEVER include exception class names, SQL fragments, or stack traces |
| instance | URI | yes | Request URI |
| correlationId | UUID | yes | From MDC; allows log correlation |
| errors[] | array | optional | Only on validation failures; each item: `{ "field": "...", "message": "..." }` |

### Example responses

**Validation failure (400):**

```json
{
  "type": "https://n11clone/errors/validation",
  "title": "Validation failed",
  "status": 400,
  "detail": "One or more fields failed validation",
  "instance": "/api/v1/identity/auth/register",
  "correlationId": "8f0e1d2a-3b4c-5d6e-7f8a-9b0c1d2e3f4a",
  "errors": [
    { "field": "email", "message": "must be a valid email address" },
    { "field": "password", "message": "must be at least 8 characters" }
  ]
}
```

**Not found (404):**

```json
{
  "type": "https://n11clone/errors/not-found",
  "title": "Resource not found",
  "status": 404,
  "detail": "Product not found",
  "instance": "/api/v1/products/abc123",
  "correlationId": "9a8b7c6d-5e4f-3a2b-1c0d-9e8f7a6b5c4d"
}
```

**Conflict (409):**

```json
{
  "type": "https://n11clone/errors/conflict",
  "title": "State conflict",
  "status": 409,
  "detail": "Order cannot be cancelled in current status",
  "instance": "/api/v1/orders/def456/cancel",
  "correlationId": "1f2e3d4c-5b6a-7c8d-9e0f-1a2b3c4d5e6f"
}
```

**Unauthorized (401):**

```json
{
  "type": "https://n11clone/errors/unauthorized",
  "title": "Unauthorized",
  "status": 401,
  "detail": "Missing or invalid authentication token",
  "instance": "/api/v1/cart",
  "correlationId": "2a3b4c5d-6e7f-8a9b-0c1d-2e3f4a5b6c7d"
}
```

**Internal (500) — sanitized:**

```json
{
  "type": "https://n11clone/errors/internal",
  "title": "Internal server error",
  "status": 500,
  "detail": "An unexpected error occurred",
  "instance": "/api/v1/orders",
  "correlationId": "3c4d5e6f-7a8b-9c0d-1e2f-3a4b5c6d7e8f"
}
```

(Note `detail` is generic — the actual exception is logged with full stack trace at the service, keyed by the same `correlationId`. T-01-04 mitigation: locked example responses use generic `detail` text; never leak exception class names, SQL fragments, or stack traces.)

The implementation lives in `common-error/src/main/java/com/n11/error/ProblemDetailControllerAdvice.java` (Plan 04) using Spring's `ProblemDetail` + `@RestControllerAdvice`.
