# Phase 4: Catalog + Inventory - Research

**Researched:** 2026-04-29
**Domain:** Spring Boot 3.5.14 product catalog (CRUD + pagination + ILIKE search) + inventory saga participant (stock reservation row-locking + idempotent event consumer)
**Confidence:** HIGH

---

## Summary

Phase 4 stands up two new business services — `product-service` (catalog, categories, PDP, ILIKE text search, sort/pagination) and `inventory-service` (stock-per-SKU, row-versioned reservation, saga consumer for `order.created`) — using the same service-template clone pattern established in Phase 3. Both services follow an identical scaffold path: copy `service-template/skeleton/`, rename, add a per-service config YAML in config-server, wire docker-compose, and write service-specific Flyway migrations.

The most technically interesting elements of this phase are: (1) Postgres `pg_trgm` GIN index for ILIKE search — one SQL extension + one DDL line delivers case-insensitive pattern matching without any JPA complexity; (2) Spring Data JPA `Pageable` with `@ParameterObject` for Springdoc wiring — 0-indexed, default size 20, sort by `price` asc/desc and `createdAt` desc; (3) inventory-service as the **first saga consumer** in the project — it wires `inventory.q.order-created`, performs a `@Version`-guarded stock reservation, and publishes `stock.reserved` / `stock.reserve_failed` to RabbitMQ, but in Phase 4 no downstream service listens yet (that's Phase 5's job); (4) stock state computation for PDP (`GET /inventory/{productId}`) returns a DTO the frontend can render as "Stokta" / "Tükendi" / "Son N ürün!".

The Springdoc gateway aggregator (already shipping one `identity-service` entry in Phase 3) gains two new `urls` entries; the pattern is already proven. The `products.tx` exchange for future `product.created/updated/deleted` events is declared in RabbitMQ but has no consumer until Phase 8's search-service skeleton.

**Primary recommendation:** Clone from service-template, add `pg_trgm` extension + GIN index in Flyway migrations, use `@ParameterObject Pageable` in controllers, use `@Version` on the `stock` entity for optimistic locking, and wire the inventory saga consumer with the same `processed_events` idempotency inbox already established in the service-template skeleton.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Product catalog (CRUD, categories, PDP fields) | API / Backend (product-service) | — | Catalog is a bounded context; product-service is the master |
| Paginated product listing + sort + ILIKE search | API / Backend (product-service) | Database (Postgres pg_trgm) | Spring Data JPA Page query; GIN index accelerates ILIKE |
| Stock state read (Stokta / Tükendi / Son N ürün!) | API / Backend (inventory-service) | — | inventory-service owns the stock numbers |
| Stock reservation (saga participant) | API / Backend (inventory-service) | Database (row-level @Version) | Concurrent reservations need optimistic locking |
| Admin product writes (ROLE_ADMIN gate) | API Gateway (path allowlist) + Backend (service @PreAuthorize) | — | Defense in depth: gateway path-level + service-level both |
| Swagger aggregation (all services at gateway) | API Gateway (springdoc aggregator) | — | Pattern established in Phase 1-3; extend `springdoc.swagger-ui.urls` |
| Turkish seed data | Database (Flyway migration) | — | Static seed delivered as a Flyway `V3__seed_products.sql` |
| `products.tx` exchange declaration | API / Backend (product-service RabbitConfig) | — | Producer declares its own exchange per project convention |

---

## User Constraints

No CONTEXT.md exists for Phase 4 yet. Constraints derived from locked project decisions:

### Locked Decisions (from PROJECT.md + Phase 1 / Phase 3 CONTEXT.md)
- **Schema isolation:** `product` schema owned by `product_user`, `inventory` schema owned by `inventory_user`; both already created in `infra/postgres/init.sh` with role-deny matrix. `spring.flyway.create-schemas: false` mandatory. [VERIFIED: init.sh lines 56-68]
- **Service template pattern:** Every new business service clones `service-template/skeleton/`, renames tokens, adds `<svc>-service.yml` in config-server, adds entry to `settings.gradle.kts` + `docker-compose.yml`. [VERIFIED: Phase 3 PATTERNS.md]
- **Port convention:** Gateway = 8080, identity = 8081, product = 8082, inventory = 8083 (808x ladder). [ASSUMED — port assignments for product/inventory inferred from the ladder; identity-service.yml confirms port 8081 for identity]
- **No cross-service DB joins:** `product_user` and `inventory_user` are REVOKEd from each other's schemas. `inventory-service` never queries `product` schema. [VERIFIED: init.sh REVOKE matrix]
- **Springdoc 2.8.17 WebMVC per service + gateway WebFlux aggregator:** Already proven in Phase 3. [VERIFIED: service-template.yml + api-gateway.yml]
- **Flyway 12.5 + flyway-database-postgresql 12.5.0:** Explicit dependency required since Flyway 10. [VERIFIED: service-template/build.gradle.kts]
- **JWT-at-edge trust model:** Services read `X-User-Id` / `X-User-Roles` from gateway-injected headers; no Spring Security JWT decoding in business services. Admin gate can be implemented at service layer via header inspection (or at gateway path). [VERIFIED: api-contracts.md §4]
- **LOC-01:** `price_gross` stored in DB (KDV-inclusive price); `kdv_rate` stored as column. [VERIFIED: REQUIREMENTS.md LOC-01]
- **Seed data:** Minimum 50 products, 8 categories, Turkish. [VERIFIED: PROD-09, Phase 4 success criterion]
- **`order.created` consumer:** inventory-service must be the saga participant for stock reservation; `processed_events` idempotency inbox is mandatory. [VERIFIED: saga-contracts.md §5.2 + ARCHITECTURE.md §2.6]

