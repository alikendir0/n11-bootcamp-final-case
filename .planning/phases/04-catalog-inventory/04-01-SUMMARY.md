---
phase: 04-catalog-inventory
plan: "01"
subsystem: product-service
tags:
  - product-service
  - catalog
  - flyway
  - ilike-search
  - gin-index
  - testcontainers
  - spring-data-jpa
  - rabbitmq

dependency_graph:
  requires:
    - 01-01 (root build conventions)
    - 01-03 (Postgres init.sh — product schema + product_user)
    - 01-05 (config-server alive)
    - 03-01 (identity-service patterns cloned)
  provides:
    - product-service Gradle module (compilable, testable)
    - GET /products (Pageable, ILIKE, categoryId filter, priceGross/createdAt sort)
    - GET /products/{id} (PDP DTO)
    - GET /categories (8 top-level Turkish categories by sort_order)
    - POST/PUT/DELETE /products (ROLE_ADMIN gated via X-User-Roles)
    - products.tx topic exchange declared (durable, no consumers)
    - 52 Turkish seed products across 8 categories with hardcoded UUIDs (B-04 stability)
    - config-server/config/product-service.yml (port 8082, product schema, no JWT block)
  affects:
    - settings.gradle.kts (product-service + inventory-service added)
    - docker-compose.yml (additive product-service block)
    - config-server (new product-service.yml)

tech_stack:
  added:
    - product-service Spring Boot 3.5.14 module (Boot+Jib+Flyway+Springdoc)
    - Hibernate @JdbcTypeCode(SqlTypes.ARRAY) for TEXT[] imageUrls
    - pg_trgm GIN index on lower(name_tr) for ILIKE search
    - @ServiceConnection Testcontainers Postgres (pgvector/pgvector:pg16) across all 5 test classes
  patterns:
    - Native ILIKE @Query with countQuery — Spring Data JPA pagination on native queries
    - Hikari connection-init-sql=SET search_path=product — makes native queries schema-agnostic in tests
    - src/test/resources/application.yml disabling configserver import for slice tests (mirrors identity-service)
    - SET enable_seqscan=off in EXPLAIN ANALYZE test to prove GIN index validity with small seed data
    - Hardcoded UUID literals in V3 seed (B-04 cross-service reference stability)

key_files:
  created:
    - product-service/build.gradle.kts
    - product-service/src/main/java/com/n11/product/ProductServiceApplication.java
    - product-service/src/main/java/com/n11/product/health/SampleHealthController.java
    - product-service/src/main/java/com/n11/product/config/ProductRabbitConfig.java
    - product-service/src/main/java/com/n11/product/category/Category.java
    - product-service/src/main/java/com/n11/product/category/CategoryRepository.java
    - product-service/src/main/java/com/n11/product/category/CategoryController.java
    - product-service/src/main/java/com/n11/product/product/Product.java
    - product-service/src/main/java/com/n11/product/product/ProductRepository.java
    - product-service/src/main/java/com/n11/product/product/ProductService.java
    - product-service/src/main/java/com/n11/product/product/ProductController.java
    - product-service/src/main/java/com/n11/product/product/dto/CreateProductRequest.java
    - product-service/src/main/java/com/n11/product/product/dto/ProductSummaryDto.java
    - product-service/src/main/java/com/n11/product/product/dto/ProductDetailDto.java
    - product-service/src/main/resources/application.yml
    - product-service/src/main/resources/logback-spring.xml
    - product-service/src/main/resources/db/migration/V1__init_processed_events.sql
    - product-service/src/main/resources/db/migration/V2__init_product_catalog.sql
    - product-service/src/main/resources/db/migration/V3__seed_products.sql
    - product-service/src/test/resources/application.yml
    - product-service/src/test/resources/application-test.yml
    - product-service/src/test/java/com/n11/product/migrations/FlywayMigrationsTest.java
    - product-service/src/test/java/com/n11/product/product/ProductRepositoryTest.java
    - product-service/src/test/java/com/n11/product/product/ProductSearchIntegrationTest.java
    - product-service/src/test/java/com/n11/product/product/ProductControllerIntegrationTest.java
    - product-service/src/test/java/com/n11/product/seed/SeedDataAssertionTest.java
    - config-server/src/main/resources/config/product-service.yml
    - product-service/README.md
  modified:
    - settings.gradle.kts (added product-service, inventory-service)
    - docker-compose.yml (additive product-service block)

decisions:
  - "Sort field in @PageableDefault uses snake_case (created_at, not createdAt) — native queries pass sort column names as-is to SQL; camelCase causes PSQLException"
  - "src/test/resources/application.yml with optional:configserver: is required for slice tests — Spring Boot 3.x loads this before profile-specific overrides, preventing ConfigClientFailFastException"
  - "Hikari connection-init-sql=SET search_path=product in application-test.yml — Testcontainers has no init.sh to set search_path for product_user; without this, native queries fail with relation does not exist"
  - "GIN index EXPLAIN ANALYZE test uses SET enable_seqscan=off — with 52 rows, Postgres planner always chooses SeqScan; disabling it proves the index exists and is correctly bound to lower(name_tr)"
  - "products.tx exchange declared in Phase 4 with no consumers (search-service binds in Phase 8)"
  - "inventory-service pre-reserved in settings.gradle.kts to avoid 04-02 touching this file"

