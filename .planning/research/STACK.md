# Stack Research

**Domain:** Microservices e-commerce backend (Spring Boot 3.x) + agentic-commerce layer (Gemini + MCP) + React storefront
**Researched:** 2026-04-28
**Overall confidence:** HIGH on Spring/JVM stack, MEDIUM on Gemini 3 model naming, MEDIUM on Iyzico Checkout Form Java sample location
**Bias note:** Versions are pinned to the *current GA-but-conservative* line — Spring Boot 3.5.x (not 4.0.x) — to maximize compatibility with Spring AI 1.1.x, Spring Cloud 2025.0.x, and the broad ecosystem the candidate's bootcamp graders are likely to recognize.

---

## TL;DR — One-Line Stack

> **Java 21 (Corretto) + Spring Boot 3.5.14 + Spring Cloud 2025.0.x (Northfields) + PostgreSQL 16 + pgvector 0.8.2 + RabbitMQ 4.x + Spring AI 1.1.5 (MCP server starter) + google-genai 1.51.0 (Gemini 3 Flash Preview) + Iyzico 2.0.141 + Jib Gradle 3.5.3 + React 19.2 + Vite 8 + Tailwind + Zustand.**

---

## Recommended Stack

### Core Backend Framework

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| Java (Amazon Corretto) | **21** (LTS) | JVM runtime | Bootcamp-locked; Beanstalk has a `Corretto 21` platform branch (`64bit Amazon Linux 2023 v4.11.1 running Corretto 21`); virtual threads available; pattern matching, records, sealed types. **Confidence: HIGH** (verified against AWS Beanstalk supported platforms doc). |
| Spring Boot | **3.5.14** | Application framework | Latest patch on the OSS-supported 3.5 line as of 2026-04-23; 3.5 still in OSS support. We deliberately do **not** jump to 4.0.x because Spring AI 1.1.x and most Spring Cloud trains are still pinned to the 3.x line. **Confidence: HIGH**. |
| Spring Cloud (BOM) | **2025.0.x (Northfields)** train | Microservices BOM | Northfields is the train compatible with Spring Boot 3.5.x; 2024.0.x (Moorgate) is the 3.4 train. Use BOM `org.springframework.cloud:spring-cloud-dependencies:2025.0.0` (or the latest 2025.0 patch in Maven Central at build time). **Confidence: HIGH** (Spring Cloud project page lists train→Boot mapping explicitly). |
| Gradle | **8.10+** (Kotlin DSL) | Build tool | Multi-module monorepo (13 services) is significantly cleaner in Gradle than Maven; Jib has first-class Gradle plugin support. We pick Gradle. Maven is the documented fallback (graders may expect Maven — see "Build" section below). |

### Spring Cloud Modules

All transitively versioned by the `2025.0.x` BOM; no explicit version on each starter.

| Module | Maven Coordinate | Purpose |
|--------|------------------|---------|
| Eureka Server | `org.springframework.cloud:spring-cloud-starter-netflix-eureka-server` | Service registry |
| Eureka Client | `org.springframework.cloud:spring-cloud-starter-netflix-eureka-client` | Service discovery |
| Spring Cloud Gateway | `org.springframework.cloud:spring-cloud-starter-gateway` | **Reactive** API gateway. Use the reactive variant (default) — JWT validation + header injection + routing is all easier in WebFlux filters. The MVC variant exists but is mainly for blocking-only environments. |
| Spring Cloud Config Server | `org.springframework.cloud:spring-cloud-config-server` | Centralized config (file-system or git backend; for the demo, native file-system is fine) |
| Spring Cloud Config Client | `org.springframework.cloud:spring-cloud-starter-config` | Per-service config consumption |
| Spring Cloud Bus (optional) | `org.springframework.cloud:spring-cloud-starter-bus-amqp` | Live-refresh config via RabbitMQ. **Skip** unless config refresh is demoed; adds complexity. |

