# Phase 1: Foundations + Day-1 Contracts - Pattern Map

**Mapped:** 2026-04-28
**Files analyzed:** 47 (new files; greenfield phase)
**Analogs found:** 0 in-repo / 47 — **expected for greenfield Phase 1**
**External references:** 100% (every analog points to an official doc page or canonical sample)

---

## Greenfield Notice (READ FIRST)

This is a **greenfield repository.** At pattern-mapping time the only files in the working tree are:

- `CLAUDE.md` (project instructions)
- `REQUIREMENTS-n11.md` (bootcamp brief)
- `.gitignore` (6 bytes — only `.*` exclusion)
- `.planning/` tree (PROJECT.md, REQUIREMENTS.md, ROADMAP.md, STATE.md, config.json, research/, phases/)
- `.git/`

There is **no `src/`, no Gradle build, no Spring code, no docker-compose, no infra/, no contracts.** Every file this phase produces is genuinely new.

**Consequence for the planner:**
1. There are zero in-repo analogs to copy from. The `## Pattern Assignments` section below cites **external references only** — official Spring docs, official Postgres docker entrypoint scripts, GitHub samples, and the verified code excerpts that already live in `01-RESEARCH.md`.
2. The single most-leveraged in-repo artifact is **`.planning/research/ARCHITECTURE.md` §2 + §3** — that file is the source-of-truth for `api-contracts.md` (§2 per-service tables) and `saga-contracts.md` (§3 happy/compensation paths + §3.4 event payloads). Treat ARCHITECTURE.md as the "analog" for the contract docs even though it isn't code.
3. The planner should NOT do net-new research. RESEARCH.md §4.1–§4.12 already extracted verified config snippets, dependency coordinates, and gotchas. This pattern map points each new file at the relevant RESEARCH.md section so plans cite them rather than re-deriving them.
4. After Phase 1 ships, **its outputs become the canonical analogs** for every later phase. The "Reusable Outputs (analogs for Phase 2+)" section at the bottom enumerates those forward-references so future phases close the loop.

---

## File Classification

