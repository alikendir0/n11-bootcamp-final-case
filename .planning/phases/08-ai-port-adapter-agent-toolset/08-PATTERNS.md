# Phase 8: AI Port + Adapter + Agent Toolset - Pattern Map

**Mapped:** 2026-05-01
**Files analyzed:** 38 (across 4 new modules + infra changes)
**Analogs found:** 34 / 38 (4 marked net-new)

---

## File Classification

| New / Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---------------------|------|-----------|----------------|---------------|
| `ai-port/build.gradle.kts` | config | — | `common-error/build.gradle.kts` | exact |
| `ai-port/.../ChatProvider.java` | port interface | request-response | `payment-service/.../IyzicoCheckoutClient.java` (port shape) | role-match |
| `ai-port/.../EmbeddingProvider.java` | port interface | request-response | `payment-service/.../IyzicoCheckoutClient.java` | role-match |
| `ai-port/.../dto/ChatMessage.java` | model/DTO | — | `common-events/Envelope.java` (record shape) | role-match |
| `ai-port/.../dto/ChatResponse.java` | model/DTO | — | `common-events/Envelope.java` | role-match |
| `ai-port/.../dto/ToolSchema.java` | model/DTO | — | `common-events/Envelope.java` | role-match |
| `ai-port/.../dto/ToolCallRequest.java` | model/DTO | — | `common-events/Envelope.java` | role-match |
| `ai-port/.../dto/ToolCallResult.java` | model/DTO | — | `common-events/Envelope.java` | role-match |
| `agent-toolset/build.gradle.kts` | config | — | `common-events/build.gradle.kts` | exact |
| `agent-toolset/.../AgentTool.java` | port interface | request-response | `payment-service/.../IyzicoCheckoutClient.java` | role-match |
| `agent-toolset/.../ToolContext.java` | model/DTO | — | `common-events/Envelope.java` | role-match |
| `agent-toolset/.../ToolResult.java` | model/DTO | — | `common-events/Envelope.java` | role-match |
| `agent-toolset/.../tools/SearchProductsTool.java` | service | request-response | `cart-service/.../product/ProductClient.java` | exact |
| `agent-toolset/.../tools/GetProductTool.java` | service | request-response | `cart-service/.../product/ProductClient.java` | exact |
| `agent-toolset/.../tools/ListCategoriesTool.java` | service | request-response | `cart-service/.../product/ProductClient.java` | exact |
| `agent-toolset/.../tools/AddToCartTool.java` | service | request-response | `cart-service/.../product/ProductClient.java` | exact |
| `agent-toolset/.../tools/ViewCartTool.java` | service | request-response | `cart-service/.../product/ProductClient.java` | exact |
| `agent-toolset/.../tools/UpdateCartItemTool.java` | service | request-response | `cart-service/.../product/ProductClient.java` | exact |
| `agent-toolset/.../tools/RemoveFromCartTool.java` | service | request-response | `cart-service/.../product/ProductClient.java` | exact |
| `agent-toolset/.../tools/CreateOrderTool.java` | service | request-response | `payment-service/.../order/OrderPaymentContextClient.java` | exact |
| `agent-toolset/.../tools/GetPaymentLinkTool.java` | service | request-response | `payment-service/.../order/OrderPaymentContextClient.java` | exact |
| `agent-toolset/.../tools/GetOrderStatusTool.java` | service | request-response | `payment-service/.../order/OrderPaymentContextClient.java` | exact |
| `ai-service/build.gradle.kts` | config | — | `cart-service/build.gradle.kts` | exact |
| `ai-service/.../GeminiChatAdapter.java` | service/adapter | request-response | `payment-service/.../iyzico/DefaultIyzicoCheckoutClient.java` | role-match |
| `ai-service/.../GeminiEmbeddingAdapter.java` | service/adapter | request-response | `payment-service/.../iyzico/DefaultIyzicoCheckoutClient.java` | role-match |
| `ai-service/.../EchoChatProvider.java` | service/adapter | request-response | net-new (no existing second adapter) | none |
| `ai-service/.../domain/ToolDispatcher.java` | service | event-driven | net-new | none |
| `ai-service/.../interfaces/rest/ChatController.java` | controller | request-response | `cart-service/.../CartController.java` | exact |
| `ai-service/.../interfaces/sse/ChatStreamController.java` | controller | streaming | net-new (no SSE controller exists) | none |
| `ai-service/.../infrastructure/http/ProductClient.java` | service | request-response | `payment-service/.../order/OrderPaymentContextClient.java` | exact |
| `ai-service/.../infrastructure/http/CartClient.java` | service | request-response | `payment-service/.../order/OrderPaymentContextClient.java` | exact |
| `ai-service/.../infrastructure/http/OrderClient.java` | service | request-response | `payment-service/.../order/OrderPaymentContextClient.java` | exact |
| `ai-service/.../infrastructure/http/PaymentClient.java` | service | request-response | `payment-service/.../order/OrderPaymentContextClient.java` | exact |
| `ai-service/src/main/resources/db/migration/V1__init_ai.sql` | migration | — | `cart-service/src/main/resources/db/migration/V2__init_cart.sql` | exact |
| `search-service/build.gradle.kts` | config | — | `cart-service/build.gradle.kts` | exact |
| `search-service/.../SearchServiceApplication.java` | config | — | `cart-service/.../CartServiceApplication.java` | exact |
| `search-service/.../db/migration/V1__init_search.sql` | migration | — | `cart-service/src/main/resources/db/migration/V2__init_cart.sql` | exact |
| `config-server/.../config/api-gateway.yml` | config | streaming | existing commented anchor in same file (lines 84-99) | exact |
| `config-server/.../config/ai-service.yml` | config | — | `config-server/.../config/cart-service.yml` | exact |
| `config-server/.../config/search-service.yml` | config | — | `config-server/.../config/cart-service.yml` | exact |
| `docker-compose.yml` (ai-service + search-service entries) | config | — | `docker-compose.yml` notification-service block (lines 431-457) | exact |
| `settings.gradle.kts` (new includes) | config | — | existing `settings.gradle.kts` | exact |
| `infra/postgres/init.sh` | config | — | existing file (already has ai + search schemas; verify-only) | exact |
| `common-error/.../ApiErrorCode.java` (new entries) | model | — | existing file | exact |
| `infra-tests/build.gradle.kts` (new deps) | config | — | existing file | exact |
| `infra-tests/.../AiServiceTestConfig.java` | config/test | — | `infra-tests/.../saga/PaymentServiceTestConfig.java` | exact |