### Persistence

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| PostgreSQL | **16** | Primary RDBMS | Project-locked. RDS supports 16; pgvector 0.8.2 supports 13+. **Confidence: HIGH**. |
| pgvector | **0.8.2** | Vector embedding extension | Latest stable; supports cosine + L2 + inner product; HNSW + IVFFlat indexes. Install via `CREATE EXTENSION vector;` after enabling on RDS parameter group (`shared_preload_libraries`). **Confidence: HIGH**. |
| Hibernate ORM | **6.6.x** | JPA implementation | Transitive via Spring Boot 3.5.14 — do not override. Supports Java 21, sealed types, records as DTOs. |
| HikariCP | bundled | JDBC connection pool | Default in Spring Boot. Config: `spring.datasource.hikari.maximum-pool-size=10` per service is fine for the demo. |
| Flyway | **12.5.0** | DB migrations | Released 2025-04-27, latest. Per-service `db/migration/V1__init.sql`. **Use Flyway over Liquibase** — simpler, plain SQL files map well to graders' mental model. **Confidence: HIGH**. |
| Postgres JDBC driver | bundled | JDBC | Transitive via Spring Boot. |

**Maven coordinates:**
```
org.flywaydb:flyway-core:12.5.0
org.flywaydb:flyway-database-postgresql:12.5.0   # required as a separate artifact since Flyway 10
org.postgresql:postgresql                        # version managed by Boot BOM
```

### Messaging

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| RabbitMQ broker | **4.3** (or 3.13 LTS as fallback) | Message broker | RabbitMQ 4.x is current; 4.3 is the latest minor. For Beanstalk demo use a managed broker (Amazon MQ for RabbitMQ) **OR** docker-compose locally. **Confidence: HIGH** (rabbitmq.com displayed `RabbitMQ 4.3 is out` banner). |
| Spring AMQP / Spring Boot Starter | bundled (Spring Boot 3.5.14) | RabbitMQ client | `org.springframework.boot:spring-boot-starter-amqp`. Provides `RabbitTemplate`, `@RabbitListener`, declarative exchanges/queues. |

**SAGA topology recommendation** (choreography):
- Topic exchange `n11.events` (durable)
- Per-consumer queues bound on routing keys (`order.created`, `stock.reserved`, `payment.completed`, `payment.failed`, `stock.reservation.failed`)
- DLX: `n11.events.dlx` topic exchange; per-queue `x-dead-letter-exchange=n11.events.dlx`, `x-dead-letter-routing-key=<originalKey>.dead`
- Idempotency: messages carry `messageId` (UUID); consumers de-duplicate via a small `processed_messages` table per service (or Redis SETNX if Redis is added later — for the demo, a DB table is simpler)

### AI Layer (the differentiator)

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| **google-genai (Java SDK)** | **1.51.0** | Direct Gemini API client | `com.google.genai:google-genai:1.51.0`. Official Google SDK, supports automatic function calling (AFC), streaming via `generateContentStream`. **This is what the `GeminiChatProvider` adapter wraps.** **Confidence: HIGH** for the SDK; **MEDIUM** that we use it directly rather than going through Spring AI (see decision note below). |
| **Spring AI (BOM + MCP starter)** | **1.1.5** | MCP server boot starter, prompt templating, tool-calling abstraction | We use Spring AI **for the MCP server** (annotation-based `@McpTool` exposure + auto-config) — that's its killer feature here. We do **not** route the storefront chat through Spring AI's `VertexAiGeminiChatModel` because as of 1.1.5 it lists supported model IDs up to `gemini-2.5-flash-preview-04-17` and does not yet enumerate `gemini-3-flash-preview`. **Confidence: HIGH** on MCP starter; **HIGH** that Spring AI 1.1.5 doesn't list Gemini 3 yet. |
| **Gemini 3 Flash Preview** | model ID: `gemini-3-flash-preview` | Chat / function-calling LLM | Latest Flash-tier model in the Gemini 3 family. Per `ai.google.dev/gemini-api/docs/text-generation`, code samples use `gemini-3-flash-preview`. The model is in **Preview** — for a demo this is acceptable; for production we'd pin to `gemini-2.5-flash` (stable). **Confidence: MEDIUM** on the exact string (Google revises model IDs; verify at implementation time). |
| **Gemini Embedding (text)** | model ID: `gemini-embedding-2` | Text embeddings for RAG / semantic product search | Default 3072 dims, configurable down to 768/1536. Task instructions are now embedded in the prompt rather than passed as a `taskType` parameter (newer pattern). For pgvector, **use 768 dims** to keep HNSW index sizes reasonable — Gemini Embedding 2 supports custom output sizes. **Confidence: MEDIUM** (model ID confirmed in docs; exact production-stability tag still flagged Preview-leaning). |

**Critical correction:** The candidate's instruction said "Gemini 3.0 Flash". The actual model ID surfaced in Google's docs is **`gemini-3-flash-preview`** (no `.0`). Track this; if Google renames before deploy, the change is a single property: `gemini.model.chat=...`.

