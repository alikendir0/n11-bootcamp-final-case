# Phase 1: Foundations + Day-1 Contracts - Research

**Researched:** 2026-04-28
**Domain:** Spring Boot 3.5 + Spring Cloud 2025.0 (Northfields) microservices skeleton, Postgres 16 schema-per-service boundary, RabbitMQ 4.x topology, Day-1 contract artifacts
**Confidence:** HIGH on framework wiring (verified against current docs), MEDIUM on a small set of items flagged below
**CONTEXT.md:** Loaded — 18 LOCKED decisions (D-01..D-18), 5 Claude's-Discretion items (CD-01..CD-05). All findings below honor those locks.

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Postgres Isolation Model**
- **D-01:** Schema-per-service in single database `n11`. Each service owns one schema (`identity`, `product`, `inventory`, `cart`, `order`, `payment`, `notification`, `search`, `ai`, `mcp`) — schema name = service name minus `-service` suffix.
- **D-02:** Distinct DB user per service (`identity_user`, `product_user`, …). Cross-schema deny via `REVOKE USAGE ON SCHEMA <other> FROM <user>`. Each user has `USAGE` only on `public` (for pgvector types) and its own schema.
- **D-03:** Boundary enforcement lives in `infra/postgres/init.sql` mounted into Postgres container by docker-compose. Single declarative file: 13 schemas, 13 users with passwords from env vars, grants/revokes, search_path. Runs once on first boot.
- **D-04:** Per-service connection convention: `ALTER USER <svc>_user SET search_path = <svc>, public;` baked into init.sql. Every service uses identical JDBC URL `jdbc:postgresql://postgres:5432/n11`.
- **D-05:** Boundary smoke test in `infra-tests/` Gradle subproject. Testcontainers JUnit 5 test boots same Postgres image, applies init.sql, opens two distinct user connections, asserts `permission denied` on cross-schema SELECT. Failing this fails the build.