Phase 1 deliverables grouped by role × data-flow. All cited line numbers are **`01-RESEARCH.md` line numbers** (the planner's primary research source) unless otherwise noted.

### Build & Tooling (config role)

| New File | Role | Data Flow | Closest External Reference | Match Quality | Research Section |
|----------|------|-----------|----------------------------|---------------|------------------|
| `settings.gradle.kts` | config | build-graph | Spring Initializr Gradle Kotlin layout + Gradle docs `libs.versions.toml` | exact | RESEARCH §4.11 (lines 901–923) |
| `build.gradle.kts` (root) | config | build-graph | Spring Boot 3.5 + Spring Cloud BOM `subprojects {}` template | exact | RESEARCH §4.11 (lines 925–962) |
| `gradle/libs.versions.toml` | config | build-graph | Gradle version-catalogs docs; Spring Initializr libs.versions.toml issue #1600 | exact | RESEARCH §3 (lines 173–193) |
| `eureka-server/build.gradle.kts` | config | build-graph | Spring Cloud Netflix Eureka Server starter | exact | RESEARCH §3 + §4.11 (lines 964–976) |
| `config-server/build.gradle.kts` | config | build-graph | Spring Cloud Config Server starter | exact | RESEARCH §4.2 |
| `api-gateway/build.gradle.kts` | config | build-graph | Spring Cloud Gateway 2025.0 (Northfields) — **NEW starter `spring-cloud-starter-gateway-server-webflux`** | exact | RESEARCH §4.1 + §3 |
| `service-template/build.gradle.kts` | config | build-graph | Boot 3.5 web app template | role-match | RESEARCH §4.11 (lines 992 forward) |
| `common-error/build.gradle.kts` | config | build-graph | Plain Java library (no Boot plugin) — RESEARCH-cited recipe | exact | RESEARCH §4.11 (lines 978–990) |
| `common-logging/build.gradle.kts` | config | build-graph | Plain Java library | exact | RESEARCH §4.11 (lines 978–990) |
| `common-events/build.gradle.kts` | config | build-graph | Plain Java library + networknt/json-schema-validator | exact | RESEARCH §3 (line 192) + §4.11 |
| `infra-tests/build.gradle.kts` | config | build-graph | Testcontainers JUnit 5 module | exact | RESEARCH §4.7 |
| `gradlew` + `gradlew.bat` + `gradle/wrapper/*` | tooling | build-graph | `gradle wrapper --gradle-version 8.10` (Gradle CLI generated) | exact | RESEARCH §12 (line 1392) |

### Infrastructure (infra role)

| New File | Role | Data Flow | Closest External Reference | Match Quality | Research Section |
|----------|------|-----------|----------------------------|---------------|------------------|
| `infra/postgres/init.sh` | infra (DDL bootstrap) | one-shot batch | docker-library/postgres `docker-entrypoint.sh` README pattern; pgvector/pgvector:pg16 README | **exact** | RESEARCH §4.4 (lines 393–428) |
| `docker-compose.yml` | infra (orchestration) | service-graph | RESEARCH-cited compose snippet + healthcheck pattern | exact | RESEARCH §4.4 (lines 432–453) + §13 (compose layout) |
| `.env.example` | infra (secrets) | env-injection | docker-compose env-file convention; lock list per CD-04 | exact | RESEARCH §4.12 + §14 (V14.1) |

### Spring Boot Application Modules (source role)

| New File | Role | Data Flow | Closest External Reference | Match Quality | Research Section |
|----------|------|-----------|----------------------------|---------------|------------------|
| `eureka-server/src/main/java/com/n11/eureka/EurekaApplication.java` | source (boot main) | request-response | Spring Cloud Netflix Eureka Server quickstart | exact | RESEARCH §4.3 (lines 363–378) |
| `eureka-server/src/main/resources/application.yml` | config | request-response | Eureka server config — `register-with-eureka:false`, `enable-self-preservation:false` | exact | RESEARCH §4.3 (lines 363–378) |
| `config-server/src/main/java/com/n11/config/ConfigServerApplication.java` | source (boot main) | request-response | Spring Cloud Config Server quickstart with `@EnableConfigServer` | exact | RESEARCH §4.2 |
| `config-server/src/main/resources/application.yml` | config | filesystem | Spring Cloud Config native profile — `search-locations: classpath:/config/` | exact | RESEARCH §4.2 (lines 282–295) + Code Examples §8 (lines 1300–1316) |
| `config-server/src/main/resources/config/application.yml` | config (shared service config) | filesystem | Spring Cloud Config "application name resolution" pattern | exact | RESEARCH §4.2 (lines 297–308) |
| `config-server/src/main/resources/config/api-gateway.yml` | config (gateway-specific) | filesystem | Spring Cloud Gateway 2025.0 discovery-locator + Springdoc aggregator combined | role-match | RESEARCH §4.1 (lines 234–266) + §4.8 (lines 676–692) + §4.10 (lines 871–893) |
| `config-server/src/main/resources/config/service-template.yml` | config (baseline) | filesystem | Aggregated baseline: Eureka client + Flyway + Springdoc + actuator | composite | RESEARCH §4.3 + §4.5 + §4.8 + §4.9 |
| `api-gateway/src/main/java/com/n11/gateway/ApiGatewayApplication.java` | source (boot main) | streaming-passthrough | Spring Cloud Gateway 2025.0 reactive bootstrap | exact | RESEARCH §4.1 |
| `api-gateway/src/main/java/com/n11/gateway/GatewayCorrelationIdFilter.java` | source (GlobalFilter) | request-response | Reactive `GlobalFilter` + Reactor Context propagation (Spring Cloud Gateway docs) | exact | RESEARCH §4.9 (lines 838–857) |
| `api-gateway/src/main/java/com/n11/gateway/GatewayHeaderInjectionFilter.java` | source (GlobalFilter, no-op stub) | request-response | "Securing Services with Spring Cloud Gateway" 2019 Spring blog (referenced in ARCHITECTURE.md §5) | role-match | ARCHITECTURE.md §5 (lines 102–112) — **stub-only in Phase 1; flips on in Phase 3** |
| `api-gateway/src/main/resources/application.yml` | config | filesystem | Bootstrap-only: `spring.application.name`, `spring.config.import` | exact | RESEARCH §8 Code Examples (lines 1272–1294) |
| `service-template/src/main/java/com/n11/template/TemplateApplication.java` | source (boot main archetype) | request-response | Spring Boot 3.5 web app skeleton with Eureka client | exact | RESEARCH §4.11 (line 1097–1102) |
| `service-template/src/main/resources/application.yml` | config (baseline) | filesystem | Eureka client + Flyway placeholder pattern + Springdoc | composite | RESEARCH §4.3 (lines 343–360) + §4.5 (lines 514–525) + §4.8 (lines 696–702) |
| `service-template/src/main/resources/logback-spring.xml` | config | structured-logs | logstash-logback-encoder 8.0 LogstashEncoder XML | exact | RESEARCH §4.9 (lines 723–753) |
| `service-template/src/main/resources/db/migration/V1__init_processed_events.sql` | source (DDL) | one-shot batch | RESEARCH-cited inbox-table DDL (saga consumer idempotency) | exact | RESEARCH §4.5 (lines 545–556) |
| `service-template/skeleton/Application.java.template` | docs (copy-paste template) | n/a | CD-02 hybrid pattern — "subproject + skeleton/ dir" | role-match | RESEARCH §4.11 (line 1103–1106) + Open Question #4 (line 1374) |
| `service-template/skeleton/application.yml.template` | docs (copy-paste template) | n/a | Mirror of `service-template/src/main/resources/application.yml` | exact | RESEARCH §4.11 |
| `service-template/skeleton/README.md` | docs | n/a | "How to clone this for a new service" — phase-3-onwards instruction sheet | role-match | RESEARCH §4.11 (line 1106) |

### Shared Library Modules (utility role)

| New File | Role | Data Flow | Closest External Reference | Match Quality | Research Section |
|----------|------|-----------|----------------------------|---------------|------------------|
| `common-error/src/main/java/com/n11/error/ProblemDetailControllerAdvice.java` | source (utility) | request-response | Spring 6.1 `ProblemDetail` + `@ControllerAdvice` + `ErrorResponseException` (Boot 3.x) | exact | ARCHITECTURE.md §8.2 (lines 657–668) + RESEARCH §6 (line 1201) |
| `common-error/src/main/java/com/n11/error/ApiErrorCode.java` | source (utility enum) | n/a | RFC-7807 `type` URI namespace pattern | role-match | RFC-7807 normative spec; RESEARCH §14 (line 1473) |
| `common-error/src/test/java/com/n11/error/ProblemDetailMapperTest.java` | test | unit | Spring `WebMvcTest` + `MockMvc` ProblemDetail assertion | exact | RESEARCH §13 (line 1431) |
| `common-logging/src/main/java/com/n11/logging/CorrelationIdFilter.java` | source (servlet filter) | request-response | `OncePerRequestFilter` + MDC pattern | **exact** | RESEARCH §4.9 (lines 758–774) |
| `common-logging/src/main/java/com/n11/logging/CorrelationIdRestClientInterceptor.java` | source (HTTP interceptor) | request-response | Spring `ClientHttpRequestInterceptor` + MDC propagation | exact | RESEARCH §4.9 (lines 781–797) |
| `common-logging/src/main/java/com/n11/logging/CorrelationIdMessagePostProcessor.java` | source (AMQP MPP) | event-driven | Spring AMQP `MessagePostProcessor` + `RabbitTemplate.setBeforePublishPostProcessors` | exact | RESEARCH §4.9 (lines 802–820) |
| `common-logging/src/main/java/com/n11/logging/RabbitListenerCorrelationAspect.java` | source (AOP aspect) | event-driven | Spring AOP `@Around` + RabbitListener message-headers extraction | role-match | RESEARCH §4.9 (lines 826–834) — **stub-only sketch in research; planner fleshes out** |
| `common-logging/src/main/java/com/n11/logging/RestClientConfig.java` | source (auto-config) | request-response | `@Configuration` + `RestClient.Builder` bean | exact | RESEARCH §4.9 (lines 791–797) |
| `common-logging/src/main/java/com/n11/logging/RabbitTemplateConfig.java` | source (auto-config) | event-driven | `@Configuration` + `RabbitTemplate` bean | exact | RESEARCH §4.9 (lines 813–820) |
| `common-logging/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` | config (auto-config registration) | n/a | Spring Boot 3 auto-configuration registration mechanism | exact | Spring Boot 3.x reference docs |
| `common-logging/src/test/java/com/n11/logging/CorrelationIdFilterTest.java` | test | unit | Servlet filter unit-test pattern (`MockHttpServletRequest`) | exact | — |
| `common-events/src/main/java/com/n11/events/Envelope.java` | source (DTO) | event-driven | Saga envelope record per ARCHITECTURE.md §3.4 | **exact** | ARCHITECTURE.md §3.4 (lines 392–405) |
| `common-events/src/main/java/com/n11/events/Declarables*.java` (Spring AMQP topology beans) | source (config skeleton) | event-driven | Spring AMQP `Declarables` aggregator pattern | exact | RESEARCH §4.6 (lines 602–632) |
| `common-events/src/main/java/com/n11/events/RabbitRetryConfig.java` | source (retry config) | event-driven | `RetryInterceptorBuilder.stateful()` + `RejectAndDontRequeueRecoverer` | exact | RESEARCH §4.6 (lines 569–595) |
| `common-events/src/main/resources/saga-schemas/*.schema.json` | contract (resource files) | n/a | Copy-of: `.planning/saga-contracts/*.schema.json` (classpath-mounted for AbstractEventSchemaTest) | exact | RESEARCH §4.6 (line 638) + D-08 |
| `common-events/src/main/java/com/n11/events/AbstractEventSchemaTest.java` | test (abstract base) | n/a | networknt/json-schema-validator 3.0.2 + JUnit 5 abstract test | **exact** | RESEARCH §3 (line 192) + RESEARCH §6 (line 1199) + D-08 |
| `common-events/src/test/java/com/n11/events/DeclarablesConfigTest.java` | test | unit | Spring `@SpringBootTest(classes=...)` for Declarables verification | exact | RESEARCH §13 (line 1424) |

### Boundary Smoke Test (test role — D-05)

| New File | Role | Data Flow | Closest External Reference | Match Quality | Research Section |
|----------|------|-----------|----------------------------|---------------|------------------|
| `infra-tests/src/test/java/com/n11/infra/CrossSchemaDenyTest.java` | test (Testcontainers) | one-shot batch | Testcontainers Postgres `withCopyFileToContainer` + `withEnv` | **exact** | RESEARCH §4.4 (lines 466–500) + §4.7 (lines 654–664) |
| `infra-tests/src/test/resources/init.sh` (copy/symlink of `infra/postgres/init.sh`) | infra | one-shot batch | Same as `infra/postgres/init.sh` | exact | RESEARCH §4.4 |

### Day-1 Contracts (docs role — D-06, D-07, D-09)

| New File | Role | Data Flow | Closest External Reference | Match Quality | Research Section |
|----------|------|-----------|----------------------------|---------------|------------------|
| `.planning/saga-contracts.md` | docs (contract narrative) | n/a | **`.planning/research/ARCHITECTURE.md` §3 (full saga walkthrough) + §3.4 (event payload schemas) + §3.5 (idempotency/retries/DLQ)** | **exact** | ARCHITECTURE.md §3 (lines 241–443) + RESEARCH §4.6 (DLQ retry policy) + Pitfall 6 (line 1250) |
| `.planning/saga-contracts/envelope.schema.json` | contract (JSON Schema 2020-12) | n/a | ARCHITECTURE.md §3.4 envelope JSON literal — translate to JSON Schema | **exact** | ARCHITECTURE.md §3.4 (lines 394–405) |
| `.planning/saga-contracts/order-created.schema.json` | contract | n/a | ARCHITECTURE.md §3.4 `order.created` payload | **exact** | ARCHITECTURE.md §3.4 (lines 409–419) |
| `.planning/saga-contracts/stock-reserved.schema.json` | contract | n/a | ARCHITECTURE.md §3.4 `stock.reserved` payload | **exact** | ARCHITECTURE.md §3.4 (line 421) |
| `.planning/saga-contracts/stock-reserve-failed.schema.json` | contract | n/a | ARCHITECTURE.md §3.4 `stock.reserve_failed` payload | **exact** | ARCHITECTURE.md §3.4 (line 423) |
| `.planning/saga-contracts/payment-completed.schema.json` | contract | n/a | ARCHITECTURE.md §3.4 `payment.completed` payload | **exact** | ARCHITECTURE.md §3.4 (line 425) |
| `.planning/saga-contracts/payment-failed.schema.json` | contract | n/a | ARCHITECTURE.md §3.4 `payment.failed` payload | **exact** | ARCHITECTURE.md §3.4 (line 427) |
| `.planning/saga-contracts/order-confirmed.schema.json` | contract | n/a | ARCHITECTURE.md §3.4 `order.confirmed` payload | **exact** | ARCHITECTURE.md §3.4 (line 429) |
| `.planning/saga-contracts/order-cancelled.schema.json` | contract | n/a | ARCHITECTURE.md §3.4 `order.cancelled` payload | **exact** | ARCHITECTURE.md §3.4 (line 431) |
| `.planning/saga-contracts/stock-released.schema.json` | contract | n/a | ARCHITECTURE.md §3.4 `stock.released` payload | **exact** | ARCHITECTURE.md §3.4 (line 433) |
| `.planning/api-contracts.md` | docs (contract narrative) | n/a | **`.planning/research/ARCHITECTURE.md` §2 (per-service contracts) + §5 (gateway responsibilities) + §8 (cross-cutting concerns)** | **exact** | ARCHITECTURE.md §2 (lines 66–238) + §5 (lines 487–499) + §8 (lines 649–687) |

### CI / Repo Hygiene (config role)

| New File | Role | Data Flow | Closest External Reference | Match Quality | Research Section |
|----------|------|-----------|----------------------------|---------------|------------------|
| `.github/workflows/ci.yml` | config (CI) | event-driven | Standard Gradle CI pattern: `actions/setup-java@v4` (Corretto 21) + `./gradlew build test` | exact | RESEARCH §13 (line 1432) + §12 (line 1398) |
| `.github/workflows/security.yml` | config (CI) | event-driven | `gitleaks/gitleaks-action@v2` reference workflow | **exact** | RESEARCH §4.12 (lines 1003–1023) |
| `.gitleaks.toml` | config | n/a | gitleaks default ruleset extension + Spring placeholder allowlist | **exact** | RESEARCH §4.12 (lines 1027–1036) |
| `.gitignore` (extension) | config | n/a | CD-04 lock list: `.env`, `secrets/`, `application-local.yml`, `*.pem`, `*.key` | exact | RESEARCH §4.12 (lines 1040–1048) |

---

## Pattern Assignments

Per-file concrete excerpts. **All excerpts below are pre-extracted in `01-RESEARCH.md`** — line ranges are cited so the planner can copy them into PLAN.md actions verbatim without re-reading external docs.

### `infra/postgres/init.sh` (infra DDL, **D-03 / ARCH-09**)

**Reference:** [docker-library/postgres entrypoint](https://github.com/docker-library/postgres/blob/master/docker-entrypoint.sh) + RESEARCH §4.4

**Why .sh not .sql:** Pure `.sql` files in `/docker-entrypoint-initdb.d/` cannot interpolate env vars; only shell scripts can (verified, RESEARCH lines 389–390). This is the reason the file is `.sh` despite the user's CONTEXT.md original naming.

**Copy-pasteable skeleton (RESEARCH §4.4 lines 393–428):**
```bash
#!/usr/bin/env bash
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
  CREATE EXTENSION IF NOT EXISTS vector;

  CREATE SCHEMA IF NOT EXISTS identity;
  CREATE USER identity_user WITH PASSWORD '${IDENTITY_DB_PASSWORD}';
  ALTER SCHEMA identity OWNER TO identity_user;
  GRANT USAGE ON SCHEMA public TO identity_user;
  ALTER USER identity_user SET search_path = identity, public;

  -- repeat block for: product, inventory, cart, orders, payment, notification, search, ai, mcp
  -- NOTE: rename "order" → "orders" — SQL reserved word (RESEARCH Pitfall 7, line 1254)

  REVOKE USAGE ON SCHEMA identity FROM product_user, inventory_user, cart_user, orders_user,
    payment_user, notification_user, search_user, ai_user, mcp_user;
  -- ...one REVOKE block per schema (or DO loop)
EOSQL
```

**Planner action items flagged:**
- A3 (line 1350): default schema name `orders` (plural) instead of reserved `order` — surface in PLAN.md
- A6 (line 1353): verify env-var interpolation works through `withCopyFileToContainer` — first thing the smoke test proves

---

### `docker-compose.yml` (infra orchestration, **D-13**)

**Reference:** RESEARCH §4.4 lines 432–453 (compose snippet for postgres+pgvector with healthcheck and init.sh mount).

**Phase 1 services (infra-only):** `postgres`, `rabbitmq`, `eureka-server`, `config-server`, `api-gateway`. **No business services in Phase 1** — they run from IDE/`bootRun`.

**Image pinning:**
- `pgvector/pgvector:pg16` (NOT plain `postgres:16` — saves the `CREATE EXTENSION` step; RESEARCH line 454)
- `rabbitmq:4.3-management` (RabbitMQ 4.x current; STACK-verified)

**Healthchecks required for ARCH-11 (Eureka cold-boot):** `pg_isready` for postgres, `rabbitmq-diagnostics ping` for rabbitmq, `/actuator/health` HTTP for the three Spring services.

**Env-var sourcing:** `.env` file (gitignored per CD-04); `.env.example` ships with placeholder values.

---

### `eureka-server/` (Spring Boot module, infra service)

**Reference:** RESEARCH §4.3 lines 363–378 (eureka-server config), Spring Cloud Netflix Eureka Server quickstart.

**Application class pattern:**
```java
@SpringBootApplication
@EnableEurekaServer
public class EurekaApplication {
    public static void main(String[] args) { SpringApplication.run(EurekaApplication.class, args); }
}
```

**`application.yml` excerpt (RESEARCH lines 363–378):**
```yaml
server: { port: 8761 }
spring: { application: { name: eureka-server } }
eureka:
  client: { register-with-eureka: false, fetch-registry: false }
  server:
    enable-self-preservation: false
    response-cache-update-interval-ms: 5000
    eviction-interval-timer-in-ms: 5000
```

---

### `config-server/` (Spring Boot module, infra service, **CD-05**)

**Reference:** RESEARCH §4.2 (lines 277–335), Spring Cloud Config docs.

**Application class:** `@SpringBootApplication @EnableConfigServer`.

**`application.yml` (RESEARCH §4.2 lines 282–295):**
```yaml
server: { port: 8888 }
spring:
  application: { name: config-server }
  profiles: { active: native }
  cloud:
    config:
      server:
        native:
          search-locations: classpath:/config/
```

**Config repo layout (CD-05) under `src/main/resources/config/`:**
- `application.yml` — shared (Eureka URL, default datasource pattern, common Springdoc keys)
- `api-gateway.yml` — gateway-specific (discovery locator, Springdoc aggregator stub with `urls: []`, SSE caveat comments)
- `service-template.yml` — baseline business-service config

**Phase 1 ships only the three files above.** Each later phase appends `<svc>-service.yml` next to them.

---

### `api-gateway/` (Spring Boot reactive module, **D-14, ARCH-04**)

**Reference:** RESEARCH §4.1 (lines 205–276), Spring Cloud 2025.0 Release Notes.

**HEADLINE:** Use the **NEW** Northfields starter coordinate:
```kotlin
implementation("org.springframework.cloud:spring-cloud-starter-gateway-server-webflux")
runtimeOnly("org.springframework.boot:spring-boot-properties-migrator")
```

**Property prefix shift (RESEARCH lines 217–219):** `spring.cloud.gateway.*` → `spring.cloud.gateway.server.webflux.*`. Have `spring-boot-properties-migrator` on the classpath as the bridge.

**Pitfall #2 lockdown (RESEARCH lines 221–229; ARCHITECTURE Pitfall 2):** The api-gateway module MUST exclude Tomcat:
```kotlin
configurations.all {
    exclude(group = "org.springframework.boot", module = "spring-boot-starter-web")
    exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
}
```

**`config-server/.../config/api-gateway.yml` excerpt (RESEARCH §4.1 lines 233–266):**
```yaml
spring:
  cloud:
    gateway:
      server:
        webflux:
          discovery:
            locator:
              enabled: true
              lower-case-service-id: true
          httpclient:
            response-timeout: 60s   # global default; SSE routes will override per-route in Phase 8
management:
  endpoints: { web: { exposure: { include: 'health,info,gateway' } } }
  endpoint: { gateway: { access: read_only } }
```

**SSE forward-compat scaffolding (RESEARCH §4.10 lines 871–893):** add commented-out SSE route shape in `api-gateway.yml` so Phase 8 has a known anchor point. Document in `.planning/api-contracts.md`: "SSE routes through gateway must have `metadata.response-timeout: -1` and no body-modifying filters."

**Per-spec Phase 1 posture (D-14):**
- `permitAll()` security chain — coded but NOT enforcing JWT (Phase 3 flips it)
- `/actuator/gateway/routes` exposed (success criterion #1)
- `springdoc.swagger-ui.urls=[]` empty (Phase 4+ phases append)
- 503 on unmatched paths

---

### `service-template/` (archetype subproject, **D-10, D-11, CD-02**)

**Reference:** RESEARCH §4.11 (lines 992–1106), CD-02 hybrid recommendation.

**CD-02 final pick (per RESEARCH line 992 + Open Question #4):** **hybrid** — `service-template/` IS a runnable Gradle subproject with all four cross-cutting wires (so it boots and proves the wiring works), AND it ships a `service-template/skeleton/` directory with `.template`-suffixed copies of `Application.java`, `application.yml`, `logback-spring.xml`, `db/migration/V1__init.sql`, plus a `README.md` documenting "to scaffold a new service: copy `skeleton/` into a sibling directory, rename, edit `application.yml` placeholders." **Surface trade-off explicitly in PLAN.md** per CD-02 lock.

**Four wires (D-11):**
1. **Eureka client + Springdoc + actuator** — RESEARCH §3 stack table, §4.3 client config, §4.8 Springdoc.
2. **logstash-logback-encoder JSON + correlationId MDC** — RESEARCH §4.9 lines 723–753.
3. **`common-error` ControllerAdvice dependency** — RESEARCH §6 line 1201.
4. **Flyway 12.5 wired to `processed_events` migration** — RESEARCH §4.5 lines 514–556.

**`service-template.yml` (in config-server) excerpt:**
```yaml
spring:
  application:
    name: ${service.name:service-template}
  flyway:
    enabled: true
    locations: classpath:db/migration
    schemas: ${flyway.schema:identity}
    default-schema: ${flyway.schema:identity}
    create-schemas: false
  datasource:
    url: jdbc:postgresql://postgres:5432/n11
    username: ${db.user}
    password: ${db.password}
eureka:
  client:
    service-url: { defaultZone: http://eureka-server:8761/eureka/ }
    registry-fetch-interval-seconds: 5
  instance: { lease-renewal-interval-in-seconds: 10, prefer-ip-address: true }
springdoc:
  api-docs: { path: /v3/api-docs }
  swagger-ui: { path: /swagger-ui.html }
```

---

### `common-error/` (utility module, **D-09 RFC-7807, QUAL-07**)

**Reference:** ARCHITECTURE.md §8.2 (lines 657–668), Spring `ProblemDetail` reference.

**Locked field set (D-09):** `type`, `title`, `status`, `detail`, `instance`, `correlationId`, `errors[]` (validation).

**`@ControllerAdvice` skeleton:**
```java
@RestControllerAdvice
public class ProblemDetailControllerAdvice {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setType(URI.create("https://n11clone/errors/validation"));
        pd.setTitle("Validation failed");
        pd.setDetail("One or more fields failed validation");
        pd.setInstance(URI.create(req.getRequestURI()));
        pd.setProperty("correlationId", MDC.get("correlationId"));
        pd.setProperty("errors", ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> Map.of("field", fe.getField(), "message", fe.getDefaultMessage())).toList());
        return pd;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex, HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        pd.setType(URI.create("https://n11clone/errors/internal"));
        pd.setTitle("Internal server error");
        // SECURITY: do NOT echo ex.getMessage() — RESEARCH §14 V7 anti-leak rule
        pd.setDetail("An unexpected error occurred");
        pd.setInstance(URI.create(req.getRequestURI()));
        pd.setProperty("correlationId", MDC.get("correlationId"));
        return pd;
    }
}
```

**4–5 example error responses required by D-09** — included in `.planning/api-contracts.md` (NOT in code).

---

### `common-logging/` (utility module, **D-09 correlation-ID, QUAL-06**)

**Reference:** RESEARCH §4.9 (lines 712–857) — has all five components verbatim.

**Five wires:**
1. `CorrelationIdFilter extends OncePerRequestFilter` (servlet inbound) — RESEARCH lines 758–774.
2. `CorrelationIdRestClientInterceptor implements ClientHttpRequestInterceptor` (HTTP outbound) — RESEARCH lines 781–789.
3. `RestClientConfig @Configuration` — registers the interceptor on auto-injected `RestClient.Builder` — RESEARCH lines 791–797.
4. `CorrelationIdMessagePostProcessor implements MessagePostProcessor` + `RabbitTemplateConfig` — RESEARCH lines 802–820.
5. `RabbitListenerCorrelationAspect` (AOP `@Around` for inbound RabbitListener) — RESEARCH lines 826–834 (sketch only; planner fills body).

**Auto-config registration:**
File: `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
Content: `com.n11.logging.RestClientConfig\ncom.n11.logging.RabbitTemplateConfig\ncom.n11.logging.CorrelationIdFilter`
(Spring Boot 3 mechanism — not `spring.factories`.)

---

### `common-events/` (utility module, **D-06, D-08, ARCH-05**)

**Reference:** RESEARCH §4.6 (lines 565–642) + §3 (line 192 networknt coord).

**Three components:**
1. **`Envelope` record** — neutral DTO mirroring ARCHITECTURE.md §3.4 (lines 392–405). Generic `payload` of type `JsonNode` so consumers don't pre-bind.
2. **`RabbitRetryConfig`** — RESEARCH §4.6 lines 569–595.
3. **`AbstractEventSchemaTest`** — base class loading `saga-schemas/<eventType>.schema.json` from classpath via networknt 3.0.2:
```java
public abstract class AbstractEventSchemaTest {
    private static final ObjectMapper OM = new ObjectMapper();
    private static final JsonSchemaFactory FACTORY =
        JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

    protected void assertEventValid(String eventType, JsonNode produced) {
        InputStream stream = getClass().getResourceAsStream("/saga-schemas/" + eventType + ".schema.json");
        if (stream == null) fail("Schema not found for eventType=" + eventType);
        JsonSchema schema = FACTORY.getSchema(stream);
        Set<ValidationMessage> errors = schema.validate(produced);
        assertThat(errors).isEmpty();
    }
}
```
**JSON-Schema files MUST be copied** from `.planning/saga-contracts/` into `common-events/src/main/resources/saga-schemas/` so they ship on the classpath. Phase 1 includes both copies; PLAN.md should tag this as "two-locations, single-source-of-truth in `.planning/`."

**Pitfall 6 wording lock (RESEARCH line 1250):** in `saga-contracts.md` write "**3 total attempts (= 1 try + 2 retries), delays 1s and 5s between attempts, then DLQ. The 30s upper bound is a safety cap on the exponential growth, not a delay between attempts 3 and 4 (there is no attempt 4).**"

---

### `infra-tests/CrossSchemaDenyTest.java` (test, **D-05**)

**Reference:** RESEARCH §4.4 lines 466–500 + §4.7 lines 645–664.

**Pre-extracted test class skeleton** (RESEARCH lines 466–498) — copy directly. Two key idioms:
- `withCopyFileToContainer(MountableFile.forClasspathResource("init.sh"), "/docker-entrypoint-initdb.d/00-init.sh")`
- `withEnv("IDENTITY_DB_PASSWORD", "test-identity")` for each of the 13 schemas

**Assertions:** open `product_user` connection, execute `SELECT 1 FROM identity.users`, assert `SQLException` with message containing `"permission denied for schema identity"`. Optionally enumerate all 13×12 = 156 cross-schema deny pairs as a parameterized test (RESEARCH line 663 — "use `@Container` static field for singleton container reuse").

---

### `.planning/saga-contracts.md` + `.planning/saga-contracts/*.schema.json` (contract docs, **D-06**)

**Reference:** **`.planning/research/ARCHITECTURE.md` §3 (entire section, lines 241–443).** This is the canonical analog — the planner formalizes the research narrative into a contract doc, NOT re-researches it.

**Doc structure (D-06 specifies):**
1. Envelope schema (8-field shape per ARCHITECTURE §3.4 lines 392–405)
2. Exchange/queue topology table (ARCHITECTURE §3.1 lines 245–271)
3. Idempotency rules (transactional outbox + processed_events inbox; ARCHITECTURE §3.5 lines 437–443)
4. Retry policy (per RESEARCH §4.6 + Pitfall 6 wording lock above)
5. DLQ naming convention (`<exchange>.dlx` / `<queue>.dlq`)
6. Manual replay convention note (deferred to Phase 11 hardening)

**JSON-Schema files** — one per event in `.planning/saga-contracts/`:
- `envelope.schema.json` — required: `eventId`, `eventType`, `eventVersion`, `occurredAt`, `correlationId`, `producer`, `payload`. Optional: `causationId`.
- 8 payload schemas — translate the inline ARCHITECTURE.md §3.4 JSON examples into JSON Schema 2020-12 (`$schema: "https://json-schema.org/draft/2020-12/schema"`).

---

### `.planning/api-contracts.md` (contract docs, **D-07, D-09**)

**Reference:** **`.planning/research/ARCHITECTURE.md` §2 (per-service contracts, lines 66–238) + §5 (gateway responsibilities, lines 487–499) + §8 (cross-cutting concerns, lines 649–687).**

**Doc structure (D-07 + D-09 specifies):**
1. **Per-service endpoint table** — extract from ARCHITECTURE §2.4–§2.13 (each "REST API" row).
2. **Gateway routing prefix map** — `/api/v1/<svc>/**` → service-id; ARCHITECTURE §2.3 (line 98) is the literal source.
3. **Public allowlist** (D-09) — `POST /auth/login`, `POST /auth/register`, `GET /products/**`, `GET /search/**`, `POST /chat/**`.
4. **Authorization-strip rule** — gateway strips `Authorization` header before forwarding (ARCHITECTURE §2.3 line 107 + §10 anti-pattern 4).
5. **Correlation-ID propagation policy** — gateway generates if absent; servlet filter at every service reads to MDC; outbound RestClient + RabbitTemplate inject; AMQP envelope carries `correlationId` (already in saga envelope D-06).
6. **RFC-7807 problem+json shape spec** — fields (`type`, `title`, `status`, `detail`, `instance`, `correlationId`, `errors[]`) + 4–5 worked example responses (validation 400, not-found 404, conflict 409, internal 500, optional unauthorized 401).
7. **SSE caveat for Phase 8** — "routes via gateway need `metadata.response-timeout: -1` and no body-modifying filters" (RESEARCH §4.10).

---

### `.github/workflows/security.yml` + `.gitleaks.toml` (CI, **CD-04, QUAL-09**)

**Reference:** RESEARCH §4.12 lines 1003–1048 — has both files verbatim.

Copy directly. The only Phase-1 customization is the allowlist regex for Spring `${ENV_VAR}` placeholders (RESEARCH lines 1033–1035) so the gitleaks scan doesn't flag intentional env-var references in `application.yml` files.

**Personal repo (per current state):** no `GITLEAKS_LICENSE` env var needed. RESEARCH Open Question #5 (lines 1378–1381) flags the org-fork wrinkle for the bootcamp coordinator — non-blocking for Phase 1.

---

### `.github/workflows/ci.yml` (CI build/test)

**Reference:** Standard Gradle CI pattern — `actions/setup-java@v4` (Corretto 21) + `./gradlew build test`.

**Java 21 setup (RESEARCH §12 line 1392):**
```yaml
- uses: actions/setup-java@v4
  with: { distribution: 'corretto', java-version: '21' }
- run: ./gradlew build test
```

**Phase 1 jobs:**
1. `build` — compile + unit tests across all modules
2. `infra-tests` — Testcontainers cross-schema deny smoke (requires Docker on the runner; default GitHub-hosted runners support this)

---

## Shared Patterns

These cross-cutting patterns apply to **every** Phase 1 module. Each plan referencing a Spring Boot module should re-cite these.

### Cross-Cutting #1: Library-vs-Boot-app Gradle plugin discipline

**Source:** RESEARCH §4.11 lines 978–990.
**Apply to:** Every `build.gradle.kts` in the repo.

| Module type | Gradle plugins applied |
|-------------|------------------------|
| **Boot app** (`eureka-server`, `config-server`, `api-gateway`, `service-template`) | `org.springframework.boot` + `io.spring.dependency-management` |
| **Library** (`common-error`, `common-logging`, `common-events`) | NO Boot plugin — plain `java` only. Use `api()` for re-exported deps. |
| **Test-only** (`infra-tests`) | NO Boot plugin — plain `java` + `testImplementation` for Testcontainers/JUnit. |

**Anti-pattern (RESEARCH line 1183):** applying the Boot plugin to library modules creates an executable JAR with a fake `main` class — fails build.

---

### Cross-Cutting #2: Spring Boot 3.x `spring.config.import` (NOT bootstrap.yml)

**Source:** RESEARCH §4.2 lines 312–321 + §9 line 1329 + Pitfall 8 (line 1258).
**Apply to:** Every Boot app's `src/main/resources/application.yml`.

```yaml
spring:
  application:
    name: <module-name>
  config:
    import: configserver:http://config-server:8888?fail-fast=true&max-attempts=10&max-interval=1500&multiplier=1.2&initial-interval=1100
```

**Do NOT** ship a `bootstrap.yml` and do NOT add `spring-cloud-starter-bootstrap`. Boot 3.x dropped the bootstrap context as default.

---

### Cross-Cutting #3: Eureka client cold-boot resilience (ARCH-11)

**Source:** RESEARCH §4.3 lines 343–360.
**Apply to:** Every Boot app's effective config (via `service-template.yml` baseline OR per-app override).

Excerpt above in §`service-template/` — single canonical block.

---

### Cross-Cutting #4: Correlation-ID propagation (D-09 / QUAL-06)

**Source:** RESEARCH §4.9 lines 712–857.
**Apply to:** Every Boot app via dependency on `common-logging` module.

- **Inbound:** servlet filter (or `WebFilter` on gateway).
- **Outbound HTTP:** `ClientHttpRequestInterceptor` on `RestClient.Builder`.
- **Outbound AMQP:** `MessagePostProcessor` on `RabbitTemplate`.
- **Inbound AMQP:** `@Around`-aspect on `@RabbitListener` (Phase 5+ activates).
- **Logback:** `<includeMdcKeyName>correlationId</includeMdcKeyName>` in JSON encoder.

---

### Cross-Cutting #5: RFC-7807 ProblemDetail (D-09 / QUAL-07)

**Source:** ARCHITECTURE.md §8.2 + RESEARCH §6 line 1201.
**Apply to:** Every Boot app via dependency on `common-error` module.

Locked field set: `type`, `title`, `status`, `detail`, `instance`, `correlationId`, `errors[]`. **Never echo exception messages** — sanitized `detail` only (RESEARCH §14 V7).

---

### Cross-Cutting #6: Verify-before-implement on the gateway property prefix (Assumption A1)

**Source:** RESEARCH Assumption A1 (line 1348) + Open Question #1 (line 1359).
**Apply to:** `api-gateway` module + first `:bootRun`.

The 2025.0 Northfields docs say gateway properties moved under `spring.cloud.gateway.server.webflux.*`, but tutorial blogs still show the flat prefix. **Action:** lead with the new prefix; ship `spring-boot-properties-migrator` as a runtime dep; on first `:bootRun --debug` confirm the canonical prefix in logs. **Document the actual binding in the PLAN.md verification step.**

---

### Cross-Cutting #7: Schema name `order` is SQL reserved (Assumption A3 / Pitfall 7)

**Source:** RESEARCH Pitfall 7 (lines 1254–1257) + Assumption A3 (line 1350).
**Apply to:** `infra/postgres/init.sh` + `service-template.yml` Flyway placeholders + every doc that mentions the order schema.

Default to `orders` (plural, non-reserved). Surface in PLAN.md as a Claude's-Discretion call needing user confirmation. Affects the schema name, the user name (`orders_user`), and every saga-contract example referencing the schema.

---

### Cross-Cutting #8: maxAttempts(3) means 1-try + 2-retries (Assumption A2 / Pitfall 6)

**Source:** RESEARCH Assumption A2 (line 1349) + Pitfall 6 (lines 1250–1252).
**Apply to:** `common-events/RabbitRetryConfig.java` + `.planning/saga-contracts.md` retry section.

Lock the wording in `saga-contracts.md`: "**3 total attempts (1 initial + 2 retries), delays 1s and 5s between attempts, then DLQ.**" The "30s" in the original CONTEXT.md narrative is a safety cap, not a delay between attempts 3 and 4.

---

## No Analog Found

Every file in this phase is greenfield; "no analog found" applies to all of them in the strict sense (no in-repo precedent). For each file, the closest substitutes — **all referenced as concrete external samples or pre-extracted research excerpts** — are documented in the Pattern Assignments section above.

The **only** files where even external analog quality drops below "exact" are the four where Phase 1 makes a project-specific composition choice:

| File | Why Closest Match Is "role-match", Not "exact" |
|------|----------------------------------------------|
| `api-gateway/src/main/java/com/n11/gateway/GatewayHeaderInjectionFilter.java` | The 2019 Spring blog "Securing Services with Spring Cloud Gateway" is the closest reference, but it predates Northfields and shows reactive patterns the planner must adapt. Phase 1 ships a **stub-only** (no-op) class; Phase 3 fleshes it out. |
| `common-logging/src/main/java/com/n11/logging/RabbitListenerCorrelationAspect.java` | RESEARCH §4.9 sketches only the aspect signature (lines 826–834); the body is left for the planner. Pure AOP idiom, no surprises. |
| `service-template/skeleton/*` | The CD-02 hybrid pattern itself is project-specific — no external sample exists for "Spring Boot subproject + skeleton/ copy-paste dir." |
| `.planning/saga-contracts.md` (narrative shape) | D-06 narrative shape is project-specific even though every event payload is exactly transcribed from ARCHITECTURE.md §3.4. |

---

## Reusable Outputs (Analogs for Phase 2+)

These Phase 1 outputs become the canonical in-repo analogs that downstream phases will copy. The planner should note this so PLAN.md tags them as "future reference points."

| Phase 1 Output | Becomes Analog For (Future Phase) | What Future Phases Copy |
|----------------|-----------------------------------|--------------------------|
| `service-template/skeleton/` | All 10 business services (Phases 3–9) | Application class, application.yml, logback-spring.xml, V1__init.sql skeleton |
| `common-error` module | Every Boot app from Phase 3 forward | `@ControllerAdvice` ProblemDetail mapper |
| `common-logging` module | Every Boot app from Phase 3 forward | All five correlation-ID propagation wires |
| `common-events` `Envelope` record | Every Phase 5+ saga producer/consumer | Event envelope DTO |
| `common-events/saga-schemas/*.json` + `AbstractEventSchemaTest` | Every Phase 5+ saga producer integration test | Schema-validation test base class (D-08 drift gate) |
| `infra/postgres/init.sh` | Every Phase 5+ smoke/integration test that boots Postgres in Testcontainers | Mounted via `withCopyFileToContainer` |
| `docker-compose.yml` (infra-only) | Phase 11 (`full` profile extension) | Same compose; Phase 11 adds business services + Jib images |
| `.planning/saga-contracts.md` + `*.schema.json` | Phase 5 (order/inventory/payment), Phase 8 (ai-service event consumers if any) | Single source of truth; producers must validate against the schema |
| `.planning/api-contracts.md` | Phase 3 (identity), Phase 4 (catalog), Phase 5 (order/payment), Phase 8 (ai), Phase 9 (mcp) | Endpoint signatures; gateway routing rules; RFC-7807 shape for error responses |
| `gradle/libs.versions.toml` | Every later phase that adds dependencies | Add new versions/libraries here, not per-module |
| `.github/workflows/ci.yml` + `.github/workflows/security.yml` | Phase 11 (release-tag workflow extends these to publish Jib images to GHCR / Docker Hub for the local docker-compose pull) | Setup-java + Gradle cache patterns |

---

## Metadata

**Analog search scope:** working tree (only `.planning/`, `CLAUDE.md`, `REQUIREMENTS-n11.md`, `.gitignore`, `.git/`).
**Files scanned:** 12 in-repo (all `.planning/` markdown + CLAUDE.md + REQUIREMENTS-n11.md + .gitignore).
**External references resolved:** 23 (all already enumerated in RESEARCH.md §15 — re-cited per-file above; planner does NOT need to re-fetch them at planning time).
**Research excerpts pre-extracted:** 100% (every "Pattern Assignments" excerpt cites a line range in RESEARCH.md so the planner copies from the local research file, not the external doc).
**Pattern extraction date:** 2026-04-28.

---

## PATTERN MAPPING COMPLETE
