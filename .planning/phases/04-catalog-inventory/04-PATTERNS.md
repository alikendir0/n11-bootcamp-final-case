# Phase 4: Catalog + Inventory - Pattern Map

**Mapped:** 2026-04-29
**Files analyzed:** 28 new/modified files across product-service, inventory-service, api-gateway config, docker-compose, settings.gradle.kts
**Analogs found:** 28 / 28 (all files have a direct analog in identity-service or service-template/skeleton)

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `product-service/build.gradle.kts` | config | build | `identity-service/build.gradle.kts` | exact |
| `product-service/.../ProductServiceApplication.java` | config | — | `identity-service/.../IdentityServiceApplication.java` | exact |
| `product-service/.../config/ProductRabbitConfig.java` | config | event-driven | `identity-service/.../outbox/IdentityRabbitConfig.java` | exact |
| `product-service/.../product/ProductController.java` | controller | CRUD + request-response | `identity-service/.../address/AddressController.java` | role-match |
| `product-service/.../category/CategoryController.java` | controller | CRUD | `identity-service/.../address/AddressController.java` | role-match |
| `product-service/.../product/dto/CreateProductRequest.java` | model | request-response | `identity-service/.../address/dto/CreateAddressRequest.java` | exact |
| `product-service/.../product/dto/ProductSummaryDto.java` | model | request-response | `identity-service/.../address/dto/AddressResponse.java` | exact |
| `product-service/.../product/dto/ProductDetailDto.java` | model | request-response | `identity-service/.../address/dto/AddressResponse.java` | exact |
| `product-service/.../product/Product.java` | model | CRUD | `identity-service/.../user/User.java` | exact |
| `product-service/.../category/Category.java` | model | CRUD | `identity-service/.../user/Role.java` | role-match |
| `product-service/.../product/ProductRepository.java` | service | CRUD | `identity-service/.../user/UserRepository.java` | exact |
| `product-service/.../product/ProductService.java` | service | CRUD | `identity-service/.../address/AddressService.java` | exact |
| `product-service/.../resources/db/migration/V1__init_processed_events.sql` | migration | batch | `identity-service/.../db/migration/V1__init_processed_events.sql` | exact |
| `product-service/.../resources/db/migration/V2__init_product_catalog.sql` | migration | batch | `identity-service/.../db/migration/V2__init_users_addresses.sql` | role-match |
| `product-service/.../resources/db/migration/V3__seed_products.sql` | migration | batch | `identity-service/.../db/migration/V3__seed_admin.sql` | role-match |
| `product-service/src/test/.../ProductRepositoryTest.java` | test | CRUD | `identity-service/src/test/.../UserEntityTest.java` | exact |
| `product-service/src/test/.../ProductSearchIntegrationTest.java` | test | CRUD | `identity-service/src/test/.../OutboxIntegrationTest.java` | role-match |
| `config-server/.../config/product-service.yml` | config | — | `config-server/.../config/identity-service.yml` | exact |
| `inventory-service/build.gradle.kts` | config | build | `identity-service/build.gradle.kts` | exact |
| `inventory-service/.../InventoryServiceApplication.java` | config | — | `identity-service/.../IdentityServiceApplication.java` | exact |
| `inventory-service/.../messaging/InventoryRabbitConfig.java` | config | event-driven | `identity-service/.../outbox/IdentityRabbitConfig.java` | exact |
| `inventory-service/.../stock/Stock.java` | model | CRUD | `identity-service/.../user/User.java` + `@Version` field | role-match |
| `inventory-service/.../reservation/StockReservation.java` | model | CRUD | `identity-service/.../address/Address.java` | role-match |
| `inventory-service/.../outbox/OutboxEvent.java` | model | event-driven | `identity-service/.../outbox/OutboxEvent.java` | exact |
| `inventory-service/.../outbox/OutboxPoller.java` | service | event-driven | `identity-service/.../outbox/OutboxPoller.java` | exact |
| `inventory-service/.../messaging/OrderCreatedConsumer.java` | service | event-driven | `identity-service/.../outbox/OutboxPoller.java` (inverted as consumer) | partial |
| `config-server/.../config/inventory-service.yml` | config | — | `config-server/.../config/identity-service.yml` | exact |
| `config-server/.../config/api-gateway.yml` (modified) | config | — | existing `api-gateway.yml` (lines 50-56, 113-116) | exact |
| `docker-compose.yml` (modified) | config | — | `docker-compose.yml` identity-service block (lines 207-236) | exact |
| `settings.gradle.kts` (modified) | config | — | existing `include("identity-service")` line | exact |

---

## Pattern Assignments

### `product-service/build.gradle.kts`

**Analog:** `identity-service/build.gradle.kts`