### Deferred Ideas (OUT OF SCOPE for Phase 4)
- Semantic search / pgvector embeddings — Phase 8 (v2 stretch, AI-V2-01)
- `product.created/updated/deleted` consumers in search-service — Phase 8
- Phase 5 saga wire-up (`stock.reserved` → payment-service) — explicitly NOT wired in Phase 4 (success criterion #4 says inventory publishes but Phase 5 wires downstream)
- Category filter sidebar on listing — Phase 10 frontend
- Admin CRUD UI — out of scope entirely (Swagger UI covers it)

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| PROD-01 | Paginated product listing (0-based index, default 20) | Spring Data JPA `Pageable` + `PageRequest.of(page, size)`, Spring Data returns `Page<T>`. Research §Standard Stack + §Architecture Patterns §Pagination |
| PROD-02 | PDP: title, gallery, KDV-inclusive price, stock state, seller info | `GET /products/{id}` + enriched with `GET /inventory/{productId}` call or bundled DTO. Stock state computed in inventory-service. Research §PDP Fields + §Stock State |
| PROD-03 | 8 top-level categories; navigation | `categories` table, `GET /categories` endpoint. Research §Schema + §Category Design |
| PROD-04 | ILIKE text search with GIN index | `pg_trgm` extension + `gin_trgm_ops` GIN index. Spring Data `@Query` with `ILIKE`. Research §ILIKE+GIN |
| PROD-05 | Sort by price asc/desc, date desc | `Pageable` sort params: `sort=priceGross,asc`, `sort=createdAt,desc`. Spring Data `Sort`. Research §Pagination |
| PROD-06 | Stock indicator: "Stokta" / "Tükendi" / "Son N ürün!" | `GET /inventory/{productId}` returns `StockStateDto`. Thresholds: `available_qty <= 0` → Tükendi; `available_qty > 0 AND available_qty <= low_stock_threshold` → Son N ürün; else → Stokta. Research §Stock State |
| PROD-07 | Springdoc Swagger UI at `/swagger-ui.html`; gateway aggregator surfaces it | Per-service `springdoc-openapi-starter-webmvc-ui:2.8.17` + gateway aggregator entry. Research §Springdoc |
| PROD-08 | inventory-service: stock per SKU, `reserve` + `release` event handlers as saga participant | `stock` table with `@Version`, `reservations` table, `@RabbitListener` on `inventory.q.order-created`. Research §Inventory Saga Consumer |
| PROD-09 | Seed data: ≥50 products in Turkish across 8 categories | Flyway `V3__seed_products.sql`. Research §Seed Data |
</phase_requirements>

---

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot Starter Web | 3.5.14 | MVC + embedded Tomcat | Service-template pattern; same as identity-service |
| Spring Boot Starter Data JPA | 3.5.14 | JPA / Hibernate 6.6.x | Standard for all business services |
| Spring Boot Starter Validation | 3.5.14 | Jakarta Validation on DTOs | RFC-7807 `errors[]` for validation failures |
| Spring Boot Starter AMQP | 3.5.14 | RabbitMQ consumer (inventory-service) | Saga participant; same listener factory as other services |
| Flyway Core | 12.5.0 | Per-service schema migrations | `org.flywaydb:flyway-core:12.5.0` |
| flyway-database-postgresql | 12.5.0 | Required since Flyway 10 | `org.flywaydb:flyway-database-postgresql:12.5.0` |
| springdoc-openapi-starter-webmvc-ui | 2.8.17 | Swagger UI per service | `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.17` |
| PostgreSQL JDBC | BOM-managed | JDBC driver | Transitive from Spring Boot |
| logstash-logback-encoder | 8.0 | JSON structured logs | Service-template standard |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| :common-error | project | RFC-7807 ProblemDetailControllerAdvice | All business services |
| :common-logging | project | CorrelationId MDC filter + RestClient interceptor | All business services |
| :common-events | project | Saga envelope DTO + JSON-Schema validator | inventory-service saga consumer |
| spring-boot-testcontainers | 3.5.14 | Testcontainers integration | Integration tests |
| testcontainers:postgresql | 2.0.5 | Real Postgres in tests | Repository + ILIKE tests |
| testcontainers:rabbitmq | 2.0.5 | Real RabbitMQ in tests | Saga consumer integration test |
| awaitility | 4.2.x | Async assertion | Saga consumer test (message arrival) |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Spring Data JPA `@Query` ILIKE | Full-text search `to_tsvector` / `ts_query` | Full-text is richer but overkill for v1 ILIKE requirement; pg_trgm ILIKE is simpler and satisfies PROD-04 |
| `@Version` optimistic locking for stock | `SELECT ... FOR UPDATE` pessimistic | Pessimistic lock blocks other readers; optimistic is sufficient for low-concurrency demo |
| Per-product `low_stock_threshold` column | Global config property | Column-level gives per-product granularity; chosen approach (see Open Questions #1) |

**Installation (both services share same deps):**
```bash
# Gradle — already in service-template/build.gradle.kts shape
# Just clone skeleton and add per-service spring.application.name
# No new binary dependencies for product-service
# inventory-service: no extra deps beyond the template (AMQP is already in template)
```

---

## Architecture Patterns

### System Architecture Diagram

```
Frontend (Phase 10)
    |
    v  GET /api/v1/products?page&size&q&sort      GET /api/v1/products/{id}
[api-gateway 8080] ─────────────────────────────> [product-service 8082]
    |                                                     |
    |  GET /api/v1/inventory/{productId}                  | (PDP: sync call to inventory-service
    +────────────────────────────────────────────> [inventory-service 8083] for stock state)
    |
    |  (admin writes) POST /api/v1/products
    +────────────────────────────────────────────> [product-service 8082]
                                                         |
                                                 [product: product schema]
                                                         |
                                                   Postgres port 5432

RabbitMQ (Phase 5 wires consumers):
  order-service ─── orders.tx/order.created ──────────> [inventory-service]
                                                              |
                                                    [inventory: inventory schema]
                                                              |
                                                        Postgres port 5432
                                                              |
                                                         inventory.tx/stock.reserved
                                                         inventory.tx/stock.reserve_failed
                                                         (published, no consumer until Phase 5)

  product-service ── products.tx/product.* ─────────────> (search-service Phase 8 skeleton)
```

### Recommended Project Structure

```
product-service/
├── build.gradle.kts
├── src/main/java/com/n11/product/
│   ├── ProductServiceApplication.java         # @SpringBootApplication @EnableDiscoveryClient
│   ├── category/
│   │   ├── Category.java                      # @Entity
│   │   ├── CategoryRepository.java            # JpaRepository<Category, UUID>
│   │   └── CategoryController.java            # GET /categories
│   ├── product/
│   │   ├── Product.java                       # @Entity
│   │   ├── ProductRepository.java             # JpaRepository + @Query ILIKE
│   │   ├── ProductService.java                # business logic
│   │   ├── ProductController.java             # GET /products, GET /products/{id}
│   │   └── dto/
│   │       ├── ProductSummaryDto.java          # listing item
│   │       └── ProductDetailDto.java           # PDP fields
│   └── config/
│       └── ProductRabbitConfig.java            # products.tx exchange declaration
└── src/main/resources/
    ├── application.yml                         # name: product-service + configserver import
    ├── logback-spring.xml
    └── db/migration/
        ├── V1__init_processed_events.sql       # from skeleton
        ├── V2__init_product_catalog.sql        # categories + products DDL + pg_trgm
        └── V3__seed_products.sql              # ≥50 Turkish products

inventory-service/
├── build.gradle.kts
├── src/main/java/com/n11/inventory/
│   ├── InventoryServiceApplication.java        # @EnableDiscoveryClient @EnableScheduling
│   ├── stock/
│   │   ├── Stock.java                          # @Entity with @Version field
│   │   ├── StockRepository.java
│   │   └── StockController.java                # GET /inventory/{productId}
│   ├── reservation/
│   │   ├── StockReservation.java               # @Entity
│   │   ├── StockReservationRepository.java
│   │   └── InventoryService.java               # reserve() / release() business logic
│   ├── messaging/
│   │   ├── InventoryRabbitConfig.java          # exchange + queue declarations
│   │   └── OrderCreatedConsumer.java           # @RabbitListener on inventory.q.order-created
│   └── outbox/
│       ├── OutboxEvent.java                    # same as identity-service pattern
│       ├── OutboxRepository.java               # FOR UPDATE SKIP LOCKED
│       └── OutboxPoller.java                   # @Scheduled drain to inventory.tx
└── src/main/resources/
    ├── application.yml                         # name: inventory-service
    ├── logback-spring.xml
    └── db/migration/
        ├── V1__init_processed_events.sql       # from skeleton
        └── V2__init_inventory.sql              # stock + reservations + outbox tables
```

### Pattern 1: Spring Data JPA Pagination with Springdoc

**What:** Standard Spring Data `Pageable` bound via `@ParameterObject` for Swagger documentation.
**When to use:** All listing endpoints (PROD-01, PROD-05).
**Example:**
```java
// Source: springdoc.org + Context7 /spring-projects/spring-data-jpa
@GetMapping("/products")
public Page<ProductSummaryDto> list(
        @ParameterObject Pageable pageable,   // @ParameterObject expands to ?page=0&size=20&sort=priceGross,asc
        @RequestParam(required = false) String q,
        @RequestParam(required = false) UUID category) {
    return productService.search(q, category, pageable);
}

// Repository — @Query with native ILIKE:
public interface ProductRepository extends JpaRepository<Product, UUID> {

    @Query("SELECT p FROM Product p WHERE " +
           "(:q IS NULL OR LOWER(p.nameTr) LIKE LOWER(CONCAT('%', :q, '%'))) AND " +
           "(:categoryId IS NULL OR p.category.id = :categoryId)")
    Page<Product> search(@Param("q") String q,
                         @Param("categoryId") UUID categoryId,
                         Pageable pageable);
}

// Alternatively, native SQL for explicit ILIKE (lets Postgres use the GIN index):
@Query(value = "SELECT * FROM product.products p WHERE " +
               "(:q IS NULL OR p.name_tr ILIKE '%' || :q || '%') AND " +
               "(:categoryId IS NULL OR p.category_id = :categoryId) " +
               "ORDER BY p.created_at DESC",
       countQuery = "SELECT count(*) FROM product.products p WHERE ...",
       nativeQuery = true)
Page<Product> searchNative(...);
```

**Pagination response shape (PageResponse DTO):**
```java
// The planner should decide whether to return Spring's Page<T> directly or wrap it.
// Recommendation: return Spring's Page<T> — Springdoc renders it correctly.
// If a custom shape is needed:
public record PageResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean last
) {
    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(),
            page.getSize(), page.getTotalElements(), page.getTotalPages(), page.isLast());
    }
}
```

**Pitfall #24 (0-indexed):** Document `page=0` is page 1 in the Swagger description. [VERIFIED: REQUIREMENTS.md PROD-01 "page index 0-based"]

### Pattern 2: Postgres pg_trgm GIN Index for ILIKE (PROD-04)

**What:** Extension + DDL enables Postgres to use an index for arbitrary ILIKE queries.
**When to use:** Mandatory for PROD-04. Without the index, ILIKE does a full table scan on every request.
**Example:**
```sql
-- In V2__init_product_catalog.sql (runs inside `product` schema)
-- pg_trgm was already enabled in init.sh as superuser (CREATE EXTENSION pg_trgm;)
-- If not, enable per-schema:
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE categories (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    slug       TEXT         NOT NULL UNIQUE,
    name_tr    TEXT         NOT NULL,
    parent_id  UUID         REFERENCES categories(id),
    sort_order INT          NOT NULL DEFAULT 0
);

CREATE TABLE products (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    sku             TEXT         NOT NULL UNIQUE,
    name_tr         TEXT         NOT NULL,
    description_tr  TEXT,
    price_gross     NUMERIC(12,2) NOT NULL,  -- KDV-inclusive per LOC-01
    kdv_rate        NUMERIC(5,2) NOT NULL DEFAULT 20.00,  -- 20% VAT
    category_id     UUID         REFERENCES categories(id),
    image_urls      TEXT[],                  -- gallery as Postgres array
    seller_name     TEXT         NOT NULL DEFAULT 'n11 Pazaryeri',
    slug            TEXT         NOT NULL UNIQUE,  -- for /urun/:slug-:id frontend URL
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- GIN trigram index: enables ILIKE '%query%' to use the index [VERIFIED: postgresql.org/docs/current/pgtrgm.html]
CREATE INDEX idx_products_name_trgm ON products USING GIN (name_tr gin_trgm_ops);
-- B-tree for sort support
CREATE INDEX idx_products_price ON products (price_gross);
CREATE INDEX idx_products_created_at ON products (created_at DESC);
```

**Turkish i/İ caveat:** pg_trgm's `gin_trgm_ops` operator class is case-insensitive by default ("in a default build of pg_trgm") [VERIFIED: postgresql.org/docs/current/pgtrgm.html]. However, the Turkish dotted-i / dotless-i distinction (`İ → i` vs `I → ı`) is locale-specific and NOT handled by pg_trgm's default case folding. The ILIKE operator uses the database-level collation. For v1 demo purposes, `LOWER(name_tr) ILIKE LOWER('%' || q || '%')` with a GIN index on `LOWER(name_tr)` provides sufficient Turkish-language matching:

```sql
-- Functional index on lower() for Turkish-friendly ILIKE:
CREATE INDEX idx_products_name_lower_trgm
    ON products USING GIN (lower(name_tr) gin_trgm_ops);
```

Then query: `WHERE lower(name_tr) ILIKE lower('%' || :q || '%')`. This is pragmatic for the demo. Full ICU `tr-x-icu` collation would require a DB-level collation change and is [ASSUMED] not worth the complexity for a 6-day demo. [MEDIUM confidence — Turkish i/İ distinction requires ICU collation for full correctness; lowercase+ILIKE covers >90% of realistic queries]

### Pattern 3: Flyway Per-Schema Configuration (Pitfall #13 mitigation)

**What:** Each service runs Flyway against its own schema using the constrained DB user from `init.sh`.
**When to use:** Every business service (already established in Phase 3).

Config in `config-server/src/main/resources/config/product-service.yml`:
```yaml
server:
  port: 8082

spring:
  datasource:
    url: jdbc:postgresql://postgres:5432/n11
    username: ${db.user:product_user}
    password: ${db.password:${PRODUCT_DB_PASSWORD}}
  flyway:
    enabled: true
    locations: classpath:db/migration
    schemas: product
    default-schema: product
    create-schemas: false        # init.sh already created schema
    user: ${spring.datasource.username}
    password: ${spring.datasource.password}
  jpa:
    properties:
      hibernate:
        default_schema: product

flyway:
  schema: product               # placeholder consumed by service-template.yml

springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
    path: /swagger-ui.html
```

Inventory-service.yml mirrors this with `schema: inventory`, port `8083`, `db.user: inventory_user`, `INVENTORY_DB_PASSWORD`.

### Pattern 4: Inventory Saga Consumer (PROD-08, ARCH-07)

**What:** `inventory-service` consumes `order.created`, performs row-versioned stock reservation, publishes result via outbox. Implements the `processed_events` idempotency inbox.
**When to use:** Mandatory for Phase 4 success criterion #4.

```java
// Source: saga-contracts.md §5.2 + ARCHITECTURE.md §3.2
@RabbitListener(queues = "inventory.q.order-created",
                containerFactory = "rabbitListenerContainerFactory")  // DLX retry from common-events
@Transactional
public void handleOrderCreated(Message message) {
    // Step 1: Deserialize envelope
    Envelope envelope = objectMapper.readValue(message.getBody(), Envelope.class);
    UUID eventId = UUID.fromString(envelope.getEventId());

    // Step 2: Idempotency check (processed_events inbox — from skeleton V1)
    if (processedEventsRepository.existsById(eventId)) {
        return;  // already processed — ack silently
    }

    OrderCreatedPayload payload = objectMapper.treeToValue(
        envelope.getPayload(), OrderCreatedPayload.class);

    // Step 3: Try to reserve stock for each item
    boolean allReserved = true;
    List<FailedItem> failedItems = new ArrayList<>();
    for (OrderItem item : payload.getItems()) {
        try {
            inventoryService.reserveStock(item.getProductId(), item.getQty());
        } catch (InsufficientStockException ex) {
            allReserved = false;
            failedItems.add(new FailedItem(item.getProductId(), item.getQty(), ex.getAvailable()));
        }
    }

    // Step 4: Publish result to outbox (same TX)
    String resultEventType = allReserved ? "stock.reserved" : "stock.reserve_failed";
    outboxRepository.save(buildOutboxEvent(resultEventType, payload, failedItems, envelope.getCorrelationId()));

    // Step 5: Mark processed (same TX — atomic with outbox insert)
    processedEventsRepository.save(new ProcessedEvent(eventId, "inventory-service", envelope.getEventType()));
}

// InventoryService.reserveStock — optimistic locking via @Version
@Transactional
public void reserveStock(UUID productId, int qty) {
    Stock stock = stockRepository.findById(productId)
        .orElseThrow(() -> new ProductNotFoundException(productId));
    int available = stock.getAvailableQty() - stock.getReservedQty();
    if (available < qty) {
        throw new InsufficientStockException(productId, qty, available);
    }
    stock.setReservedQty(stock.getReservedQty() + qty);
    stockRepository.save(stock);  // @Version check done by Hibernate on UPDATE
}
```

**`@Version` optimistic locking on Stock entity:**
```java
@Entity
@Table(name = "stock")
public class Stock {
    @Id
    private UUID productId;

    @Column(nullable = false)
    private int availableQty;

    @Column(nullable = false)
    private int reservedQty;

    @Version
    private Long version;        // Hibernate adds WHERE version=? to UPDATE — prevents lost updates
}
```

**Phase 4 scope note:** The `stock.reserved` / `stock.reserve_failed` events ARE published to `inventory.tx` but **no service consumes them in Phase 4**. Phase 5 adds the `payment.q.stock-reserved` and `order.q.stock-reserved` consumer bindings. This is explicitly called out in the Phase 4 success criterion: "does not publish `stock.reserved` to live consumers yet." The outbox poller still drains the events to RabbitMQ — they just accumulate until Phase 5 adds listeners. [VERIFIED: ROADMAP.md Phase 4 SC #4]

### Pattern 5: Stock State DTO for PDP (PROD-06)

**What:** `GET /inventory/{productId}` returns a DTO the PDP uses to render the stock badge.
**When to use:** Called by the frontend (via gateway) when rendering PDP.

```java
public record StockStateDto(
    UUID productId,
    int availableQty,         // available_qty - reserved_qty
    String stockState,        // "STOKTA" | "TUKENDI" | "SON_URUN"
    String stockStateLabel,   // "Stokta" | "Tükendi" | "Son N ürün!"
    int displayQty            // N for "Son N ürün!" when state == SON_URUN
) {}

// In InventoryService:
public StockStateDto getStockState(UUID productId) {
    Stock stock = stockRepository.findById(productId)
        .orElseThrow(() -> new ProductNotFoundException(productId));
    int available = stock.getAvailableQty() - stock.getReservedQty();
    if (available <= 0) {
        return new StockStateDto(productId, 0, "TUKENDI", "Tükendi", 0);
    } else if (available <= stock.getLowStockThreshold()) {
        return new StockStateDto(productId, available, "SON_URUN",
                                 "Son " + available + " ürün!", available);
    }
    return new StockStateDto(productId, available, "STOKTA", "Stokta", 0);
}
```

**Low-stock threshold:** stored per-product as `low_stock_threshold` column on the `stock` table, defaulting to 5. See Open Questions #1 for the locked decision.

### Pattern 6: Springdoc Gateway Aggregator Extension (PROD-07, QUAL-01)

**What:** Add `product-service` and `inventory-service` entries to the existing `springdoc.swagger-ui.urls` in `api-gateway.yml`.
**When to use:** Every new service gets an entry.

Add to `config-server/src/main/resources/config/api-gateway.yml` under `springdoc.swagger-ui.urls`:
```yaml
      - name: product-service
        url: /api/v1/products/v3/api-docs
      - name: inventory-service
        url: /api/v1/inventory/v3/api-docs
```

The StripPrefix=3 filter on the routes (same as `identity-service`) strips `/api/v1/products` → forwards bare path to product-service. Springdoc's `/v3/api-docs` is served by product-service at its own path; the gateway proxies it at `/api/v1/products/v3/api-docs`. [VERIFIED: existing api-gateway.yml Phase 3 pattern for identity-service + Springdoc official docs]

Gateway routes to add to `api-gateway.yml`:
```yaml
          routes:
            - id: product-service
              uri: lb://PRODUCT-SERVICE
              predicates:
                - Path=/api/v1/products/**
              filters:
                - StripPrefix=3
            - id: inventory-service
              uri: lb://INVENTORY-SERVICE
              predicates:
                - Path=/api/v1/inventory/**
              filters:
                - StripPrefix=3
```

### Pattern 7: PDP Fields + Admin Gate

**Product entity fields (for PDP — PROD-02):**
- `title` → `name_tr`
- `gallery` → `image_urls TEXT[]` (Postgres array; mapped via `@Type` in Hibernate or `@Column` with array handling)
- `price` → `price_gross` (KDV-inclusive, `NUMERIC(12,2)`) + `kdv_rate` (default 20.00)
- `stock state` → fetched from `GET /inventory/{productId}` (sync call from frontend via gateway, not from product-service)
- `seller info` → `seller_name TEXT NOT NULL DEFAULT 'n11 Pazaryeri'`
- `slug` → generated at product-create time; Turkish normalization (dotted-i → `i`, space → `-`, lower-cased)

**Slug generation:**
```java
public static String toSlug(String nameTr) {
    return nameTr.toLowerCase(new Locale("tr", "TR"))   // Turkish locale for proper dotted-i
        .replaceAll("[\\s]+", "-")
        .replaceAll("[^a-z0-9\\-]", "")
        .replaceAll("-{2,}", "-")
        .replaceAll("^-|-$", "");
}
// Then URL is /urun/{slug}-{id} (Phase 10 frontend)
```

**Admin gate — where to enforce ROLE_ADMIN:**
Phase 3 already wired the gateway with `@EnableWebFluxSecurity` and path-level security. For Phase 4, admin endpoints (`POST /products`, `PUT /products/{id}`, `DELETE /products/{id}`) need protection. Two options:
1. Gateway allowlist (already covers public routes); add a path-matcher `hasRole("ROLE_ADMIN")` in api-gateway SecurityConfig — cleaner, one central location.
2. `@PreAuthorize("@headerAuth.isAdmin(request)")` in product-service controller (reads `X-User-Roles` header).

**Recommendation:** Defense-in-depth — gateway already gates (any non-allowlisted route requires `authenticated()`). Add `@PreAuthorize` guard in product-service as a second layer. Implement it as a simple utility that reads the `X-User-Roles` request header:
```java
// Not Spring Security, just header inspection:
private void requireAdmin(HttpServletRequest req) {
    String roles = req.getHeader("X-User-Roles");
    if (roles == null || !roles.contains("ROLE_ADMIN")) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
}
```
This is consistent with the project's "downstream services trust gateway-injected mesh" pattern. No Spring Security config needed in product-service (it's permitAll() like identity-service). [VERIFIED: api-contracts.md §4, identity-service uses same header-read pattern]

### Anti-Patterns to Avoid

- **Cross-schema join:** Never write `FROM inventory.stock s JOIN product.products p ON s.product_id = p.id` in any JPA query or native SQL. The role-deny matrix prevents it at runtime anyway.
- **Dual-write (RabbitMQ + DB):** Inventory saga consumer MUST write the outbox event in the SAME transaction as the stock reservation. Never `save(stock); rabbitTemplate.send(...)` without the outbox pattern.
- **JPQL ILIKE:** Hibernate's JPQL `LIKE` is case-sensitive in standard SQL; use native `@Query` with `ILIKE` or Spring's `LOWER(field) LIKE LOWER(?)` JPQL form to use the GIN index correctly.
- **Missing `@Transactional` on saga consumer:** If `@RabbitListener` method is not `@Transactional`, the outbox insert and `processed_events` insert don't share a transaction boundary — idempotency guarantee breaks.
- **Fetching all products without Pageable:** Never `findAll()` in the listing controller — forces full table scan and OOM for large datasets.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Case-insensitive text search | Custom `toLowerCase()` filter in Java | `pg_trgm` GIN index + SQL `ILIKE` | DB-side; index-accelerated; O(log n) vs O(n) |
| Pagination offset math | Manual `LIMIT / OFFSET` SQL | Spring Data `Pageable` + `Page<T>` | Total count query, page metadata, cursor safety |
| Swagger documentation | Custom JSON-spec files | `@ParameterObject Pageable` + Springdoc auto-generation | Auto-syncs with controller signatures |
| Optimistic locking | Check-then-act with two queries | `@Version` on Stock entity | Race-condition-safe; one CAS UPDATE |
| Idempotent consumer | Custom dedup logic | `processed_events` table + inbox pattern (from skeleton) | Already scaffolded in V1__init_processed_events.sql |
| Saga event publishing | Direct `rabbitTemplate.send()` on stock update | Transactional outbox + poller | Prevents dual-write; outbox pattern is the project standard |

**Key insight:** The skeleton's `V1__init_processed_events.sql` already creates the inbox table. The outbox table pattern is established in identity-service (Phase 3). Both services just need to use the existing patterns — zero new infrastructure.

---

## Runtime State Inventory

> This section is N/A — Phase 4 is greenfield (no rename, no migration of existing runtime state).

Phase 4 creates brand-new services from the skeleton. No existing runtime data, no OS-registered state, no stored data to migrate.

**Product and inventory schemas exist** in Postgres (created by `infra/postgres/init.sh`) but are empty — no tables yet. Flyway will create them.

---

## Common Pitfalls

### Pitfall 11: Cross-Schema Accidental Joins
**What goes wrong:** Developer writes a JPA query in product-service that joins with the `inventory` schema tables (or vice versa), bypassing the service boundary.
**Why it happens:** Both schemas live on the same Postgres host; JPQL or native SQL can reference fully-qualified table names.
**How to avoid:** Hibernate `default_schema` in JPA config points product-service only to the `product` schema. The `product_user` RDBMS role is REVOKEd from `inventory` schema — any cross-schema SQL fails at runtime with `permission denied`. [VERIFIED: init.sh REVOKE matrix lines 123-130]
**Warning signs:** `permission denied for schema inventory` in product-service logs; `permission denied for schema product` in inventory-service logs.

### Pitfall 13: Flyway Migration Coordination on Shared Postgres
**What goes wrong:** Both product-service and inventory-service start simultaneously on a fresh database; Flyway tries to create the `flyway_schema_history` table in both schemas concurrently — no race condition on separate schemas, but if `create-schemas: false` is missing, Flyway tries to CREATE SCHEMA and fails (user lacks permission).
**Why it happens:** Flyway 10+ has `create-schemas: true` as default. Schema was created by `init.sh` as superuser; service user can't CREATE SCHEMA.
**How to avoid:** `spring.flyway.create-schemas: false` in every service YAML (already in `service-template.yml` baseline). [VERIFIED: service-template.yml]
**Warning signs:** `FlywayException: CREATE SCHEMA product FAILED: ERROR: permission denied to create schema`

### Pitfall 24: Pagination Off-by-One
**What goes wrong:** Frontend sends `page=1` expecting page 1 data; Spring Data interprets it as page 2 (0-indexed). User sees the wrong data.
**Why it happens:** Spring Data Pageable is 0-indexed by default. `PageRequest.of(0, 20)` = first page.
**How to avoid:** Document in Swagger (`@Operation(description = "Page is 0-indexed. Page 0 = first page.")`) and in the API contracts section. The contract already states this: "page index 0-based, configurable size, default 20" [VERIFIED: REQUIREMENTS.md PROD-01]. Include this note in `@GetMapping` Javadoc.
**Warning signs:** Frontend shows wrong products per page; page 0 returns empty.

### Pitfall (New): `@Version` OptimisticLockException under concurrent reservations
**What goes wrong:** Two saga consumers try to reserve stock for the same product at near-simultaneous time; one throws `ObjectOptimisticLockingFailureException`. The DLX retry policy (3 attempts, 1s/5s backoff from `saga-contracts.md §4`) will retry the consumer — on retry the stock may be available or not. If stock is exhausted on retry, the event should produce `stock.reserve_failed`, not propagate an exception.
**Why it happens:** At-least-once delivery + concurrent load during demo.
**How to avoid:** Catch `ObjectOptimisticLockingFailureException` in the saga consumer and retry the stock-check logic (not the whole consumer — just the `reserveStock` call) before emitting the failure event. Spring AMQP's retry interceptor from `common-events` already retries the whole message delivery which covers this.
**Warning signs:** `ObjectOptimisticLockingFailureException` in inventory-service logs; DLQ accumulation.

### Pitfall (New): Missing `products.tx` exchange declaration
**What goes wrong:** product-service starts but never declares `products.tx`; Phase 8's search-service tries to bind `search.q.product-events` to a non-existent exchange → `channel error; protocol method: #method<channel.close>(reply-code=404)`.
**Why it happens:** Phase 4 only declares the exchange (no producer yet); easy to skip.
**How to avoid:** Add a `TopicExchange` bean in `ProductRabbitConfig.java` that declares `products.tx` as a durable topic exchange. Phase 8 adds the producer. [VERIFIED: saga-contracts.md — `products.tx` added in Phase 4, note on line 53]

---

## Code Examples

Verified patterns from project sources and official docs:

### Pageable Controller (PROD-01, PROD-05)
```java
// Source: springdoc.org @ParameterObject + Context7 /spring-projects/spring-data-jpa
@Operation(summary = "Paginated product listing",
           description = "Page is 0-indexed. Page 0 = first page. Default size = 20. " +
                         "Sort examples: sort=priceGross,asc | sort=createdAt,desc")
@GetMapping
public Page<ProductSummaryDto> list(
        @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
        Pageable pageable,
        @RequestParam(required = false) String q,
        @RequestParam(required = false) UUID categoryId) {
    return productService.search(q, categoryId, pageable);
}
```

### Native ILIKE Query with GIN Index
```java
// Source: postgresql.org/docs/current/pgtrgm.html + verified GIN index pattern
@Query(value = """
    SELECT * FROM products
    WHERE (:q IS NULL OR lower(name_tr) ILIKE lower('%' || :q || '%'))
      AND (:categoryId IS NULL OR category_id = :categoryId::uuid)
    """,
       countQuery = """
    SELECT count(*) FROM products
    WHERE (:q IS NULL OR lower(name_tr) ILIKE lower('%' || :q || '%'))
      AND (:categoryId IS NULL OR category_id = :categoryId::uuid)
    """,
       nativeQuery = true)
Page<Product> search(@Param("q") String q,
                     @Param("categoryId") String categoryId,
                     Pageable pageable);
```

### Flyway Product Schema Migration Skeleton
```sql
-- V2__init_product_catalog.sql
-- Runs inside `product` schema (Flyway default-schema=product, create-schemas=false)

CREATE EXTENSION IF NOT EXISTS pg_trgm;   -- [VERIFIED: postgresql.org/docs/current/pgtrgm.html]

CREATE TABLE categories (
    id         UUID  PRIMARY KEY DEFAULT gen_random_uuid(),
    slug       TEXT  NOT NULL UNIQUE,
    name_tr    TEXT  NOT NULL,
    parent_id  UUID  REFERENCES categories(id),
    sort_order INT   NOT NULL DEFAULT 0
);

CREATE TABLE products (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    sku             TEXT          NOT NULL UNIQUE,
    name_tr         TEXT          NOT NULL,
    description_tr  TEXT,
    price_gross     NUMERIC(12,2) NOT NULL,
    kdv_rate        NUMERIC(5,2)  NOT NULL DEFAULT 20.00,
    category_id     UUID          REFERENCES categories(id),
    image_urls      TEXT[]        NOT NULL DEFAULT '{}',
    seller_name     TEXT          NOT NULL DEFAULT 'n11 Pazaryeri',
    slug            TEXT          NOT NULL UNIQUE,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- GIN trigram index for ILIKE [VERIFIED: postgresql.org/docs/current/pgtrgm.html]
CREATE INDEX idx_products_name_lower_trgm ON products USING GIN (lower(name_tr) gin_trgm_ops);
CREATE INDEX idx_products_price ON products (price_gross);
CREATE INDEX idx_products_created_at ON products (created_at DESC);
CREATE INDEX idx_products_category_id ON products (category_id);
```

### Flyway Inventory Schema Migration Skeleton
```sql
-- V2__init_inventory.sql
-- Runs inside `inventory` schema

CREATE TABLE stock (
    product_id         UUID    PRIMARY KEY,
    available_qty      INT     NOT NULL DEFAULT 0 CHECK (available_qty >= 0),
    reserved_qty       INT     NOT NULL DEFAULT 0 CHECK (reserved_qty >= 0),
    low_stock_threshold INT    NOT NULL DEFAULT 5,
    version            BIGINT  NOT NULL DEFAULT 0   -- optimistic locking @Version
);

CREATE TABLE stock_reservations (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id        UUID        NOT NULL,
    product_id      UUID        NOT NULL REFERENCES stock(product_id),
    reserved_qty    INT         NOT NULL,
    status          TEXT        NOT NULL DEFAULT 'RESERVED',  -- RESERVED | RELEASED | COMMITTED
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ
);
CREATE INDEX idx_reservations_order_id ON stock_reservations (order_id);

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

### docker-compose Service Block Pattern (mirrors Phase 3)
```yaml
  product-service:
    image: n11/product-service:dev
    container_name: n11-product-service
    env_file: [ .env ]
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
      test: ["CMD-SHELL", "wget -q -O- http://localhost:8082/actuator/health | grep -q '\"status\":\"UP\"'"]
      interval: 5s
      timeout: 3s
      retries: 6
      start_period: 25s
    restart: unless-stopped
    networks: [ n11-net ]
    # NOTE: No `ports:` mapping — Pitfall #14 mitigation; access only via gateway
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `@ParameterObject` not available | `@ParameterObject Pageable` for Swagger docs | springdoc-openapi v1.6.0+ | Pageable params now auto-expanded in Swagger UI |
| `flyway-core` alone for Postgres | `flyway-core` + `flyway-database-postgresql` required | Flyway 10 | Separate artifact required — already in build.gradle.kts template |
| `spring-cloud-starter-gateway` | `spring-cloud-starter-gateway-server-webflux` | Spring Cloud 2025.0 (Northfields) | Already established in Phase 1 — no change needed |

**Deprecated/outdated:**
- JPQL `LIKE` with case-insensitive hack: replaced by `ILIKE` + `pg_trgm` GIN index for better performance.
- `@EnableJpaRepositories` explicit annotation: not needed in Boot 3.x; auto-configured.

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit Jupiter 5.11.x + Spring Boot Test 3.5.14 + Testcontainers 2.0.5 |
| Config file | none — Spring Boot Test auto-configure |
| Quick run command | `./gradlew :product-service:test --tests "*Smoke*" -x intTest` |
| Full suite command | `./gradlew :product-service:test :inventory-service:test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| PROD-01 | Paginated listing returns page 0 size 20 | unit | `./gradlew :product-service:test --tests "*ProductRepositoryTest*"` | Wave 0 gap |
| PROD-04 | ILIKE search returns matching products | integration (TC Postgres) | `./gradlew :product-service:test --tests "*ProductSearchIntegrationTest*"` | Wave 0 gap |
| PROD-05 | Sort by priceGross asc returns correct order | unit | `./gradlew :product-service:test --tests "*ProductRepositoryTest*"` | Wave 0 gap |
| PROD-06 | Stock state: Stokta/Tükendi/Son N ürün! | unit | `./gradlew :inventory-service:test --tests "*StockStateTest*"` | Wave 0 gap |
| PROD-07 | Swagger UI reachable, Pageable params expanded | smoke (integration) | `./gradlew :product-service:test --tests "*SwaggerSmokeTest*"` | Wave 0 gap |
| PROD-08 | Saga consumer: order.created → stock.reserved emitted (idempotent) | integration (TC Postgres + RabbitMQ) | `./gradlew :inventory-service:test --tests "*OrderCreatedConsumerIntegrationTest*"` | Wave 0 gap |
| ARCH-07 | Re-delivered event_id processed exactly once | integration (TC) | included in PROD-08 test | Wave 0 gap |

### Sampling Rate
- **Per task commit:** `./gradlew :product-service:test --tests "*Smoke*" :inventory-service:test --tests "*Smoke*"` (pure unit, < 10s)
- **Per wave merge:** `./gradlew :product-service:test :inventory-service:test` (includes TC integration, ~60s)
- **Phase gate:** Full suite green before `/gsd-verify-work`

### Wave 0 Gaps
- [ ] `product-service/src/test/java/com/n11/product/ProductRepositoryTest.java` — pagination + ILIKE + sort
- [ ] `product-service/src/test/java/com/n11/product/ProductSearchIntegrationTest.java` — Testcontainers Postgres + GIN index
- [ ] `inventory-service/src/test/java/com/n11/inventory/StockStateTest.java` — unit: threshold logic
- [ ] `inventory-service/src/test/java/com/n11/inventory/OrderCreatedConsumerIntegrationTest.java` — Testcontainers Postgres + RabbitMQ + Awaitility

---

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | JWT validated at gateway (Phase 3) |
| V3 Session Management | no | Stateless services |
| V4 Access Control | yes | Admin gate: `X-User-Roles` header check in product-service for write endpoints |
| V5 Input Validation | yes | Jakarta Validation (`@NotBlank`, `@DecimalMin`) on product create/update DTOs |
| V6 Cryptography | no | No crypto in these services |

### Known Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Unauthenticated admin product write | Elevation of Privilege | Header `X-User-Roles` check + gateway `authenticated()` |
| SQL injection via `q` parameter | Tampering | Parameterized Spring Data `@Query` with `:q` — never string concat |
| Large page size (DoS via huge result) | DoS | `@PageableDefault(max=100)` or clamp `size` in service layer |
| CSRF on admin write endpoints | Tampering | Stateless JWT-based; no session cookies; CSRF not applicable |

---

## Open Questions

1. **`low_stock_threshold` storage: per-product column vs global config property**
   - What we know: REQUIREMENTS.md PROD-06 says "Son N ürün!" but doesn't specify where the threshold lives.
   - What's unclear: Should it be per-product (5 for phones, 20 for consumables) or global?
   - **Recommendation:** Per-product column `low_stock_threshold INT DEFAULT 5` on the `stock` table. More flexible and SOLID-friendly. Admin can set it via `POST /inventory/{productId}/restock` (already in the api-contracts §2.6). Default 5 covers all seed data.

2. **Port assignments for product-service (8082) and inventory-service (8083)**
   - What we know: Identity = 8081; the 808x convention is established in CONTEXT.md D-21.
   - What's unclear: Exact port numbers are not locked in any document.
   - **Recommendation:** product-service = 8082, inventory-service = 8083 (sequential ladder). Planner should confirm and lock in product-service.yml + inventory-service.yml.

3. **`image_urls` storage: Postgres TEXT[] vs JSON column**
   - What we know: The products table needs a gallery array (PROD-02).
   - What's unclear: Hibernate's handling of `TEXT[]` requires `@Type(PostgreSQLArrayType.class)` (Hypersistence Utils) or a simple `@Convert` approach. JSON column is simpler but loses Postgres array operators.
   - **Recommendation:** Use `TEXT[]` with Spring Boot's `@JdbcTypeCode(SqlTypes.ARRAY)` annotation (Hibernate 6.x native support, no extra library needed). [ASSUMED — Hibernate 6.6 array support needs verification at impl time]

4. **Seller info: single `seller_name` TEXT column vs dedicated seller entity**
   - What we know: PROD-02 requires "seller info"; PROJECT.md Out-of-Scope explicitly bans multi-vendor seller dashboards.
   - What's unclear: The simplest approach is a denormalized `seller_name TEXT DEFAULT 'n11 Pazaryeri'` on the product row.
   - **Recommendation:** Single `seller_name` text column, hardcoded to `'n11 Pazaryeri'` in seed data. All products belong to the same fictional seller. No foreign key, no seller table.

5. **KDV rate: fixed 20% vs configurable**
   - What we know: CLAUDE.md says "KDV (Turkish VAT) is 20% at v1"; LOC-01 says `price_gross` + `kdv_rate` stored.
   - What's unclear: Whether it should be a config value or per-product DB column.
   - **Recommendation:** Store `kdv_rate NUMERIC(5,2) DEFAULT 20.00` per product row (forward-compatible if different categories ever get different rates). Seed all rows with 20.00.

6. **Phase 4 search-service skeleton scope**
   - What we know: saga-contracts.md §2 says `products.tx` added in Phase 4 "alongside the search-service skeleton." ROADMAP.md says search-service's pgvector work is in Phase 8.
   - What's unclear: What exactly constitutes the "skeleton" for Phase 4 — just the `products.tx` exchange declaration in product-service, or also a `search-service` Gradle module?
   - **Recommendation:** Phase 4 scope = declare `products.tx` exchange in product-service's `ProductRabbitConfig`. Creating a full `search-service` module in Phase 4 would add scope; the gateway route for `/api/v1/search/**` can remain a 503 until Phase 8. Planner decision.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| PostgreSQL 16 + pgvector | Flyway migrations, ILIKE GIN | ✓ (docker-compose infra-only) | pgvector/pgvector:pg16 | — |
| RabbitMQ | inventory-service saga consumer | ✓ (docker-compose infra-only) | rabbitmq:4.3-management | — |
| Eureka Server | Service discovery | ✓ (Phase 1, port 8761) | Spring Boot 3.5.14 | — |
| Config Server | Per-service YAML | ✓ (Phase 1, port 8888) | Spring Boot 3.5.14 | — |
| Identity Service | Gateway JWT auth (for admin writes) | ✓ (Phase 3, port 8081) | Spring Boot 3.5.14 | — |
| `pg_trgm` extension | ILIKE GIN index | ASSUMED available (init.sh runs as superuser) | bundled with Postgres 16 | Use JPQL LOWER LIKE without GIN (slower) |

[VERIFIED: docker-compose.yml shows postgres + rabbitmq services; Phase 1/3 services confirmed healthy]

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | product-service port = 8082, inventory-service port = 8083 | Open Questions #2, Code Examples | Port conflict; needs explicit lock in CONTEXT.md |
| A2 | Hibernate 6.6 supports `TEXT[]` via `@JdbcTypeCode(SqlTypes.ARRAY)` without Hypersistence Utils | Open Questions #3 | Need to add Hypersistence Utils dependency (~minor) |
| A3 | `pg_trgm` extension available in `pgvector/pgvector:pg16` container (enabled by init.sh or bundled) | Pattern 2, Common Pitfalls | GIN index DDL fails at Flyway migration time; workaround: add `CREATE EXTENSION IF NOT EXISTS pg_trgm` to init.sh |
| A4 | Turkish dotted-i ILIKE works adequately with `lower(name_tr) ILIKE lower(q)` for demo | Pattern 2 | Correct Turkish case-fold for `İ → i` requires `LOWER(..., 'tr-x-icu')`; for a demo where seed data is lowercase, this is acceptable |
| A5 | `search-service` skeleton is NOT in Phase 4 scope (only `products.tx` exchange declaration) | Open Questions #6 | If roadmap expects a full skeleton, Phase 4 is under-scoped |

---

## Sources

### Primary (HIGH confidence)
- [Context7 /spring-projects/spring-data-jpa] — Pageable, Page, JpaRepository usage patterns
- [postgresql.org/docs/current/pgtrgm.html] — GIN index with `gin_trgm_ops`, ILIKE support confirmed
- `.planning/phases/01-foundations-day-1-contracts/01-03-PLAN.md` — init.sh creates `product` + `inventory` schemas + users + REVOKE matrix
- `.planning/phases/01-foundations-day-1-contracts/01-07-PLAN.md` — service-template skeleton, Flyway non-owner config, processed_events pattern
- `.planning/phases/03-identity-gateway-auth/03-PATTERNS.md` — Phase 3 clone pattern, config-server YAML shape, docker-compose block pattern
- `.planning/phases/03-identity-gateway-auth/03-CONTEXT.md` — Port 8081 for identity (808x convention), gateway aggregator pattern
- `.planning/api-contracts.md` — product-service + inventory-service endpoint table, gateway routing table, admin ROLE_ADMIN, public allowlist
- `.planning/saga-contracts.md` — `inventory.q.order-created`, `inventory.tx`, `products.tx`, outbox schema, processed_events schema
- `.planning/research/ARCHITECTURE.md` §2.5 + §2.6 — per-service contracts for product-service and inventory-service

### Secondary (MEDIUM confidence)
- [springdoc.org] — `@ParameterObject` with Pageable, Spring Cloud Gateway aggregator via `springdoc.swagger-ui.urls`
- [postgresql.org/docs/current/pgtrgm.html] — GIN trigram index confirmed; Turkish i/İ collation caveat is ASSUMED (not explicitly in docs)
- [github.com/springdoc/springdoc-openapi issues #717] — Springdoc priority vs Gateway routing for `/v3/api-docs`; StripPrefix filter is the established workaround (proven in Phase 3 identity-service pattern)

### Tertiary (LOW confidence)
- WebSearch results on Turkish Postgres collation — Turkish dotted-i ILIKE coverage is LOW; ICU `tr-x-icu` collation is theoretically correct but unverified for this project's Postgres 16 container

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — mirrors Phase 3 established patterns; no new libraries
- Architecture: HIGH — patterns locked in api-contracts.md + saga-contracts.md
- Flyway schema config: HIGH — verbatim from Phase 3 PATTERNS.md
- Pagination: HIGH — Spring Data Pageable standard
- ILIKE + GIN index: HIGH (index strategy) / MEDIUM (Turkish i/İ edge case)
- Inventory saga consumer: HIGH — pattern established in identity-service outbox + saga-contracts.md
- Pitfalls: HIGH — drawn from verified PITFALLS.md items #11, #13, #24

**Research date:** 2026-04-29
**Valid until:** 2026-05-20 (stable stack; Flyway/Spring Boot versions fixed by BOM)
