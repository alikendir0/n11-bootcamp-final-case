# config-server

> **Phase 1** — Centralized Configuration

Spring Cloud Config Server using the **native profile** — serves configuration from `classpath:/config/` inside its JAR. Each business service pulls its config at boot via `spring.config.import=configserver:http://config-server:8888`.

## Configuration Files Served

| File | Consumer | Purpose |
|------|----------|---------|
| `application.yml` | All services (shared baseline) | Eureka URL, datasource template, Hikari/JPA defaults, actuator, logging |
| `identity-service.yml` | identity-service | JWT config, Flyway schema `identity`, BCrypt settings |
| `product-service.yml` | product-service | Flyway schema `product`, ILIKE search config |
| `inventory-service.yml` | inventory-service | Flyway schema `inventory`, stock thresholds |
| `cart-service.yml` | cart-service | Flyway schema `cart`, product snapshot client |
| `order-service.yml` | order-service | Flyway schema `orders` (plural — SQL reserved word), saga config |
| `payment-service.yml` | payment-service | Iyzico SDK config, Flyway schema `payment`, timeout settings |
| `notification-service.yml` | notification-service | Flyway schema `notification`, Turkish email templates |
| `ai-service.yml` | ai-service | Gemini model config, conversation persistence |
| `search-service.yml` | search-service | Flyway schema `search`, embedding config |
| `mcp-server.yml` | mcp-server | MCP transport config, agent JWT cache |
| `api-gateway.yml` | api-gateway | Routes, JWT/JWKS config, CORS, SSE timeout overrides |

## ⚠️ Stale-Image Hazard

Editing `config-server/src/main/resources/config/*.yml` requires rebuilding the Jib image **before** `docker compose up`:

```bash
./gradlew :config-server:jibDockerBuild
docker compose up -d config-server
```

The native profile reads from `classpath:/config/` which is JAR-bound at image-build time. Changes to YAML files won't take effect until the image is rebuilt.

## Build & Run

```bash
./gradlew :config-server:jibDockerBuild
docker compose up -d config-server

# Verify config serving
curl http://localhost:8888/actuator/health
curl http://localhost:8888/product-service/default
```

## Key Design Decisions

- **Native profile** (not Git-backed) — config lives in the JAR, no external Git repo needed
- **No Eureka registration** — resolved by hostname (`config-server:8888`) in docker-compose
- Self-config (`application.yml`) vs content-config (`config/*.yml`) are two different files
]]>