**Full pattern** (lines 1-78):
```kotlin
plugins {
    id("org.springframework.boot")
    id("com.google.cloud.tools.jib")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")
    implementation("org.springframework.cloud:spring-cloud-starter-config")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.17")
    implementation("org.flywaydb:flyway-core:12.5.0")
    implementation("org.flywaydb:flyway-database-postgresql:12.5.0")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("net.logstash.logback:logstash-logback-encoder:8.0")
    implementation(project(":common-error"))
    implementation(project(":common-logging"))
    implementation(project(":common-events"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:rabbitmq")
    testImplementation("org.testcontainers:junit-jupiter")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveBaseName.set("product-service")     // CHANGE: identity-service → product-service
}

jib {
    from { image = "eclipse-temurin:21-jre" }
    to { image = "n11/product-service:dev" }  // CHANGE: identity-service → product-service
    container {
        ports = listOf("8082")                  // CHANGE: 8081 → 8082
        jvmFlags = listOf("-XX:+UseG1GC", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0")
    }
}
```

**Changes from analog:** remove `spring-boot-starter-oauth2-resource-server`, `spring-boot-starter-security` (product-service has no JWT logic — header inspection only), `networknt.json.schema` test dep (not needed unless outbox integration test added), `h2` (TC Postgres preferred for ILIKE/GIN index tests). Rename `identity-service` → `product-service`, port `8081` → `8082`.

---

### `product-service/src/main/java/com/n11/product/ProductServiceApplication.java`

**Analog:** `identity-service/src/main/java/com/n11/identity/IdentityServiceApplication.java`

**Full pattern** (lines 1-16):
```java
package com.n11.product;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.n11")
@EnableDiscoveryClient
@EnableScheduling          // needed for OutboxPoller @Scheduled
public class ProductServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args);
    }
}
```

**Changes from analog:** package `com.n11.identity` → `com.n11.product`, class name `IdentityServiceApplication` → `ProductServiceApplication`. Keep `@EnableScheduling` (OutboxPoller will be added if product.* event publishing is needed in Phase 4; if only exchange declaration, poller is not needed yet — planner decision).

---

### `product-service/src/main/java/com/n11/product/config/ProductRabbitConfig.java`

**Analog:** `identity-service/src/main/java/com/n11/identity/outbox/IdentityRabbitConfig.java`

**Full pattern** (lines 1-28):
```java
package com.n11.identity.outbox;

import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IdentityRabbitConfig {

    public static final String EXCHANGE_IDENTITY_TX = "identity.tx";

    @Bean
    public TopicExchange identityExchange() {
        return ExchangeBuilder
                .topicExchange(EXCHANGE_IDENTITY_TX)
                .durable(true)
                .build();
    }
}
```

**Changes from analog:** package → `com.n11.product.config`, class name → `ProductRabbitConfig`, constant → `EXCHANGE_PRODUCTS_TX = "products.tx"`, bean method → `productsExchange()`, exchange name → `"products.tx"`. No queue/binding declarations — Phase 4 only declares the exchange; search-service (Phase 8) binds to it.

---

### `product-service/src/main/java/com/n11/product/product/ProductController.java`

**Analog:** `identity-service/src/main/java/com/n11/identity/address/AddressController.java` + `identity-service/.../auth/AuthController.java`

**Header-read admin guard pattern** (from `AuthController.java` lines 52-65):
```java
private static final String HEADER_USER_ID    = "X-User-Id";
private static final String HEADER_USER_ROLES = "X-User-Roles";

// Admin guard — reads gateway-injected header (D-15; no Spring Security in product-service)
private void requireAdmin(HttpServletRequest request) {
    String roles = request.getHeader(HEADER_USER_ROLES);
    if (roles == null || !roles.contains("ROLE_ADMIN")) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Yetkisiz erişim");
    }
}
```

**Core controller pattern** (from `AddressController.java` lines 1-60):
```java
package com.n11.product.product;

import com.n11.product.product.dto.CreateProductRequest;
import com.n11.product.product.dto.ProductDetailDto;
import com.n11.product.product.dto.ProductSummaryDto;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/products")
public class ProductController {

    private static final String HEADER_USER_ROLES = "X-User-Roles";
    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @Operation(summary = "Paginated product listing",
               description = "Page is 0-indexed. Page 0 = first page. Default size = 20. " +
                             "Sort: sort=priceGross,asc | sort=createdAt,desc")
    @GetMapping
    public Page<ProductSummaryDto> list(
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID categoryId) {
        return productService.search(q, categoryId, pageable);
    }

    @GetMapping("/{id}")
    public ProductDetailDto get(@PathVariable UUID id) {
        return productService.getDetail(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductDetailDto create(HttpServletRequest request,
                                   @Valid @RequestBody CreateProductRequest body) {
        requireAdmin(request);
        return productService.create(body);
    }

    @PutMapping("/{id}")
    public ProductDetailDto update(HttpServletRequest request,
                                   @PathVariable UUID id,
                                   @Valid @RequestBody CreateProductRequest body) {
        requireAdmin(request);
        return productService.update(id, body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(HttpServletRequest request, @PathVariable UUID id) {
        requireAdmin(request);
        productService.delete(id);
    }

    private void requireAdmin(HttpServletRequest request) {
        String roles = request.getHeader(HEADER_USER_ROLES);
        if (roles == null || !roles.contains("ROLE_ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Yetkisiz erişim");
        }
    }
}
```