**Fallback ladder** (if Gemini 3 Flash Preview is unavailable / rate-limited / removed during demo week):
1. `gemini-3-flash-preview` (target)
2. `gemini-2.5-flash` (stable, broadly available)
3. `gemini-2.5-flash-lite` (cheapest)

The `ChatProvider` port abstracts this — model swap is config-only.

#### MCP Server Implementation

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| Spring AI MCP Server starter (WebMVC) | **1.1.5** | Expose Spring beans as MCP tools | `org.springframework.ai:spring-ai-starter-mcp-server-webmvc`. Use `@McpTool` and `@McpToolParam` annotations on `@Component` beans; auto-registered. Choose **WebMVC + SSE/Streamable-HTTP** transport (not stdio) — we want the MCP server reachable over HTTP for the Claude Desktop / external-agent demo. **Confidence: HIGH**. |
| MCP Java SDK (transitively pulled by Spring AI) | **io.modelcontextprotocol.sdk:mcp:1.1.2** | Underlying MCP protocol implementation | Don't depend on this directly; let Spring AI bring it in. **Confidence: HIGH**. |
| MCP spec revision | **2025-11-25** | Wire-protocol contract | Latest spec revision per `modelcontextprotocol.io/specification`. Spring AI 1.1.5 targets it. |

**MCP tool exposure pattern (recommended):**
```java
@Component
public class StorefrontMcpTools {
    @McpTool(name = "search_products", description = "Search the n11 catalog by query, with optional category filter")
    public List<ProductSummary> searchProducts(
        @McpToolParam(description = "Free-text query, Turkish or English", required = true) String query,
        @McpToolParam(description = "Category slug, e.g. 'elektronik'") String category) { ... }

    @McpTool(name = "add_to_cart", description = "Add a product to the user's cart")
    public CartView addToCart(@McpToolParam String productId, @McpToolParam int quantity) { ... }

    @McpTool(name = "place_order", description = "Place an order for the current cart and return an Iyzico checkout-form URL")
    public CheckoutLink placeOrder() { ... }
}
```
Same beans are also called by the `ai-service` chat handler — the **DRY one-toolset-two-surfaces** decision in PROJECT.md.

#### Conversation State

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| **PostgreSQL table** (`ai_conversations`) | n/a | Conversation history, messages, tool-call traces | Reuse the existing Postgres host inside `ai-service`'s schema. Schema: `(id, user_id, started_at, last_at)` + `messages(id, conversation_id, role, content, tool_calls_jsonb, ts)`. **Why not Redis:** project budget rules out a second managed service. Postgres is plenty fast for chat-window-scale histories (single-user, last N messages). **Confidence: HIGH** (architecturally validated against PROJECT.md constraints). |

**Prompt management:** Use Spring AI's `PromptTemplate` (StringTemplate-based) for system prompts. Do **not** add LangChain4j — it duplicates Spring AI's surface area and we don't need its richer agent/chain abstractions for this scope.

### Payment

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| Iyzico Java SDK | **2.0.141** | Iyzico payment gateway client | `com.iyzipay:iyzipay-java:2.0.141`. **Confidence: HIGH** (verified on iyzico/iyzipay-java README). |
| Iyzico sandbox base URL | `https://sandbox-api.iyzipay.com` | Test environment | All sandbox calls; `Options.setBaseUrl(...)` per request OR a single bean. **Confidence: HIGH**. |

**Integration choice — use Checkout Form (Hosted Payment Page), not direct 3DS card capture:**
- Class: `com.iyzipay.model.CheckoutFormInitialize`, `com.iyzipay.model.CheckoutForm`
- Sample reference: `com.iyzipay.sample.CheckoutFormSample` (in the SDK test sources)
- Flow:
  1. `CheckoutFormInitialize.create(request, options)` → returns `paymentPageUrl` + `token`
  2. Frontend redirects user to `paymentPageUrl` (or embeds in iframe via `checkoutFormContent`)
  3. After user pays, Iyzico POSTs back to `callbackUrl` with the token
  4. `CheckoutForm.retrieve(retrieveRequest, options)` → final payment status
- **Why Checkout Form over direct 3DS:** No PCI scope on the candidate's side; n11 itself uses Iyzico Hosted Payment Page; far less code; sandbox cards "just work" without iframe-3DS plumbing.