---

## Pattern Assignments

### `ai-port/build.gradle.kts` (zero-dependency java-library)

**Analog:** `common-error/build.gradle.kts` (lines 1-8)

**Core pattern:**
```kotlin
plugins {
    `java-library`
}

dependencies {
    // ZERO external dependencies — ai-port must compile with no runtime deps.
    // compileOnly for any type-reference deps only (e.g., jakarta annotations if needed).
    // Verify: ./gradlew :ai-port:dependencies | grep "com.google.genai" → must be 0
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
```

Key distinction from `common-error`: no `compileOnly` on Spring, no external SDKs at all. The goal stated in CONTEXT.md D-01 is a zero-dep jar. Run `./gradlew :ai-port:dependencies | grep -c "com.google.genai"` as a CI gate after wiring.

---

### `ai-port/.../dto/*.java` (neutral DTOs — records only)

**Analog:** `common-events/src/main/java/com/n11/events/Envelope.java`

**Imports pattern** (lines 1-10 of Envelope.java):
```java
package com.n11.events;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record Envelope(
    String eventId,
    String eventType,
    ...
) {}
```

**Copy this shape for all ai-port DTOs.** Use Java `record` — no JPA annotations, no Jackson annotations (Jackson can serialize plain records), no Gemini SDK types anywhere in this package.

---

### `agent-toolset/build.gradle.kts` (java-library, depends on ai-port)

**Analog:** `common-events/build.gradle.kts` (full file)

**Core pattern:**
```kotlin
plugins {
    `java-library`
}

dependencies {
    implementation(project(":ai-port"))
    // Jackson for JsonNode in ToolResult / args — version from Spring Boot BOM
    implementation("com.fasterxml.jackson.core:jackson-databind")
    // Spring context for @Component on tool implementations
    implementation("org.springframework:spring-context")
    // NO google-genai. NO Spring Boot. Tools are pure domain logic + HTTP calls.
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
```

Note: `common-events/build.gradle.kts` uses `api(...)` for Jackson because downstream modules need `JsonNode` transitively. Use `implementation(...)` for agent-toolset since ai-service and mcp-server will import jackson directly via their Spring Boot starters.

---

### `agent-toolset/.../tools/*.java` (10 tool implementations — RestClient callers)

**Analog for all cart tools:** `cart-service/src/main/java/com/n11/cart/product/ProductClient.java` (full file, 52 lines)

**Imports pattern** (lines 1-12):
```java
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
```

**Core RestClient call pattern** (lines 21-51):
```java
@Component
public class ProductClient {

    private final RestClient restClient;
    private final String productBaseUrl;

    public ProductClient(RestClient.Builder builder,
                         @Value("${app.clients.product.base-url:http://product-service:8082}") String productBaseUrl) {
        this.restClient = builder.build();
        this.productBaseUrl = productBaseUrl;
    }

    public ProductSnapshot fetchSnapshot(UUID productId) {
        try {
            ProductSnapshot snapshot = restClient.get()
                .uri(productBaseUrl + "/products/{id}", productId)
                .retrieve()
                .body(ProductSnapshot.class);
            if (snapshot == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ürün bulunamadı: " + productId);
            }
            return snapshot;
        } catch (HttpClientErrorException.NotFound nf) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ürün bulunamadı: " + productId);
        }
    }
}
```