---

### `product-service/src/main/java/com/n11/product/category/CategoryController.java`

**Analog:** `identity-service/src/main/java/com/n11/identity/address/AddressController.java`

**Core pattern** (AddressController lines 36-38, adapted):
```java
@RestController
@RequestMapping("/categories")
public class CategoryController {

    private final CategoryRepository categoryRepository;

    public CategoryController(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @GetMapping
    public List<Category> list() {
        return categoryRepository.findAllByOrderBySortOrderAsc();
    }
}
```

**Changes from analog:** No `HttpServletRequest` (categories are public, no auth required). Direct repository call is fine (no domain logic needed for simple read).

---

### `product-service/src/main/java/com/n11/product/product/dto/CreateProductRequest.java`

**Analog:** `identity-service/src/main/java/com/n11/identity/address/dto/CreateAddressRequest.java`

**Validation record pattern** (lines 1-43):
```java
package com.n11.product.product.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CreateProductRequest(
        @NotBlank(message = "Ürün adı zorunludur")
        @Size(max = 255, message = "Ürün adı en fazla 255 karakter olabilir")
        String nameTr,

        String descriptionTr,

        @NotNull(message = "Fiyat zorunludur")
        @DecimalMin(value = "0.01", message = "Fiyat 0'dan büyük olmalıdır")
        BigDecimal priceGross,

        @DecimalMin(value = "0.00")
        @DecimalMax(value = "100.00")
        BigDecimal kdvRate,

        UUID categoryId,

        List<String> imageUrls,

        @NotBlank(message = "SKU zorunludur")
        String sku,

        String sellerName
) { }
```

**Changes from analog:** Record fields match `products` table columns. Turkish validation messages. Use `BigDecimal` for price/kdv (not `String`).

---

### `product-service/src/main/java/com/n11/product/product/dto/ProductSummaryDto.java` and `ProductDetailDto.java`

**Analog:** `identity-service/src/main/java/com/n11/identity/address/dto/AddressResponse.java`

**Response record pattern** (lines 1-18):
```java
// ProductSummaryDto — listing card fields
public record ProductSummaryDto(
        UUID id,
        String nameTr,
        BigDecimal priceGross,
        BigDecimal kdvRate,
        String slug,
        String categoryName,
        String firstImageUrl  // first element of image_urls array for card thumbnail
) { }

// ProductDetailDto — PDP fields (extends summary with gallery + description)
public record ProductDetailDto(
        UUID id,
        String nameTr,
        String descriptionTr,
        BigDecimal priceGross,
        BigDecimal kdvRate,
        String slug,
        String sku,
        String sellerName,
        List<String> imageUrls,
        UUID categoryId,
        String categoryName
) { }
```

**Changes from analog:** Use `BigDecimal` for prices (not `String`), include `List<String> imageUrls`.

---

### `product-service/src/main/java/com/n11/product/product/Product.java`

**Analog:** `identity-service/src/main/java/com/n11/identity/user/User.java`

**JPA entity pattern** (lines 1-57):
```java
package com.n11.identity.user;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "email", nullable = false, unique = true)
    private String email;
    // ... more columns ...

    protected User() { /* JPA */ }

    public User(UUID id, ...) { /* constructor */ }

    // accessor methods only — no setters except domain methods
}
```

**Product.java adaptation:**
```java
@Entity
@Table(name = "products")
public class Product {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "sku", nullable = false, unique = true)
    private String sku;

    @Column(name = "name_tr", nullable = false)
    private String nameTr;

    @Column(name = "description_tr")
    private String descriptionTr;

    @Column(name = "price_gross", nullable = false)
    private BigDecimal priceGross;

    @Column(name = "kdv_rate", nullable = false)
    private BigDecimal kdvRate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    // image_urls TEXT[] — Hibernate 6.6 native array support:
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "image_urls", columnDefinition = "TEXT[]")
    private String[] imageUrls;

    @Column(name = "seller_name", nullable = false)
    private String sellerName;

    @Column(name = "slug", nullable = false, unique = true)
    private String slug;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Product() { /* JPA */ }
    // ... canonical constructor + accessors
}
```

**Changes from analog:** Different columns, `@ManyToOne` to Category instead of `@ManyToMany` to Role, `@JdbcTypeCode(SqlTypes.ARRAY)` for `TEXT[]`, `BigDecimal` prices.

---

### `product-service/src/main/java/com/n11/product/category/Category.java`

**Analog:** `identity-service/src/main/java/com/n11/identity/user/Role.java`

**Simple entity pattern** (lines 1-30):
```java
package com.n11.identity.user;

import jakarta.persistence.*;

@Entity
@Table(name = "roles")
public class Role {

    @Id
    @Column(name = "id")
    private Integer id;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    protected Role() { /* JPA */ }
    public Integer getId()  { return id; }
    public String getName() { return name; }
}
```