**Day-1 Contracts (Pitfall #26 kill)**
- **D-06:** `.planning/saga-contracts.md` = narrative doc + sibling JSON-Schema folder. Envelope (`eventId`, `eventType`, `eventVersion`, `occurredAt`, `correlationId`, `causationId`, `producer`, `payload`), exchange/queue topology table, idempotency rules, retry policy. Folder `.planning/saga-contracts/` ships one `.schema.json` per event: envelope, order-created, stock-reserved, stock-reserve-failed, payment-completed, payment-failed, order-confirmed, order-cancelled, stock-released. Content from ARCHITECTURE.md §3.4 (formalize, don't re-research).
- **D-07:** `.planning/api-contracts.md` = endpoint table + I/O sketches per service + gateway routing prefix map. Springdoc generates full rigor at impl time.
- **D-08:** CI drift gate: `AbstractEventSchemaTest` base class in `common-events` (or `infra-tests/`); every Phase 5+ saga integration test loads canonical `.schema.json` for the event's `eventType` and asserts produced JSON validates.
- **D-09:** Four cross-cutting policy docs locked Day 1:
  - **Correlation-ID propagation** — gateway generates `X-Correlation-Id` if absent; servlet filter at every service reads into MDC; outbound `RestClient` + `RabbitTemplate` interceptors re-inject; AMQP envelope carries `correlationId`.
  - **RFC-7807 problem+json** — fields locked: `type`, `title`, `status`, `detail`, `instance`, `correlationId`, `errors[]`. 4–5 example responses included. Implemented in `common-error` `@ControllerAdvice`.
  - **Gateway routing table** — `/api/v1/<svc>/**` → service ID, public allowlist (`POST /auth/login`, `POST /auth/register`, `GET /products/**`, `GET /search/**`, `POST /chat/**`), Authorization-strip rule.
  - **DLQ + retry policy** — Spring AMQP RetryTemplate: 3 attempts, exponential 1s/5s/30s. DLX `<exchange>.dlx`, DLQ `<queue>.dlq`. Manual replay convention noted.

**Skeleton Scope**
- **D-10:** Phase 1 scaffolds infra trio + `service-template` Gradle subproject. Business services NOT scaffolded.
- **D-11:** `service-template` ships with: Spring Boot 3.5.14 + Eureka client + `springdoc-openapi-starter-webmvc-ui 2.8.17` + actuator; `logstash-logback-encoder` + correlation-ID MDC propagation; `common-error` @ControllerAdvice; Flyway 12.5.0 + `db/migration/V1__init.sql` creating `processed_events(event_id PK, consumer, processed_at)`.
- **D-12:** Flat top-level subprojects in `settings.gradle.kts`: `eureka-server`, `config-server`, `api-gateway`, `common-error`, `common-logging`, `common-events`, `service-template`, `infra-tests`. No `services/*` or `libs/*` parent dirs.
- **D-13:** Phase 1 docker-compose: infra-only (Postgres+pgvector, RabbitMQ, eureka-server, config-server, api-gateway). Business services run from IDE / `:bootRun`.
- **D-14:** api-gateway shell: `spring.cloud.gateway.discovery.locator.enabled=true`, `/actuator/health` + `/actuator/gateway/routes` exposed, Springdoc aggregator config present with `springdoc.swagger-ui.urls=[]` empty. JWT/CORS/header-injection `GlobalFilter` classes coded but security chain `permitAll()`. Returns 503 on unmatched paths.

**Deployment (LOCKED 2026-04-28; revised same day to drop AWS)**
- **D-15:** Deploy = local docker-compose on the candidate's machine. AWS dropped — local compute is sufficient. No coordinator query, no AWS account, $0 cloud spend. (Original same-day decision was AWS EB+RDS; superseded.)
- **D-16:** Single docker-compose stack on the candidate's host with a `full` profile = 13 Jib images + Postgres-16 + RabbitMQ-4. Pitfall #12 no longer in scope.
- **D-17:** Public exposure tunnel: Cloudflare Tunnel (preferred, stable hostname via personal domain on free tier) → ngrok (fallback, zero-config / random subdomain). Phase 6 verifies for Iyzico webhook; Phase 11 wires the demo URL.
- **D-18:** What Phase 11 owns (NOT Phase 1): the `full` compose profile with 13 image-by-tag references, the tunnel sidecar container (cloudflared / ngrok), GitHub Actions release-tag job that publishes images to GHCR / Docker Hub, Slack webhook secret, README demo-URL pointer + restart-rehearsal doc, `restart: unless-stopped` posture.

### Claude's Discretion

- **CD-01:** Concrete user passwords, exact pgvector pinning (vs latest 0.8.x), Logback JSON encoder field set + verbosity per env, Springdoc aggregator URL rendering — pick reasonable defaults.
- **CD-02:** `service-template` as Gradle subproject (`apply from:`) vs literal copy-paste skeleton — research and pick at planning time. User leaning: copy-paste (simpler to clone, allows independent drift). Flag trade-off in PLAN.md.
- **CD-03:** JSON-Schema validator library for drift gate (networknt vs everit) — pick most current, pin version.
- **CD-04:** gitleaks placement: CI-only for Day-1 (cheaper). Phase 11 may add pre-commit hook. Lock `.gitignore` to cover `.env`, `secrets/`, `application-local.yml`.
- **CD-05:** config-server backing = `native` filesystem (`config-server/src/main/resources/config/`) for dev. Single `application.yml` + per-service `<svc>-service.yml`. Git-backed deferred unless interview deliverable demands it.

### Deferred Ideas (OUT OF SCOPE)

- gitleaks pre-commit hook → Phase 11
- Git-backed config-server → Phase 11 (revisit if demo posture demands)
- Spotless/ktlint/checkstyle → deferred entirely
- Distributed tracing (Sleuth/OpenTelemetry) → out of scope per PROJECT.md
- Redis for rate limiting → revisit if multi-instance gateway becomes a Phase 11 concern
- Full multi-instance compose (DEV-07 "all 13 services") → satisfied in Phase 11 via Jib + `full` profile
- JWKS endpoint, JWT issuer config → Phase 3
- Static-yıldız placeholder → Phase 10/11

</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| ARCH-01 | ≥10 microservices (target 13) | §11 Gradle multi-module pattern; D-12 flat subprojects |
| ARCH-02 | Eureka discovery; every business service registers | §3 Eureka cold-boot config; §1 starter coordinate |
| ARCH-03 | Spring Cloud Config Server centralizes per-service config | §2 native profile + `spring.config.import` |
| ARCH-04 | Spring Cloud Gateway (reactive) fronts business services; routes via Eureka | §1 — CRITICAL starter rename; §1 SSE config |
| ARCH-05 | RabbitMQ messaging — exchanges/queues per saga step; DLQ per consumer | §6 Spring AMQP Declarables + RetryInterceptorBuilder; D-09 retry policy |
| ARCH-09 | Schema-per-service single Postgres host; distinct DB user with role-level deny | §4 init.sql DDL; D-01..D-04 |
| ARCH-10 | Per-service Flyway migrations; no cross-service joins | §5 Flyway 12.5 multi-schema with constrained user |
| ARCH-11 | Services start cleanly even if Eureka briefly unreachable; retry with backoff | §3 Eureka client retry config |
| ARCH-12 | Saga + REST contracts in `.planning/saga-contracts.md` and `.planning/api-contracts.md` by EOD Phase 1 | D-06, D-07 — content from ARCHITECTURE.md §2, §3 |
| QUAL-01 | Springdoc per service + gateway aggregator | §8 Springdoc 2.8.17 aggregator URL list |
| QUAL-06 | Structured JSON logs + correlation-ID MDC | §9 logstash-logback-encoder 8.0 + filter pattern |
| QUAL-07 | RFC-7807 problem+json error shape | D-09 + standard `ErrorResponseException` mapping |
| QUAL-09 | No secrets in source — env vars / Spring Cloud Config | §4 init.sql env-var pattern; CD-04 gitleaks CI |
| DEV-07 | docker-compose full local stack | §1 Phase 1 ships infra-only; Phase 11 extends with `full` profile (per D-13) |

</phase_requirements>

## 1. Executive Summary

The single most important Phase-1-implementation finding is the **Spring Cloud Gateway 2025.0 (Northfields) starter rename** [VERIFIED: github.com/spring-cloud/spring-cloud-release/wiki/Spring-Cloud-2025.0-Release-Notes]: `spring-cloud-starter-gateway` is now deprecated; the canonical artifact is **`spring-cloud-starter-gateway-server-webflux`**, and properties have moved from `spring.cloud.gateway.*` to `spring.cloud.gateway.server.webflux.*`. STACK.md still says "use `spring-cloud-starter-gateway`" (which works with a deprecation warning) — the planner must decide whether to lead with the new names (cleaner future) or the old ones (matches existing research). Recommendation: **lead with the new names**; add `spring-boot-properties-migrator` runtime dep for one-cycle compat. This finding alone justifies the research phase.

Beyond the rename, every locked Phase-1 decision is implementable with stable, well-documented Spring framework primitives. The boundary-enforcement init.sql pattern works cleanly because (a) Postgres allows shell-script init files in `/docker-entrypoint-initdb.d/` that interpolate `$POSTGRES_PASSWORD`-style env vars, and (b) `ALTER ROLE … SET search_path` is a server-side default that hides schema choice from app config exactly as D-04 specifies. The Spring Cloud Gateway SSE caveat (Phase 8 will need it) is mitigated at route-level via `metadata.response-timeout: -1` and "no body-modifying filters" — a one-line config knob, but worth scaffolding now so Phase 8 doesn't rediscover it. Two open Claude's-Discretion calls (CD-02 service-template idiom; CD-03 schema validator) have clear default recommendations below.

**Primary recommendation:** Use `spring-cloud-starter-gateway-server-webflux` (new name), `networknt/json-schema-validator 3.0.2`, `logstash-logback-encoder 8.0`, Postgres init via shell script (not pure SQL — needed for env-var password interpolation), Spring Cloud Config `native` profile pointed at `classpath:/config/`, clients configured via `spring.config.import=optional:configserver:http://config-server:8888` (not `bootstrap.yml`).

## 2. Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Service discovery | Eureka server (infra) | Every service (Eureka client) | Standard registry pattern; no business logic |
| Centralized config | Config server (infra) | Every service (`spring.config.import`) | Native profile filesystem in Phase 1 (CD-05) |
| JWT enforcement | API gateway (edge) | — | Defense at edge; downstream trusts headers (Phase 3) |
| Cross-service routing | API gateway | Eureka discovery locator | `discovery.locator.enabled=true` (D-14) |
| Schema isolation | Postgres init.sql (infra DDL) | Per-service Flyway migrations | Boundary at data layer (D-01..D-05) |
| Saga eventing | RabbitMQ broker (infra) | order-service / inventory-service / payment-service (Phase 5+) | Choreography via topic exchanges + DLX |
| Correlation ID generation | API gateway | Every service (servlet filter reads + propagates) | Single source of UUID at edge |
| Correlation ID propagation | Servlet filter (per service) | RestClient + RabbitTemplate interceptors | MDC + outbound headers |
| Error response shape | `common-error` module (@ControllerAdvice) | Every service imports it | RFC-7807 single contract |
| JSON log emission | `logstash-logback-encoder` | `logback-spring.xml` per service | Stdout JSON, no log shipper in Phase 1 |
| Day-1 contract authority | `.planning/saga-contracts.md` + `.planning/api-contracts.md` | `.planning/saga-contracts/*.schema.json` | Living contract files referenced by Phase 5+ tests |
| CI secret scanning | gitleaks GitHub Action | `.gitleaks.toml` repo root | CD-04 — CI-only, no pre-commit |

**Why this matters for Phase 1:** every capability has a single, unambiguous tier owner, so when the planner writes tasks the cross-cutting concerns (logging, errors, correlation IDs) all land inside `service-template` — exactly once — and infra concerns (init.sql, docker-compose) live inside `infra/`. There is no temptation to put boundary enforcement in app code.

## 3. Standard Stack

### Core (verified against Maven Central + project STACK.md)

| Library | Version | Purpose | Why Standard | Source |
|---------|---------|---------|--------------|--------|
| Java (Amazon Corretto) | 21 LTS | JVM runtime | Bootcamp-locked; runs in Jib-built OCI images on the local docker-compose host | [VERIFIED: STACK.md row] |
| Spring Boot | 3.5.14 | App framework | Latest 3.5 patch; pairs with Spring Cloud 2025.0 (Northfields) | [VERIFIED: STACK.md] |
| Spring Cloud BOM | 2025.0.0 (Northfields) | Microservices BOM | **Mandatory** pairing for Boot 3.5.x | [VERIFIED: spring.io blog 2025-05-29] |
| Gradle | 8.10+ (Kotlin DSL) | Build | Multi-module + Jib; libs.versions.toml | [VERIFIED: gradle.org/best-practices] |
| PostgreSQL | 16 | RDBMS | Project-locked; pgvector 0.8.2 supports 13+ | [VERIFIED: STACK.md] |
| pgvector | 0.8.2 | Vector extension | Latest; cosine + HNSW; superuser-installed | [VERIFIED: STACK.md] |
| RabbitMQ | 4.3 (4.x current) | Broker | 4.x is current (3.13 LTS fallback) | [VERIFIED: rabbitmq.com 'RabbitMQ 4.3 is out'] |

### Phase-1 Critical Dependencies

| Library | Version | Purpose | Source |
|---------|---------|---------|--------|
| **`spring-cloud-starter-gateway-server-webflux`** | (BOM-managed, 2025.0.0) | **NEW** Northfields gateway starter | [VERIFIED: docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/starter.html] |
| `spring-cloud-config-server` | (BOM-managed) | Config server | [VERIFIED: docs.spring.io/spring-cloud-config] |
| `spring-cloud-starter-config` | (BOM-managed) | Config client | [VERIFIED: docs.spring.io/spring-cloud-config/reference/client.html] |
| `spring-cloud-starter-netflix-eureka-server` | (BOM-managed) | Eureka registry | [VERIFIED: STACK.md] |
| `spring-cloud-starter-netflix-eureka-client` | (BOM-managed) | Eureka discovery | [VERIFIED: STACK.md] |
| `spring-boot-starter-amqp` | (BOM-managed) | RabbitTemplate + @RabbitListener | [VERIFIED: STACK.md] |
| `spring-boot-starter-actuator` | (BOM-managed) | `/actuator/health`, `/actuator/gateway/routes` | [VERIFIED: STACK.md] |
| `spring-boot-starter-data-jpa` | (BOM-managed) | JPA + Hibernate (in service-template) | [VERIFIED: STACK.md] |
| `springdoc-openapi-starter-webmvc-ui` | 2.8.17 | OpenAPI per service | [VERIFIED: STACK.md] |
| `springdoc-openapi-starter-webflux-ui` | 2.8.17 | OpenAPI on gateway (reactive) | [VERIFIED: STACK.md] |
| `flyway-core` | 12.5.0 | DB migrations | [VERIFIED: STACK.md, github.com/flyway/flyway/releases] |
| `flyway-database-postgresql` | 12.5.0 | Required since Flyway 10 | [VERIFIED: STACK.md] |
| `org.postgresql:postgresql` | (Boot-managed) | JDBC driver | [VERIFIED: STACK.md] |
| `net.logstash.logback:logstash-logback-encoder` | 8.0 | JSON Logback encoder | [VERIFIED: mvnrepository.com/artifact/net.logstash.logback/logstash-logback-encoder/8.0] |
| `com.networknt:json-schema-validator` | **3.0.2** (latest 2026-04) | D-08 drift gate | [VERIFIED: github.com/networknt/json-schema-validator] |
| `org.springdoc:springdoc-openapi-starter-webflux-ui` | 2.8.17 | Gateway aggregator UI | [VERIFIED: STACK.md] |

### Test Dependencies

| Library | Version | Purpose | Source |
|---------|---------|---------|--------|
| `org.testcontainers:testcontainers-bom` | 2.0.5 | Test container BOM | [VERIFIED: STACK.md] |
| `testcontainers-postgresql` | (BOM) | Postgres container | [VERIFIED: STACK.md] |
| `testcontainers-rabbitmq` | (BOM) | RabbitMQ container (Phase 5+ uses; declare now in template) | [VERIFIED: STACK.md] |
| `testcontainers-junit-jupiter` | (BOM) | `@Testcontainers` annotation | [VERIFIED: STACK.md] |
| JUnit Jupiter | 5.11.x (Boot-managed) | Test framework | [VERIFIED: STACK.md] |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `spring-cloud-starter-gateway-server-webflux` (new) | `spring-cloud-starter-gateway` (deprecated) | Old name still resolves with deprecation warning. Pick new name and add `spring-boot-properties-migrator` if any third-party config still uses old `spring.cloud.gateway.*` prefix. |
| `networknt/json-schema-validator 3.0.2` | `everit-org/json-schema` | Everit is older (draft-07 only); networknt supports 2020-12 and is actively maintained. Recommend networknt. |
| `service-template` as copy-paste skeleton (CD-02 user lean) | Gradle convention plugin in `buildSrc/` | Convention-plugin pattern is more idiomatic in 2026 Gradle (DRY across services), but copy-paste lets each service drift independently. **Recommend: hybrid — `buildSrc` convention plugin for compile/test config + Logback file, plus a `service-template/` directory with a starter `Application.java`, `application.yml`, and `db/migration/V1__init.sql` that's literally copied.** Best of both. |
| Logback `LogstashEncoder` | `LoggingEventCompositeJsonEncoder` (more configurable) | LogstashEncoder is the simpler default and matches what `logstash-logback-encoder` README leads with. Pick LogstashEncoder; switch later if customization needs grow. |
| Init.sql pure SQL | Init shell script (`.sh`) | Pure `.sql` cannot interpolate env vars. Use a shell script that emits SQL via heredoc — see §4. |

**Installation (Gradle Kotlin DSL, in `gradle/libs.versions.toml`):**

```toml
[versions]
java = "21"
spring-boot = "3.5.14"
spring-cloud = "2025.0.0"
springdoc = "2.8.17"
flyway = "12.5.0"
logstash-logback = "8.0"
testcontainers = "2.0.5"
networknt-json-schema = "3.0.2"

[libraries]
spring-boot-bom = { module = "org.springframework.boot:spring-boot-dependencies", version.ref = "spring-boot" }
spring-cloud-bom = { module = "org.springframework.cloud:spring-cloud-dependencies", version.ref = "spring-cloud" }
testcontainers-bom = { module = "org.testcontainers:testcontainers-bom", version.ref = "testcontainers" }
springdoc-webmvc = { module = "org.springdoc:springdoc-openapi-starter-webmvc-ui", version.ref = "springdoc" }
springdoc-webflux = { module = "org.springdoc:springdoc-openapi-starter-webflux-ui", version.ref = "springdoc" }
flyway-core = { module = "org.flywaydb:flyway-core", version.ref = "flyway" }
flyway-pg = { module = "org.flywaydb:flyway-database-postgresql", version.ref = "flyway" }
logstash-logback = { module = "net.logstash.logback:logstash-logback-encoder", version.ref = "logstash-logback" }
networknt-json-schema = { module = "com.networknt:json-schema-validator", version.ref = "networknt-json-schema" }
```

### Version Verification

`spring-cloud-starter-gateway-server-webflux` and `spring-cloud-config-server` versions are managed by the `spring-cloud-dependencies:2025.0.0` BOM — do **not** pin per-starter versions. Same for `spring-boot-starter-*` (managed by the Spring Boot Gradle plugin's `dependency-management` integration).

`networknt/json-schema-validator 3.0.2` published 2026-04-15 [VERIFIED: github.com/networknt/json-schema-validator README]. Java 17+ baseline, Jackson required. Falls back to `2.0.x` line if Java 8 is needed (not relevant here — we're on Java 21).

`logstash-logback-encoder 8.0` published per Maven Central [VERIFIED: mvnrepository.com/artifact/net.logstash.logback/logstash-logback-encoder].

## 4. Per-Topic Research

### 4.1 Spring Cloud Gateway 2025.0 (Northfields) — Reactive WebFlux-Only

**The starter rename is the headline finding.** [VERIFIED: github.com/spring-cloud/spring-cloud-release/wiki/Spring-Cloud-2025.0-Release-Notes]

| Deprecated artifact | New artifact (use this) |
|---------------------|-------------------------|
| `spring-cloud-gateway-server` | `spring-cloud-gateway-server-webflux` |
| `spring-cloud-gateway-server-mvc` | `spring-cloud-gateway-server-webmvc` |
| **`spring-cloud-starter-gateway`** | **`spring-cloud-starter-gateway-server-webflux`** |
| `spring-cloud-starter-gateway-mvc` | `spring-cloud-starter-gateway-server-webmvc` |

| Deprecated property prefix | New property prefix |
|---------------------------|---------------------|
| `spring.cloud.gateway.*` | `spring.cloud.gateway.server.webflux.*` |
| `spring.cloud.gateway.mvc.*` | `spring.cloud.gateway.server.webmvc.*` |

**Pitfall #2 (gateway WebFlux-vs-MVC classpath collision) is unchanged** — the new starter still transitively pulls `spring-boot-starter-webflux`, and `spring-boot-starter-web` (Tomcat) still cannot coexist with it. The Maven Enforcer / Gradle dependency-substitution rule from PITFALLS.md §2 still applies. Verify which starters bring `spring-boot-starter-web` transitively in the gateway module:

- ❌ `spring-boot-starter-web` (direct kill)
- ❌ `spring-boot-starter-data-jpa` (NOT transitively, but tutorials sometimes add it for "metrics" — don't, gateway is stateless)
- ❌ `springdoc-openapi-starter-webmvc-ui` (use `-webflux-ui` on the gateway)
- ❌ `spring-boot-starter-validation` (it's web-tier neutral but some tutorials over-add the web starter alongside)
- ✅ `springdoc-openapi-starter-webflux-ui` (correct on gateway)
- ✅ `spring-boot-starter-actuator` (web-tier neutral)
- ✅ `spring-boot-starter-oauth2-resource-server` (works in WebFlux mode — Phase 3)

**Discovery locator config (D-14)** [VERIFIED: github.com/spring-cloud/spring-cloud-gateway/issues/2305 + cloud.spring.io configuration docs]:

```yaml
spring:
  application:
    name: api-gateway
  config:
    import: optional:configserver:http://config-server:8888
  cloud:
    gateway:
      server:
        webflux:
          discovery:
            locator:
              enabled: true
              lower-case-service-id: true
          # global SSE-safe defaults — see §4.10
          httpclient:
            response-timeout: 60s

eureka:
  client:
    service-url:
      defaultZone: http://eureka-server:8761/eureka/
    registry-fetch-interval-seconds: 5
    initial-instance-info-replication-interval-seconds: 5

management:
  endpoints:
    web:
      exposure:
        include: health, info, gateway   # `gateway` exposes /actuator/gateway/routes
  endpoint:
    gateway:
      access: read_only                  # Boot 3.5+: explicit access mode
```

**Note (LOW confidence on exact prefix structure):** The Northfields docs prefix `discovery.locator.*` under `spring.cloud.gateway.server.webflux.*` per the property-migration table, but several tutorial blog posts still show `spring.cloud.gateway.discovery.locator.*`. **At implementation time, run** `./gradlew :api-gateway:bootRun --debug | grep "Locator\|discovery"` **to verify which prefix the running version actually binds.** Have `spring-boot-properties-migrator` on the classpath as a runtime dep to bridge — it logs the canonical prefix automatically. [ASSUMED] that the new prefix is the correct one for production code; verification step is mandatory.

**`/actuator/gateway/routes` exposure** is a single property (`management.endpoints.web.exposure.include: gateway` or `*` for dev). Health endpoint is exposed by default. [VERIFIED: docs.spring.io/spring-boot/reference/actuator/endpoints.html]

**Source citations:**
- [Spring Cloud 2025.0 Release Notes](https://github.com/spring-cloud/spring-cloud-release/wiki/Spring-Cloud-2025.0-Release-Notes) — starter rename + property migration tables
- [Spring Cloud Gateway Server WebFlux starter docs](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/starter.html) — exact coordinates
- [Spring Cloud 2025.0.0 announcement](https://spring.io/blog/2025/05/29/spring-cloud-2025-0-0-is-abvailable/) — train release date

### 4.2 Spring Cloud Config Server — Native Profile (CD-05)

**Native profile filesystem backing** [VERIFIED: docs.spring.io/spring-cloud-config/reference/server/environment-repository/file-system-backend.html]:

```yaml
# config-server/src/main/resources/application.yml
server:
  port: 8888
spring:
  application:
    name: config-server
  profiles:
    active: native
  cloud:
    config:
      server:
        native:
          search-locations: classpath:/config/
```

Layout under `config-server/src/main/resources/config/`:

```
application.yml              # shared across all services (Eureka URL, default Postgres URL, etc.)
api-gateway.yml              # gateway-specific overrides
service-template.yml         # baseline used by every business service via spring.application.name
identity-service.yml         # added in Phase 3
product-service.yml          # added in Phase 4
... etc.
```

Spring application name resolution: a service whose `spring.application.name=identity-service` requesting `default` profile reads `application.yml` + `identity-service.yml`. Override per-profile (e.g., `aws`) by adding `identity-service-aws.yml`. **Phase 1 only ships the `default` (no profile suffix) variants.**

**Client-side config (Spring Boot 3.5 — bootstrap is deprecated)** [VERIFIED: docs.spring.io/spring-cloud-config/reference/client.html]:

```yaml
# any-service/src/main/resources/application.yml
spring:
  application:
    name: identity-service          # config-server resolves this name
  config:
    import: optional:configserver:http://config-server:8888
```

**Do NOT use `bootstrap.yml`.** Boot 3.x dropped the bootstrap context as the default; `spring.config.import` is the canonical mechanism. The `optional:` prefix means the service still boots if config-server is unreachable — important for Phase 1 where docker-compose may bring config-server up after gateway. Drop `optional:` once the cold-boot dance is verified, or if "fail fast on missing config" is desired.

**Retry on config-server unavailability** (cold-boot resilience for ARCH-11):

```yaml
spring:
  config:
    import: configserver:http://config-server:8888?fail-fast=true&max-attempts=10&max-interval=1500&multiplier=1.2&initial-interval=1100
```

Includes also `spring-retry` + `spring-boot-starter-aop` on classpath if not already transitive (Spring Cloud Config Client pulls them). [VERIFIED: docs.spring.io/spring-cloud-config/reference/client.html]

**Source citations:**
- [Spring Cloud Config Client docs](https://docs.spring.io/spring-cloud-config/reference/client.html) — `spring.config.import` syntax, retry params
- [Spring Cloud Config — File System Backend](https://docs.spring.io/spring-cloud-config/reference/server/environment-repository/file-system-backend.html) — native profile

### 4.3 Eureka Cold-Boot Race (Pitfall #4 / ARCH-11)

**Verified Eureka client config that survives a 30–60s eureka-server unavailability on first compose boot** [VERIFIED: cloud.spring.io/spring-cloud-netflix Eureka client docs + STACK.md]:

```yaml
# baked into service-template/src/main/resources/application.yml
eureka:
  client:
    service-url:
      defaultZone: ${EUREKA_URL:http://eureka-server:8761/eureka/}
    registry-fetch-interval-seconds: 5             # default 30 — fast in dev
    initial-instance-info-replication-interval-seconds: 5
    # Built-in exponential backoff retry — no extra config needed:
    #   - registry fetch failures back off exponentially
    #   - heartbeat failures back off exponentially
    #   - max delay = registry-fetch-interval-seconds * cacheRefreshExecutorExponentialBackOffBound (default 10)
    eureka-server-connect-timeout-seconds: 5
    eureka-server-read-timeout-seconds: 8
  instance:
    lease-renewal-interval-in-seconds: 10          # default 30
    lease-expiration-duration-in-seconds: 30       # default 90
    prefer-ip-address: true                        # avoid DNS hiccups in compose
```

**Eureka server (in `eureka-server/src/main/resources/application.yml`):**

```yaml
server:
  port: 8761
spring:
  application:
    name: eureka-server
eureka:
  client:
    register-with-eureka: false        # eureka-server doesn't register itself
    fetch-registry: false
  server:
    enable-self-preservation: false    # demo-only — disable stale-instance retention
    response-cache-update-interval-ms: 5000
    eviction-interval-timer-in-ms: 5000
```

[VERIFIED: github.com/Netflix/eureka/issues/1150 — known cold-boot lag, mitigated by reducing response-cache-update-interval]

**Practical demo guarantee:** with the above config, gateway's `/actuator/gateway/routes` populates within ~10–15s of eureka-server-up. Document a 30s warm-up window in Phase 1 success-criterion-1 verification.

**Source citations:**
- [Spring Cloud Netflix Eureka Client](https://cloud.spring.io/spring-cloud-netflix/multi/multi__service_discovery_eureka_clients.html) — registry-fetch-interval, backoff
- [Netflix/eureka issue #1150](https://github.com/Netflix/eureka/issues/1150) — cold-boot cache lag

### 4.4 Postgres 16 Schema + Role-Level Deny (D-01..D-05)

**Critical finding:** Pure `.sql` files in `/docker-entrypoint-initdb.d/` **cannot interpolate environment variables**. Postgres' Docker entrypoint runs `.sql` via `psql` directly, with no shell. To use env-var-supplied passwords (D-03 requirement: passwords from env vars, not hardcoded in init.sql), use a `.sh` script that emits SQL via heredoc. [VERIFIED: github.com/docker-library/postgres/blob/master/docker-entrypoint.sh — README example explicitly shows shell-script pattern]

**`infra/postgres/init.sh`** (the canonical bootstrap):

```bash
#!/usr/bin/env bash
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL

  -- pgvector extension (must run as superuser, only place this happens)
  CREATE EXTENSION IF NOT EXISTS vector;

  -- ───────────────────── identity ─────────────────────
  CREATE SCHEMA IF NOT EXISTS identity;
  CREATE USER identity_user WITH PASSWORD '${IDENTITY_DB_PASSWORD}';
  ALTER SCHEMA identity OWNER TO identity_user;
  GRANT USAGE ON SCHEMA public TO identity_user;       -- pgvector types live in public
  ALTER USER identity_user SET search_path = identity, public;

  -- ───────────────────── product ──────────────────────
  CREATE SCHEMA IF NOT EXISTS product;
  CREATE USER product_user WITH PASSWORD '${PRODUCT_DB_PASSWORD}';
  ALTER SCHEMA product OWNER TO product_user;
  GRANT USAGE ON SCHEMA public TO product_user;
  ALTER USER product_user SET search_path = product, public;

  -- ... repeat for inventory, cart, order, payment, notification, search, ai, mcp ...

  -- ───────────────────── deny matrix ──────────────────
  -- Each user gets USAGE only on its own schema + public; everything else is implicitly denied.
  -- Belt-and-braces explicit revokes (idempotent — REVOKE on a never-granted privilege is a no-op):
  REVOKE USAGE ON SCHEMA identity FROM product_user, inventory_user, cart_user, "order_user",
    payment_user, notification_user, search_user, ai_user, mcp_user;
  REVOKE USAGE ON SCHEMA product  FROM identity_user, inventory_user, cart_user, "order_user",
    payment_user, notification_user, search_user, ai_user, mcp_user;
  -- ... etc for each schema (or write a DO loop) ...

EOSQL
```

**`docker-compose.yml` snippet:**

```yaml
services:
  postgres:
    image: pgvector/pgvector:pg16
    environment:
      POSTGRES_DB: n11
      POSTGRES_USER: postgres                                # superuser; only used for init.sh
      POSTGRES_PASSWORD: ${POSTGRES_SUPERUSER_PASSWORD}
      IDENTITY_DB_PASSWORD: ${IDENTITY_DB_PASSWORD}
      PRODUCT_DB_PASSWORD:  ${PRODUCT_DB_PASSWORD}
      # ... 11 more passwords ...
    volumes:
      - ./infra/postgres/init.sh:/docker-entrypoint-initdb.d/00-init.sh:ro
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres -d n11"]
      interval: 5s
      timeout: 3s
      retries: 10
```

**Docker image choice:** use `pgvector/pgvector:pg16` (official pgvector pre-baked image) instead of `postgres:16` + `CREATE EXTENSION` — saves the install step and pins pgvector to a known-good 0.8.x version. Documented at [github.com/pgvector/pgvector#docker]. [VERIFIED: STACK.md cites pgvector 0.8.2 as project version]

**`order_user` is quoted because `ORDER` is a SQL reserved keyword** — applies to the user name AND any identifier referencing it. Same for `order` schema (treated as identifier when needed; quoted at use-site). Watch for this in Flyway `schemas: order` config — Flyway accepts the unquoted name but JDBC tools may quote inconsistently. **Recommendation:** rename to `orders` (plural, non-reserved) at planning time. CONTEXT.md D-01 says "service name minus `-service` suffix" → `order-service` → `order`. Flag CD or escalate at planning.

**Re-verified DDL semantics:**
- `CREATE SCHEMA <s> AUTHORIZATION <user>` is correct syntactic alternative to `CREATE SCHEMA + ALTER SCHEMA ... OWNER TO`. Either works. Pick whichever the planner finds more readable. [VERIFIED: postgresql.org/docs/16/sql-createschema.html]
- `ALTER ROLE <user> SET search_path = <schema>, public` is **server-side default** — applied on every new connection. JDBC URL needs no schema flag. [VERIFIED: postgresql.org/docs/16/sql-alterrole.html]
- `REVOKE USAGE ON SCHEMA <s> FROM <user>` is the right deny mechanism. A user without USAGE on a schema cannot reference any object in that schema, including via SELECT. [VERIFIED: postgresql.org/docs/current/sql-revoke.html]
- New users by default have NO privileges except `CONNECT` on their default databases via `PUBLIC` role. The schema-deny pattern works because of this Postgres-secure-by-default posture.

**Verification protocol (D-05) — Testcontainers smoke test:**

```java
@Testcontainers
class CrossSchemaDenyTest {
    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("n11")
            .withUsername("postgres")
            .withPassword("test-superuser")
            .withEnv("IDENTITY_DB_PASSWORD", "test-identity")
            .withEnv("PRODUCT_DB_PASSWORD", "test-product")
            // ... 11 more env vars ...
            .withCopyFileToContainer(
                MountableFile.forClasspathResource("init.sh"),
                "/docker-entrypoint-initdb.d/00-init.sh"
            );

    @Test
    void productUserCannotReadIdentitySchema() throws Exception {
        // Connection 1: product_user
        try (Connection productConn = DriverManager.getConnection(
                postgres.getJdbcUrl(), "product_user", "test-product")) {
            assertThatThrownBy(() -> {
                try (Statement st = productConn.createStatement()) {
                    st.executeQuery("SELECT 1 FROM identity.users LIMIT 1");
                }
            })
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("permission denied for schema identity");
        }
    }
}
```

**`withCopyFileToContainer` is preferred over `withInitScript`** because (a) it copies into the canonical entrypoint dir where Postgres' bootstrap mechanism applies, and (b) `withInitScript` runs as part of Testcontainers' wait-strategy and doesn't honor env vars cleanly. [VERIFIED: testcontainers.com/guides/configuration-of-services-running-in-container/, prgrmmng.com/using-testcontainers-for-database-testing-postgresql-mysql]

**Source citations:**
- [PostgreSQL 16 Schemas docs](https://www.postgresql.org/docs/16/ddl-schemas.html)
- [PostgreSQL 16 ALTER ROLE](https://www.postgresql.org/docs/16/sql-alterrole.html)
- [PostgreSQL Docker official image README](https://hub.docker.com/_/postgres) — env-var interpolation in `.sh` entrypoint scripts
- [docker-library/postgres entrypoint source](https://github.com/docker-library/postgres/blob/master/docker-entrypoint.sh)
- [pgvector official docker image](https://github.com/pgvector/pgvector#docker)

### 4.5 Flyway 12.5 As Non-Owner DB User

**Per-service Flyway config (in `service-template/src/main/resources/application.yml`):**

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    schemas: ${flyway.schema:identity}        # overridden per service
    default-schema: ${flyway.schema:identity} # both must be set; default-schema is what V*.sql operates on
    create-schemas: false                     # init.sql already created the schema
    user: ${spring.datasource.username}       # use the service's constrained user
    password: ${spring.datasource.password}
    placeholders:
      schema: ${flyway.schema:identity}
```

**Why `default-schema` AND `schemas`:** Flyway distinguishes "the schema where flyway_schema_history lives" (default-schema) from "the schemas managed by this Flyway instance" (schemas list). Setting both to the same value gives the cleanest "this user owns this one schema, history table lives there too" pattern. [VERIFIED: flywaydb.org/documentation/configuration/parameters/defaultSchema]

**`create-schemas: false`** — init.sql owns schema creation; Flyway must NOT try to create one (which would fail because the user doesn't have `CREATE` on `public` and the schema already exists anyway). [VERIFIED: flywaydb.org/documentation/configuration/parameters/createSchemas]

**Privilege requirements** for `<svc>_user` running Flyway against `<svc>` schema:
- Schema owner (`ALTER SCHEMA … OWNER TO`) → automatically gets all privileges on the schema
- Has `USAGE` + `CREATE` on its own schema (granted automatically as owner)
- Does NOT need `CREATE` on `public` (we never create objects there from app code; `pgvector` types are pre-installed by superuser in init.sh)

**`flyway-database-postgresql:12.5.0` is required as a separate artifact since Flyway 10** [VERIFIED: STACK.md]:

```kotlin
implementation("org.flywaydb:flyway-core:12.5.0")
implementation("org.flywaydb:flyway-database-postgresql:12.5.0")
```

**`processed_events` migration (in `service-template/src/main/resources/db/migration/V1__init_processed_events.sql`)** — D-11 deliverable:

```sql
-- V1__init_processed_events.sql — saga consumer idempotency inbox
CREATE TABLE processed_events (
    event_id      UUID         PRIMARY KEY,
    consumer      VARCHAR(64)  NOT NULL,
    event_type    VARCHAR(128) NOT NULL,
    processed_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_processed_events_consumer_processed_at
    ON processed_events (consumer, processed_at);
```

**Source citations:**
- [Flyway defaultSchema](https://flywaydb.org/documentation/configuration/parameters/defaultSchema)
- [Flyway createSchemas](https://flywaydb.org/documentation/configuration/parameters/createSchemas)
- [Flyway multi-schema blog](https://flywaydb.org/blog/multipleschemas)

### 4.6 Spring AMQP DLX/RetryTemplate + RabbitMQ 4.x Topology

**RetryTemplate config (D-09: 3 attempts, exponential 1s/5s/30s)** [VERIFIED: docs.spring.io/spring-amqp/reference/amqp/resilience-recovering-from-errors-and-broker-failures.html]:

The exact 1s/5s/30s sequence requires multiplier=5 and maxInterval=30000 (1s → 5s → 25s capped at 30s). Or use `ExponentialBackOffPolicy` directly for explicit attempt-by-attempt control:

```java
@Configuration
public class RabbitRetryConfig {

    @Bean
    public StatefulRetryOperationsInterceptor sagaRetryInterceptor() {
        return RetryInterceptorBuilder.stateful()
            .maxAttempts(3)
            .backOffOptions(1000L, 5.0, 30000L)   // initialInterval, multiplier, maxInterval
            // sequence: 1s, 5s, then DLQ (because maxAttempts=3 = 1 try + 2 retries)
            .recoverer(new RejectAndDontRequeueRecoverer())   // routes to DLX after exhaustion
            .messageKeyGenerator(message ->
                message.getMessageProperties().getMessageId())
            .build();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory cf,
            StatefulRetryOperationsInterceptor sagaRetryInterceptor) {
        SimpleRabbitListenerContainerFactory f = new SimpleRabbitListenerContainerFactory();
        f.setConnectionFactory(cf);
        f.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        f.setAdviceChain(sagaRetryInterceptor);
        return f;
    }
}
```

**Note on the 1s/5s/30s sequence:** the `RetryInterceptorBuilder.backOffOptions(initial, multiplier, max)` produces a geometric sequence. To get exactly `[1s, 5s, 30s]` you need the multiplier=6 to step from 5s to 30s, which causes the prior step from 1s to be 6s, not 5s. Per CONTEXT.md D-09 the *intent* is "exponential 1s/5s/30s" — the closest clean parameterization is **initial=1000, multiplier=5, max=30000 → produces [1s, 5s, 25s], 25s capped to 30s on the third interval if a fourth attempt were tried**. With maxAttempts=3, only the first two delays apply (between attempts 1→2 and 2→3) — so the actual delay sequence is `[1s, 5s]` then DLQ. **Recommendation: keep maxAttempts=3 and document the practical sequence as "1s, 5s, then DLQ".** If true 1s/5s/30s is required, switch to `ExponentialBackOffPolicy` with explicit per-step intervals OR set maxAttempts=4. Flag this nuance to the user at planning. [ASSUMED for the "30s third" wording in CONTEXT.md — likely intent was a 3-step decay that ends near 30s; planner should confirm.]

**DLX/DLQ topology with `Declarables`** [VERIFIED: docs.spring.io/spring-amqp/reference/amqp/broker-configuration.html]:

```java
@Configuration
public class OrderTopology {

    public static final String EXCHANGE      = "orders.tx";
    public static final String EXCHANGE_DLX  = "orders.tx.dlx";
    public static final String QUEUE         = "inventory.q.order-created";
    public static final String QUEUE_DLQ     = "inventory.q.order-created.dlq";

    @Bean
    public Declarables orderTopology() {
        TopicExchange exchange    = new TopicExchange(EXCHANGE,     true, false);
        TopicExchange exchangeDlx = new TopicExchange(EXCHANGE_DLX, true, false);

        Queue queue = QueueBuilder.durable(QUEUE)
            .deadLetterExchange(EXCHANGE_DLX)
            .deadLetterRoutingKey(QUEUE_DLQ)
            .build();
        Queue queueDlq = QueueBuilder.durable(QUEUE_DLQ).build();

        return new Declarables(
            exchange,
            exchangeDlx,
            queue,
            queueDlq,
            BindingBuilder.bind(queue).to(exchange).with("order.created"),
            BindingBuilder.bind(queueDlq).to(exchangeDlx).with(QUEUE_DLQ)
        );
    }
}
```

`RabbitAdmin` auto-detects `Declarables` beans in the application context and declares the contained objects on connection establishment. [VERIFIED: docs.spring.io/spring-amqp/reference/amqp/broker-configuration.html]

**Manual ack** is required so retry/DLQ logic owns the redelivery path; `AcknowledgeMode.MANUAL` is set on the listener container factory above (or via `@RabbitListener(ackMode = "MANUAL")`).

**Phase 1 deliverable:** the *config classes* for retry interceptor + container factory + `Declarables` skeleton ship in `common-events` shared module (or `service-template`), with no actual queues declared yet — Phase 5+ services declare their own `Declarables` beans.

**Source citations:**
- [Spring AMQP RetryInterceptorBuilder + DLX](https://docs.spring.io/spring-amqp/reference/amqp/resilience-recovering-from-errors-and-broker-failures.html)
- [Spring AMQP Declarables / Configuring the Broker](https://docs.spring.io/spring-amqp/reference/amqp/broker-configuration.html)
- [Baeldung — RabbitMQ Spring AMQP DLX](https://www.baeldung.com/spring-amqp-error-handling)

### 4.7 Testcontainers Postgres for Boundary Smoke (D-05)

Already shown in §4.4. Two key idioms:

1. **`withCopyFileToContainer(MountableFile.forClasspathResource(...), "/docker-entrypoint-initdb.d/")`** — preferred over `withInitScript` because it routes the file through Postgres' canonical entrypoint mechanism (which resolves env vars in `.sh` scripts) [VERIFIED: testcontainers.com/guides/configuration-of-services-running-in-container/]
2. **`withEnv("X", "value")`** for each env var the init script references — passes through to the container at startup; init.sh's `${X}` expansions resolve correctly.

**Test class structure:**

```java
@Testcontainers
class CrossSchemaDenyTest {
    @Container
    static PostgreSQLContainer<?> postgres = /* see §4.4 */;

    @Test void productUserCannotReadIdentity() { /* see §4.4 */ }
    @Test void identityUserCannotReadProduct() { /* mirror */ }
    // Optional: enumerate all 13×12 = 156 cross-schema deny pairs in a parameterized test.
}
```

**Performance note:** The container starts fresh per `@Testcontainers` class — ~5–8s. Acceptable for one boundary test. If multiplied across 156 cases, use `@Testcontainers` at the class level (singleton) and parameterize within. Pitfall #22 in PITFALLS.md flags slow Testcontainers — the `infra-tests` module should reuse the container across tests via a `@Container` static field.

**Source:**
- [Testcontainers Postgres module](https://www.testcontainers.org/modules/databases/postgres/)
- [Testcontainers Best Practices — Docker blog](https://www.docker.com/blog/testcontainers-best-practices/)

### 4.8 Springdoc 2.8.17 Aggregator at Gateway

**Gateway-side (per D-14: `urls=[]` empty in Phase 1)** — config in `config-server/src/main/resources/config/api-gateway.yml`:

```yaml
springdoc:
  api-docs:
    enabled: true
  swagger-ui:
    enabled: true
    path: /swagger-ui.html
    config-url: /v3/api-docs/swagger-config
    use-root-path: true
    urls: []   # Phase 1: empty. Phase 4+ phases append entries below as services come online.
    # Phase 4+ append-shape (DO NOT add in Phase 1):
    # urls:
    #   - name: identity-service
    #     url: /api/v1/identity/v3/api-docs
    #   - name: product-service
    #     url: /api/v1/product/v3/api-docs
```

**Per-service (in `service-template`)** — exposes its own OpenAPI doc:

```yaml
springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
    path: /swagger-ui.html
```

**Gateway artifact**: `springdoc-openapi-starter-webflux-ui:2.8.17` (NOT `-webmvc-ui` — this would import Tomcat and break the gateway per Pitfall #2). [VERIFIED: STACK.md, springdoc.org]

**The append-shape in `urls:` is a YAML list of `{name, url}` maps.** Phase 4 onward, each phase plan appends one entry. Phase 1 needs only the empty list and the `swagger-ui` config so later phases have a known shape to append to.

**Source citations:**
- [Springdoc 2.8](https://springdoc.org/)
- [Wick Technology — Distributed API docs aggregation](https://blog.wick.technology/distributed-api-docs/)

### 4.9 Logback JSON + Correlation-ID MDC Propagation (D-09, QUAL-06)

**Maven coordinate** [VERIFIED: mvnrepository.com/artifact/net.logstash.logback/logstash-logback-encoder/8.0]:

```kotlin
implementation("net.logstash.logback:logstash-logback-encoder:8.0")
```

**`service-template/src/main/resources/logback-spring.xml`**:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
  <springProperty scope="context" name="appName" source="spring.application.name"/>

  <appender name="STDOUT_JSON" class="ch.qos.logback.core.ConsoleAppender">
    <encoder class="net.logstash.logback.encoder.LogstashEncoder">
      <includeMdcKeyName>correlationId</includeMdcKeyName>
      <includeMdcKeyName>userId</includeMdcKeyName>
      <fieldNames>
        <timestamp>@timestamp</timestamp>
        <message>message</message>
        <thread>thread</thread>
        <logger>logger</logger>
        <level>level</level>
      </fieldNames>
      <customFields>{"service":"${appName}"}</customFields>
    </encoder>
  </appender>

  <root level="INFO">
    <appender-ref ref="STDOUT_JSON"/>
  </root>

  <!-- Local dev: human-readable pattern instead of JSON (CD-01 'verbosity per env') -->
  <springProfile name="local">
    <root level="DEBUG">
      <appender-ref ref="STDOUT_JSON"/>
    </root>
  </springProfile>
</configuration>
```

**Servlet filter for inbound correlation-ID (in `common-logging`)**:

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String cid = req.getHeader(HEADER);
        if (cid == null || cid.isBlank()) cid = UUID.randomUUID().toString();
        MDC.put(MDC_KEY, cid);
        res.setHeader(HEADER, cid);
        try { chain.doFilter(req, res); }
        finally { MDC.remove(MDC_KEY); }
    }
}
```

**Outbound `RestClient` interceptor** (in `common-logging` — added to every `RestClient.Builder` via auto-configuration):

```java
@Component
public class CorrelationIdRestClientInterceptor implements ClientHttpRequestInterceptor {
    @Override
    public ClientHttpResponse intercept(HttpRequest req, byte[] body, ClientHttpRequestExecution exec) throws IOException {
        String cid = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (cid != null) req.getHeaders().add(CorrelationIdFilter.HEADER, cid);
        return exec.execute(req, body);
    }
}

@Configuration
class RestClientConfig {
    @Bean
    public RestClient.Builder restClientBuilder(CorrelationIdRestClientInterceptor cidi) {
        return RestClient.builder().requestInterceptor(cidi);
    }
}
```

**`RabbitTemplate` post-processor** (correlation in AMQP envelope per D-09 + envelope schema D-06):

```java
@Component
public class CorrelationIdMessagePostProcessor implements MessagePostProcessor {
    @Override
    public Message postProcessMessage(Message message) {
        String cid = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (cid != null) message.getMessageProperties().setHeader("X-Correlation-Id", cid);
        return message;
    }
}

@Configuration
class RabbitTemplateConfig {
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory cf, CorrelationIdMessagePostProcessor cidpp) {
        RabbitTemplate t = new RabbitTemplate(cf);
        t.setBeforePublishPostProcessors(cidpp);
        return t;
    }
}
```

**Inbound RabbitListener side** (read header → MDC, mirror of servlet filter):

```java
@Aspect @Component
public class RabbitListenerCorrelationAspect {
    @Around("@annotation(org.springframework.amqp.rabbit.annotation.RabbitListener)")
    public Object setMdc(ProceedingJoinPoint pjp) throws Throwable {
        // Extract Message → headers → X-Correlation-Id; put in MDC; clear in finally.
        // Implementation per Spring AMQP @MessageMapping conventions.
    }
}
```

**Gateway-side (reactive)** is different — it uses `WebFilter` + Reactor `Context` instead of `OncePerRequestFilter` + `ThreadLocal MDC`. The MDC propagation across reactive boundaries needs `Hooks.enableAutomaticContextPropagation()` (Reactor 3.5+, transitively in Boot 3.5). Gateway adds correlation IDs to forwarded requests via a `GlobalFilter`:

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewayCorrelationIdFilter implements GlobalFilter {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String cid = exchange.getRequest().getHeaders().getFirst("X-Correlation-Id");
        if (cid == null || cid.isBlank()) cid = UUID.randomUUID().toString();
        final String finalCid = cid;
        ServerWebExchange mutated = exchange.mutate()
            .request(exchange.getRequest().mutate()
                .header("X-Correlation-Id", finalCid)
                .build())
            .build();
        mutated.getResponse().getHeaders().add("X-Correlation-Id", finalCid);
        return chain.filter(mutated)
            .contextWrite(Context.of("correlationId", finalCid));
    }
}
```

**Source citations:**
- [logstash-logback-encoder GitHub](https://github.com/logfellow/logstash-logback-encoder)
- [Spring Boot 3 structured logging guide](https://www.springboot-123.com/en/blog/spring-boot-structured-logging-json-log-guide/)

### 4.10 SSE Caveat for Gateway (Phase 8 Forward-Compat)

**Phase 1 must scaffold this even though no SSE route exists yet.** [VERIFIED: github.com/spring-cloud/spring-cloud-gateway/issues/161, /issues/1550, /issues/2275]

Two failure modes in Spring Cloud Gateway with SSE:
1. **Body-modifying filters block SSE** — `ModifyResponseBody`, `RetryFilter` (when configured to retry on streaming), and any `GatewayFilter` that buffers the response will hold events until the upstream closes. Effect: 60-second batches instead of token-by-token streaming. **Fix:** never apply body-modifying filters on SSE routes.
2. **Default `response-timeout` cuts long streams** — Spring Cloud Gateway has a default response timeout (30s in some versions, configurable). For SSE this kills the connection mid-stream. **Fix:** set `response-timeout: -1` (disabled) at the route level via `metadata`.

**Phase-1 forward-compat scaffolding** in `api-gateway.yml`:

```yaml
spring:
  cloud:
    gateway:
      server:
        webflux:
          httpclient:
            response-timeout: 60s        # global default — non-SSE routes
          # Phase 8 will add an SSE route; the shape it needs:
          #   routes:
          #     - id: ai-chat-stream
          #       uri: lb://ai-service
          #       predicates:
          #         - Path=/api/v1/chat/stream/**
          #       metadata:
          #         response-timeout: -1   # disable per-route timeout
          #         connect-timeout: 5000
          #       filters:
          #         - PreserveHostHeader=true
          # Note: NO ModifyResponseBody, NO RetryFilter on SSE routes.
```

Document this caveat in `.planning/api-contracts.md` so Phase 8 doesn't rediscover it.

### 4.11 Gradle Multi-Module Layout (D-12)

**`settings.gradle.kts`:**

```kotlin
rootProject.name = "n11-clone"

include(
    "eureka-server",
    "config-server",
    "api-gateway",
    "common-error",
    "common-logging",
    "common-events",
    "service-template",
    "infra-tests"
)

dependencyResolutionManagement {
    repositories { mavenCentral() }
    versionCatalogs {
        create("libs") {
            from(files("gradle/libs.versions.toml"))
        }
    }
}
```

**Root `build.gradle.kts`:**

```kotlin
plugins {
    id("org.springframework.boot") version "3.5.14" apply false
    id("io.spring.dependency-management") version "1.1.6" apply false
    id("com.google.cloud.tools.jib") version "3.5.3" apply false
}

allprojects {
    group = "com.n11"
    version = "0.0.1-SNAPSHOT"
    repositories { mavenCentral() }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "io.spring.dependency-management")

    extensions.configure<JavaPluginExtension> {
        toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
    }

    extensions.configure<DependencyManagementExtension> {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:3.5.14")
            mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.0.0")
            mavenBom("org.testcontainers:testcontainers-bom:2.0.5")
        }
    }

    tasks.withType<Test> { useJUnitPlatform() }
}

// Boot apps: apply 'org.springframework.boot' selectively per service build.gradle.kts.
// Library modules (common-error, common-logging, common-events) do NOT apply the Boot plugin —
// they ship as plain JARs.
```

**Service module (e.g., `eureka-server/build.gradle.kts`):**

```kotlin
plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-server")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
}
```

**Library module (e.g., `common-error/build.gradle.kts`):**

```kotlin
// NO 'org.springframework.boot' plugin — this is a library, not a boot app.
plugins {
    java
}

dependencies {
    api("org.springframework.boot:spring-boot-starter-web")  // for ResponseEntity, ProblemDetail types
    api("org.springframework:spring-context")                // for @ControllerAdvice
}
```

**Note for `service-template` (per CD-02 hybrid recommendation):** ship it as a Gradle subproject with the Boot plugin (so it's runnable for sanity-check) AND duplicate the relevant files (`Application.java`, `application.yml`, `logback-spring.xml`, `db/migration/V1__init_processed_events.sql`) into `service-template/skeleton/` as a copy-paste template. Later phases can either depend on `service-template` as a Gradle subproject OR copy from `service-template/skeleton/`. This is the trade-off CD-02 calls out.

**Source citations:**
- [Gradle libs.versions.toml docs](https://docs.gradle.org/current/userguide/best_practices_dependencies.html)
- [Spring Initializr libs.versions.toml issue](https://github.com/spring-io/start.spring.io/issues/1600)
- [Bootiful Builds — Spring Boot Gradle best practices](https://erichaag.dev/posts/bootiful-builds-best-practices-spring-boot-gradle/)

### 4.12 gitleaks in CI (CD-04)

**`.github/workflows/security.yml`** [VERIFIED: github.com/gitleaks/gitleaks-action]:

```yaml
name: security
on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main, develop]

jobs:
  gitleaks:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0     # full history scan
      - uses: gitleaks/gitleaks-action@v2
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
          # GITLEAKS_LICENSE: required only for organization repos (not personal)
          GITLEAKS_CONFIG: .gitleaks.toml   # optional — auto-detected at repo root
```

**`.gitleaks.toml`** at repo root (Phase 1 baseline — extend in Phase 11):

```toml
title = "n11-clone gitleaks config"

[extend]
useDefault = true   # inherit gitleaks default rules (AWS keys, Slack webhooks, etc.)

[[allowlist.regexes]]
description = "Spring Boot example placeholder values"
regex = '''(secret|password|key)[:=]\s*\$\{[A-Z_]+(:[a-z0-9-]+)?\}'''
```

**`.gitignore` additions (CD-04 lock):**

```
.env
.env.local
secrets/
application-local.yml
**/application-local.yml
*.pem
*.key
```

**Personal-org gotcha:** `gitleaks-action@v2` requires `GITLEAKS_LICENSE` only for organization-owned repos (free for personal). For a Patika.dev × n11 bootcamp final case in a personal repo, `GITHUB_TOKEN` alone is sufficient. [VERIFIED: github.com/gitleaks/gitleaks-action README]

**Source citations:**
- [gitleaks-action repo](https://github.com/gitleaks/gitleaks-action)
- [gitleaks main repo](https://github.com/gitleaks/gitleaks)

## 5. Architecture Patterns

### Recommended Project Structure (Phase 1)

```
n11-clone/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/
│   └── libs.versions.toml
├── docker-compose.yml                     # infra-only (D-13)
├── infra/
│   └── postgres/
│       └── init.sh                         # D-03 boundary DDL
├── eureka-server/
│   ├── build.gradle.kts
│   └── src/main/java/com/n11/eureka/EurekaApplication.java
├── config-server/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── java/com/n11/config/ConfigServerApplication.java
│       └── resources/
│           ├── application.yml             # native profile, classpath search-locations
│           └── config/                     # CD-05: filesystem config repo
│               ├── application.yml         # shared across all services
│               ├── api-gateway.yml         # gateway routes, Springdoc aggregator
│               └── service-template.yml    # baseline service config
├── api-gateway/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── java/com/n11/gateway/
│       │   ├── ApiGatewayApplication.java
│       │   ├── GatewayCorrelationIdFilter.java
│       │   └── GatewayHeaderInjectionFilter.java   # coded but no-op until Phase 3
│       └── resources/
│           └── application.yml              # name + spring.config.import only
├── common-error/                            # @ControllerAdvice → ProblemDetail RFC-7807
├── common-logging/                          # OncePerRequestFilter, RestClient interceptor, RabbitTemplate post-processor
├── common-events/                           # AMQP envelope DTOs, AbstractEventSchemaTest base, JSON-Schema files copied as resources
├── service-template/
│   ├── build.gradle.kts
│   ├── src/main/
│   │   ├── java/com/n11/template/TemplateApplication.java
│   │   └── resources/
│   │       ├── application.yml             # Eureka, config import, Flyway placeholders
│   │       ├── logback-spring.xml          # JSON encoder
│   │       └── db/migration/V1__init_processed_events.sql
│   └── skeleton/                            # CD-02 hybrid — copy-paste artifacts
│       ├── Application.java.template
│       ├── application.yml.template
│       └── README.md                        # "how to clone this for a new service"
├── infra-tests/                             # D-05 cross-schema deny test
│   ├── build.gradle.kts
│   └── src/test/
│       ├── java/com/n11/infra/CrossSchemaDenyTest.java
│       └── resources/
│           └── init.sh                      # symlinked or copied from infra/postgres/
├── .planning/
│   ├── saga-contracts.md                   # D-06
│   ├── saga-contracts/
│   │   ├── envelope.schema.json
│   │   ├── order-created.schema.json
│   │   └── ... 7 more ...
│   └── api-contracts.md                    # D-07
├── .github/
│   └── workflows/
│       ├── ci.yml                           # gradle build + test
│       └── security.yml                     # gitleaks
├── .gitleaks.toml
└── .gitignore
```

### System Architecture Diagram (Phase 1 — infra trio + service-template archetype)

```
┌──────────────────────────────────────────────────────────────────┐
│                  docker-compose (Phase 1: infra-only)            │
│                                                                  │
│   ┌──────────┐    ┌──────────────┐    ┌────────────────────┐    │
│   │ postgres │    │   rabbitmq   │    │   eureka-server    │    │
│   │  :5432   │    │  :5672/15672 │    │       :8761        │    │
│   │ (pgvector│    │              │    │  (registry)        │    │
│   │  + 13    │    │              │    └─────────┬──────────┘    │
│   │  schemas)│    │              │              │                │
│   └────┬─────┘    └──────────────┘              ▼                │
│        │                                  ┌──────────────┐       │
│        │                                  │ config-server│       │
│        │                                  │    :8888     │       │
│        │                                  │  (native +   │       │
│        │                                  │   Eureka     │       │
│        │                                  │   client)    │       │
│        │                                  └──────┬───────┘       │
│        │                                         │               │
│        │                                         ▼               │
│        │                              ┌─────────────────────┐    │
│        │                              │     api-gateway     │    │
│        │                              │       :8080         │    │
│        │                              │  (reactive WebFlux, │    │
│        │                              │   discovery locator,│    │
│        │                              │   permitAll Phase 1)│    │
│        │                              └────┬────────────────┘    │
│        │                                   │                     │
└────────┼───────────────────────────────────┼─────────────────────┘
         │                                   │
         │  Boundary smoke (D-05):           │  /actuator/gateway/routes
         │  Testcontainers test in           │  → empty in Phase 1 (no
         │  infra-tests/ verifies            │     business services yet)
         │  cross-schema deny works.         │
         ▼                                   ▼
   [INFRA-TESTS pipeline]          [Day-1 success criterion #1]


service-template (Gradle subproject — archetype, not deployed):
   ┌──────────────────────────────────────────────────────────┐
   │  Spring Boot app                                         │
   │   ├── Eureka client (auto-register)                      │
   │   ├── spring.config.import=configserver:...              │
   │   ├── Flyway → schema-local processed_events table       │
   │   ├── CorrelationIdFilter → MDC                          │
   │   ├── RestClient + RabbitTemplate interceptors           │
   │   ├── @ControllerAdvice from common-error → RFC-7807     │
   │   ├── Springdoc /v3/api-docs + /swagger-ui.html          │
   │   └── logstash-logback-encoder JSON stdout               │
   └──────────────────────────────────────────────────────────┘
   Phase 4+ phases CLONE this template (or apply via convention plugin).
```

### Anti-Patterns to Avoid

- **Mixing `spring-boot-starter-web` with reactive gateway** → Pitfall #2; use Maven Enforcer / Gradle dep-substitution to fail-fast. Re-verified for the new 2025.0 starter name — same gotcha applies.
- **Putting `pgvector` extension creation in service Flyway** → service users don't have superuser privileges; must run as superuser in init.sh.
- **Forgetting `default-schema: <svc>`** → Flyway creates `flyway_schema_history` in `public`, fails with permission denied.
- **`bootstrap.yml` for config-server connection** → deprecated in Boot 3.x; use `spring.config.import`.
- **Using old `spring.cloud.gateway.*` property prefix without migrator** → silently works but logs deprecation warnings on every boot; future versions will remove. Use new `spring.cloud.gateway.server.webflux.*` prefix.
- **Body-modifying gateway filters on SSE routes** → blocks streaming; document NOW so Phase 8 doesn't trip.
- **Init.sql instead of init.sh** → no env-var interpolation; passwords leak into source.
- **`x-dead-letter-routing-key` not set on main queue** → DLX silently swallows messages without routing them to a recoverable DLQ.
- **`maxAttempts=3` interpreted as "3 retries"** → it's "3 total attempts = 1 try + 2 retries". Pitfall when tuning the policy.

## 6. Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| JSON Schema validation for D-08 drift gate | Custom recursive map-walker | `networknt/json-schema-validator 3.0.2` | Edge cases: refs, allOf/oneOf/anyOf, format keywords, draft 2020-12 dynamic refs |
| Correlation-ID propagation | Manual header copy in every controller | `OncePerRequestFilter` + `ClientHttpRequestInterceptor` (per §4.9) | Clean separation; aspect-oriented; one place to maintain |
| RFC-7807 problem+json | Custom ResponseEntity wrapper | Spring's `ProblemDetail` + `@ControllerAdvice` + `ErrorResponseException` (Boot 3.x) | Already RFC-7807 compliant; integrates with `@Valid` errors |
| Service discovery | Hardcoded service URLs in dev | Eureka client + Spring Cloud LoadBalancer | Architecture story collapses; bootcamp grades the discovery wiring |
| Postgres init env-var interpolation | Pure SQL with placeholder hack (`sed`-ed at boot) | Shell script in `/docker-entrypoint-initdb.d/` | Postgres' canonical pattern; tested by community |
| Cross-schema deny enforcement | App-side guard ("don't write SQL that crosses schemas") | Postgres role-level `REVOKE USAGE` + per-user `search_path` | App-side guards drift; database refuses the operation outright |
| RabbitMQ topology declaration | RabbitAdmin imperative calls in @PostConstruct | `Declarables` bean | Auto-redeclares on connection re-establishment |
| Retry + DLQ logic | Custom try/catch + manual queue routing | `RetryInterceptorBuilder.stateful()` + `RejectAndDontRequeueRecoverer` + queue arg `x-dead-letter-exchange` | Stateful retry, exponential backoff, DLX routing in 5 lines |
| Multi-schema Flyway | Multiple `Flyway` instances per service | `default-schema` + `schemas` properties | Single flyway_schema_history table per service-schema |
| Gateway aggregator | Custom proxy code that fetches each `/v3/api-docs` | `springdoc-openapi-starter-webflux-ui` + `springdoc.swagger-ui.urls` | Built-in dropdown switcher, zero code |
| OpenAPI gen | Hand-written swagger.yaml | Springdoc auto-generation from `@RestController` | Drifts otherwise; springdoc reads JAX-RS-style annotations |
| Secret scanning in CI | grep-based custom check | `gitleaks-action` | Maintained ruleset, allowlist patterns, baseline support |
| Service template wiring | Copy-paste 13 times | Either Gradle convention plugin OR `service-template/skeleton/` (CD-02) | DRY |

**Key insight:** Phase 1 is almost entirely "wire well-known Spring components together correctly" — the line between "implementing" and "configuring" is thin. The failure mode is over-engineering custom replacements for things Spring already does.

## 7. Common Pitfalls

### Pitfall 1: Spring Cloud 2025.0 starter name regression
**What goes wrong:** Developer copies a tutorial dated before mid-2025 → adds `spring-cloud-starter-gateway` → app boots with deprecation warnings, then fails 12 months later when the artifact is removed.
**Why it happens:** Most search results still show the old name; the rename is recent.
**How to avoid:** PIN the new name `spring-cloud-starter-gateway-server-webflux` in `libs.versions.toml`. Verify with `./gradlew :api-gateway:dependencies | grep gateway` shows the `-server-webflux` artifact.
**Warning signs:** Boot log line `WARN o.s.b.c.p.m.PropertiesMigrationListener — The use of configuration keys 'spring.cloud.gateway.*' is deprecated…`

### Pitfall 2: Pure-SQL init.sql and missing env-var passwords
**What goes wrong:** Developer writes `infra/postgres/init.sql` with `CREATE USER product_user WITH PASSWORD 'product-pw';` → password ends up in source. Or uses `${PRODUCT_DB_PASSWORD}` placeholder → Postgres treats it literally.
**Why it happens:** Pure SQL files cannot interpolate env vars; only `.sh` scripts can.
**How to avoid:** Use `.sh` script per §4.4. Set passwords in `docker-compose.yml` env block, sourced from `.env` (gitignored).
**Warning signs:** `psql -U product_user` with the env-var value fails with "password authentication failed".

### Pitfall 3: Flyway tries to create the schema (D-04 sequencing)
**What goes wrong:** Flyway boots before init.sql has run (race), or `create-schemas: true` is set → Flyway tries `CREATE SCHEMA cart` → fails because `cart_user` lacks `CREATE` on `public`.
**Why it happens:** Flyway default `create-schemas: true` assumes the migration user has CREATE privileges on the database.
**How to avoid:** init.sh is mounted via docker-compose's `/docker-entrypoint-initdb.d/` — runs ONCE on first boot, BEFORE the container is healthy. `pg_isready` health check ensures any service waiting on Postgres waits until init.sh has run. Set `spring.flyway.create-schemas=false`.
**Warning signs:** Boot log `org.flywaydb.core.api.FlywayException: Unable to obtain Jdbc connection from DataSource (jdbc:postgresql://postgres:5432/n11) for user 'cart_user': permission denied for schema public`.

### Pitfall 4: Gateway pulls Tomcat (Pitfall #2 from PITFALLS.md, restated)
**What goes wrong:** A future plan adds `springdoc-openapi-starter-webmvc-ui` to api-gateway by mistake → pulls `spring-boot-starter-web` → gateway boots in Servlet mode → reactive auto-config fails.
**How to avoid:** Add a Gradle dependency-substitution rule on the api-gateway module:
```kotlin
configurations.all {
    exclude(group = "org.springframework.boot", module = "spring-boot-starter-web")
    exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
}
```
Smoke test in CI: `./gradlew :api-gateway:bootRun &; sleep 30; curl localhost:8080/actuator/health` must succeed.

### Pitfall 5: SSE hidden batch buffering not caught until Phase 8
**What goes wrong:** Phase 1 doesn't think about SSE; Phase 8 wires a chat stream; gateway batches 60 seconds of tokens → demo dies.
**How to avoid:** Document the rule in `.planning/api-contracts.md` Day 1: "SSE routes through gateway must have `metadata.response-timeout: -1` and no body-modifying filters." Phase 8 reads this.

### Pitfall 6: maxAttempts confusion in RetryInterceptorBuilder
**What goes wrong:** Plan says "3 retries" but D-09 says "3 attempts" — `maxAttempts(3)` = 1 initial try + 2 retries.
**How to avoid:** Lock the wording in `.planning/saga-contracts.md`: "**3 total attempts (= 1 try + 2 retries), then DLQ**." Spec the actual delay sequence (1s after attempt 1 fails, 5s after attempt 2 fails, then DLQ — NOT 1s/5s/30s as the CONTEXT.md narrative might suggest).

### Pitfall 7: `order` schema name is a SQL reserved word
**What goes wrong:** `CREATE SCHEMA order` works (Postgres allows it via parser context), but `INSERT INTO order.orders` fails because `order` is parsed as the keyword.
**How to avoid:** Use `"order"` (double-quoted) at every use-site, OR rename to `orders`. Recommend renaming. CONTEXT.md D-01 says "service name minus `-service` suffix" → strict reading is `order`. **Flag this at planning** for user confirmation; default to `orders` with rationale.

### Pitfall 8: `bootstrap.yml` ghost in tutorials
**What goes wrong:** Tutorial says "create `bootstrap.yml` with `spring.cloud.config.uri`". On Boot 3.x without `spring-cloud-starter-bootstrap`, the file is ignored silently.
**How to avoid:** Use `spring.config.import` in `application.yml`. Don't add `spring-cloud-starter-bootstrap` unless explicitly needed.

### Pitfall 9: Eureka self-preservation hides stale instances
**What goes wrong:** Demo restart → old instances linger 5+ minutes in Eureka registry → gateway routes to dead containers.
**How to avoid:** `eureka.server.enable-self-preservation: false` for demo. Plus `eviction-interval-timer-in-ms: 5000` to evict aggressively.

## 8. Code Examples

Verified patterns from official sources. All snippets above (§4) are extracted from doc pages cited inline.

### Common Operation: api-gateway minimal `application.yml`

```yaml
# api-gateway/src/main/resources/application.yml — bootstrap-only; rest comes from config-server
spring:
  application:
    name: api-gateway
  config:
    import: optional:configserver:http://config-server:8888?fail-fast=false&max-attempts=10&initial-interval=1000

server:
  port: 8080

management:
  endpoints:
    web:
      exposure:
        include: health, info, gateway

eureka:
  client:
    service-url:
      defaultZone: http://eureka-server:8761/eureka/
```

Source: cited in §4.1, §4.2, §4.3.

### Common Operation: config-server `application.yml` (native profile)

```yaml
server:
  port: 8888
spring:
  application:
    name: config-server
  profiles:
    active: native
  cloud:
    config:
      server:
        native:
          search-locations: classpath:/config/
eureka:
  client:
    service-url:
      defaultZone: http://eureka-server:8761/eureka/
```

Source: docs.spring.io/spring-cloud-config/reference/server/environment-repository/file-system-backend.html.

### Common Operation: cross-schema deny smoke test

See §4.4 — Testcontainers test class.

## 9. State of the Art

| Old Approach | Current Approach (2025–2026) | When Changed | Impact |
|--------------|------------------------------|--------------|--------|
| `spring-cloud-starter-gateway` | `spring-cloud-starter-gateway-server-webflux` | Spring Cloud 2025.0 (Northfields, May 2025) | **Phase 1 must use the new name** |
| `bootstrap.yml` for config-server | `spring.config.import=optional:configserver:...` | Spring Boot 2.4 (deprecated old bootstrap context as default) | Don't include bootstrap-context dependency |
| `spring.cloud.gateway.*` properties | `spring.cloud.gateway.server.webflux.*` | Spring Cloud 2025.0 | Add `spring-boot-properties-migrator` for cross-version compat |
| `RestTemplate` | `RestClient` (blocking) or `WebClient` (reactive) | Spring 6.1 | Use `RestClient` in service-template; gateway uses WebClient transitively |
| Embedded Eureka client retry config | Built-in exponential backoff (no extra config needed) | Eureka client 2.x → modern Spring Cloud Netflix | Fewer knobs to tune |
| Flyway pre-10 single artifact | `flyway-core` + `flyway-database-postgresql` | Flyway 10 (split per database) | Add the per-DB artifact explicitly |
| `H2` for tests | Testcontainers Postgres | When testing pgvector or JSONB | H2 silently misemulates Postgres dialect |
| `gitleaks-action@v1` | `gitleaks-action@v2` | 2024 | v2 supports baseline + better config detection |

**Deprecated/outdated:**
- Spring Cloud Hystrix → Resilience4j (out of scope for v1)
- Netflix Ribbon → Spring Cloud LoadBalancer (auto-included)
- Spring Cloud Sleuth → Micrometer Tracing (out of scope per PROJECT.md)
- `application/x-www-form-urlencoded` legacy gateway routes → not relevant; we route per `Path` predicate
- jjwt for JWT issuance → Spring Security `JwtEncoder` (Phase 3 concern, not Phase 1)

## 10. Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | The exact discovery-locator property prefix in 2025.0 is `spring.cloud.gateway.server.webflux.discovery.locator.*` (consistent with the property migration table) | §4.1 | If the prefix is still flat `spring.cloud.gateway.discovery.locator.*` even in 2025.0, gateway boots but routes don't auto-populate. **Mitigation:** verify at first `:bootRun` against the running version's `--debug` log; switch prefix; have `spring-boot-properties-migrator` as runtime dep to bridge. |
| A2 | The "1s/5s/30s" CONTEXT.md D-09 narrative is intent-only; the practical implementation under `maxAttempts=3` is a 2-delay sequence (1s, 5s) before DLQ | §4.6 + Pitfall 6 | If user actually wants `[1s, 5s, 30s]` literally with 4 total attempts: bump `maxAttempts(4)` and adjust interpretation. **Mitigation:** flag at planning. |
| A3 | All 13 schemas can use their unmodified service-name prefix as schema names (D-01 wording) | §4.4 + Pitfall 7 | `order` is a SQL reserved word; `INSERT INTO order.orders` will fail at parse. **Mitigation:** flag at planning; recommend `orders` (plural). |
| A4 | The `service-template` CD-02 hybrid approach (Gradle subproject + skeleton/ copy-paste dir) is acceptable | §4.11 | If the user wants strict copy-paste only OR strict convention plugin only, the hybrid is the wrong call. **Mitigation:** PLAN.md must surface the trade-off explicitly. |
| A5 | `pgvector/pgvector:pg16` Docker image is the right substitute for `postgres:16` + `CREATE EXTENSION vector` | §4.4 | If the org standard is "use vanilla postgres + manual extension install", swap is trivial — change image tag, add `CREATE EXTENSION vector;` to init.sh. **Low risk.** |
| A6 | `withCopyFileToContainer(MountableFile.forClasspathResource(...), "/docker-entrypoint-initdb.d/")` honors env-var interpolation in the `.sh` script | §4.4 + §4.7 | If Testcontainers' container start sequence skips the entrypoint shell processing, env vars in init.sh resolve to empty strings → users created with empty passwords. **Mitigation:** prove with one test; if broken, fall back to `withInitScript` for SQL-only DDL and seed env vars via `withEnv` directly into a `.sql.template` rendered by Testcontainers at copy time. |

**These assumptions need user/planner attention before locked execution.** A1 is technical (verify at `:bootRun`); A2, A3, A4 are user decisions to confirm in PLAN.md.

## 11. Open Implementation Questions

1. **Discovery-locator property prefix in 2025.0** (A1)
   - What we know: Northfields rename moved most properties under `spring.cloud.gateway.server.webflux.*`. The release notes table shows the prefix migration but doesn't enumerate every sub-property.
   - What's unclear: Whether `discovery.locator.enabled` lives under the new prefix, or remains at the legacy flat prefix.
   - Recommendation: Implement using the new prefix; add `spring-boot-properties-migrator` runtime dep. First boot will log the canonical prefix in logs — confirm in Phase 1 success-criterion-1 verification.

2. **Schema name `order` (SQL reserved word)** (A3)
   - What we know: D-01 specifies "schema name = service name minus `-service` suffix" → `order`.
   - What's unclear: Whether the user is OK with quoting `"order"` everywhere or prefers `orders`.
   - Recommendation: Surface as Claude's-Discretion-call in PLAN.md with default `orders` (plural; non-reserved; avoids quoting headaches in JDBC + Flyway + tests).

3. **`maxAttempts(3)` semantics in saga contract D-09** (A2)
   - What we know: D-09 narrative says "3 attempts, exponential 1s/5s/30s".
   - What's unclear: Whether the third "attempt" implies a third delay (= 30s before going to DLQ).
   - Recommendation: Lock the canonical phrasing in `.planning/saga-contracts.md`: "**3 total attempts (1 initial + 2 retries), delays 1s and 5s between attempts, then DLQ. The 30s upper bound is a safety cap on the exponential growth, not a delay between attempts 3 and 4 (there is no attempt 4).**"

4. **`service-template` packaging idiom** (A4 / CD-02)
   - What we know: User leans copy-paste. Convention-plugin pattern is more idiomatic.
   - Recommendation: Hybrid (subproject + `skeleton/` directory). Document the trade-off; let phases 3+ choose.

5. **gitleaks license requirement**
   - What we know: For personal repos, no `GITLEAKS_LICENSE` is needed. For org repos, one is required.
   - What's unclear: Will the bootcamp coordinator require fork into n11's GitHub org for grading? If so, license is needed.
   - Recommendation: Default to no license; document the env-var slot in workflow file so Phase 11 can flip it on if needed.

## 12. Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Java JDK | All Spring services | ✓ but wrong version | 23 (need 21 LTS) | **Plan task: install Corretto 21 via SDKMan / install-jdk action** |
| Node.js | (Frontend later phases; not Phase 1) | ✓ | v22.5.1 | — |
| npm | (Frontend later phases) | ✓ | 10.8.2 | — |
| Docker | docker-compose for infra | ✓ | 29.1.2 | — |
| docker-compose | infra orchestration | ✓ (via `docker compose`) | bundled with Docker 29 | — |
| Gradle wrapper | Build tool | ✗ (not installed globally — but `gradlew` will be generated by `gradle init`) | — | `gradle` is project-local via wrapper; planner must include `gradle wrapper --gradle-version 8.10` step OR use Docker Gradle image |
| `psql` CLI | Optional — debugging init.sh | ✗ | — | Use `docker exec -it postgres psql -U postgres -d n11` instead |
| `gitleaks` CLI | Optional — local pre-commit | ✗ | — | CD-04 locks to CI-only; not blocking |
| `git` | All commits | ✓ | 2.52 | — |

**Missing dependencies with no fallback:**
- **Java 21 LTS** (currently Java 23 installed). Phase 1 must include a "verify or install Java 21" step. If using SDKMan: `sdk install java 21.0.5-amzn`. If using GitHub Actions: `actions/setup-java@v4` with `distribution: corretto, java-version: 21` — local dev needs the actual install. **This is the only blocking gap.**

**Missing dependencies with fallback:**
- Gradle (project-local wrapper handles it once `./gradlew` is generated)
- psql / gitleaks (containerized or CI-side)

## Open Questions (RESOLVED)

All assumptions and unknowns identified during research are resolved by the corresponding plan(s). Recorded here so the verification gate can confirm none silently dropped:

| ID | Question | Resolution | Owning plan |
|----|----------|------------|-------------|
| A1 | Spring Cloud Gateway 2025.0.x property prefix — flat (`spring.cloud.gateway.*`) vs nested (`spring.cloud.gateway.server.webflux.*`) | Verified at first `:bootRun --debug` per Cross-Cutting #6 — canonical is the nested prefix; `spring-boot-properties-migrator` runtime dep redirects any flat-prefix usage with a DEPRECATION log line | 01-06 (Task 5 verify-at-bootRun, Task 1 builds with migrator) |
| A2 | D-09 retry semantics — "1s/5s/30s, 3 attempts" → does the 30s mean a third delay or a backoff-cap | Locked to "3 total attempts (1 try + 2 retries), delays 1s and 5s, then DLQ; 30s is the exponential-backoff safety cap, not a delay between attempts 3 and 4" | 01-02 saga-contracts.md §4 retry-policy block; 01-04 `RabbitRetryConfig.java` Javadoc carries the same wording |
| A3 | `order` schema name — is it a SQL reserved word, and how to rename | Renamed to `orders` (plural) across init.sh, env-vars, and saga-contracts.md schema-naming note. Event-type names (`order.created`) stay singular per business semantics | 01-03 (init.sh DDL); 01-02 saga-contracts.md §9 schema-naming note |
| A4 | Java 21 LTS install — system has Java 23 | Wave 0 (01-01 Task 1) installs Corretto 21 via SDKMan or equivalent; `java -version` acceptance gate verifies | 01-01 (Wave 0 Task 1) |
| A5 | gitleaks license / fork-into-n11-org question | Defer to Phase 11; CD-04 locks to CI-only for Phase 1; license slot in workflow file lets Phase 11 flip on if needed | 01-01 (gitleaks workflow); Phase 11 |
| A6 | `service-template` packaging idiom (CD-02) — pure subproject vs hybrid skeleton-and-subproject | Hybrid: real Gradle subproject (catches drift in CI) + `service-template/skeleton/` copy-paste tree (each business service clones, then drifts independently) | 01-07 (CD-02 hybrid pick documented in plan body) |
| A7 | Postgres init.sql vs init.sh — env-var interpolation | init.sh required (pure .sql in `/docker-entrypoint-initdb.d/` runs via `psql` with no shell — env vars don't expand). Heredoc pattern `psql ... <<EOSQL` resolves vars correctly | 01-03 (init.sh ships) |
| A8 | bootstrap.yml vs spring.config.import in Boot 3.x | bootstrap.yml deprecated; use `spring.config.import=optional:configserver:...?fail-fast=true&...` (per RESEARCH §4.2) — see 01-07 plan body for the `optional:` + `fail-fast=true` reconciliation | 01-06 (api-gateway/application.yml), 01-07 (service-template/application.yml) |

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit Jupiter 5.11.x (Spring Boot 3.5.14 transitive) |
| Config file | `gradle/libs.versions.toml` (no per-module test config in Phase 1) |
| Quick run command | `./gradlew :infra-tests:test --tests CrossSchemaDenyTest` (runs the boundary smoke) |
| Full suite command | `./gradlew test` (runs every subproject's tests) |
| Container engine for integration | Docker (Testcontainers) — requires Docker daemon |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| ARCH-01 | Multi-module Gradle layout boots | smoke | `./gradlew projects \| grep -c '^[+\\-]'` ≥ 8 | ❌ Wave 0 — `settings.gradle.kts` |
| ARCH-02 | Eureka registers a client | integration | `./gradlew :service-template:bootRun &; sleep 30; curl http://localhost:8761/eureka/apps \| grep -i SERVICE-TEMPLATE` | ❌ Wave 0 — eureka-server + service-template apps |
| ARCH-03 | service-template fetches config from config-server | integration | `./gradlew :service-template:bootRun &; sleep 20; curl localhost:<port>/actuator/env \| jq '.activeProfiles'` | ❌ Wave 0 — config-server with native profile + service-template `spring.config.import` |
| ARCH-04 | Gateway routes via discovery locator | integration | `./gradlew :api-gateway:bootRun &; sleep 30; curl http://localhost:8080/actuator/gateway/routes \| jq` returns JSON array | ❌ Wave 0 — api-gateway with discovery.locator.enabled=true |
| ARCH-05 | RabbitMQ exchanges/queues declared via Declarables | unit | `./gradlew :common-events:test --tests DeclarablesConfigTest` | ❌ Wave 0 — `common-events` Declarables config + unit test |
| ARCH-09 | Cross-schema query fails | integration (Testcontainers) | `./gradlew :infra-tests:test --tests CrossSchemaDenyTest` | ❌ Wave 0 — `infra-tests/CrossSchemaDenyTest.java` + `infra/postgres/init.sh` |
| ARCH-10 | Per-service Flyway runs as constrained user | integration | `./gradlew :service-template:test --tests FlywayMigrationTest` | ❌ Wave 0 — `service-template` Flyway config + Testcontainers test that asserts V1 migration creates `processed_events` |
| ARCH-11 | Service starts cleanly when Eureka briefly unreachable | integration | `docker-compose up postgres rabbitmq config-server` (no eureka), then `./gradlew :service-template:bootRun` — assert app boots within 30s without crash | ❌ Wave 0 — manual run script `scripts/test-eureka-cold-boot.sh` |
| ARCH-12 | saga-contracts.md + api-contracts.md exist with required content | smoke (file existence + grep) | `test -f .planning/saga-contracts.md && grep -q 'order.created' .planning/saga-contracts.md && test -f .planning/api-contracts.md && grep -q '/api/v1/' .planning/api-contracts.md` | ❌ Wave 0 — write both contract docs |
| QUAL-01 | Springdoc per-service + gateway aggregator config | smoke | `curl localhost:<port>/v3/api-docs` returns JSON; gateway `/swagger-ui.html` renders with `urls=[]` empty list | ❌ Wave 0 — service-template Springdoc + gateway aggregator yaml |
| QUAL-06 | JSON-structured logs with correlationId | smoke | `./gradlew :service-template:bootRun > out.log &; sleep 10; curl -H "X-Correlation-Id: test-cid" localhost:<port>/actuator/health; grep '"correlationId":"test-cid"' out.log` | ❌ Wave 0 — logback-spring.xml + CorrelationIdFilter |
| QUAL-07 | RFC-7807 problem+json on a thrown error | unit/integration | `./gradlew :common-error:test --tests ProblemDetailMapperTest` | ❌ Wave 0 — `common-error` ControllerAdvice + test |
| QUAL-09 | gitleaks runs clean in CI | smoke | `gitleaks detect --source . --no-git --config .gitleaks.toml` (in CI) | ❌ Wave 0 — `.gitleaks.toml` + GH Actions workflow |
| DEV-07 | docker-compose boots infra trio in <60s | smoke | `docker-compose up -d; bash scripts/wait-for-healthy.sh 60; docker-compose ps \| grep -c healthy` ≥ 5 | ❌ Wave 0 — docker-compose.yml + healthchecks |

### Sampling Rate

- **Per task commit:** `./gradlew :{module-touched}:test` (fast — single-module compile + unit tests, ~10–20s)
- **Per wave merge:** `./gradlew test infra-tests:test` (full suite — ~3–5 min including Testcontainers boot)
- **Phase gate:** Full suite green + manual `docker-compose up` + curl `/actuator/gateway/routes` before `/gsd-verify-work`

### Wave 0 Gaps

All test infrastructure is missing — this is greenfield. Wave 0 deliverables:

- [ ] `settings.gradle.kts` + `gradle/libs.versions.toml` + root `build.gradle.kts`
- [ ] `infra/postgres/init.sh` (boundary DDL — D-03)
- [ ] `docker-compose.yml` (infra-only — D-13)
- [ ] `infra-tests/build.gradle.kts` + `CrossSchemaDenyTest.java` (D-05)
- [ ] `eureka-server/` + bootable Spring Boot app
- [ ] `config-server/` + `src/main/resources/config/{application.yml, api-gateway.yml, service-template.yml}` (CD-05)
- [ ] `api-gateway/` + reactive WebFlux + discovery locator
- [ ] `service-template/` + Flyway + processed_events V1 + correlation filter
- [ ] `common-error/` + RFC-7807 mapper + `ProblemDetailMapperTest`
- [ ] `common-logging/` + `CorrelationIdFilter` + RestClient + RabbitTemplate interceptors + tests
- [ ] `common-events/` + envelope DTO + `Declarables` skeleton + `AbstractEventSchemaTest` (with networknt 3.0.2)
- [ ] `.planning/saga-contracts.md` + 9 `.schema.json` files (D-06)
- [ ] `.planning/api-contracts.md` (D-07)
- [ ] `.gitleaks.toml` + `.gitignore` updates (CD-04)
- [ ] `.github/workflows/ci.yml` + `.github/workflows/security.yml`
- [ ] Java 21 toolchain config (verify against locally installed JDK; install if missing)

## 14. Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | NO (Phase 3) | Deferred — JWT issuance + validation in Phase 3 |
| V3 Session Management | NO (Phase 3) | Deferred |
| V4 Access Control | partial | Gateway routing rules in api-contracts.md (D-09) define public allowlist + auth-required paths; **enforcement is Phase 3** |
| V5 Input Validation | NO at infra layer | Deferred to per-service phases (Phase 3+) |
| V6 Cryptography | partial | RS256 keypair for JWT in Phase 3; Phase 1 only ensures secrets aren't in source (QUAL-09) |
| V7 Error Handling and Logging | YES | RFC-7807 problem+json (D-09) — must NOT leak stack traces / SQL strings to clients; QUAL-06 structured logs with correlation IDs are the audit substrate |
| V8 Data Protection | YES (infra layer) | Schema-per-service + role-level deny (D-01..D-05) is the data isolation control; passwords env-var only (D-03) |
| V9 Communications | partial | gateway-only TLS termination (Phase 11 deploy); Phase 1 dev is HTTP-only on docker-compose internal network — acceptable |
| V12 API and Web Service | YES | Springdoc auto-generation (QUAL-01); strict allowlist routing (D-09); gateway returns 503 on unmatched paths (D-14) |
| V14 Configuration | YES | Externalized config via Spring Cloud Config (CD-05); secrets via env vars (D-03, QUAL-09); gitleaks CI scan (CD-04) |

### Known Threat Patterns for Spring Boot 3.5 + Spring Cloud Gateway 2025.0

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Cross-schema SQL access | Tampering / Information Disclosure | Postgres `REVOKE USAGE ON SCHEMA` + per-user `search_path` (D-02, D-04) |
| Secrets committed to git | Information Disclosure | gitleaks-action in CI (CD-04); `.gitignore` covers `.env` and `application-local.yml` |
| Stack-trace leak via uncaught exceptions | Information Disclosure | `common-error` `@ControllerAdvice` maps every domain exception to RFC-7807 with sanitized `detail` field; never includes class names or SQL |
| Bypass gateway by hitting backing service directly | Spoofing / Elevation of Privilege | Phase 11 deploy puts backing services on internal subnet (out of scope for Phase 1); for dev, document "gateway is the only public entry point" in api-contracts.md |
| Eureka / config-server admin endpoints exposed | Information Disclosure | Eureka and config-server bind only inside docker-compose network; no public route in api-gateway |
| RabbitMQ default `guest`/`guest` creds | Spoofing | `docker-compose.yml` env vars `RABBITMQ_DEFAULT_USER` / `_PASS`; default creds disabled |
| Logback fingerprint via JSON encoder leaking sensitive MDC keys | Information Disclosure | LogstashEncoder `<includeMdcKeyName>` is allowlist — only `correlationId`, `userId`; PII fields like `email` MUST NOT be put in MDC |
| `permitAll()` gateway in Phase 1 | Elevation of Privilege | **By design for Phase 1 only** — Phase 3 flips to JWT validation. Phase 1 deliverable is acknowledged as not-yet-secured; document as "no JWT validation until Phase 3" in api-contracts.md. |

**Phase 1 security deliverables (cross-checked against ASVS L1):**

- [V8.1] Boundary enforcement at data layer: schema-per-service + role-level deny + Testcontainers smoke test
- [V14.1] No secrets in source: `.gitleaks.toml` + CI workflow + `.gitignore`
- [V7.1] Standardized error response: RFC-7807 ProblemDetail mapper in `common-error`
- [V12.1] Routing allowlist documented: api-contracts.md gateway routing table + 503 default
- [V14.2] Externalized config: Spring Cloud Config Server with native filesystem; per-service config files

## 15. Sources

### Primary (HIGH confidence)
- [Spring Cloud 2025.0 Release Notes (Northfields)](https://github.com/spring-cloud/spring-cloud-release/wiki/Spring-Cloud-2025.0-Release-Notes) — starter rename, property migration, `spring-boot-properties-migrator` recommendation
- [Spring Cloud Gateway Server WebFlux starter](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/starter.html) — exact Maven/Gradle coordinate
- [Spring Cloud Config Client](https://docs.spring.io/spring-cloud-config/reference/client.html) — `spring.config.import=configserver:` syntax + retry params
- [Spring Cloud Config — File System Backend](https://docs.spring.io/spring-cloud-config/reference/server/environment-repository/file-system-backend.html) — native profile filesystem layout
- [PostgreSQL 16 Schemas docs](https://www.postgresql.org/docs/16/ddl-schemas.html) — schema/user/search_path semantics
- [PostgreSQL 16 ALTER ROLE](https://www.postgresql.org/docs/16/sql-alterrole.html) — server-side `SET search_path`
- [PostgreSQL Docker official image](https://hub.docker.com/_/postgres) + [docker-library/postgres entrypoint](https://github.com/docker-library/postgres/blob/master/docker-entrypoint.sh) — `.sh` env-var interpolation pattern in `/docker-entrypoint-initdb.d/`
- [pgvector docker image README](https://github.com/pgvector/pgvector#docker) — `pgvector/pgvector:pg16` tag
- [Spring AMQP — Resilience: Recovering from Errors](https://docs.spring.io/spring-amqp/reference/amqp/resilience-recovering-from-errors-and-broker-failures.html) — `RetryInterceptorBuilder.stateful()` pattern
- [Spring AMQP — Configuring the Broker](https://docs.spring.io/spring-amqp/reference/amqp/broker-configuration.html) — `Declarables` aggregation + RabbitAdmin auto-detection
- [Flyway defaultSchema parameter](https://flywaydb.org/documentation/configuration/parameters/defaultSchema)
- [Flyway createSchemas parameter](https://flywaydb.org/documentation/configuration/parameters/createSchemas)
- [Testcontainers Configuration of Services Running in Container](https://testcontainers.com/guides/configuration-of-services-running-in-container/) — `withCopyFileToContainer` to `/docker-entrypoint-initdb.d/`
- [Testcontainers Postgres module](https://java.testcontainers.org/modules/databases/postgres/)
- [networknt/json-schema-validator](https://github.com/networknt/json-schema-validator) — version 3.0.2, Java 17+, Jackson required
- [logstash-logback-encoder GitHub](https://github.com/logfellow/logstash-logback-encoder) — version 8.0
- [Maven Central — logstash-logback-encoder 8.0](https://mvnrepository.com/artifact/net.logstash.logback/logstash-logback-encoder/8.0)
- [gitleaks-action repo](https://github.com/gitleaks/gitleaks-action) — `@v2` action + `.gitleaks.toml` auto-detection
- [Spring Cloud Netflix Eureka Client](https://cloud.spring.io/spring-cloud-netflix/multi/multi__service_discovery_eureka_clients.html) — registry-fetch-interval, exponential backoff
- [Spring Cloud Gateway SSE issue #161](https://github.com/spring-cloud/spring-cloud-gateway/issues/161) + [#1550](https://github.com/spring-cloud/spring-cloud-gateway/issues/1550) + [#2275](https://github.com/spring-cloud/spring-cloud-gateway/issues/2275) — body-modifying filters block SSE; route-level `metadata.response-timeout` controls timeout

### Secondary (MEDIUM confidence — verified against official source)
- [Spring Cloud 2025.0.0 announcement blog](https://spring.io/blog/2025/05/29/spring-cloud-2025-0-0-is-abvailable/) — release date corroboration
- [Wick Technology — Distributed API docs aggregation](https://blog.wick.technology/distributed-api-docs/) — Springdoc gateway aggregator pattern
- [Bootiful Builds — Spring Boot Gradle best practices](https://erichaag.dev/posts/bootiful-builds-best-practices-spring-boot-gradle/)
- [Gradle — best practices for dependencies](https://docs.gradle.org/current/userguide/best_practices_dependencies.html)
- [Baeldung — Error Handling with Spring AMQP](https://www.baeldung.com/spring-amqp-error-handling)
- [Initialization strategies with Testcontainers (Rieckpil)](https://rieckpil.de/initialization-strategies-with-testcontainers-for-integration-tests/)

### Tertiary (LOW confidence — flagged for verification)
- Discovery-locator property prefix in 2025.0 Northfields — see Assumption A1; verify at first `:bootRun`.

## 16. Metadata

**Confidence breakdown:**
- Standard stack: HIGH — every version pinned in `libs.versions.toml` is verified against Maven Central and STACK.md.
- Architecture: HIGH — patterns (schema-per-service deny, Declarables topology, correlation MDC, Springdoc aggregator) are all canonical Spring/Postgres idioms with multiple authoritative source citations.
- Pitfalls: HIGH — restated from PITFALLS.md with re-verification; one new pitfall (starter rename) added with Spring Cloud release-notes citation.
- Property prefix for discovery locator in 2025.0: MEDIUM — release notes table is unambiguous about the global rename but doesn't enumerate every sub-property; flagged as A1.
- RetryInterceptorBuilder semantics for "1s/5s/30s": MEDIUM — the literal sequence requires a 4-attempt configuration; the practical interpretation for `maxAttempts(3)` differs; flagged as A2.

**Research date:** 2026-04-28
**Valid until:** 2026-05-28 (30 days for stable Spring framework versions; re-verify discovery-locator prefix at first :bootRun regardless)

---

*Research for Phase 1: Foundations + Day-1 Contracts of n11-clone bootcamp final case*
