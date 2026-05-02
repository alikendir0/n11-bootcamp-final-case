# eureka-server

> **Phase 1** — Service Discovery

Netflix Eureka discovery server for the n11-clone microservices fleet. Every business service and the API gateway registers here. The discovery root — no dependencies on config-server or any other service.

## Role in the Architecture

- All 10 business services + api-gateway register as Eureka clients
- API gateway uses Eureka for service-id → host:port resolution (discovery locator)
- Config-server does **not** register (resolved by hostname in docker-compose)

## Configuration

| Property | Value | Notes |
|----------|-------|-------|
| Port | `8761` | Eureka dashboard at `http://localhost:8761` |
| Self-preservation | disabled | Single-instance dev mode |
| Register-with-eureka | `false` | Discovery root — no peer |
| Fetch-registry | `false` | Discovery root — no peer |

## Build & Run

```bash
# Build Jib image
./gradlew :eureka-server:jibDockerBuild

# Start via docker-compose
docker compose up -d eureka-server

# Verify
curl http://localhost:8761/actuator/health
```

## Health Check

Uses `wget` (not `curl`) — `eclipse-temurin:21-jre` ships BusyBox utilities including wget but NOT curl.

```yaml
healthcheck:
  test: ["CMD-SHELL", "wget -q -O- http://localhost:8761/actuator/health | grep -q '\"status\":\"UP\"'"]
  interval: 5s
  timeout: 3s
  retries: 6
  start_period: 15s
```

## Cold-Boot Performance

Observed startup: ~8–12s on a warm Docker daemon. Well within the 60-second SC-1 budget.
]]>