**Category.java adaptation:**
```java
@Entity
@Table(name = "categories")
public class Category {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "slug", nullable = false, unique = true)
    private String slug;

    @Column(name = "name_tr", nullable = false)
    private String nameTr;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;       // self-referential FK for sub-categories

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected Category() { /* JPA */ }
    // ... accessors
}
```

**Changes from analog:** UUID PK (not Integer), self-referential `@ManyToOne parent`, added `slug`, `nameTr`, `sortOrder`.

---

### `product-service/src/main/java/com/n11/product/product/ProductRepository.java`

**Analog:** `identity-service/src/main/java/com/n11/identity/user/UserRepository.java`

**Repository pattern** (lines 1-12) + native ILIKE query from RESEARCH.md:
```java
package com.n11.identity.user;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
```

**ProductRepository.java adaptation:**
```java
public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findBySlug(String slug);
    boolean existsBySku(String sku);

    // PROD-04: native ILIKE query — uses GIN trigram index on lower(name_tr)
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
}
```

---

### `product-service/src/main/java/com/n11/product/product/ProductService.java`

**Analog:** `identity-service/src/main/java/com/n11/identity/address/AddressService.java`

**Service pattern** (lines 1-72):
```java
@Service
public class AddressService {

    private final AddressRepository addressRepository;

    public AddressService(AddressRepository addressRepository) {
        this.addressRepository = addressRepository;
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> listForUser(UUID userId) {
        return addressRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(AddressService::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public AddressResponse create(UUID userId, CreateAddressRequest req) {
        // ... entity build, save, return DTO
    }

    private static AddressResponse toResponse(Address a) {
        return new AddressResponse(...);
    }
}
```

**ProductService.java adaptation:**
```java
@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public ProductService(ProductRepository productRepository,
                          CategoryRepository categoryRepository) { ... }

    @Transactional(readOnly = true)
    public Page<ProductSummaryDto> search(String q, UUID categoryId, Pageable pageable) {
        Page<Product> page = productRepository.search(
            q,
            categoryId != null ? categoryId.toString() : null,
            pageable);
        return page.map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public ProductDetailDto getDetail(UUID id) {
        Product p = productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ürün bulunamadı"));
        return toDetail(p);
    }

    @Transactional
    public ProductDetailDto create(CreateProductRequest req) {
        if (productRepository.existsBySku(req.sku())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "SKU zaten mevcut");
        }
        // build entity, generate slug, save, return DTO
    }

    private ProductSummaryDto toSummary(Product p) { ... }
    private ProductDetailDto  toDetail(Product p)  { ... }
}
```

---

### `product-service/src/main/resources/db/migration/V1__init_processed_events.sql`

**Analog:** `identity-service/src/main/resources/db/migration/V1__init_processed_events.sql`

**Copy verbatim** (lines 1-25) — identical content. The file creates the `processed_events` table in the current Flyway schema, which is `product` for this service. No changes needed.

---

### `product-service/src/main/resources/db/migration/V2__init_product_catalog.sql`

**Analog:** `identity-service/src/main/resources/db/migration/V2__init_users_addresses.sql` (DDL shape only)

**DDL pattern** (from V2 lines 1-46, adapted with RESEARCH.md Pattern 2):
```sql
-- V2__init_product_catalog.sql
-- Runs inside `product` schema (Flyway default-schema=product, create-schemas=false)

CREATE EXTENSION IF NOT EXISTS pg_trgm;

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
CREATE INDEX idx_products_price        ON products (price_gross);
CREATE INDEX idx_products_created_at   ON products (created_at DESC);
CREATE INDEX idx_products_category_id  ON products (category_id);
```

---

### `product-service/src/main/resources/db/migration/V3__seed_products.sql`

**Analog:** `identity-service/src/main/resources/db/migration/V3__seed_admin.sql`

**Seed pattern** (lines 1-15):
```sql
-- V3__seed_admin.sql — uses ON CONFLICT DO NOTHING for re-run safety
INSERT INTO users (id, email, password_hash, full_name, created_at)
VALUES (gen_random_uuid(), '${adminSeedEmail}', '${adminSeedPasswordHash}', 'Admin', now())
ON CONFLICT (email) DO NOTHING;
```