metrics:
  duration: "32 minutes"
  completed_date: "2026-04-29"
  tasks_completed: 5
  tasks_total: 5
  files_created: 28
  files_modified: 2
---

# Phase 04 Plan 01: Product-Service Scaffold + Catalog Implementation Summary

**One-liner:** Full product-service module with pg_trgm GIN ILIKE search, 52 hardcoded-UUID Turkish seed products across 8 categories, ROLE_ADMIN admin gate, and 19 passing Testcontainers tests proving migration correctness, search behavior, and controller auth.

## Tasks Completed

| Task | Name | Commit | Key Files |
|------|------|--------|-----------|
| 1 | Scaffold product-service module | 49a21c2 | build.gradle.kts, ProductServiceApplication, settings.gradle.kts, docker-compose.yml, V1 migration |
| 2 | Config-server YAML + Flyway migrations + FlywayMigrationsTest | d2ff36d | product-service.yml, V2 DDL (GIN index), V3 seed (52 products), FlywayMigrationsTest |
| 3 | Domain layer — entities, repositories, DTOs, ProductRabbitConfig | b64f60d | Category, Product, ProductRepository (ILIKE native query), DTOs, ProductRabbitConfig |
| 4 | Service + Controller layer | 7f4f874 | ProductService (toSlug tr-TR locale), ProductController (admin gate), CategoryController |
| 5 | Test resources + 4 test classes | e26f1a3 | application-test.yml, ProductRepositoryTest, ProductSearchIntegrationTest, SeedDataAssertionTest, ProductControllerIntegrationTest |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocker] Missing src/test/resources/application.yml for slice test config server bypass**
- **Found during:** Task 2
- **Issue:** @JdbcTest (slice test) loads application.yml with `spring.config.import: configserver:...` at bootstrap time before @ActiveProfiles("test") profile overrides can kick in, causing ConfigClientFailFastException
- **Fix:** Added `src/test/resources/application.yml` with `spring.config.import: "optional:configserver:"` — mirrors the exact pattern from identity-service
- **Files modified:** product-service/src/test/resources/application.yml
- **Commit:** d2ff36d

**2. [Rule 1 - Bug] @PageableDefault sort field used camelCase (createdAt) with native query**
- **Found during:** Task 5
- **Issue:** Native queries pass sort column names directly to SQL; `createdAt` causes `PSQLException: column "createdat" does not exist`
- **Fix:** Changed `@PageableDefault(sort = "createdAt")` to `@PageableDefault(sort = "created_at")` in ProductController
- **Files modified:** ProductController.java
- **Commit:** e26f1a3

**3. [Rule 3 - Blocker] Native queries failed with "relation products does not exist" in Testcontainers**
- **Found during:** Task 5
- **Issue:** Testcontainers has no init.sh to set `search_path` for `product_user`; native queries without schema prefix fail in tests
- **Fix:** Added `spring.datasource.hikari.connection-init-sql: "SET search_path = product"` to application-test.yml
- **Files modified:** product-service/src/test/resources/application-test.yml
- **Commit:** e26f1a3

**4. [Rule 1 - Bug] EXPLAIN ANALYZE GIN index assertion fails with 52-row dataset**
- **Found during:** Task 5
- **Issue:** Postgres planner chooses SeqScan over GIN index for tables with < ~1000 rows; the test assertion `contains("idx_products_name_lower_trgm")` always failed
- **Fix:** Added `SET enable_seqscan = off` before EXPLAIN ANALYZE to force the planner to use the GIN index (proves the index exists and is correctly bound to `lower(name_tr)`)
- **Files modified:** ProductSearchIntegrationTest.java
- **Commit:** e26f1a3

## Test Results

All 19 tests pass across 5 classes:

| Test Class | Count | What it Proves |
|-----------|-------|----------------|
| FlywayMigrationsTest | 5 | All 3 Flyway migrations apply cleanly; categories/products tables + GIN index exist; seed counts |
| ProductRepositoryTest | 4 | Pagination (page=0, size=20, totalElements>=50); sort by price_gross asc; sort by created_at desc; 8 top-level categories |
| ProductSearchIntegrationTest | 4 | ILIKE returns matching products; case-insensitive Turkish; GIN index provably usable (enable_seqscan=off); empty query returns all |
| SeedDataAssertionTest | 2 | count>=50 products; 8 top-level categories |
| ProductControllerIntegrationTest | 4 | GET /products public 200; GET /categories=8; POST without ROLE_ADMIN=403; POST with ROLE_ADMIN=201 |

## Self-Check: PASSED

All key files exist and all commits are present in git history.

| Check | Result |
|-------|--------|
| product-service/build.gradle.kts | FOUND |
| ProductServiceApplication.java | FOUND |
| ProductRabbitConfig.java | FOUND |
| ProductController.java | FOUND |
| ProductRepository.java | FOUND |
| ProductService.java | FOUND |
| V2__init_product_catalog.sql | FOUND |
| V3__seed_products.sql | FOUND |
| config-server/config/product-service.yml | FOUND |
| Task 1 commit 49a21c2 | FOUND |
| Task 2 commit d2ff36d | FOUND |
| Task 3 commit b64f60d | FOUND |
| Task 4 commit 7f4f874 | FOUND |
| Task 5 commit e26f1a3 | FOUND |
| ./gradlew :product-service:test --no-daemon | BUILD SUCCESSFUL (19 tests, 0 failures) |