### Auth

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| Spring Security (Resource Server + OAuth2) | bundled (Spring Boot 3.5.14) | JWT validation at gateway, password hashing in identity-service | `org.springframework.boot:spring-boot-starter-oauth2-resource-server`. **Use Spring Security's built-in Nimbus JOSE+JWT integration**, not jjwt — cleaner, idiomatic for Spring Boot 3.x, JWK URL / public-key validation already wired. **Confidence: HIGH**. |
| Spring Security crypto / BCrypt | bundled | Password hashing | `BCryptPasswordEncoder` with strength=12 (default 10 is fine for the demo). |
| jjwt (only if we want a tiny JWT issuer in identity-service) | **0.13.0** | Convenience JWT builder | `io.jsonwebtoken:jjwt-api:0.13.0`, `jjwt-impl:0.13.0` (runtime), `jjwt-jackson:0.13.0` (runtime). **Optional** — Spring Security can issue tokens too via `JwtEncoder`, but jjwt's fluent builder is genuinely nicer for the issuer side. Pick one and document it. **Recommendation: use `JwtEncoder` (Spring) for both sides; skip jjwt.** **Confidence: HIGH**. |

**Token strategy:** Access token (15 min, RS256 signed by identity-service's private key) + refresh token (7 days, opaque, stored hashed in `refresh_tokens` table). Gateway only validates the access token; identity-service handles refresh. JWK Set endpoint on identity-service: `/.well-known/jwks.json` so the gateway can fetch public keys.

### Testing

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| JUnit Jupiter | 5.11.x (transitive via Boot) | Test framework | All tests |
| Spring Boot Test | bundled | Slice and full-context tests | `@WebMvcTest`, `@DataJpaTest`, `@SpringBootTest` |
| Mockito | bundled | Mocks | Unit tests |
| AssertJ | bundled | Fluent assertions | All tests |
| Testcontainers | **2.0.5** | Throwaway Postgres/RabbitMQ in tests | Integration tests for repositories + listeners. BOM: `org.testcontainers:testcontainers-bom:2.0.5`. Modules: `testcontainers-postgresql`, `testcontainers-rabbitmq`, `testcontainers-junit-jupiter`. **Confidence: HIGH**. |
| Awaitility | **4.2.x** | Async assertions for saga tests | Verifying `OrderCreated` triggers `StockReserved` within N seconds in integration tests |
| WireMock | 3.x | Stubbing Iyzico + Gemini in tests | Unit-test the payment-service and ai-service against deterministic responses |
| RestAssured (optional) | 5.x | End-to-end API tests through gateway | One smoke E2E that posts an order through the full stack; can also do this with `WebTestClient` |

### DevOps

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| Jib Gradle plugin | **3.5.3** (released 2025-02-06) | Container image build w/o Dockerfile | Plugin id: `com.google.cloud.tools.jib`. Configure once in root `build.gradle.kts`, override per service. Push to ECR or Docker Hub. **Confidence: HIGH**. |
| Jib Maven plugin | 3.5.1 | Maven equivalent | If we switch to Maven, same plugin under `com.google.cloud.tools:jib-maven-plugin:3.5.1`. |
| GitHub Actions: setup-java | `actions/setup-java@v4` | Provision Corretto 21 in CI | `distribution: corretto`, `java-version: 21` |
| GitHub Actions: gradle | `gradle/actions/setup-gradle@v4` | Cached Gradle execution | Replaces deprecated `gradle/gradle-build-action` |
| GitHub Actions: AWS auth | `aws-actions/configure-aws-credentials@v4` | OIDC-based AWS creds (no static keys) | Configure GitHub OIDC trust against an IAM role with `AWSElasticBeanstalkWebTier` policy |
| GitHub Actions: EB deploy | `einaregilsson/beanstalk-deploy@v22` | Push artifact to Beanstalk env | Established community action; or use `aws elasticbeanstalk` CLI in a `run:` step for simpler control |
| GitHub Actions: Slack | `slackapi/slack-github-action@v2` | Slack deploy notifications | Use webhook stored in `secrets.SLACK_WEBHOOK_URL`. Send on `success` AND `failure` job conclusions. |
| AWS Elastic Beanstalk platform | `64bit Amazon Linux 2023 v4.11.1 running Corretto 21` | Java 21 runtime | One env per service is excessive (and expensive). Recommendation: **deploy gateway + a few core services on Beanstalk**; run the rest via docker-compose on a single EC2 if cost matters, or fold everything into a single Beanstalk multi-container Docker env. **Confirm AWS scope with bootcamp coordinator** (open question in PROJECT.md). |

### API Documentation

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| springdoc-openapi (WebMVC) | **2.8.17** | OpenAPI 3.x + Swagger UI | `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.17`. Per-service, mounted at `/swagger-ui.html`. Spring Boot 3.x compatible. **Confidence: HIGH**. |
| springdoc-openapi (WebFlux — for the gateway only) | 2.8.17 | OpenAPI for the reactive gateway | `org.springdoc:springdoc-openapi-starter-webflux-ui:2.8.17`. The gateway aggregates downstream specs. |

### Frontend

Frontend toolchain is technically deferred until the Playwright n11 recon (per PROJECT.md), but here's the recommended baseline that the recon will most likely confirm:

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| React | **19.2.x** | UI framework | Bootcamp-locked (React.js). 19.2 is GA. **Confidence: HIGH**. |
| Vite | **8.x** | Dev/build toolchain | Vite 8 is current. SPA is sufficient for n11 storefront — no SSR required (n11 itself is largely client-rendered after first paint). **Confidence: HIGH** (Vite 8 verified on vite.dev). |
| TypeScript | **5.6+** | Type safety | Strongly recommended. Pairs with Vite's `@vitejs/plugin-react` template. |
| React Router | **7.x** | SPA routing | Pages: home, category, PDP, cart, checkout |
| TanStack Query (React Query) | **5.x** | Server-state cache | Better than raw `useEffect`-fetch for product lists, pagination, cart sync. Note: brief explicitly requires `useState` + `useEffect` for state — **TanStack Query uses `useState`/`useEffect` internally**, and the brief is satisfied by also having explicit `useState`/`useEffect` examples in the codebase (e.g., the chat input box, loading spinners). |
| Zustand | **5.x** | Client state (cart, auth, chat-bubble open/closed) | Simpler than Redux Toolkit; no boilerplate; tiny bundle. **Recommendation over Context** for cart because cart updates are frequent and Context re-renders the world. |
| Tailwind CSS | **4.x** | Styling | Utility-first; matches n11's dense layout idiom; fast to build with. Fallback: plain CSS modules. |
| Axios | **1.x** | HTTP client | Or native `fetch` — both are fine. Axios for interceptors (auth header injection). |

**SSE for chat streaming:** the browser uses native `EventSource` (or `fetch` with `ReadableStream` for POST-bodied SSE) to consume `ai-service`'s `/api/chat/stream` endpoint.

### Logging

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| SLF4J + Logback | bundled (Spring Boot 3.5) | Logging facade + impl | Default; no override needed |
| logstash-logback-encoder | **8.x** | JSON-structured logs | `net.logstash.logback:logstash-logback-encoder:8.0`. Each log line includes `serviceName`, `traceId`, `userId`, `correlationId` — saga debugging is impossible without this. **Recommended.** |
| Micrometer Tracing (Brave) | bundled (Spring Boot 3.5) | Trace + correlation IDs | `org.springframework.boot:spring-boot-starter-actuator` + `io.micrometer:micrometer-tracing-bridge-brave`. Auto-injects `traceId`/`spanId` into MDC. We do not run a Zipkin/Tempo backend (out of scope); the trace IDs in logs are still extremely useful for grep-debugging. |

### Build Coordinates Quick-Reference

```kotlin
// build.gradle.kts (root)
plugins {
    id("org.springframework.boot") version "3.5.14" apply false
    id("io.spring.dependency-management") version "1.1.6" apply false
    id("com.google.cloud.tools.jib") version "3.5.3" apply false
}

// per-service build.gradle.kts
dependencies {
    implementation(platform("org.springframework.cloud:spring-cloud-dependencies:2025.0.0"))
    implementation(platform("org.springframework.ai:spring-ai-bom:1.1.5"))
    implementation(platform("org.testcontainers:testcontainers-bom:2.0.5"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")
    implementation("org.springframework.cloud:spring-cloud-starter-config")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.17")
    implementation("org.flywaydb:flyway-core:12.5.0")
    implementation("org.flywaydb:flyway-database-postgresql:12.5.0")
    implementation("net.logstash.logback:logstash-logback-encoder:8.0")
    runtimeOnly("org.postgresql:postgresql")

    // ai-service & mcp-server only:
    implementation("com.google.genai:google-genai:1.51.0")
    implementation("org.springframework.ai:spring-ai-starter-mcp-server-webmvc")  // mcp-server only

    // payment-service only:
    implementation("com.iyzipay:iyzipay-java:2.0.141")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:rabbitmq")
    testImplementation("org.awaitility:awaitility")
    testImplementation("org.wiremock:wiremock-standalone:3.10.0")
}
```

---

## Alternatives Considered

| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|-------------------------|
| Gradle (Kotlin DSL) | Maven | If graders explicitly weight Maven (the bootcamp materials show Maven examples) — switch the root build, keep dependency coords identical. |
| Spring Cloud Gateway (reactive) | Spring Cloud Gateway MVC | If the team is uncomfortable with WebFlux; for this project the reactive variant is fine because all gateway logic is thin filters (auth header injection, routing). |
| google-genai SDK direct + Spring AI MCP | Spring AI VertexAI Gemini chat model | When/if Spring AI 1.2 ships and lists `gemini-3-flash-preview` in its supported model IDs — then the whole AI stack becomes one library. We're early; we split. |
| Flyway | Liquibase | Liquibase is more powerful for cross-DB portability (XML/YAML changesets). We're single-DB Postgres; Flyway's plain-SQL files are simpler and grader-friendly. |
| PostgreSQL + pgvector | Pinecone / Weaviate / Chroma | Pinecone needs a paid account; Weaviate/Chroma add a service. Sticking with pgvector keeps DB-per-service and zero new infra. |
| Postgres-backed conversation history | Redis | Redis is ideal for chat at scale; for a 6-day demo with one concurrent user, Postgres avoids spinning up another managed service. |
| Spring Security `JwtEncoder` for issuance | jjwt | jjwt's fluent API is nice but means two libraries doing the same thing. Stay native. |
| Iyzico Checkout Form (Hosted Payment Page) | Iyzico direct 3DS card capture | If we wanted full custom card-input UI (we don't) and were OK with the PCI / iframe-3DS complexity (we're not). |
| Testcontainers | H2 + embedded RabbitMQ stub | H2's Postgres dialect-fudge breaks on pgvector and on real JSONB queries. Pay the Testcontainers startup cost; it catches real bugs. |
| MCP via Spring AI (annotation-driven) | Hand-rolled JSON-RPC over SSE | Possible, but Spring AI's `@McpTool` annotation is the single biggest "why this stack is opinionated" payoff. |
| Vite + React | Next.js (App Router) | If the n11 recon shows heavy SSR-dependent SEO needs (it won't — n11 is a logged-out marketplace, but n11 itself uses SSR for SEO). For our demo, SSR is overkill. |
| Zustand | Redux Toolkit | RTK is fine but ~10x the boilerplate. Pick Zustand. |
| Zustand | React Context | Context re-renders all consumers on any state change — fine for theme/locale, bad for cart. |

---

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| Spring Boot 4.0.x | Released 2026-04 but Spring AI 1.1.x is still on 3.x; mixing is risky for a 6-day project | Spring Boot 3.5.14 |
| Spring Cloud Hystrix | Deprecated and removed from Spring Cloud since 2020 | Resilience4j (`io.github.resilience4j:resilience4j-spring-boot3`) — **only if** you actually need a circuit breaker; for the demo, skip resilience patterns and keep service code simple |
| Netflix Ribbon | Deprecated | Spring Cloud LoadBalancer (auto-included with Eureka client) |
| RestTemplate | "In maintenance mode" since Spring 5.x | `RestClient` (Spring Framework 6.1+, blocking, fluent — the Boot 3.5 idiom) **or** `WebClient` for reactive. Stick with `RestClient` for blocking services. |
| Create React App (CRA) | Officially deprecated, no longer maintained | Vite |
| Spring Cloud Sleuth (standalone) | Replaced by Micrometer Tracing | `spring-boot-starter-actuator` + `micrometer-tracing-bridge-brave` |
| spring-cloud-starter-zuul | Removed years ago | Spring Cloud Gateway |
| Hibernate Validator's deprecated annotations | Subtle Jakarta vs javax confusion | Use `jakarta.validation.constraints.*` exclusively (Spring Boot 3 = Jakarta EE 9+) |
| H2 in tests | Doesn't faithfully emulate Postgres for JSONB, full-text search, or pgvector | Testcontainers Postgres |
| LangChain4j | Adds a third AI abstraction layer on top of Spring AI + google-genai | Stick with Spring AI (for MCP) + google-genai (for chat); two libraries is enough |
| `gemini-pro` / `gemini-1.5-pro` | Older generation; deprecated | `gemini-3-flash-preview` (target) or `gemini-2.5-flash` (stable fallback) |
| `gemini-embedding-001` | Older embedding API w/ separate `taskType` parameter | `gemini-embedding-2` (current) |
| Manually-coded MCP JSON-RPC handler | Spring AI's `@McpTool` does this for you with auto-discovered Spring beans | `spring-ai-starter-mcp-server-webmvc` |
| Iyzico direct 3DS API | Adds PCI scope, iframe gymnastics, callback choreography for no demo value | `CheckoutFormInitialize` (Hosted Payment Page) |
| Storing JWT signing key in source | Obvious security smell | `application.yml` with placeholder + real key in env var or AWS Secrets Manager |
| Static AWS access keys in GitHub Actions | Credential leakage risk | OIDC federation: `aws-actions/configure-aws-credentials@v4` with role-to-assume |

---

## Stack Patterns by Variant

**If demo runs locally only (no AWS at all):**
- Drop EB platform decision; use docker-compose with a single Postgres container, one RabbitMQ container, and 13 Jib-built service images
- GitHub Actions still does `gradle build` + `jib dockerBuild` to prove containerization
- Slack webhook fires from CI on green build

**If we get AWS budget for full deploy:**
- One Beanstalk env per critical service (gateway, identity, product, order, payment) = ~5 envs
- Other services co-located on one EC2 via docker-compose, behind the gateway via internal hostnames
- RDS Postgres single instance, one DB per service (DB-per-service via schema isolation)
- Amazon MQ for RabbitMQ (single-instance for cost)

**If Gemini 3 Flash Preview proves unstable mid-build:**
- Flip `ai.gemini.model` config to `gemini-2.5-flash` in `config-server`
- All 13 services restart; no code change
- This is exactly why the `ChatProvider` port exists

**If the candidate prefers Maven (graders' familiarity):**
- All version coords above are Maven-compatible; switch `build.gradle.kts` → `pom.xml` per service, parent pom holding the BOMs
- Multi-module Maven is verbose but works; expect ~50 more lines of XML per service
- Jib Maven plugin: `com.google.cloud.tools:jib-maven-plugin:3.5.1`

---

## Version Compatibility

| Component | Compatible With | Notes |
|-----------|-----------------|-------|
| Spring Boot 3.5.14 | Java 17+ (we use Java 21) | Java 21 fully supported, virtual threads opt-in via `spring.threads.virtual.enabled=true` |
| Spring Boot 3.5.14 | Spring Cloud 2025.0.x (Northfields) | **Mandatory pairing** — using 2024.0.x with Boot 3.5 will silently work for many starters but mismatches the supported BOM |
| Spring AI 1.1.5 | Spring Boot 3.4.x or 3.5.x | Confirmed compatible with both; targets MCP spec 2025-11-25 |
| Spring AI 1.1.5 MCP server | MCP Java SDK 1.1.2 | Brought in transitively |
| Hibernate 6.6.x | PostgreSQL 16 | Full support; pgvector via custom `@Type` or Hypersistence Utils library |
| pgvector 0.8.2 | PostgreSQL 13–17 | Use 16 |
| Testcontainers 2.0.5 | Java 17+, Docker 20+ | CI runner needs Docker available; GitHub Actions Linux runners have it pre-installed |
| google-genai 1.51.0 | Java 11+ | Java 21 fine |
| Iyzico 2.0.141 | Java 8+ | Java 21 fine |
| Flyway 12.5.0 | PostgreSQL 12+, requires `flyway-database-postgresql` artifact since v10 | Add the artifact explicitly |
| Springdoc 2.8.17 | Spring Boot 3.x | WebMVC and WebFlux flavors are separate artifacts |
| Jib 3.5.3 (Gradle) | Gradle 7.4+ | Use Gradle 8.10 or newer |
| Vite 8 | Node 20.19+ or 22.12+ | Use Node 22 LTS |

---

## Sources

| Source | Verified | Confidence |
|--------|----------|------------|
| `spring.io/projects/spring-cloud` (release-train compatibility table) | Train→Boot mapping (2025.0.x ↔ 3.5.x; 2024.0.x ↔ 3.4.x) | HIGH |
| `github.com/spring-projects/spring-boot/releases` | Latest 3.5.14 (2026-04-23), 4.0.6 (2026-04-23) | HIGH |
| `ai.google.dev/gemini-api/docs/models` | Gemini 3.x model lineup, 3.0 Flash NOT available; 3 Flash Preview exists; embedding 2 current | HIGH (overall lineup); MEDIUM (exact production-stable string) |
| `ai.google.dev/gemini-api/docs/text-generation` | Code samples use `gemini-3-flash-preview` model ID | MEDIUM (Preview tag means string may change) |
| `ai.google.dev/gemini-api/docs/embeddings` | `gemini-embedding-2` confirmed; default 3072 dims | HIGH |
| `github.com/googleapis/java-genai` | google-genai 1.51.0; AFC + streaming supported | HIGH |
| `docs.spring.io/spring-ai/reference/index.html` | Spring AI 1.1.5; MCP Server starters listed | HIGH |
| `docs.spring.io/spring-ai/reference/api/chat/vertexai-gemini-chat.html` | Spring AI Gemini chat lists up to `gemini-2.5-flash-preview-04-17`; **does not** list Gemini 3 — driving the decision to use google-genai directly | HIGH |
| `docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html` | `@McpTool`/`@McpToolParam` annotations; WebMVC and WebFlux starters; SSE + Streamable-HTTP transports | HIGH |
| `github.com/modelcontextprotocol/java-sdk` | `io.modelcontextprotocol.sdk:mcp:1.1.2` | HIGH |
| `modelcontextprotocol.io/specification` | Current spec revision: 2025-11-25 | HIGH |
| `github.com/iyzico/iyzipay-java` | iyzipay-java 2.0.141, Maven coords confirmed | HIGH |
| `docs.iyzico.com` (sandbox + checkout-form sample) | `https://sandbox-api.iyzipay.com`; `CheckoutFormSample` reference | MEDIUM (full sample code not extracted; class names match Iyzico's Java SDK conventions) |
| `github.com/pgvector/pgvector` | 0.8.2, Postgres 13+ | HIGH |
| `github.com/GoogleContainerTools/jib/releases` | Gradle 3.5.3 (2025-02-06); Maven 3.5.1 (2024-11) | HIGH |
| `docs.aws.amazon.com/elasticbeanstalk/latest/platforms/platforms-supported.html` | Corretto 21 platform branch confirmed (`64bit Amazon Linux 2023 v4.11.1 running Corretto 21`) | HIGH |
| `rabbitmq.com/docs/which-erlang` | RabbitMQ 4.3 current | HIGH |
| `springdoc.org` | springdoc-openapi 2.8.17, Spring Boot 3.x compatible | HIGH |
| `github.com/jwtk/jjwt` | jjwt 0.13.0 | HIGH |
| `java.testcontainers.org` | Testcontainers 2.0.5; modules listed | HIGH |
| `github.com/flyway/flyway/releases` | Flyway 12.5.0 (2025-04-27) | HIGH |
| `vite.dev` | Vite 8.0.10 current | HIGH |
| `react.dev/blog` | React 19.2.x current line | HIGH |

---

## Open Items / Lower-Confidence Flags

1. **Gemini 3 Flash model ID volatility (MEDIUM).** Google has been re-tagging models actively. Implementer must run a single `curl` against `ai.google.dev/gemini-api/docs/models` on the day they add the dependency to confirm `gemini-3-flash-preview` is still the live string. Have `gemini-2.5-flash` config-ready as a fallback.
2. **Iyzico Checkout Form Java sample (MEDIUM).** The Iyzico Java SDK README does not show the Checkout Form sample inline; the sample lives in `src/test/java/com/iyzipay/sample/CheckoutFormSample.java` in the SDK repo. Implementer should clone iyzipay-java and read that file before writing the payment-service integration. The sandbox base URL `https://sandbox-api.iyzipay.com` is verified.
3. **Spring Cloud 2025.0 patch level (MEDIUM).** We pin to `2025.0.0` because that's the GA version surfaced in releases. Maven Central likely has 2025.0.x patches by now — implementer should pin to the latest patch at build time (e.g., 2025.0.1+ if available). Plain `2025.0.0` is the safe minimum.
4. **AWS deploy scope (open question, in PROJECT.md).** Stack assumes Beanstalk + RDS but flags single-EC2-docker-compose as the cost-realistic alternative. Deferred to coordinator confirmation.
5. **Spring AI Gemini 3 support (LOW-likelihood watch-item).** If Spring AI 1.2.x ships during build week with Gemini 3 model IDs added, swap from google-genai to Spring AI's `VertexAiGeminiChatModel` for a smaller dependency footprint. Until then, the split is correct.

---

*Stack research for: n11-clone microservices e-commerce + agentic-commerce layer*
*Researched: 2026-04-28*