**V3__seed_products.sql adaptation:**
```sql
-- V3__seed_products.sql
-- Minimum 50 Turkish products across 8 categories (PROD-09).
-- Uses ON CONFLICT DO NOTHING so re-runs and test teardown are safe.

-- 8 top-level categories (PROD-03)
INSERT INTO categories (id, slug, name_tr, sort_order) VALUES
    (gen_random_uuid(), 'elektronik',         'Elektronik',           1),
    (gen_random_uuid(), 'moda',               'Moda',                 2),
    (gen_random_uuid(), 'ev-yasam',           'Ev & Yaşam',           3),
    (gen_random_uuid(), 'anne-bebek',         'Anne & Bebek',         4),
    (gen_random_uuid(), 'kozmetik',           'Kozmetik',             5),
    (gen_random_uuid(), 'spor-outdoor',       'Spor & Outdoor',       6),
    (gen_random_uuid(), 'supermarket',        'Süpermarket',          7),
    (gen_random_uuid(), 'kitap-muzik-film',   'Kitap-Müzik-Film',     8)
ON CONFLICT (slug) DO NOTHING;

-- Products: INSERT ... SELECT from categories so foreign keys are correct
-- Use CTEs to resolve category UUIDs by slug before inserting products.
-- Pattern: INSERT INTO products (...) SELECT ... FROM categories WHERE slug = '...'
-- [50+ product rows follow — planner writes these]
```

---

### `product-service/src/test/java/com/n11/product/ProductRepositoryTest.java`

**Analog:** `identity-service/src/test/java/com/n11/identity/user/UserEntityTest.java`

**Slice test pattern** (lines 1-40):
```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class UserEntityTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("identity_test")
            .withUsername("identity_user")
            .withPassword("identity_test_password");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    }
    // tests ...
}
```

**ProductRepositoryTest.java adaptation:** change container username to `product_user`, DB name to `product_test`, add `spring.flyway.placeholders.schema=public` override, change `@Autowired` to inject `ProductRepository` and `CategoryRepository`. Tests cover ILIKE search, pagination, sort by price.

---

### `product-service/src/test/java/com/n11/product/ProductSearchIntegrationTest.java`

**Analog:** `identity-service/src/test/java/com/n11/identity/outbox/OutboxIntegrationTest.java`

**Full SpringBootTest pattern** (lines 57-83):
```java
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class OutboxIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(
                    DockerImageName.parse("pgvector/pgvector:pg16")
                            .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("n11")
                    .withUsername("identity_user")
                    .withPassword("test-password");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }

    @Autowired UserService userService;
    // ... @Test methods
}
```

**ProductSearchIntegrationTest.java adaptation:** use `product_user` / `product_test`, no RabbitMQ container (product-service has no consumer). Tests verify: ILIKE returns correct products, GIN index plan used (via `EXPLAIN ANALYZE`), pagination returns correct page/size.

---

### `config-server/src/main/resources/config/product-service.yml`

**Analog:** `config-server/src/main/resources/config/identity-service.yml`

**Full config pattern** (lines 1-73):
```yaml
server:
  port: 8081    # D-21 / 808x convention

spring:
  datasource:
    url: jdbc:postgresql://postgres:5432/n11
    username: identity_user
    password: ${IDENTITY_DB_PASSWORD}
    hikari:
      maximum-pool-size: 10
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
      flyway.schema: identity
      # no admin seed placeholders for product-service
  jpa:
    open-in-view: false
    properties:
      hibernate:
        default_schema: identity
  rabbitmq:
    host: rabbitmq
    port: 5672
    username: ${RABBITMQ_DEFAULT_USER}
    password: ${RABBITMQ_DEFAULT_PASS}
```

**product-service.yml changes:**
- `port: 8082`
- `username: product_user`
- `password: ${PRODUCT_DB_PASSWORD}`
- `schemas: product`, `default-schema: product`, `flyway.schema: product`
- `hibernate.default_schema: product`
- Remove `jwt:` block (no JWT logic in product-service)
- Remove `adminSeedEmail` / `adminSeedPasswordHash` placeholders
- Keep `springdoc:`, `management:`, `rabbitmq:`, `eureka:` blocks verbatim

---

### `inventory-service/build.gradle.kts`

**Analog:** `identity-service/build.gradle.kts`

**Pattern:** identical to product-service `build.gradle.kts` (section above) with these differences:
- `archiveBaseName.set("inventory-service")`
- `to { image = "n11/inventory-service:dev" }`
- `ports = listOf("8083")`
- Keep `spring-boot-starter-amqp` (inventory-service has the `@RabbitListener` consumer)

---

### `inventory-service/src/main/java/com/n11/inventory/InventoryServiceApplication.java`

**Analog:** `identity-service/src/main/java/com/n11/identity/IdentityServiceApplication.java`

**Pattern:** identical to ProductServiceApplication above with:
- Package `com.n11.inventory`
- Class `InventoryServiceApplication`
- Keep `@EnableScheduling` (OutboxPoller needed — inventory publishes `stock.reserved`)

---

### `inventory-service/src/main/java/com/n11/inventory/messaging/InventoryRabbitConfig.java`

**Analog:** `identity-service/src/main/java/com/n11/identity/outbox/IdentityRabbitConfig.java`