**Analog for order/payment tools:** `payment-service/src/main/java/com/n11/payment/order/OrderPaymentContextClient.java` (full file, 43 lines)

**Core pattern with onStatus** (lines 22-35):
```java
public OrderPaymentContext getPaymentContext(UUID orderId) {
    try {
        return restClient.get()
            .uri("/internal/orders/{orderId}/payment-context", orderId)
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                throw new PaymentInitializationException("payment context unavailable for order " + orderId);
            })
            .body(OrderPaymentContext.class);
    } catch (PaymentInitializationException e) {
        throw e;
    } catch (RestClientException e) {
        throw new PaymentInitializationException("...", e);
    }
}
```

**Backing endpoint mapping per tool:**

| Tool | Backing Endpoint | Controller Signature (analog) |
|------|-----------------|-------------------------------|
| `search_products` | `GET /products?q=&categoryId=&page=&size=` | `ProductController.list()` — `product-service/src/main/java/com/n11/product/product/ProductController.java` line 43 |
| `get_product` | `GET /products/{id}` | `ProductController.get()` — same file, line 52 |
| `list_categories` | `GET /categories` | `CategoryController.list()` — `product-service/.../category/CategoryController.java` line 22 |
| `add_to_cart` | `POST /cart/items` body `{productId, qty}` | `CartController.add()` — `cart-service/.../CartController.java` line 39 |
| `view_cart` | `GET /cart` | `CartController.get()` — same file, line 35 |
| `update_cart_item` | `PATCH /cart/items/{productId}` body `{qty}` | `CartController.update()` — same file, line 44 |
| `remove_from_cart` | `DELETE /cart/items/{productId}` | `CartController.remove()` — same file, line 51 |
| `create_order` | `POST /orders` header `Idempotency-Key: <uuid>` | `OrderController.create()` — `order-service/.../OrderController.java` line 36 |
| `get_order_status` | `GET /orders/{id}` | `OrderController.detail()` — same file, line 52 |
| `get_payment_link` | `GET /payments/{orderId}` | `PaymentController.getPaymentForOrder()` — `payment-service/.../PaymentController.java` line 69 |

**X-User-Id forwarding pattern** — tool implementations receive `ToolContext(userId, correlationId, ...)` and must set `X-User-Id` on outgoing requests. The `RestClient.Builder` from `common-logging.RestClientConfig` already attaches `X-Correlation-Id` via `CorrelationIdRestClientInterceptor`. Add `X-User-Id` manually:
```java
restClient.post()
    .uri(cartBaseUrl + "/cart/items")
    .header("X-User-Id", ctx.userId().toString())
    .contentType(MediaType.APPLICATION_JSON)
    .body(requestBody)
    .retrieve()
    .body(CartView.class);
```

---

### `ai-service/build.gradle.kts`

**Analog:** `cart-service/build.gradle.kts` (full file)

**Core pattern:**
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
    // NO spring-boot-starter-amqp — ai-service has no AMQP in v1

    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")
    implementation("org.springframework.cloud:spring-cloud-starter-config")

    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.17")
    implementation("org.flywaydb:flyway-core:12.5.0")
    implementation("org.flywaydb:flyway-database-postgresql:12.5.0")

    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("net.logstash.logback:logstash-logback-encoder:8.0")

    implementation(project(":ai-port"))
    implementation(project(":agent-toolset"))
    implementation(project(":common-error"))
    implementation(project(":common-logging"))
    // NO :common-outbox, NO :common-events (ai-service publishes no events in v1)

    implementation("com.google.genai:google-genai:1.52.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:junit-jupiter")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveBaseName.set("ai-service")
}

jib {
    from { image = "eclipse-temurin:21-jre" }
    to { image = "n11/ai-service:dev" }
    container {
        ports = listOf("8088")  // next port after notification-service:8087
        jvmFlags = listOf("-XX:+UseG1GC", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0")
    }
}
```

---

### `search-service/build.gradle.kts`

**Analog:** `cart-service/build.gradle.kts` minus AMQP and common-events

**Key differences from ai-service:**
- NO `google-genai` dependency (uses `EmbeddingProvider` port only)
- NO `springdoc` in v1 (D-09: no REST endpoints)
- NO `spring-boot-starter-amqp`
- Port: `8089` (next after ai-service)
- `jib.to.image = "n11/search-service:dev"`

---

### `ai-service/.../interfaces/rest/ChatController.java` (controller, request-response)

**Analog:** `cart-service/src/main/java/com/n11/cart/cart/CartController.java` (full file, 70 lines)

**Imports pattern** (lines 1-11):
```java
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
```

**X-User-Id resolution pattern** (lines 59-69) — copy verbatim:
```java
private UUID resolveUserId(HttpServletRequest req) {
    String h = req.getHeader(HEADER_USER_ID);
    if (h == null || h.isBlank()) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Kimlik doğrulaması gerekli");
    }
    try {
        return UUID.fromString(h);
    } catch (IllegalArgumentException e) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Geçersiz kullanıcı kimliği");
    }
}
```

**Controller shape** (lines 26-57):
```java
@RestController
@RequestMapping("/chat")
public class ChatController {

