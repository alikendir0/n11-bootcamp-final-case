# product-service

n11-clone catalog service — Phase 4. REST endpoints under `/api/v1/products/**` and `/api/v1/categories/**` (gateway-prefixed). Internal port 8082; not directly exposed to the host (Pitfall #14).

## Endpoints (api-contracts.md §1)

| Verb | Path | Auth | Purpose |
|------|------|------|---------|
| GET | /products | public | Paginated listing (page/size/sort/q/categoryId). PROD-01/04/05. |
| GET | /products/{id} | public | PDP fields. PROD-02. |
| GET | /categories | public | Top-level categories. PROD-03. |
| POST | /products | ROLE_ADMIN | Create product. |
| PUT | /products/{id} | ROLE_ADMIN | Update product. |
| DELETE | /products/{id} | ROLE_ADMIN | Delete product. |

## Required env vars

| Variable | Source | Purpose |
|----------|--------|---------|
| `PRODUCT_DB_PASSWORD` | random per-host secret | Postgres password for product_user (created by infra/postgres/init.sh). |

## Boot procedure

```bash
./gradlew :product-service:jibDockerBuild
docker compose up -d product-service
curl http://localhost:8082/actuator/health   # (only from inside the n11-net Docker network — verify via gateway in plan 04-03)
```

## Test pattern

Repository slice + integration: `./gradlew :product-service:test` (Testcontainers Postgres ~30s cold start).