**Exchange + queue declaration pattern** (lines 1-28, extended):
```java
// IdentityRabbitConfig declares only a TopicExchange.
// InventoryRabbitConfig must also declare queues + bindings for the consumer.

@Configuration
public class InventoryRabbitConfig {

    public static final String EXCHANGE_INVENTORY_TX  = "inventory.tx";
    public static final String EXCHANGE_ORDERS_TX     = "orders.tx";
    public static final String QUEUE_INVENTORY_ORDERS = "inventory.q.order-created";
    public static final String DLQ_INVENTORY_ORDERS   = "inventory.q.order-created.dlq";

    @Bean
    public TopicExchange inventoryExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE_INVENTORY_TX).durable(true).build();
    }

    // Consumer queue — binds to orders.tx with routing key "order.created"
    @Bean
    public Queue orderCreatedQueue() {
        return QueueBuilder.durable(QUEUE_INVENTORY_ORDERS)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", DLQ_INVENTORY_ORDERS)
                .build();
    }

    @Bean
    public Queue orderCreatedDlq() {
        return QueueBuilder.durable(DLQ_INVENTORY_ORDERS).build();
    }

    @Bean
    public Binding orderCreatedBinding(Queue orderCreatedQueue, TopicExchange ordersExchange) {
        return BindingBuilder.bind(orderCreatedQueue)
                .to(ordersExchange)
                .with("order.created");
    }

    // ordersExchange — declared by order-service; reference bean here for binding
    @Bean
    public TopicExchange ordersExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE_ORDERS_TX).durable(true).build();
    }
}
```

**NOTE:** This is an extension of the IdentityRabbitConfig analog — the pattern adds `QueueBuilder` and `BindingBuilder` on top of the exchange-only pattern. The analog shows the exchange bean shape; the queue+binding is new but follows the same AMQP builder style.

---

### `inventory-service/src/main/java/com/n11/inventory/stock/Stock.java`

**Analog:** `identity-service/src/main/java/com/n11/identity/user/User.java` + `@Version` addition

**Pattern** (User.java entity structure, lines 1-57, adapted):
```java
@Entity
@Table(name = "stock")
public class Stock {

    @Id
    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(name = "available_qty", nullable = false)
    private int availableQty;

    @Column(name = "reserved_qty", nullable = false)
    private int reservedQty;

    @Column(name = "low_stock_threshold", nullable = false)
    private int lowStockThreshold = 5;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;        // Hibernate adds WHERE version=? to UPDATE — prevents lost updates

    protected Stock() { /* JPA */ }

    public Stock(UUID productId, int availableQty) {
        this.productId = productId;
        this.availableQty = availableQty;
        this.reservedQty = 0;
        this.lowStockThreshold = 5;
    }

    // accessors + domain method:
    public int getEffectiveAvailable() {
        return availableQty - reservedQty;
    }
    // ... getters, setters for reservedQty
}
```

**Changes from analog:** single-table entity (no joins), `@Version Long version` field, UUID PK is the product ID (not a surrogate UUID), domain logic method `getEffectiveAvailable()`.

---

### `inventory-service/src/main/java/com/n11/inventory/reservation/StockReservation.java`

**Analog:** `identity-service/src/main/java/com/n11/identity/address/Address.java`

**FK entity pattern** — Address has `user_id UUID NOT NULL REFERENCES users(id)`. StockReservation similarly has `product_id UUID NOT NULL REFERENCES stock(product_id)`:
```java
@Entity
@Table(name = "stock_reservations")
public class StockReservation {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;      // FK to stock.product_id (no @ManyToOne — cross-table joins avoided)

    @Column(name = "reserved_qty", nullable = false)
    private int reservedQty;

    @Column(name = "status", nullable = false)
    private String status;       // "RESERVED" | "RELEASED" | "COMMITTED"

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    protected StockReservation() { /* JPA */ }
    // ... constructor + accessors
}
```

---

### `inventory-service/src/main/java/com/n11/inventory/outbox/OutboxEvent.java`

**Analog:** `identity-service/src/main/java/com/n11/identity/outbox/OutboxEvent.java`

**Copy verbatim** (lines 1-63) — change only package from `com.n11.identity.outbox` → `com.n11.inventory.outbox`. The `@Table(name = "outbox")` stays the same (Flyway schema isolation ensures it lands in the `inventory` schema).

---

### `inventory-service/src/main/java/com/n11/inventory/outbox/OutboxPoller.java`

**Analog:** `identity-service/src/main/java/com/n11/identity/outbox/OutboxPoller.java`

**Copy verbatim** (lines 1-62) — change only:
- Package: `com.n11.inventory.outbox`
- Exchange name derivation: `event.getAggregate() + ".tx"` produces `"inventory.tx"` (same formula, different aggregate value stored in the outbox row)
- Native query schema reference: `inventory.outbox` instead of `identity.outbox`

**Drain query in OutboxRepository:**
```java
@Query(
    value = "SELECT * FROM inventory.outbox WHERE sent_at IS NULL ORDER BY occurred_at LIMIT :batchSize FOR UPDATE SKIP LOCKED",
    nativeQuery = true
)
List<OutboxEvent> findUnsentBatch(@Param("batchSize") int batchSize);
```