    private static final String HEADER_USER_ID = "X-User-Id";

    private final ChatUseCase chatUseCase;

    public ChatController(ChatUseCase chatUseCase) { this.chatUseCase = chatUseCase; }

    @PostMapping
    public ChatResponse chat(HttpServletRequest req, @Valid @RequestBody ChatRequest body) {
        UUID userId = resolveOptionalUserId(req);  // nullable for guests
        return chatUseCase.chat(body.conversationId(), body.message(), userId);
    }
}
```

---

### `ai-service/.../interfaces/sse/ChatStreamController.java` (controller, streaming — net-new)

**No direct analog in codebase.** Pattern comes from RESEARCH.md §Pattern 4 (SseEmitter Threading Model). Key rules:

1. Return `SseEmitter` with `new SseEmitter(0L)` — gateway controls timeout via `metadata.response-timeout: -1`
2. Delegate loop to `executor.execute(...)` — never block the request thread
3. MDC propagation inside the executor lambda (copy correlationId from request header, set before loop, clear in `finally`)
4. Never call `emitter.send()` after `emitter.complete()` — IllegalStateException
5. Produces `MediaType.TEXT_EVENT_STREAM_VALUE`

**SSE event names (D-07):** `delta`, `tool_call`, `tool_result`, `done`, `error` — all typed via `SseEmitter.event().name(...).data(...)`.

**Gateway route that must be activated** (from `config-server/.../config/api-gateway.yml`, lines 84-99 of the commented block):
```yaml
- id: ai-service-chat-stream
  uri: lb://AI-SERVICE
  predicates:
    - Path=/api/v1/chat/stream/**
  metadata:
    response-timeout: -1
    connect-timeout: 5000
  filters:
    - StripPrefix=2
    - PreserveHostHeader=true
  # NO ModifyResponseBody filter, NO RetryFilter — both buffer and break streaming
```

---

### `ai-service/.../GeminiChatAdapter.java` + `GeminiEmbeddingAdapter.java` (adapter, request-response)

**Analog:** `payment-service/src/main/java/com/n11/payment/iyzico/DefaultIyzicoCheckoutClient.java`

**Pattern shape** (implements an interface, all SDK types internal, translated from/to neutral DTOs):
```java
@Component
@ConditionalOnProperty(name = "ai.provider", havingValue = "gemini", matchIfMissing = true)
public class GeminiChatAdapter implements ChatProvider {

    private static final Logger log = LoggerFactory.getLogger(GeminiChatAdapter.class);

    private final Client client;
    private String chatModel;
    private final String fallbackChatModel;

    public GeminiChatAdapter(
            @Value("${ai.gemini.model.chat:gemini-3-flash-preview}") String chatModel,
            @Value("${ai.gemini.model.fallback:gemini-2.5-flash}") String fallbackChatModel,
            @Value("${ai.gemini.api-key:${GEMINI_API_KEY:}}") String apiKey) {
        this.chatModel = chatModel;
        this.fallbackChatModel = fallbackChatModel;
        this.client = Client.builder().apiKey(apiKey).build();
    }

    @Override
    public ChatResponse chat(List<ChatMessage> messages, List<ToolSchema> tools) {
        // ALL Gemini SDK types (Content, Part, FunctionCall, etc.) stay in this method.
        // Convert neutral DTOs → Gemini types → call SDK → convert back to neutral DTOs.
        // NEVER return Gemini types through the ChatProvider port.
    }
}
```

**Startup model probe** (ApplicationReadyEvent listener — Pitfall #1):
```java
@EventListener(ApplicationReadyEvent.class)
public void verifyModel() {
    try {
        client.models.get(chatModel);
        log.info("ai-service: resolved chat model = {} (provider = {})",
                 chatModel, getClass().getSimpleName());
    } catch (Exception e) {
        log.warn("ai-service: primary model {} not available ({}), falling back to {}",
                 chatModel, e.getMessage(), fallbackChatModel);
        this.chatModel = fallbackChatModel;
    }
}
```

---

### `ai-service/.../EchoChatProvider.java` (adapter, net-new — no codebase analog)

**No analog.** This is the SOLID demonstration artifact. Shape:

```java
@Component
@ConditionalOnProperty(name = "ai.provider", havingValue = "echo")
public class EchoChatProvider implements ChatProvider {

    @Override
    public ChatResponse chat(List<ChatMessage> messages, List<ToolSchema> tools) {
        String lastUserMessage = messages.stream()
            .filter(m -> m.role() == MessageRole.USER)
            .reduce((a, b) -> b)
            .map(ChatMessage::content)
            .orElse("(boş mesaj)");
        return new ChatResponse("[ECHO] " + lastUserMessage, List.of(), "STOP");
    }

    @Override
    public void chatStream(List<ChatMessage> messages, List<ToolSchema> tools,
                           Consumer<String> onDelta, Runnable onComplete, Consumer<Throwable> onError) {
        onDelta.accept("[ECHO] " + /* same extraction */ "");
        onComplete.run();
    }
}
```

**Test activation:** Set `ai.provider=echo` in `config-server/.../config/ai-service.yml`. Restart ai-service. Hit `/api/v1/chat`. SOLID proof point for graders.

---

### `ai-service/.../infrastructure/http/ProductClient.java` + `CartClient.java` + `OrderClient.java` + `PaymentClient.java`

**Analog:** `payment-service/src/main/java/com/n11/payment/order/OrderPaymentContextClient.java` (full file, 43 lines)

**Copy constructor pattern** (lines 16-19):
```java
@Component
public class ProductClient {

    private final RestClient restClient;

    public ProductClient(RestClient.Builder builder,
                         @Value("${app.clients.product.base-url:http://product-service:8082}") String baseUrl) {
        this.restClient = builder.baseUrl(stripTrailingSlash(baseUrl)).build();
    }
```

Note: `RestClient.Builder` comes from `common-logging.RestClientConfig` which auto-registers `CorrelationIdRestClientInterceptor` — do NOT create a raw `RestClient.builder()`. Always inject `RestClient.Builder`.

**stripTrailingSlash helper** (lines 37-42) — copy verbatim:
```java
private static String stripTrailingSlash(String value) {
    if (value == null || value.isBlank()) {
        return "http://product-service:8082";
    }
    return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
}
```

---

### `ai-service/src/main/resources/db/migration/V1__init_ai.sql`

**Analog:** `cart-service/src/main/resources/db/migration/V2__init_cart.sql` (full file, 26 lines)

**Structural pattern:**
```sql
-- V1__init_ai.sql
-- NO `CREATE SCHEMA` — schema 'ai' already created by infra/postgres/init.sh.
-- Flyway's create-schemas: false (in ai-service.yml) enforces this.

CREATE TABLE ai_conversations (
    id           UUID         PRIMARY KEY,
    user_id      UUID,               -- NULL for guest sessions (D-02/D-03)
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE messages (
    id               UUID         PRIMARY KEY,
    conversation_id  UUID         NOT NULL REFERENCES ai_conversations(id) ON DELETE CASCADE,
    role             VARCHAR(16)  NOT NULL,   -- USER | ASSISTANT | TOOL
    content          TEXT,
    tool_calls_json  TEXT,         -- JSON array of ToolCallRequest
    tool_results_json TEXT,        -- JSON array of ToolCallResult
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_messages_conversation_created ON messages (conversation_id, created_at);
```

---

### `search-service/src/main/resources/db/migration/V1__init_search.sql`

**Analog:** `cart-service/src/main/resources/db/migration/V2__init_cart.sql` (structural shape)

**Key addition — pgvector column type** (verified via `infra/postgres/init.sh` which creates the `vector` extension at line 47):
```sql
-- V1__init_search.sql
-- pgvector 'vector' type is available because infra/postgres/init.sh runs
-- `CREATE EXTENSION IF NOT EXISTS vector;` as superuser before service users connect.
-- Schema 'search' already exists; Flyway's create-schemas: false enforces this.

CREATE TABLE product_embeddings (
    product_id   UUID          PRIMARY KEY,
    embedding    vector(768),  -- D-09: 768-dim per STACK.md recommendation
    name_tr      TEXT          NOT NULL,
    indexed_at   TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- HNSW index for approximate nearest-neighbor search (v2 activation only).
-- Commented out for v1 skeleton — no queries run against this table in v1.
-- CREATE INDEX idx_product_embeddings_hnsw
--     ON product_embeddings USING hnsw (embedding vector_cosine_ops)
--     WITH (m = 16, ef_construction = 64);
```

---

### `config-server/src/main/resources/config/ai-service.yml`

**Analog:** `config-server/src/main/resources/config/cart-service.yml` (full file, 65 lines)

**Core pattern to copy:**
```yaml
server:
  port: 8088

eureka:
  client:
    registry-fetch-interval-seconds: 5
    eureka-server-connect-timeout-seconds: 5
    eureka-server-read-timeout-seconds: 8
    initial-instance-info-replication-interval-seconds: 5
  instance:
    lease-renewal-interval-in-seconds: 10
    lease-expiration-duration-in-seconds: 30
    prefer-ip-address: true

spring:
  datasource:
    url: jdbc:postgresql://postgres:5432/n11
    username: ai_user
    password: ${AI_DB_PASSWORD}
    hikari:
      maximum-pool-size: 3   # Pitfall #8 budget (ai-service pool ~3)
      connection-init-sql: SET search_path = ai, public

  flyway:
    enabled: true
    locations: classpath:db/migration
    schemas: ai
    default-schema: ai
    create-schemas: false
    user: ${spring.datasource.username}
    password: ${spring.datasource.password}

  jpa:
    open-in-view: false
    properties:
      hibernate:
        default_schema: ai

  # NO rabbitmq section — ai-service has no AMQP in v1

ai:
  provider: gemini              # flip to 'echo' for SOLID demo swap
  gemini:
    api-key: ${GEMINI_API_KEY}
    model:
      chat: gemini-3-flash-preview
      fallback: gemini-2.5-flash
      embedding: gemini-embedding-2

app:
  clients:
    product:
      base-url: ${PRODUCT_SERVICE_BASE_URL:http://product-service:8082}
    cart:
      base-url: ${CART_SERVICE_BASE_URL:http://cart-service:8084}
    order:
      base-url: ${ORDER_SERVICE_BASE_URL:http://order-service:8085}
    payment:
      base-url: ${PAYMENT_SERVICE_BASE_URL:http://payment-service:8086}

management:
  endpoints:
    web:
      exposure:
        include: health,info

springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
    path: /swagger-ui.html
```

---

### `config-server/src/main/resources/config/search-service.yml`

**Analog:** `config-server/src/main/resources/config/cart-service.yml`

**Key differences:**
- `server.port: 8089`
- `username: search_user`, `password: ${SEARCH_DB_PASSWORD}`
- `hikari.maximum-pool-size: 2` (Pitfall #8 — skeleton only)
- `connection-init-sql: SET search_path = search, public`
- `schemas: search`, `default-schema: search`, `hibernate.default_schema: search`
- NO `rabbitmq` section
- NO `springdoc` section (D-09: no REST endpoints in v1)
- NO `ai.*` section (search-service uses only `EmbeddingProvider` port, config is handled by the Gemini adapter in ai-service)

---

### `config-server/src/main/resources/config/api-gateway.yml` (SSE route activation)

**Analog:** The existing commented anchor in the same file. The verbatim block to uncomment and add to the `routes:` list (lines 84-99 of current file):

```yaml
# ADD these two routes under the existing routes: list (after payment-service route):
- id: ai-service-chat-stream
  uri: lb://AI-SERVICE
  predicates:
    - Path=/api/v1/chat/stream/**
  metadata:
    response-timeout: -1          # SSE: no per-route timeout
    connect-timeout: 5000
  filters:
    - StripPrefix=2               # /api/v1 stripped → /chat/stream/...
    - PreserveHostHeader=true
  # NO ModifyResponseBody filter, NO RetryFilter — both buffer and break streaming

- id: ai-service-chat
  uri: lb://AI-SERVICE
  predicates:
    - Path=/api/v1/chat/**
  filters:
    - StripPrefix=2

# search-service route stays commented until v2 (AI-V2-01):
# - id: search-service
#   uri: lb://SEARCH-SERVICE
#   predicates:
#     - Path=/api/v1/search/**
#   filters:
#     - StripPrefix=2
```

Also add ai-service to the `springdoc.swagger-ui.urls` list:
```yaml
      - name: ai-service
        url: /api/v1/chat/v3/api-docs
# search-service entry stays commented (no Springdoc in v1)
```

---

### `docker-compose.yml` (ai-service + search-service entries)

**Analog:** `docker-compose.yml` notification-service block (lines 431-457)

**ai-service entry — copy notification-service block and adjust:**
```yaml
  # ---------------------------------------------------------------------------
  # ai-service -- Phase 8 (port 8088).
  # Chat assistant + SSE streaming. Calls product/cart/order/payment services
  # via Eureka discovery + X-User-Id forwarding (CONTEXT D-05).
  # Image: ./gradlew :ai-service:jibDockerBuild
  # ---------------------------------------------------------------------------
  ai-service:
    image: n11/ai-service:dev
    container_name: n11-ai-service
    env_file:
      - .env
    environment:
      SPRING_CONFIG_IMPORT: configserver:http://config-server:8888?fail-fast=true&max-attempts=10&max-interval=1500&multiplier=1.2&initial-interval=1100
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka-server:8761/eureka/
      SPRING_PROFILES_ACTIVE: docker
    depends_on:
      postgres:
        condition: service_healthy
      eureka-server:
        condition: service_healthy
      config-server:
        condition: service_healthy
      identity-service:
        condition: service_healthy   # X-User-Id mesh must be in place
    healthcheck:
      test: ["CMD-SHELL", "wget -q -O- http://localhost:8088/actuator/health | grep -q '\"status\":\"UP\"'"]
      interval: 5s
      timeout: 3s
      retries: 6
      start_period: 25s
    restart: unless-stopped
    networks:
      - n11-net
```

**search-service entry — same shape, port 8089, no identity-service dep needed (skeleton only):**
```yaml
  search-service:
    image: n11/search-service:dev
    container_name: n11-search-service
    env_file:
      - .env
    environment:
      SPRING_CONFIG_IMPORT: configserver:http://config-server:8888?fail-fast=true&max-attempts=10&max-interval=1500&multiplier=1.2&initial-interval=1100
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka-server:8761/eureka/
      SPRING_PROFILES_ACTIVE: docker
    depends_on:
      postgres:
        condition: service_healthy
      eureka-server:
        condition: service_healthy
      config-server:
        condition: service_healthy
    healthcheck:
      test: ["CMD-SHELL", "wget -q -O- http://localhost:8089/actuator/health | grep -q '\"status\":\"UP\"'"]
      interval: 5s
      timeout: 3s
      retries: 6
      start_period: 25s
    restart: unless-stopped
    networks:
      - n11-net
```

---

### `settings.gradle.kts` (new module inclusions)

**Analog:** existing `settings.gradle.kts` (lines 3-17)

**Add four new entries to the `include(...)` block:**
```kotlin
include(
    // ... existing entries ...
    "ai-port",
    "agent-toolset",
    "ai-service",
    "search-service"
)
```

---

### `infra/postgres/init.sh` (verify-only — already complete)

The file already contains (lines 39-40, 111-115, 136-137):
- `AI_DB_PASSWORD` and `SEARCH_DB_PASSWORD` required-var checks
- `ai` schema + `ai_user` + USAGE on public + search_path
- `search` schema + `search_user` + USAGE on public + search_path
- REVOKE matrix entries for both schemas across all other users

**No modifications needed.** Phase 8 planner should verify these entries are present and correct before adding any `ALTER` commands. If needed, the extension pattern per schema is (lines 55-59 for `identity`):
```bash
CREATE SCHEMA IF NOT EXISTS <name>;
CREATE USER <name>_user WITH PASSWORD '${<NAME>_DB_PASSWORD}';
ALTER SCHEMA <name> OWNER TO <name>_user;
GRANT USAGE ON SCHEMA public TO <name>_user;
ALTER USER <name>_user SET search_path = <name>, public;
```

---

### `common-error/src/main/java/com/n11/error/ApiErrorCode.java` (enum extension)

**Analog:** existing file (full, 38 lines)

**Extension pattern** — add three new entries alongside existing five:
```java
// Existing (lines 15-19):
VALIDATION("validation",    "Validation failed"),
NOT_FOUND("not-found",      "Resource not found"),
CONFLICT("conflict",        "State conflict"),
UNAUTHORIZED("unauthorized","Unauthorized"),
INTERNAL("internal",        "Internal server error");

// New entries for Phase 8 AI failures:
UPSTREAM_LLM_ERROR("upstream-llm-error",     "LLM upstream error"),
TOOL_VALIDATION_FAILED("tool-validation",    "Tool argument validation failed"),
RATE_LIMITED("rate-limited",                 "Rate limit exceeded");
```

---

### `infra-tests/.../AiServiceTestConfig.java` (bean disambiguation)

**Analog:** `infra-tests/src/test/java/com/n11/infratests/saga/PaymentServiceTestConfig.java` (full file, 68 lines)

**Copy verbatim, substituting ai-service packages:**
```java
@SpringBootApplication(scanBasePackages = {
    "com.n11.ai",      // ai-service application + domain + infrastructure + interfaces
    "com.n11.logging"  // common-logging RabbitTemplateConfig, CorrelationIdFilter
    // NO com.n11.events, NO com.n11.outbox — ai-service has no AMQP in v1
})
@ComponentScan(
    basePackages = {
        "com.n11.ai",
        "com.n11.logging"
    },
    excludeFilters = {
        @ComponentScan.Filter(
            type = FilterType.ANNOTATION,
            classes = org.springframework.boot.autoconfigure.SpringBootApplication.class
        )
    }
)
@EntityScan(basePackages = {"com.n11.ai"})
@EnableJpaRepositories(basePackages = {"com.n11.ai"})
class AiServiceTestConfig {
    // Bean for EchoChatProvider so tests don't need a real GEMINI_API_KEY:
    // @Bean @Primary ChatProvider chatProvider() { return new EchoChatProvider(); }
}
```

**Key difference from PaymentServiceTestConfig:** no `@EnableScheduling` (ai-service has no scheduled job in v1), no `com.n11.outbox` in scan packages.

---

### `infra-tests/build.gradle.kts` (new service deps)

**Analog:** existing `infra-tests/build.gradle.kts` (lines 35-53 for service deps)

**Add two new `testImplementation` entries:**
```kotlin
testImplementation(project(":ai-service"))
testImplementation(project(":search-service"))
// Note: agent-toolset and ai-port are transitive through ai-service
```

---

## Shared Patterns

### X-User-Id Header Resolution (trust-the-gateway)
**Source:** `cart-service/src/main/java/com/n11/cart/cart/CartController.java` lines 59-69
**Apply to:** `ChatController`, `ChatStreamController`
```java
private static final String HEADER_USER_ID = "X-User-Id";

private UUID resolveUserId(HttpServletRequest req) {
    String h = req.getHeader(HEADER_USER_ID);
    if (h == null || h.isBlank()) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Kimlik doğrulaması gerekli");
    }
    try {
        return UUID.fromString(h);
    } catch (IllegalArgumentException e) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Geçersiz kullanıcı kimliği");
    }
}
```
For guest-aware endpoints (D-03): use `resolveOptionalUserId()` returning `Optional<UUID>` — same pattern but `required = false` header.

### RFC-7807 Error Handling
**Source:** `common-error/src/main/java/com/n11/error/ProblemDetailControllerAdvice.java` (full file, 97 lines)
**Apply to:** `ai-service` (auto-applied via `common-error` dep + Spring auto-config)
```java
// Auto-registered via META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
// No manual @Import needed — add :common-error to build.gradle.kts and it is active.
// For SSE errors: catch in executor thread, emit event:error SSE event, then call emitter.completeWithError()
// Do NOT rely on @RestControllerAdvice for SSE stream errors — the response is already committed.
```

### Correlation ID Propagation
**Source:** `common-logging/src/main/java/com/n11/logging/CorrelationIdFilter.java` lines 25-45
**Apply to:** All inbound HTTP requests in ai-service (auto-applied via `common-logging` dep)

**For SSE streaming (manual MDC propagation in executor thread):**
```java
executor.execute(() -> {
    String cid = correlationIdFromRequest != null
        ? correlationIdFromRequest
        : UUID.randomUUID().toString();
    MDC.put(CorrelationIdFilter.MDC_KEY, cid);
    try {
        chatUseCase.handleStream(conversationId, message, userId, cid, emitter);
    } finally {
        MDC.clear();  // critical: executor threads are reused
    }
});
```

### RestClient with Correlation-ID Auto-Propagation
**Source:** `common-logging/src/main/java/com/n11/logging/RestClientConfig.java` lines 21-29
**Apply to:** All `*Client.java` files in ai-service infrastructure/http layer and agent-toolset tools
```java
// Inject RestClient.Builder (not RestClient.builder()) — pre-wired with CorrelationIdRestClientInterceptor
public ProductClient(RestClient.Builder builder,
                     @Value("${app.clients.product.base-url:...}") String baseUrl) {
    this.restClient = builder.baseUrl(stripTrailingSlash(baseUrl)).build();
}
```

### Flyway Schema Convention
**Source:** `config-server/src/main/resources/config/cart-service.yml` lines 31-39
**Apply to:** `ai-service.yml`, `search-service.yml`
```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    schemas: <schema-name>
    default-schema: <schema-name>
    create-schemas: false      # schema already created by init.sh
    user: ${spring.datasource.username}
    password: ${spring.datasource.password}
```

### Bean Disambiguation in infra-tests
**Source:** `infra-tests/src/test/java/com/n11/infratests/saga/PaymentServiceTestConfig.java` (full file, 68 lines)
**Apply to:** `AiServiceTestConfig.java`
Key: `excludeFilters` on `@SpringBootApplication`-annotated classes prevents classpath-wide entity scan explosion.

---

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `ai-service/.../interfaces/sse/ChatStreamController.java` | controller | streaming | No SSE controller exists anywhere in the codebase |
| `ai-service/.../domain/ToolDispatcher.java` | service | event-driven | No tool dispatch / function-calling orchestrator exists |
| `ai-service/.../EchoChatProvider.java` | adapter | request-response | No second adapter pattern exists; this is the SOLID proof-of-concept |
| `agent-toolset/.../AgentTool.java` (interface shape) | port interface | — | No tool-definition abstraction exists; shape comes from RESEARCH.md §Architecture Patterns |

---

## Metadata

**Analog search scope:** `common-error/`, `common-events/`, `common-logging/`, `payment-service/`, `cart-service/`, `order-service/`, `product-service/`, `infra-tests/`, `config-server/`, `docker-compose.yml`, `infra/postgres/init.sh`, `settings.gradle.kts`
**Files scanned:** 47
**Pattern extraction date:** 2026-05-01