---

### `inventory-service/src/main/java/com/n11/inventory/messaging/OrderCreatedConsumer.java`

**Analog:** `identity-service/src/main/java/com/n11/identity/outbox/OutboxPoller.java` (inverted: producer → consumer pattern)

This file has no direct existing `@RabbitListener` consumer analog in the codebase. Use the RESEARCH.md Pattern 4 as the primary code source, combined with OutboxPoller's `@Transactional` + `@Scheduled` structural pattern:

**Structural pattern** (from OutboxPoller.java lines 29-62):
```java
@Component
public class OutboxPoller {

    private final OutboxRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;

    public OutboxPoller(OutboxRepository outboxRepository, RabbitTemplate rabbitTemplate) { ... }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void poll() {
        // ... process + save in same transaction
    }
}
```

**OrderCreatedConsumer.java pattern** (from RESEARCH.md Pattern 4):
```java
@Component
public class OrderCreatedConsumer {

    private final ProcessedEventRepository processedEventsRepository;
    private final InventoryService inventoryService;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    // constructor injection

    @RabbitListener(queues = InventoryRabbitConfig.QUEUE_INVENTORY_ORDERS)
    @Transactional
    public void handleOrderCreated(Message message) {
        // 1. Deserialize envelope
        // 2. Idempotency check: processedEventsRepository.existsById(eventId) → return if true
        // 3. reserveStock for each item (catch InsufficientStockException)
        // 4. Save outbox row (stock.reserved / stock.reserve_failed) in SAME TX
        // 5. processedEventsRepository.save(new ProcessedEvent(eventId, ...))
    }
}
```

**Key delta from OutboxPoller:** `@RabbitListener` replaces `@Scheduled`; the method signature changes to `(Message message)`. The `@Transactional` + same-TX persistence pattern is identical.

---

### `config-server/src/main/resources/config/inventory-service.yml`

**Analog:** `config-server/src/main/resources/config/identity-service.yml`

**Changes from analog:**
- `port: 8083`
- `username: inventory_user`
- `password: ${INVENTORY_DB_PASSWORD}`
- `schemas: inventory`, `default-schema: inventory`, `flyway.schema: inventory`
- `hibernate.default_schema: inventory`
- Remove `jwt:` block
- Remove `adminSeedEmail` / `adminSeedPasswordHash` Flyway placeholders
- Add `flyway.schema: inventory` placeholder (same reason as identity: V1 SQL comment uses `${flyway.schema}`)

---

### `config-server/src/main/resources/config/api-gateway.yml` (modified)

**Analog:** existing `api-gateway.yml` routes block (lines 50-56) and urls block (lines 113-116)

**Existing route pattern** (lines 50-56 verbatim):
```yaml
          routes:
            - id: identity-service
              uri: lb://IDENTITY-SERVICE
              predicates:
                - Path=/api/v1/identity/**
              filters:
                - StripPrefix=3
```

**Routes to append** (identical shape, different values):
```yaml
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

**Existing Springdoc urls pattern** (lines 113-116):
```yaml
    urls:
      - name: identity-service
        url: /api/v1/identity/v3/api-docs
```

**Urls entries to append:**
```yaml
      - name: product-service
        url: /api/v1/products/v3/api-docs
      - name: inventory-service
        url: /api/v1/inventory/v3/api-docs
```

---

### `docker-compose.yml` (modified — add product-service and inventory-service)

**Analog:** identity-service block (lines 207-236)

**Full pattern** (lines 207-236):
```yaml
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
      start_period: 20s
    restart: unless-stopped
    networks:
      - n11-net
```

**product-service block changes:** image `n11/product-service:dev`, container `n11-product-service`, healthcheck port `8082`, start_period `25s` (Flyway + GIN index creation slower), no extra environment vars needed.

**inventory-service block changes:** image `n11/inventory-service:dev`, container `n11-inventory-service`, healthcheck port `8083`, start_period `25s`.

---

### `settings.gradle.kts` (modified)

**Analog:** existing `include("identity-service")` line in `settings.gradle.kts` (line 10)

**Change:** append `"product-service"` and `"inventory-service"` to the include block:
```kotlin
include(
    "eureka-server",
    "config-server",
    "api-gateway",
    "common-error",
    "common-logging",
    "common-events",
    "identity-service",
    "product-service",       // ADD
    "inventory-service",     // ADD
    "service-template",
    "infra-tests"
)
```

---

## Shared Patterns

### 1. Gateway-Injected Header Auth (Admin Gate)
**Source:** `identity-service/src/main/java/com/n11/identity/auth/AuthController.java` (lines 52-65)
**Apply to:** `ProductController.java` (POST/PUT/DELETE endpoints)
```java
private static final String HEADER_USER_ROLES = "X-User-Roles";

private void requireAdmin(HttpServletRequest request) {
    String roles = request.getHeader(HEADER_USER_ROLES);
    if (roles == null || !roles.contains("ROLE_ADMIN")) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Yetkisiz erişim");
    }
}
```

### 2. Flyway Per-Schema Non-Owner Config
**Source:** `config-server/src/main/resources/config/identity-service.yml` (lines 29-50)
**Apply to:** `product-service.yml`, `inventory-service.yml`
```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    schemas: ${schema}          # override in per-service YAML
    default-schema: ${schema}
    create-schemas: false       # CRITICAL — init.sh owns schema creation
    user: ${spring.datasource.username}
    password: ${spring.datasource.password}
    placeholders:
      schema: ${schema}
      flyway.schema: ${schema}  # satisfies V1 comment placeholder substitution
```

### 3. Transactional Outbox + Poller
**Source:** `identity-service/src/main/java/com/n11/identity/outbox/OutboxPoller.java` (lines 29-62)
**Apply to:** `inventory-service/.../outbox/OutboxPoller.java` (copy, change schema name in native query)
```java
@Scheduled(fixedDelay = 5000)
@Transactional
public void poll() {
    List<OutboxEvent> unsent = outboxRepository.findUnsentBatch(BATCH_SIZE);
    for (OutboxEvent event : unsent) {
        String exchange = event.getAggregate() + ".tx";  // "inventory.tx"
        rabbitTemplate.convertAndSend(exchange, event.getEventType(), event.getPayload());
        event.markSent(Instant.now());
        outboxRepository.save(event);
    }
}
```

### 4. Processed Events Idempotency Inbox
**Source:** `identity-service/src/main/resources/db/migration/V1__init_processed_events.sql` (lines 16-25)
**Apply to:** Both `product-service` and `inventory-service` V1 migrations — copy verbatim
```sql
CREATE TABLE processed_events (
    event_id      UUID         PRIMARY KEY,
    consumer      VARCHAR(64)  NOT NULL,
    event_type    VARCHAR(128) NOT NULL,
    processed_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
```

### 5. JPA Entity Structural Pattern
**Source:** `identity-service/src/main/java/com/n11/identity/user/User.java` (lines 1-57)
**Apply to:** All new entities (`Product`, `Category`, `Stock`, `StockReservation`)
- Protected no-arg JPA constructor
- Package-private or public canonical constructor with all required fields
- Accessor methods only (no setters except domain methods)
- `@Column` annotations explicit on every field
- `UUID` PKs with `updatable = false`
- `Instant` for timestamps

### 6. Testcontainers Slice Test Pattern
**Source:** `identity-service/src/test/java/com/n11/identity/user/UserEntityTest.java` (lines 33-57)
**Apply to:** `ProductRepositoryTest.java`, `StockStateTest.java`
```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class UserEntityTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("identity_test")
            .withUsername("identity_user")
            .withPassword("identity_test_password");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    }
}
```

### 7. SpringBootTest Full Integration Test Pattern
**Source:** `identity-service/src/test/java/com/n11/identity/outbox/OutboxIntegrationTest.java` (lines 57-83)
**Apply to:** `OrderCreatedConsumerIntegrationTest.java` (additionally needs RabbitMQ container)
```java
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class OutboxIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("n11").withUsername("identity_user").withPassword("test-password");

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBIT =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:4.0-management"));

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }
}
```

### 8. Test application-test.yml Pattern
**Source:** `identity-service/src/test/resources/application-test.yml` (lines 1-84)
**Apply to:** `product-service/src/test/resources/application-test.yml`, `inventory-service/src/test/resources/application-test.yml`

Critical overrides to copy:
```yaml
spring:
  cloud:
    config:
      enabled: false
  config:
    import: ""
  jpa:
    hibernate:
      ddl-auto: none
  flyway:
    enabled: true
    create-schemas: true     # Testcontainers: schema doesn't pre-exist
    schemas: product         # CHANGE per service
    default-schema: product  # CHANGE per service
    placeholders:
      schema: product
      flyway.schema: product
  rabbitmq:
    listener:
      simple:
        auto-startup: false  # disable consumers in unit tests
eureka:
  client:
    enabled: false
    register-with-eureka: false
    fetch-registry: false
```
Note: product-service's `application-test.yml` has no `jwt:` block (no JWT config in that service). inventory-service similarly has none.

---

## No Analog Found

All files have a match in identity-service or service-template. The one file requiring significant new logic (beyond pattern cloning) is:

| File | Role | Data Flow | Reason |
|---|---|---|---|
| `inventory-service/.../messaging/OrderCreatedConsumer.java` | service | event-driven | No `@RabbitListener` consumer exists in the codebase yet. Pattern is derived from RESEARCH.md §Pattern 4 + OutboxPoller structural pattern. The consumer body is net-new logic. |

---

## Metadata

**Analog search scope:** `identity-service/` (full), `service-template/skeleton/` (full), `config-server/src/main/resources/config/`, `docker-compose.yml`, `settings.gradle.kts`
**Files scanned:** 28 source files read directly
**Pattern extraction date:** 2026-04-29
