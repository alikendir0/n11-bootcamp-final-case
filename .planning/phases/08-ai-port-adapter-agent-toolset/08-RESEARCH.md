# Phase 8: AI Port + Adapter + Agent Toolset - Research

**Researched:** 2026-05-01
**Domain:** Provider-agnostic LLM abstraction, google-genai 1.52.0, SSE streaming, agent toolset, Postgres conversation state
**Confidence:** HIGH (SDK verified against GitHub README + Google AI docs; architecture verified against existing codebase)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** Conversation state in Postgres (`ai_conversations` + `messages` tables). JPA + Flyway, same convention as every other service.
- **D-02:** Retention forever for authed users (rows keyed by `user_id`, no TTL). Ephemeral for guests (in-memory Map only, no DB row). No cleanup job in v1.
- **D-03:** Guest sessions in an in-memory `Map<conversationId, Conversation>` with 1-hour idle TTL. Frontend generates UUID `conversationId`, persists in `localStorage`, sends with every request. No anonymous JWT, no HttpOnly cookie. DB tables written only when request carries `X-User-Id`.
- **D-04:** Mutating tools enforce auth via `tool.requiresAuth()`. ToolDispatcher returns `ToolResult.Err("AUTH_REQUIRED", "...")` for guests hitting mutating tools. All 10 tools remain in Gemini's schema — it learns from AUTH_REQUIRED errors.
- **D-05:** Internal hops from ai-service to cart/order/product/payment use direct REST + Eureka discovery + forwarded `X-User-Id`/`X-User-Roles` headers. No JWT minting. Same pattern as Phase 6.
- **D-06:** Manual function-calling loop. ai-service drives every step (send → inspect → dispatch → append → loop). No AFC. Reasons: SSE interleaving control, Pitfall #10 validation centralized in ToolDispatcher, clean EchoChatProvider port contract.
- **D-07:** SSE wire format uses typed events: `event: delta` (Gemini token chunk), `event: tool_call` (before execution), `event: tool_result` (after), `event: done` (terminal with finalText), `event: error` (terminal failure).
- **D-08:** Belt-and-braces ID provenance: conversation-scoped `seenIds` set (hallucinated ID returns UNKNOWN_ID error to Gemini) + live repo lookup before mutating (catches deleted IDs).
- **D-09:** search-service ships as TRUE SKELETON: Gradle module + Boot app + Flyway V1 (`product_embeddings` with vector(768)) + `EmbeddingProvider` injected into a `SearchService` with TODO body. No REST endpoint in v1. Second consumer of AI port to prove port substitutability.
- **D-10:** `search_products` tool v1 backs onto product-service `GET /products?q=` (ILIKE+GIN from Phase 4 PROD-04). EmbeddingProvider wired into search-service but not invoked at runtime.

### Claude's Discretion

- Gemini model identifier verification (researcher fetches; pin `ai.gemini.model.chat=gemini-3-flash-preview` or current, fallback `gemini-2.5-flash`).
- Embedding dimension (`gemini-embedding-2` default 3072; STACK.md recommends 768).
- HikariCP pool sizing for ai-service (~3) and search-service (~2).
- System prompt template wording (Turkish, ~150-300 words).
- Conversation idle TTL exact value (D-03: 1-hour start point; 30-120 min range).
- Optional `/chat/conversations/{id}/claim` guest-to-authed migration endpoint (skip unless trivially cheap).
- `ToolRegistry` Spring config and module boundaries.
- Testcontainers test posture (`EchoChatProvider` for CI, one real-Gemini smoke test gated on `GEMINI_API_KEY`).

### Deferred Ideas (OUT OF SCOPE)

- `/chat/conversations/{id}/claim` endpoint (D-03: optional).
- Real semantic search (AI-V2-01 / AI-V2-02).
- Conversational PDP summaries, compare-products, cart-aware suggestions (all v2).
- Streaming chat UX with mid-stream cancel (Phase 11 frontend, v2).
- mcp-server auth bridge (Phase 9).
- Conversation TTL cleanup job for guest sessions.
- `/api/v1/search/**` v1 endpoint.
- Spring AI swap for Gemini chat (if Spring AI 1.2 ever ships Gemini 3 Flash support).
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| AI-01 | `ChatProvider` and `EmbeddingProvider` ports in zero-dependency `ai-port` module with neutral DTOs | Standard Stack §ai-port; Architecture Patterns §hexagonal layering; Pitfall #7 mitigation |
| AI-02 | `GeminiChatAdapter` via google-genai 1.52.0 | SDK Code Examples §GeminiChatAdapter skeleton; Standard Stack §google-genai |
| AI-03 | `GeminiEmbeddingAdapter` via google-genai 1.52.0 | SDK Code Examples §GeminiEmbeddingAdapter; Standard Stack §embedding |
| AI-04 | `EchoChatProvider` second adapter proving port substitutability | Code Examples §EchoChatProvider skeleton; Don't Hand-Roll notes |
| AI-05 | `agent-toolset` shared module with 10 canonical tools | Architecture Patterns §agent-toolset module shape; Code Examples §tool interface |
| AI-06 | ai-service chat assistant with Turkish responses via Gemini 3 Flash; verified model ID at startup | Pitfall #1 mitigations; Standard Stack §model identifier verification |
| AI-07 | ai-service dispatches Gemini function-calls via shared agent toolset; ID validation | Code Examples §manual function-calling loop; Pitfall #10 mitigations |
| AI-08 | SSE streaming with mid-stream tool-call indicators | Code Examples §SseEmitter pattern; D-07 wire format |
| AI-09 | Conversation state persisted in Postgres (Redis vs Postgres decided = Postgres per D-01) | Conversation persistence schema; Flyway DDL |
| AI-10 | System prompt forces Turkish `dil: tr-TR`; test catches drift | System prompt skeleton; Validation Architecture §Turkish drift test |
| AI-14 | Chat assistant and MCP both hit same gateway with same JWT/header injection | Architecture Patterns §D-05 REST hop pattern; api-contracts §4 |
| AI-15 | Cart added by chat is visible in cart page; cart-service is single source of truth | D-05 tool dispatch via direct REST to cart-service; Integration Points |
| QUAL-08 | SOLID auditable — clean layering, explicit ports for external integrations | ai-port module shape; EchoChatProvider; Architecture Patterns §hexagonal |
</phase_requirements>

---

## Summary

Phase 8 builds the SOLID grading centerpiece of the project. It delivers three things that are distinct but tightly coupled: (1) the `ai-port` zero-dependency Gradle module with neutral DTOs that is the tangible evidence of SOLID compliance, (2) the Gemini adapters that implement those ports, and (3) the `agent-toolset` shared Gradle module that is Phase 9's handoff point.

The google-genai SDK is now at version 1.52.0 (released 2026-04-30; STACK.md pinned 1.51.0 — a patch bump, no breaking changes). The `gemini-3-flash-preview` model identifier is confirmed active as of April 2026. Function calling in Java requires a manual loop since the SDK's Automatic Function Calling (AFC) is not used per D-06 — the loop is straightforward but requires care around response inspection (check each Part for `FunctionCall` before assuming text).

The Spring MVC `SseEmitter` is the correct choice for streaming given ai-service's synchronous tool dispatch loop (RestClient, not WebFlux). The key pitfall is the `SseEmitter` ABBA deadlock in Spring Framework 6.2.x — ai-service is on Spring Boot 3.5.14 (Spring Framework 6.3.x), so this issue from 6.2.x should be resolved; however the safe pattern is still to run the loop on a separate executor thread and never call `emitter.send()` from within a lock.

The existing codebase is well-prepared: `infra/postgres/init.sh` already has `ai` and `search` schemas + users + deny matrix entries. Docker-compose already references `AI_DB_PASSWORD` and `SEARCH_DB_PASSWORD`. The SSE route anchor is already commented-in `api-gateway.yml`. The planner needs to activate it and wire two new Gradle modules + two new services.

**Primary recommendation:** Implement `ai-port` and `agent-toolset` as zero-dependency library modules first. Then wire `GeminiChatAdapter` and `GeminiEmbeddingAdapter` in `ai-adapter` (or directly in `ai-service`). Bootstrap `EchoChatProvider` in ai-service's test scope. Implement the SSE manual loop last, after the functional non-streaming path is tested end-to-end with `EchoChatProvider`.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| ChatProvider / EmbeddingProvider ports | `ai-port` library module (zero-dep) | — | Ports must be dependency-free to serve as the SOLID demonstration |
| Gemini adapter implementations | `ai-service` infrastructure layer | — | Only importer of `com.google.genai.*` in the codebase |
| EchoChatProvider (second adapter) | `ai-service` (test scope or main with @Profile) | — | Lives alongside the Gemini adapter; substitutable via `ai.provider` property |
| Agent toolset (10 tools) | `agent-toolset` shared Gradle module | — | Shared with Phase 9 mcp-server; cannot live in ai-service |
| ToolDispatcher + seenIds provenance | `ai-service` domain layer | — | Per D-06: Pitfall #10 logic centralized here, not in tool implementations |
| SSE streaming / chat endpoint | `ai-service` interfaces layer | — | Spring MVC SseEmitter; RestClient for sync tool dispatch |
| Conversation persistence | `ai-service` infrastructure (JPA) | — | Per D-01: Postgres `ai` schema; JPA + Flyway V1 |
| Guest conversation state | `ai-service` application layer (in-memory Map) | — | Per D-03: ephemeral, no DB row |
| product_embeddings table / EmbeddingProvider consumer | `search-service` (skeleton only) | — | Per D-09: proves port substitutability across services |
| Internal REST hops (tool dispatch) | `ai-service` infrastructure/http layer | product/cart/order/payment-service | Per D-05: RestClient + Eureka + X-User-Id forwarding |
| Gateway SSE route activation | `api-gateway.yml` in config-server | — | Uncomment the existing anchor; add ai-chat and search stub routes |

---

## Standard Stack

### Core (Phase 8 specific)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| google-genai | **1.52.0** | Gemini chat + embeddings SDK | Latest GA as of 2026-04-30; STACK.md pinned 1.51.0 — safe to bump to 1.52.0; the sole Gemini SDK in the codebase [VERIFIED: github.com/googleapis/java-genai CHANGELOG] |
| Spring Boot Starter Web | 3.5.14 (BOM) | Servlet container + SseEmitter for ai-service | ai-service is a standard MVC service; SseEmitter is in `spring-webmvc` |
| Spring Boot Starter Data JPA | 3.5.14 (BOM) | Conversation persistence | Same pattern as every service in the project |
| Flyway | 12.5.0 | DB migrations for ai_conversations + messages + search product_embeddings | Consistent with all other services |
| Springdoc | 2.8.17 | OpenAPI docs for ai-service chat endpoint | Per QUAL-01 |

### Supporting (Phase 8)

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Spring AI BOM (1.1.5) | imported as BOM only | Version alignment; NOT used in ai-service directly | Phase 9 (mcp-server) uses `spring-ai-starter-mcp-server-webmvc`; Phase 8 does NOT import Spring AI starters into ai-service |
| common-error | project dep | `ProblemDetailControllerAdvice` + new AI error codes | ai-service adds `UPSTREAM_LLM_ERROR`, `TOOL_VALIDATION_FAILED`, `RATE_LIMITED` to `ApiErrorCode` |
| common-logging | project dep | MDC correlation ID propagation | ai-service SSE endpoint generates or copies `X-Correlation-Id` |
| common-events | project dep | Shared event envelope (reference only; ai-service publishes no events in v1) | Import for compile-time type access if needed; not strictly required |

### NOT Used (important negatives)

| Avoid | Reason |
|-------|--------|
| Spring AI Gemini chat model (`VertexAiGeminiChatModel`) | Spring AI 1.1.5 does not list `gemini-3-flash-preview`; using google-genai directly per STACK.md "What NOT to Use" [VERIFIED: STACK.md] |
| `spring-ai-starter-mcp-server-webmvc` in ai-service | MCP starter is Phase 9 (mcp-server) only. ai-service has no MCP wire layer |
| `common-outbox` | ai-service publishes no events in v1 |
| Spring Boot Starter AMQP | ai-service has no AMQP consumers or publishers in v1 |
| LangChain4j | Redundant with google-genai; adds a third abstraction layer |

### Installation (new modules to add to settings.gradle.kts)

```bash
# New Gradle modules to include() in settings.gradle.kts:
#   "ai-port"         — zero-dependency library module
#   "agent-toolset"   — shared tool definitions (imports ai-port)
#   "ai-service"      — Spring Boot service (imports ai-port, agent-toolset)
#   "search-service"  — Spring Boot service skeleton (imports ai-port)
```

```kotlin
// ai-port/build.gradle.kts (java-library only — NO Spring Boot, NO Gemini SDK)
plugins {
    id("java-library")
}
// ZERO external dependencies — ai-port must compile and run with no runtime deps.
// Verify: ./gradlew :ai-port:dependencies | grep -c "com.google.genai" → must be 0
```

```kotlin
// agent-toolset/build.gradle.kts
plugins {
    id("java-library")
}
dependencies {
    implementation(project(":ai-port"))
    // Jackson for JsonNode in ToolResult / args — use the version from Spring Boot BOM
    implementation("com.fasterxml.jackson.core:jackson-databind")
    // Spring context for @Component on tool implementations
    implementation("org.springframework:spring-context")
    // No google-genai. No Spring Boot. Tools are pure domain logic + HTTP calls.
}
```

```kotlin
// ai-service/build.gradle.kts
plugins {
    id("org.springframework.boot")
    id("com.google.cloud.tools.jib")
}
dependencies {
    implementation(project(":ai-port"))
    implementation(project(":agent-toolset"))
    implementation(project(":common-error"))
    implementation(project(":common-logging"))
    implementation("com.google.genai:google-genai:1.52.0")
    // ... standard service deps (web, jpa, flyway, springdoc, etc.)
}
```

```kotlin
// search-service/build.gradle.kts
plugins {
    id("org.springframework.boot")
    id("com.google.cloud.tools.jib")
}
dependencies {
    implementation(project(":ai-port"))
    // Standard service deps (web, jpa, flyway, eureka, config)
    // NO google-genai — search-service uses EmbeddingProvider port only
}
```

**Version verification (run before finalizing):**
```bash
# google-genai latest:
# npm view google-genai version  # JS — not applicable
# Check: https://central.sonatype.com/artifact/com.google.genai/google-genai
# 1.52.0 verified via GitHub CHANGELOG [VERIFIED: github.com/googleapis/java-genai CHANGELOG 2026-04-30]
```

---

## Architecture Patterns

### System Architecture Diagram

```
Browser / Frontend
    |
    | POST /api/v1/chat  (non-streaming)
    | GET  /api/v1/chat/stream  (SSE)
    v
[api-gateway]
    | metadata.response-timeout: -1 on SSE route
    | Injects X-User-Id, X-User-Roles, X-Correlation-Id
    v
[ai-service: ChatController / ChatStreamController]
    |
    | Creates SseEmitter, delegates to Executor thread
    v
[ChatService — manual loop]
    |
    | 1. Loads conversation (DB for authed, in-memory for guest)
    | 2. Builds Contents[] from message history + new user message
    | 3. Calls GeminiChatAdapter.chat(contents, tools)
    |        -> Client.models.generateContent(model, contents, config)
    v
[GenerateContentResponse]
    |
    |--[has FunctionCall parts]----------------------------+
    |                                                      |
    |                                          [ToolDispatcher.dispatch()]
    |                                              |
    |                                              | seenIds provenance check
    |                                              | tool.requiresAuth() check
    |                                              | tool.execute(ctx, args)
    |                                              |
    |                           +---------+--------+---------+---------+
    |                           |         |        |         |         |
    |                    [ProductClient] [CartClient] [OrderClient] [PaymentClient]
    |                           |         |        |         |
    |                      product-service cart-service order-service payment-service
    |                           (RestClient + Eureka + X-User-Id forwarding)
    |
    | Emits: event:tool_call + event:tool_result SSE events
    | Appends tool-role Content to history
    | Loops back to generateContent with updated history
    |
    |--[text response]--------> Emits: event:delta chunks (streaming)
                                       event:done (terminal)
                                Persists assistant message to DB
```

### Recommended Project Structure

```
ai-port/
├── src/main/java/com/n11/ai/port/
│   ├── ChatProvider.java           # interface: chat(List<ChatMessage>, List<ToolSchema>) → ChatResponse
│   ├── EmbeddingProvider.java      # interface: embed(String text, int dims) → float[]
│   └── dto/
│       ├── ChatMessage.java        # record: role, content, toolCalls, toolResults
│       ├── ChatResponse.java       # record: text (nullable), toolCalls (list), finishReason
│       ├── ToolSchema.java         # record: name, descriptionTr, parametersJsonSchema
│       ├── ToolCallRequest.java    # record: callId, name, argsJson
│       └── ToolCallResult.java     # record: callId, resultJson, isError

agent-toolset/
├── src/main/java/com/n11/agent/
│   ├── AgentTool.java              # interface: name, descriptionTr, requiresAuth, paramSchema, execute
│   ├── ToolContext.java            # record: userId (nullable), correlationId, seenIds (Set<String>)
│   ├── ToolResult.java             # sealed: Ok(JsonNode data) | Err(String code, String message)
│   ├── ToolRegistry.java           # collects all @Component AgentTool beans
│   └── tools/
│       ├── SearchProductsTool.java
│       ├── GetProductTool.java
│       ├── ListCategoriesTool.java
│       ├── AddToCartTool.java
│       ├── ViewCartTool.java
│       ├── UpdateCartItemTool.java
│       ├── RemoveFromCartTool.java
│       ├── CreateOrderTool.java
│       ├── GetPaymentLinkTool.java
│       └── GetOrderStatusTool.java

ai-service/
├── src/main/java/com/n11/ai/
│   ├── domain/
│   │   ├── chat/
│   │   │   ├── Conversation.java    # JPA entity (ai schema)
│   │   │   ├── Message.java         # JPA entity (ai schema)
│   │   │   └── ChatService.java     # orchestrates loop
│   │   └── tools/
│   │       └── ToolDispatcher.java  # seenIds + auth check + dispatch
│   ├── application/
│   │   └── ChatUseCase.java
│   ├── infrastructure/
│   │   ├── llm/
│   │   │   ├── GeminiChatAdapter.java      # implements ChatProvider
│   │   │   ├── GeminiEmbeddingAdapter.java # implements EmbeddingProvider
│   │   │   └── EchoChatProvider.java       # implements ChatProvider (@Profile("echo") or conditionally)
│   │   ├── http/
│   │   │   ├── ProductClient.java
│   │   │   ├── CartClient.java
│   │   │   ├── OrderClient.java
│   │   │   └── PaymentClient.java
│   │   └── persistence/
│   │       ├── ConversationRepository.java
│   │       └── MessageRepository.java
│   └── interfaces/
│       ├── rest/
│       │   └── ChatController.java         # POST /chat (non-streaming)
│       └── sse/
│           └── ChatStreamController.java   # GET /chat/stream (SSE)

search-service/
├── src/main/java/com/n11/search/
│   ├── SearchServiceApplication.java
│   ├── SearchService.java           # @Autowired EmbeddingProvider; TODO body
│   └── db/migration/
│       └── V1__init.sql             # product_embeddings(product_id, embedding vector(768), ...)
```

### Pattern 1: ai-port Zero-Dependency Port

**What:** `ai-port` is a `java-library` Gradle module with no external dependencies. It defines only interfaces and records (neutral DTOs). Gemini SDK types (`Content`, `Part`, `FunctionCall`) must never appear in this module.

**Verification command:**
```bash
./gradlew :ai-port:dependencies | grep "com.google.genai"
# Must return zero matches — if any appear, the abstraction has leaked.
```

**Port interfaces:**

```java
// Source: ai-port design per CLAUDE.md Non-negotiable Rule #1 + CONTEXT.md decisions
// Package: com.n11.ai.port

public interface ChatProvider {
    /**
     * Send a conversation turn. Returns text response OR function-call requests.
     * @param messages  Full conversation history (role + content + optional toolCalls/Results)
     * @param tools     Tool schemas to expose to the LLM (may be empty)
     * @return ChatResponse with text (non-null when done) or toolCalls (non-empty when LLM wants to call a tool)
     */
    ChatResponse chat(List<ChatMessage> messages, List<ToolSchema> tools);

    /**
     * Streaming variant — emits partial text chunks.
     * For EchoChatProvider: emits a single chunk then completes.
     * For GeminiChatAdapter: streams tokens from generateContentStream.
     */
    void chatStream(List<ChatMessage> messages, List<ToolSchema> tools,
                    Consumer<String> onDelta, Runnable onComplete, Consumer<Throwable> onError);
}

public interface EmbeddingProvider {
    /**
     * Embed a single text string.
     * @param text           The text to embed
     * @param outputDims     Desired output dimensions (e.g., 768 for pgvector index)
     * @return float array of length outputDims
     */
    float[] embed(String text, int outputDims);
}
```

**Neutral DTOs (all records, no SDK types):**

```java
// Role enum — neutral names, no Gemini-specific values
public enum MessageRole { USER, ASSISTANT, TOOL }

public record ChatMessage(
    MessageRole role,
    String content,                      // text content (null for pure tool messages)
    List<ToolCallRequest> toolCalls,     // set when role=ASSISTANT called tools
    List<ToolCallResult> toolResults     // set when role=TOOL returning results
) {}

public record ChatResponse(
    String text,                         // non-null when LLM returned text (loop ends)
    List<ToolCallRequest> toolCalls,     // non-empty when LLM wants tools (loop continues)
    String finishReason                  // "STOP", "TOOL_CALLS", "MAX_TOKENS", etc.
) {}

public record ToolSchema(
    String name,                         // "search_products" — English, used as Gemini function name
    String descriptionTr,                // Turkish description for LLM prompt
    String parametersJsonSchema          // JSON Schema string for the function parameters
) {}

public record ToolCallRequest(
    String callId,                       // Gemini-generated call ID (for correlation)
    String name,                         // function name
    String argsJson                      // JSON string of arguments
) {}

public record ToolCallResult(
    String callId,                       // matches ToolCallRequest.callId
    String resultJson,                   // JSON string of result
    boolean isError                      // true → Gemini sees this as an error result
) {}
```

### Pattern 2: GeminiChatAdapter Manual Function-Calling Loop

**What:** `GeminiChatAdapter` implements `ChatProvider` using the google-genai 1.52.0 SDK. It translates neutral DTOs to/from Gemini SDK types. The mapping layer (neutral ↔ Gemini types) is entirely contained in this class.

**Key SDK facts (VERIFIED: github.com/googleapis/java-genai README + CHANGELOG):**
- `Client` is constructed from `GEMINI_API_KEY` env var or via `Client.builder().apiKey(key).build()`
- `client.models.generateContent(modelId, contents, config)` — synchronous call
- `client.models.generateContentStream(modelId, contents, config)` — returns `ResponseStream<GenerateContentResponse>`
- Response parts inspection: iterate `response.candidates().get(0).content().parts()`, check each `Part` for text vs `FunctionCall`
- For manual function calling: set `GenerateContentConfig` with `ToolConfig.builder().functionCallingConfig(FunctionCallingConfig.builder().mode(Mode.ANY or AUTO))` — use `AUTO` for normal conversation (model decides), `NONE` to force text-only finish
- `FunctionDeclaration` is built with `Schema` for parameters
- Tool results are sent back as `Content` with `role="tool"` containing `FunctionResponsePart` objects

**Startup probe (Pitfall #1 mitigation):**

```java
// Source: CONTEXT.md <specifics> + Pitfall #1 description
// Run on ApplicationReadyEvent — before serving requests

@EventListener(ApplicationReadyEvent.class)
public void verifyModel() {
    String primary = chatModel;  // "gemini-3-flash-preview" from config
    try {
        client.models.get(primary);
        log.info("ai-service: resolved chat model = {} (provider = {})", 
                 primary, getClass().getSimpleName());
    } catch (Exception e) {
        log.warn("ai-service: primary model {} not available ({}), falling back to {}", 
                 primary, e.getMessage(), fallbackChatModel);
        this.chatModel = fallbackChatModel;  // "gemini-2.5-flash"
    }
}
```

**Manual function-calling loop (non-streaming path, for non-SSE `POST /chat`):**

```java
// Source: google-genai SDK patterns + Pitfall #7 guidance + CONTEXT.md D-06
// This is the ADAPTER layer — neutral DTOs in, Gemini types internal only

public ChatResponse chat(List<ChatMessage> messages, List<ToolSchema> tools) {
    List<Content> geminiHistory = toGeminiContents(messages);         // neutral → Gemini
    List<Tool> geminiTools = toGeminiTools(tools);                    // neutral → Gemini
    
    GenerateContentConfig config = GenerateContentConfig.builder()
        .tools(geminiTools)
        .systemInstruction(Content.fromParts(Part.fromText(systemPrompt)))
        .build();
    
    while (true) {
        GenerateContentResponse response = client.models.generateContent(
            chatModel, geminiHistory, config);
        
        List<Part> parts = response.candidates().get(0).content().parts();
        
        // Check if this turn has function calls
        List<Part> functionCallParts = parts.stream()
            .filter(Part::hasFunctionCall)
            .collect(Collectors.toList());
        
        if (functionCallParts.isEmpty()) {
            // Text response — loop ends
            String text = parts.stream()
                .filter(p -> p.text() != null)
                .map(Part::text)
                .collect(Collectors.joining());
            return new ChatResponse(text, List.of(), "STOP");  // neutral DTO
        }
        
        // Append model's function-call turn to history
        geminiHistory.add(response.candidates().get(0).content());
        
        // Build function response content (role="tool")
        List<Part> responseParts = new ArrayList<>();
        List<ToolCallRequest> callRequests = new ArrayList<>();
        
        for (Part part : functionCallParts) {
            FunctionCall fc = part.functionCall();
            String callId = fc.id().orElse(fc.name());  // fallback to name if no id
            String argsJson = fc.args().isPresent() 
                ? fc.args().get().toString() : "{}";
            callRequests.add(new ToolCallRequest(callId, fc.name(), argsJson));
        }
        
        // Return tool calls to ChatService — ChatService dispatches and calls back
        // (alternative: dispatch inline; but ChatService needs seenIds + SSE events)
        return new ChatResponse(null, callRequests, "TOOL_CALLS");  // neutral DTO
    }
    // Note: ChatService owns the outer loop, calling chat() repeatedly.
    // GeminiChatAdapter returns ChatResponse.toolCalls when model wants tools.
    // ChatService dispatches, gets results, builds new ChatMessage(TOOL, ...) and calls chat() again.
}
```

**Important:** The `while(true)` loop above is shown for conceptual clarity. In the actual implementation, ChatService (domain layer) owns the loop because it needs to:
1. Emit SSE events (`event:tool_call`, `event:tool_result`) between tool dispatch steps
2. Apply Pitfall #10 seenIds validation in ToolDispatcher before calling `AgentTool.execute()`
3. Persist messages to DB at each turn

`GeminiChatAdapter.chat()` therefore returns a `ChatResponse` with either `text` (loop ends) or `toolCalls` (loop continues). ChatService dispatches the tool calls, gets results, builds a `ChatMessage(TOOL, ...)` and calls `chat()` again with the extended history.

**Streaming variant (for SSE path):**

```java
// Source: google-genai SDK README generateContentStream
// Used in the streaming path; FunctionCall parts still appear in stream chunks

public void chatStream(List<ChatMessage> messages, List<ToolSchema> tools,
                       Consumer<String> onDelta, Runnable onComplete, Consumer<Throwable> onError) {
    try {
        List<Content> geminiHistory = toGeminiContents(messages);
        GenerateContentConfig config = /* same as chat() */;
        
        ResponseStream<GenerateContentResponse> stream = client.models.generateContentStream(
            chatModel, geminiHistory, config);
        
        for (GenerateContentResponse chunk : stream) {
            String text = chunk.text();
            if (text != null && !text.isEmpty()) {
                onDelta.accept(text);  // ChatService will emit event:delta SSE event
            }
            // Note: FunctionCall parts in streaming chunks are accumulated
            // by ChatService and dispatched at stream end
        }
        stream.close();
        onComplete.run();
    } catch (Exception e) {
        onError.accept(e);
    }
}
```

**Streaming + function-calling nuance:** In the streaming path, FunctionCall parts appear in the stream chunks, not as a separate response. The practical approach: stream text chunks, accumulate any FunctionCall parts, detect end-of-stream, then run the tool dispatch synchronously and loop. This is the standard manual orchestration pattern for LLM streaming with tool use.

### Pattern 3: EmbeddingProvider (GeminiEmbeddingAdapter)

**SDK method:**

```java
// Source: google-genai SDK README embedContent
// Model: "gemini-embedding-2" [VERIFIED: ai.google.dev/gemini-api/docs/embeddings]
// Note: taskType is NOT supported for gemini-embedding-2; use task prefixes in prompt instead [CITED: ai.google.dev/gemini-api/docs/embeddings]

EmbedContentResponse response = client.models.embedContent(
    "gemini-embedding-2",
    "task: product search | query: " + text,   // task prefix replaces deprecated taskType
    EmbedContentConfig.builder()
        .outputDimensionality(dims)            // 768 for pgvector index per STACK.md
        .build()
);
float[] vector = toFloatArray(response.embeddings().get(0).values());
```

**Important note on task prefixes vs taskType:**
`gemini-embedding-2` deprecated the `taskType` enum parameter. Instead, prepend a task instruction to the text: `"task: retrieval document | content: ..."` for indexing, `"task: retrieval query | query: ..."` for search-time embedding. The `EmbedContentConfig.outputDimensionality(768)` parameter is still the correct way to set output dimensions. [CITED: ai.google.dev/gemini-api/docs/embeddings]

**Embedding dimensions (ASSUMED for the specific 768 recommendation):** Gemini Embedding 2 default is 3072. 768 is verified as a supported truncation via MRL. For pgvector with HNSW index, 768 is a good balance of recall vs index size. [CITED: developers.googleblog.com/building-with-gemini-embedding-2]

### Pattern 4: SseEmitter Threading Model

**What:** Spring MVC `SseEmitter` requires the HTTP response to stay open while the manual loop runs. The controller returns the `SseEmitter` immediately; the loop runs on a separate executor thread.

**Pattern:**

```java
// Source: howtodoinjava.com/spring-boot/spring-async-controller-sseemitter [MEDIUM confidence]
// Package: com.n11.ai.interfaces.sse

@GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter streamChat(@RequestParam String conversationId,
                              @RequestParam String message,
                              @RequestHeader(value = "X-User-Id", required = false) String userId,
                              @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
    
    SseEmitter emitter = new SseEmitter(0L);  // 0 = no timeout (gateway handles it at route level)
    
    executor.execute(() -> {
        MDC.put("correlationId", correlationId != null ? correlationId : UUID.randomUUID().toString());
        try {
            chatUseCase.handleStream(conversationId, message, userId, emitter);
        } catch (Exception e) {
            try {
                emitter.send(SseEmitter.event()
                    .name("error")
                    .data(new ErrorEvent("INTERNAL_ERROR", "Bir hata oluştu")));
            } catch (IOException ignored) {}
            emitter.completeWithError(e);
        } finally {
            MDC.clear();
        }
    });
    
    return emitter;
}
```

**SseEmitter lifecycle rules:**
1. `emitter.complete()` — normal end; client receives `event:done` before this
2. `emitter.completeWithError(e)` — sends error event then closes
3. `emitter.send(...)` — must NOT be called after `complete()` (throws `IllegalStateException`)
4. `onTimeout(() -> emitter.complete())` — handle gateway/client timeout gracefully
5. Never call `send()` while holding a lock that the Netty I/O thread might also hold — ABBA deadlock risk in Spring Framework 6.2.x (resolved in 6.3.x used by Boot 3.5.14, but still good practice to avoid) [CITED: github.com/spring-projects/spring-framework/issues/33421]

**SSE event emission helpers:**

```java
// Typed SSE events matching D-07 wire format
emitter.send(SseEmitter.event().name("delta").data(Map.of("text", chunk, "conversationId", convId)));
emitter.send(SseEmitter.event().name("tool_call").data(Map.of("name", toolName, "callId", callId)));
emitter.send(SseEmitter.event().name("tool_result").data(Map.of("callId", callId, "ok", !isErr)));
emitter.send(SseEmitter.event().name("done").data(Map.of("conversationId", convId, "finalText", text)));
emitter.send(SseEmitter.event().name("error").data(Map.of("code", code, "messageTr", msg)));
```

**Gateway route activation (Phase 8 task):**
Uncomment the existing commented anchor in `config-server/src/main/resources/config/api-gateway.yml` and activate it as a real route under the existing `routes:` block:

```yaml
# ADD under existing routes: list in api-gateway.yml
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
  # NO ModifyResponseBody, NO RetryFilter — both buffer and break streaming

- id: ai-service-chat
  uri: lb://AI-SERVICE
  predicates:
    - Path=/api/v1/chat/**
  filters:
    - StripPrefix=2

# Phase 9 will add mcp-server route.
# search-service route stays commented until v2 (AI-V2-01):
# - id: search-service
#   uri: lb://SEARCH-SERVICE
#   predicates:
#     - Path=/api/v1/search/**
#   filters:
#     - StripPrefix=2
```

Also add to Springdoc aggregator `urls:` in api-gateway.yml:
```yaml
- name: ai-service
  url: /api/v1/chat/v3/api-docs
# search-service stays commented until v2
```

### Pattern 5: EchoChatProvider (SOLID Demonstration Artifact)

**What:** The second `ChatProvider` adapter. Activated by `ai.provider=echo`. Proves the port is substitutable without touching service logic.

**Shape rules (CRITICAL per Pitfall #7):**
- Zero Gemini SDK imports
- Zero `com.google.genai.*` imports
- Entire class fits in ~40 lines
- No tool dispatch — echoes the latest user message as text

```java
// Source: CONTEXT.md decisions D-06 + Pitfall #7 mitigation
// Package: com.n11.ai.infrastructure.llm
// File: EchoChatProvider.java

@Component("echoChatProvider")
@ConditionalOnProperty(name = "ai.provider", havingValue = "echo")
public class EchoChatProvider implements ChatProvider {

    private static final Logger log = LoggerFactory.getLogger(EchoChatProvider.class);

    @Override
    public ChatResponse chat(List<ChatMessage> messages, List<ToolSchema> tools) {
        log.info("EchoChatProvider: echoing (zero Gemini calls)");
        String lastUserMessage = messages.stream()
            .filter(m -> m.role() == MessageRole.USER)
            .reduce((first, second) -> second)   // last user message
            .map(ChatMessage::content)
            .orElse("(no user message)");
        String reply = "[ECHO] " + lastUserMessage;
        return new ChatResponse(reply, List.of(), "STOP");
    }

    @Override
    public void chatStream(List<ChatMessage> messages, List<ToolSchema> tools,
                           Consumer<String> onDelta, Runnable onComplete,
                           Consumer<Throwable> onError) {
        String reply = chat(messages, tools).text();
        onDelta.accept(reply);    // single chunk
        onComplete.run();
    }
}
```

**GeminiChatAdapter configuration (primary path):**

```java
@Component("geminiChatAdapter")
@ConditionalOnProperty(name = "ai.provider", havingValue = "gemini", matchIfMissing = true)
public class GeminiChatAdapter implements ChatProvider { ... }
```

**Config property:**

```yaml
# config-server/src/main/resources/config/ai-service.yml
ai:
  provider: gemini          # flip to "echo" to prove SOLID substitutability
  gemini:
    model:
      chat: gemini-3-flash-preview     # verified at startup; fallback: gemini-2.5-flash
      chat-fallback: gemini-2.5-flash
      embedding: gemini-embedding-2
    embedding-dims: 768
```

### Pattern 6: agent-toolset Module Shape

**Interface:**

```java
// Source: ARCHITECTURE.md §6.2 + CONTEXT.md Phase 9 handoff requirements
// Package: com.n11.agent

public interface AgentTool {
    String name();                           // "search_products" — English, stable across v1→v2
    String descriptionTr();                  // Turkish description for LLM prompt
    boolean requiresAuth();                  // D-04: mutating tools return AUTH_REQUIRED for guests
    String parametersJsonSchema();           // JSON Schema string for function parameters
    ToolResult execute(ToolContext ctx, JsonNode args);  // synchronous dispatch
}

public record ToolContext(
    String userId,           // null for guest
    String correlationId,    // for MDC propagation through outbound tool HTTP calls
    Set<String> seenIds      // Pitfall #10: IDs seen in prior tool results this conversation
) {}

public sealed interface ToolResult permits ToolResult.Ok, ToolResult.Err {
    record Ok(JsonNode data) implements ToolResult {}
    record Err(String code, String message) implements ToolResult {}
}
```

**Phase 9 compatibility note:** Phase 9 (mcp-server) will re-expose the same `AgentTool` beans via Spring AI MCP starter's `@Tool` annotation. This means tool implementations must NOT depend on ai-service-specific beans (no conversation state, no SSE emitter). `ToolContext` is the only per-call context the tools see. Pitfall #10 provenance (`seenIds`) lives in ai-service's `ToolDispatcher`, not in tool implementations.

**Tool dispatch flow:**

```java
// Source: CONTEXT.md D-08 + ARCHITECTURE.md §6.2
// Package: com.n11.ai.domain.tools

@Component
public class ToolDispatcher {
    
    public ToolResult dispatch(String toolName, String argsJson,
                               String userId, String correlationId,
                               Set<String> seenIds) {
        AgentTool tool = toolRegistry.find(toolName)
            .orElseThrow(() -> new UnknownToolException(toolName));
        
        // D-04: auth check
        if (tool.requiresAuth() && userId == null) {
            return ToolResult.Err("AUTH_REQUIRED", "Bu işlem için giriş yapman gerekiyor.");
        }
        
        JsonNode args = parseArgs(argsJson);
        
        // D-08: Pitfall #10 — provenance check for ID-shaped arguments
        validateIdProvenance(tool, args, seenIds);  // throws ToolValidationException on hallucinated ID
        
        ToolContext ctx = new ToolContext(userId, correlationId, seenIds);
        ToolResult result = tool.execute(ctx, args);
        
        // D-08: register any new IDs from this result into seenIds
        if (result instanceof ToolResult.Ok ok) {
            extractAndRegisterIds(ok.data(), seenIds);
        }
        
        return result;
    }
}
```

### Pattern 7: Pitfall #10 ID Provenance Algorithm

**Approach (D-08 belt-and-braces):**

1. **What to track in seenIds:** Any string value that looks like an ID in tool results. Strategy: extract all UUIDs and strings matching `/^[a-zA-Z0-9_-]{8,64}$/` from `ToolResult.Ok.data` JSON tree. Add them to `conversation.seenIds`.

2. **What to validate:** Tool arguments where the key name ends in `Id` (e.g., `productId`, `orderId`, `cartItemId`). Extract by JSON key name suffix pattern.

3. **Implementation:**
```java
private void validateIdProvenance(AgentTool tool, JsonNode args, Set<String> seenIds) {
    Iterator<Map.Entry<String, JsonNode>> fields = args.fields();
    while (fields.hasNext()) {
        Map.Entry<String, JsonNode> field = fields.next();
        if (field.getKey().endsWith("Id") && field.getValue().isTextual()) {
            String id = field.getValue().asText();
            if (!seenIds.contains(id)) {
                throw new ToolValidationException(
                    "UNKNOWN_ID",
                    "'" + field.getKey() + "' değeri '" + id + "' önceki araç sonuçlarında görülmedi. " +
                    "Önce search_products veya get_product kullanarak geçerli bir ID al.");
            }
        }
    }
}
```

4. **ID extraction from results:**
```java
private void extractAndRegisterIds(JsonNode data, Set<String> seenIds) {
    // Walk JSON tree recursively; collect all string values for keys ending in "id" or "Id"
    data.findValues("id").stream()
        .filter(JsonNode::isTextual)
        .map(JsonNode::asText)
        .filter(s -> s.length() >= 8)
        .forEach(seenIds::add);
    // Also collect values for productId, orderId, cartItemId, etc.
    // Regex: field name matches /.*[iI]d$/ 
}
```

5. **Where seenIds lives:** In `Conversation` domain object (stored in DB for authed users as JSON column on `ai_conversations`; in the in-memory Map entry for guests). Updated after each successful tool dispatch.

### Anti-Patterns to Avoid

- **Leaking Gemini types through the port** (Pitfall #7): `ChatProvider` signature must never mention `Content`, `Part`, `FunctionCall`, `HarmCategory`. If a grader runs `grep -r "import com.google.genai" ai-port/` and it returns anything, the SOLID demo is compromised.
- **Storing Gemini `Content` JSON in the DB** (Pitfall #17): `messages` table stores neutral role/content/tool_call_json. Not Gemini's wire format.
- **Calling `emitter.send()` after `emitter.complete()`** — throws `IllegalStateException`; always use a boolean flag to guard.
- **Using `@Profile("test")` for EchoChatProvider** — graders can't easily see the SOLID demo if it's test-only. Use `@ConditionalOnProperty(ai.provider=echo)` so config is the only switch.
- **Putting `seenIds` logic inside individual tool implementations** — defeats the DRY principle and makes the mcp-server path skip validation. ToolDispatcher owns it centrally.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| JSON Schema for tool parameters | Hand-coded Schema string builders | Jackson `JsonSchemaGenerator` or simple record-to-JSON | Generating JSON Schema from a Java record avoids typos in property names |
| HTTP clients for tool dispatch | Raw `HttpURLConnection` or Spring's `WebClient` (reactive) | Spring's `RestClient` (blocking, synchronous, Spring Framework 6.1+) | Consistent with Phase 6 pattern (D-05); simpler mental model than reactive for sequential tool calls |
| SSE typed event serialization | Manual string concatenation `"event: delta\ndata: {...}\n\n"` | `SseEmitter.event().name().data()` builder | Built into Spring; handles escaping, retry, id fields |
| UUID generation for conversation/call IDs | `String.format("conv-%d", System.currentTimeMillis())` | `UUID.randomUUID().toString()` | Guaranteed uniqueness |
| In-memory TTL map for guest sessions | Custom `HashMap` + scheduled eviction | `com.github.ben-manes.caffeine:caffeine` with `expireAfterAccess(1, HOURS)` OR a `ConcurrentHashMap` + `ScheduledExecutorService.scheduleAtFixedRate` | Caffeine is already on the classpath transitively via Spring Boot Cache; correct LRU/TTL semantics |

**Key insight:** The primary complexity of this phase is not in any single library but in the careful mapping layer between neutral DTOs and Gemini SDK types. Invest in making that mapping obvious and well-tested.

---

## Runtime State Inventory

> Not a rename/refactor phase. However, Phase 8 ADDS new state and must declare what gets created.

| Category | Items Found / Created | Action Required |
|----------|----------------------|-----------------|
| Stored data | `ai_conversations` + `messages` tables (NEW). `product_embeddings` table in search schema (NEW). | Flyway V1 migrations in ai-service and search-service |
| Live service config | `config-server/.../config/ai-service.yml` (NEW). `config-server/.../config/search-service.yml` (NEW). | Create files; requires `jibDockerBuild` before `docker compose up -d` |
| OS-registered state | None | None |
| Secrets/env vars | `GEMINI_API_KEY` (NEW — ai-service only). `AI_DB_PASSWORD` (ALREADY in init.sh + docker-compose). `SEARCH_DB_PASSWORD` (ALREADY in init.sh + docker-compose). | Add `GEMINI_API_KEY` to `.env` file and docker-compose ai-service env block |
| Build artifacts | ai-service + search-service Jib images (NEW). `settings.gradle.kts` needs 4 new module entries. | Run `./gradlew :ai-service:jibDockerBuild :search-service:jibDockerBuild` before `docker compose up -d` |

**Nothing found to migrate** — this is a greenfield addition of two services and two library modules.

---

## Common Pitfalls

### Pitfall 1: Gemini model identifier mismatch on startup
**What goes wrong:** Hard-coded model string doesn't match the API; every call returns 404/`MODEL_NOT_FOUND`. [Source: PITFALLS.md Pitfall #1]
**Why it happens:** Preview model IDs change. "Gemini 3 Flash" colloquially ≠ API identifier.
**How to avoid:** `gemini-3-flash-preview` is confirmed active as of April 2026 [VERIFIED: ai.google.dev/gemini-api/docs/models/gemini-3-flash-preview]. Treat as config, not constant. Run `ApplicationReadyEvent` probe (see Pattern 2 above). Log resolved model on startup.
**Warning signs:** 404 or `INVALID_ARGUMENT` on first chat call; error body mentions "model not found".

### Pitfall 2: Leaky `ChatProvider` abstraction
**What goes wrong:** Gemini SDK types (`Content`, `Part`, `FunctionCall`) leak into `ai-port` or service layer; port is no longer substitutable. Graders see the SOLID story falls apart. [Source: PITFALLS.md Pitfall #7]
**Why it happens:** Time pressure; Gemini types are what the SDK gives you.
**How to avoid:** `ai-port` has zero `com.google.genai.*` dependencies. Verify with `./gradlew :ai-port:dependencies | grep com.google.genai`. Mapping lives entirely in `GeminiChatAdapter`.
**Warning signs:** `grep -rn "import com.google.genai" ai-port/` returns any matches.

### Pitfall 3: Conversation state persisted in Gemini's JSON shape
**What goes wrong:** `messages.content` column stores Gemini's `Content` JSON (`"role":"user","parts":[...]`). Port substitutability leaks into the database. [Source: PITFALLS.md Pitfall #17]
**Why it happens:** Direct serialization of the SDK response is easiest.
**How to avoid:** `messages` table stores neutral columns: `role` (enum), `content` (text), `tool_call_json` (JSON), `tool_result_json` (JSON), `sequence` (int). Adapter maps Gemini ↔ neutral before/after DB access.
**Warning signs:** `content` column contains literal `"parts":[` JSON.

### Pitfall 4: SseEmitter called after completion
**What goes wrong:** Race condition between the loop thread completing and an exception handler also calling `send()`; throws `IllegalStateException`.
**Why it happens:** Two code paths (normal completion + error handler) both call emitter methods.
**How to avoid:** Use an `AtomicBoolean completed` flag. Set on first call to `complete()` or `completeWithError()`. Guard all subsequent `send()` calls with `if (!completed.get())`.
**Warning signs:** `IllegalStateException: ResponseBodyEmitter is already set complete` in logs.

### Pitfall 5: Gemini free-tier 10 RPM limit during demo
**What goes wrong:** Multi-turn chat + tool-calling can produce 3–5 model calls per user turn. Free tier = 10 RPM = 2-3 full turns before `429 RESOURCE_EXHAUSTED`. [Source: PITFALLS.md Pitfall #9]
**Why it happens:** Free tier halved in December 2025; function-calling is conversational (multiple roundtrips).
**How to avoid:** Catch `429` in `GeminiChatAdapter`; return `ToolResult.Err("RATE_LIMITED", "Asistan biraz yoğun, lütfen bir dakika bekleyin.")`. Document "demo budget" in runbook. EchoChatProvider bypasses quota entirely for structural demos.
**Warning signs:** `429` in ai-service logs after 2-3 chat turns.

### Pitfall 6: Tool dispatch returns Gemini SDK exceptions to frontend
**What goes wrong:** `NullPointerException`, `EntityNotFoundException`, or Gemini SDK exception message appears in the chat bubble.
**Why it happens:** Exceptions not caught at the ToolDispatcher boundary.
**How to avoid:** `ToolDispatcher.dispatch()` catches all exceptions, wraps in `ToolResult.Err("TOOL_ERROR", "Araç çalıştırılırken hata oluştu.")`. Gemini sees the error result and formulates a Turkish apology response.
**Warning signs:** Raw Java exception class names appear in chat responses.

### Pitfall 7: mcp-server hand-off broken if agent-toolset depends on ai-service beans
**What goes wrong:** Phase 9 cannot compile `mcp-server` because `agent-toolset` imports `ConversationRepository` or `SseEmitter` from ai-service.
**Why it happens:** Shortcut — tool implementation directly injects an ai-service bean.
**How to avoid:** Tools receive `ToolContext` (userId, correlationId, seenIds) — nothing ai-service specific. All state they need is in args or context. seenIds validation is in ToolDispatcher (ai-service), not in tool implementations.
**Warning signs:** `agent-toolset/build.gradle.kts` imports `project(":ai-service")`.

### Pitfall 8: HikariCP pool exhaustion when two new services join
**What goes wrong:** Adding ai-service + search-service brings pool pressure to potential exhaustion. [Source: PITFALLS.md Pitfall #8]
**See detailed math in HikariCP Budget section below.**

---

## Code Examples

### Flyway V1 DDL for ai-service (ai schema)

```sql
-- Source: CONTEXT.md D-01 / D-02 + ARCHITECTURE.md §2.12 + Pitfall #17 guidance
-- File: ai-service/src/main/resources/db/migration/ai/V1__init.sql

-- NOTE: schema is set via search_path = ai (from infra/postgres/init.sh ai_user config)

CREATE TABLE ai_conversations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL,          -- from X-User-Id header (identity-service UUID)
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_activity_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    seen_ids_json   TEXT,                   -- JSON array of ID strings for Pitfall #10 seenIds
    metadata_json   TEXT                    -- reserved for future use
);

CREATE INDEX idx_conversations_user_id ON ai_conversations(user_id);

CREATE TABLE messages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES ai_conversations(id) ON DELETE CASCADE,
    role            VARCHAR(20) NOT NULL,   -- 'USER', 'ASSISTANT', 'TOOL'
    content         TEXT,                   -- text content (null for pure tool messages)
    tool_call_json  TEXT,                   -- JSON array of ToolCallRequest records (ASSISTANT role)
    tool_result_json TEXT,                  -- JSON array of ToolCallResult records (TOOL role)
    sequence        INT NOT NULL,           -- ordering within conversation
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    
    CONSTRAINT messages_role_check CHECK (role IN ('USER', 'ASSISTANT', 'TOOL'))
);

CREATE INDEX idx_messages_conversation_id ON messages(conversation_id);
CREATE INDEX idx_messages_conversation_sequence ON messages(conversation_id, sequence);
```

### Flyway V1 DDL for search-service (search schema)

```sql
-- Source: CONTEXT.md D-09 + ARCHITECTURE.md §2.11
-- File: search-service/src/main/resources/db/migration/search/V1__init.sql
-- NOTE: pgvector extension is already created in infra/postgres/init.sh (superuser)

CREATE TABLE product_embeddings (
    product_id      UUID PRIMARY KEY,          -- FK to product-service (logical only — no cross-schema FK)
    embedding       vector(768) NOT NULL,       -- gemini-embedding-2 at 768 dims
    name_tr         TEXT,                       -- denormalized for display in search results
    indexed_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- HNSW index for cosine similarity (pgvector 0.8.2 — v2 will use this)
-- Phase 8 creates the table but does NOT populate it or query it (D-09 skeleton)
-- Phase v2 will create the HNSW index when data is populated:
-- CREATE INDEX idx_product_embeddings_hnsw ON product_embeddings
--     USING hnsw (embedding vector_cosine_ops)
--     WITH (m = 16, ef_construction = 64);
```

### ai-service Config YAML

```yaml
# config-server/src/main/resources/config/ai-service.yml
db:
  user: ai_user
  password: ${AI_DB_PASSWORD}

spring:
  application:
    name: ai-service
  jpa:
    properties:
      hibernate:
        default_schema: ai
  flyway:
    default-schema: ai
    schemas: ai
    locations: classpath:db/migration/ai
  datasource:
    hikari:
      maximum-pool-size: 3    # ai-service is I/O-bound (Gemini latency); small pool sufficient
      minimum-idle: 1

server:
  port: 8088

ai:
  provider: gemini                      # flip to "echo" to prove SOLID substitutability
  gemini:
    model:
      chat: gemini-3-flash-preview
      chat-fallback: gemini-2.5-flash
      embedding: gemini-embedding-2
    embedding-dims: 768
  conversation:
    guest-ttl-minutes: 60
```

### search-service Config YAML

```yaml
# config-server/src/main/resources/config/search-service.yml
db:
  user: search_user
  password: ${SEARCH_DB_PASSWORD}

spring:
  application:
    name: search-service
  jpa:
    properties:
      hibernate:
        default_schema: search
  flyway:
    default-schema: search
    schemas: search
    locations: classpath:db/migration/search
  datasource:
    hikari:
      maximum-pool-size: 2    # skeleton only; no active query path in v1
      minimum-idle: 1

server:
  port: 8089
```

---

## HikariCP Pool Budget Revision (Pitfall #8)

**Current state before Phase 8:**

| Service | Pool Size | Notes |
|---------|-----------|-------|
| identity-service | 10 | |
| product-service | 10 | |
| inventory-service | 10 | |
| cart-service | 10 | |
| order-service | 10 | |
| payment-service | 10 | |
| notification-service | 2 | Already capped |
| **Subtotal** | **62** | |

[VERIFIED: grep of config-server/*.yml files in current codebase]

**After Phase 8 additions:**

| New Service | Pool Size | Rationale |
|-------------|-----------|-----------|
| ai-service | 3 | I/O-bound on Gemini API; pool of 3 handles sequential conversation turns with headroom |
| search-service | 2 | Skeleton only in v1; no active query path |
| **New subtotal** | **67** | |

**Budget math:**
- PostgreSQL `max_connections` default = 100
- 80% safe usage ceiling = 80 connections
- Current after Phase 8: 67 connections (identity 10 + product 10 + inventory 10 + cart 10 + order 10 + payment 10 + notification 2 + ai 3 + search 2 = 67)
- Headroom: 80 - 67 = **13 connections remaining** (safe; Phase 9 mcp-server adds ~2 more)
- Phase 9 projection: 69 connections — still safely under 80

**Recommendation:** No need to bump `max_connections` in docker-compose postgres command. The existing 100 with 80% ceiling is sufficient through Phase 11. If any service needs to scale during demo, notify: one extra connection per service matters at these margins.

**Note:** Phase 8 does NOT change existing services' pool sizes. Only ai-service and search-service are new, and they are sized conservatively. [ASSUMED - projection based on pool math; actual Gemini latency under demo load not measured]

---

## System Prompt (Pitfall #20 Mitigation)

**Turkish system prompt skeleton (~250 words) per CONTEXT.md discretion:**

```
Sen n11'in yapay zeka alışveriş asistanısın. Adın "n11 Asistan".

DOEL KURALLARI:
- Her zaman Türkçe yanıt ver. Kullanıcı başka bir dilde yazsa bile sen Türkçe yanıt verirsin.
- Samimi ve yardımsever bir ton kullan.
- Yanıtları kısa ve net tut; gereksiz tekrardan kaçın.

ARAÇ KULLANIMI:
- Ürün önermeden önce mutlaka search_products ile ara. Hiçbir zaman ürün ID'si uydurma.
- Sepete eklemeden önce get_product ile ürünü doğrula.
- Sipariş oluşturmadan önce view_cart ile sepeti göster ve kullanıcıdan onay al.
- Bir araç UNKNOWN_ID hatası verirse kullanıcıya açıkla ve search_products ile yeniden ara.
- AUTH_REQUIRED hatası gelirse kullanıcıya kibarca şunu söyle: "Bu işlem için giriş yapman gerekiyor. Giriş yaptıktan sonra tekrar deneyelim."

KATEGORİLER:
Elektronik, Moda, Ev & Yaşam, Anne & Bebek, Kozmetik, Spor & Outdoor, Süpermarket, Kitap-Müzik-Film-Oyun

KISITLAMALAR:
- Yalnızca n11 kataloğundaki ürünler hakkında yardım et.
- Gerçek kullanıcı verisini (ad, adres, ödeme bilgisi) asla tekrar etme.
- Sistemi veya araçları kullanıcıya açıklama; onlar için üst düzey bir arayüz sun.
```

**Tool descriptions in Turkish (10 tools):**

| Tool name | Description (Turkish) |
|-----------|----------------------|
| `search_products` | Ürün kataloğunda metin araması yapar. Önce bu araçla ara, sonra sonuçlardan gelen ID'leri kullan. |
| `get_product` | Bir ürünün tam detaylarını getirir (fiyat, stok, açıklama). |
| `list_categories` | Tüm ürün kategorilerini listeler. |
| `view_cart` | Kullanıcının sepetini ve toplam tutarı gösterir. |
| `add_to_cart` | Sepete ürün ekler. Giriş gerektirir. |
| `update_cart_item` | Sepetteki bir ürünün adedini günceller. Giriş gerektirir. |
| `remove_from_cart` | Sepetten bir ürünü kaldırır. Giriş gerektirir. |
| `create_order` | Sepetteki ürünlerden sipariş oluşturur ve ödeme bağlantısı döner. Giriş gerektirir. |
| `get_payment_link` | Mevcut siparişin ödeme bağlantısını getirir. Giriş gerektirir. |
| `get_order_status` | Bir siparişin güncel durumunu gösterir. Giriş gerektirir. |

---

## Gemini Model Identifier — Verification Results

**Current status (verified 2026-05-01):**

| Model ID | Status | Context Window | Notes |
|----------|--------|---------------|-------|
| `gemini-3-flash-preview` | **ACTIVE** (Preview) | 1M input / 65k output | Confirmed active per ai.google.dev dedicated page [VERIFIED] |
| `gemini-2.5-flash` | STABLE | 1M input | Stable fallback; recommended for production |
| `gemini-2.5-flash-lite` | STABLE | 1M input | Cheapest; 3rd tier fallback |
| `gemini-embedding-2` | **GA** | n/a | Confirmed generally available [CITED: developers.googleblog.com/gemini-embedding-available-gemini-api] |

**Fallback chain to use in config:**
```
primary:  gemini-3-flash-preview
fallback: gemini-2.5-flash
3rd:      gemini-2.5-flash-lite
```

**Important clarification:** `gemini-3-flash-preview` (not `gemini-3.0-flash-preview`, not `gemini-3-flash`) is the confirmed identifier. The lack of `.0` in the model name is intentional and has been a consistent source of confusion. [VERIFIED: ai.google.dev/gemini-api/docs/models/gemini-3-flash-preview]

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `gemini-embedding-001` with `taskType` enum | `gemini-embedding-2` with task prefix in prompt text | gemini-embedding-2 GA (2026) | Update: `taskType` param no longer sent; prepend `"task: retrieval document | content: ..."` to text |
| Google Gen AI Java SDK 1.51.0 | 1.52.0 (2026-04-30) | Minor version bump | No breaking changes per CHANGELOG; safe to upgrade from STACK.md pinned version |
| Spring AI Gemini chat model (if it listed Gemini 3) | Still using google-genai directly | Not changed | Spring AI 1.1.5 does not list `gemini-3-flash-preview` — direct SDK remains correct |

**Deprecated/outdated:**
- `gemini-embedding-001`: replaced by `gemini-embedding-2`; separate `taskType` parameter no longer used for embedding-2
- `gemini-3-pro-preview`: deprecated and shut down 2026-03-09 (NOT Flash — only Pro variant)

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 / Spring Boot Test (bundled with Boot 3.5.14) |
| Config file | None — standard Spring Boot test conventions |
| Quick run (per-module) | `./gradlew :ai-service:test -x integrationTest` or `--tests "*UnitTest"` |
| Full suite | `./gradlew :ai-service:test :search-service:test :ai-port:test :agent-toolset:test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| AI-01 | `ai-port` module has zero `com.google.genai` runtime/compile deps | Gradle dependency guard | `./gradlew :ai-port:dependencies \| grep -c "com.google.genai"` — must return 0 | Wave 0 Gradle check |
| AI-01 | `ChatProvider.chat()` contract honored by both adapters | Unit | `AiPortContractTest` in ai-service test | ❌ Wave 0 |
| AI-04 | Setting `ai.provider=echo` activates `EchoChatProvider` | Integration | `EchoProviderActivationTest` — POST /chat with `ai.provider=echo` asserts echo response | ❌ Wave 0 |
| AI-04 | `EchoChatProvider.chat()` returns the echoed prompt as text | Unit | `EchoChatProviderTest` | ❌ Wave 0 |
| AI-05 | All 10 tools registered in ToolRegistry | Unit | `AgentToolRegistryTest` — `assertThat(registry.all()).hasSize(10)` | ❌ Wave 0 |
| AI-07 | Hallucinated productId rejected by ToolDispatcher | Unit | `ToolDispatcherIdProvenanceTest` — call `add_to_cart` with unknown ID, assert `ToolResult.Err("UNKNOWN_ID", ...)` | ❌ Wave 0 |
| AI-08 | SSE endpoint emits typed events | Integration (SpringBootTest) | `ChatStreamSseTest` — use `MockMvc` SSE consumer, assert `event:delta`, `event:done` received | ❌ Wave 0 |
| AI-09 | Conversation persisted to Postgres for authed user | Integration (Testcontainers) | `ConversationPersistenceTest` — POST chat with X-User-Id, assert DB row exists | ❌ Wave 0 |
| AI-10 | System prompt enforces Turkish; 5-turn mixed-language assert | Integration (EchoChatProvider) | `TurkishSystemPromptTest` — EchoChatProvider; inject system prompt; assert all responses non-English | ❌ Wave 0 |
| QUAL-08 | search-service Spring context loads with EmbeddingProvider injected | Smoke (SpringBootTest) | `SearchServiceContextTest` — `@SpringBootTest` loads; `EmbeddingProvider` bean present | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** `./gradlew :ai-port:test :agent-toolset:test` (fast, no containers)
- **Per wave merge:** `./gradlew :ai-service:test :search-service:test` (Testcontainers)
- **Phase gate:** Full suite green; `EchoChatProvider` swap demo runnable

### Wave 0 Gaps

- [ ] `ai-port/src/test/java/.../AiPortContractTest.java` — verifies zero Gemini deps + interface signatures
- [ ] `ai-service/src/test/java/.../EchoChatProviderTest.java` — unit test EchoChatProvider
- [ ] `ai-service/src/test/java/.../EchoProviderActivationTest.java` — Spring profile switch test
- [ ] `agent-toolset/src/test/java/.../AgentToolRegistryTest.java` — all 10 tools registered
- [ ] `ai-service/src/test/java/.../ToolDispatcherIdProvenanceTest.java` — Pitfall #10 rejection
- [ ] `ai-service/src/test/java/.../ChatStreamSseTest.java` — SSE typed event format
- [ ] `ai-service/src/test/java/.../ConversationPersistenceTest.java` — Testcontainers Postgres
- [ ] `search-service/src/test/java/.../SearchServiceContextTest.java` — context load smoke
- [ ] `infra-tests` extensions: add ai-service + search-service to multi-service classpath (bean disambiguation per Plan 05-04 pattern)
- [ ] Real Gemini smoke test (skip-if-absent): `GeminiSmokeTest` gated on `GEMINI_API_KEY != null` — sends one message, asserts non-empty Turkish response

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| PostgreSQL 16 + pgvector | Flyway migrations, ai_conversations, product_embeddings | ✓ (existing docker-compose) | 16 + pgvector 0.8.2 | — |
| Java 21 | All Gradle modules | ✓ | OpenJDK 21.0.10 | — |
| Gemini API key (`GEMINI_API_KEY`) | GeminiChatAdapter, GeminiEmbeddingAdapter | Must be provisioned | n/a | `EchoChatProvider` (ai.provider=echo) bypasses |
| google-genai 1.52.0 | ai-service only | ✓ (Maven Central) | 1.52.0 | — |
| Spring MVC SseEmitter | ChatStreamController | ✓ (spring-webmvc, bundled) | Boot 3.5.14 | — |

**Missing dependencies with no fallback:** None that block Phase 8 infrastructure. The `GEMINI_API_KEY` is required for real Gemini calls but `EchoChatProvider` provides a structural fallback for all CI tests.

**Missing dependencies with fallback:**
- `GEMINI_API_KEY` → `ai.provider=echo` allows full Phase 8 to run without a real Gemini key; real key needed only for demo and the single smoke test.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | ai-service pool of 3 is sufficient for single-user demo | HikariCP Budget | If demo has concurrent users, pool might exhaust; bump to 5 safely |
| A2 | search-service pool of 2 is sufficient for skeleton-only phase | HikariCP Budget | No risk — search-service has no active query path in v1 |
| A3 | `seenIds` JSON stored as `TEXT` column on `ai_conversations` is sufficient | Flyway DDL | If conversation grows large, switch to `JSONB` for GIN indexing; TEXT is fine for v1 |
| A4 | Spring Framework 6.3.x (Boot 3.5.14) has resolved the SseEmitter ABBA deadlock from 6.2.x | SseEmitter pattern | If issue persists, switch to `VirtualThread` executor and pin `emitter.onCompletion` guard |
| A5 | google-genai 1.52.0 is backward compatible with 1.51.0 API surface | Standard Stack | No breaking changes per CHANGELOG — safe bump |
| A6 | `gemini-3-flash-preview` will remain available through demo week (May 2026) | Model identifier | If model removed, fallback to `gemini-2.5-flash` via config change only |

---

## Open Questions

1. **`ToolContext.seenIds` as `Set<String>` — thread safety for concurrent SSE streams?**
   - What we know: each conversation has its own `seenIds`; SSE loop runs on a single executor thread per request
   - What's unclear: if two requests for the same conversationId arrive concurrently (page reload), `seenIds` could race
   - Recommendation: Use `CopyOnWriteArraySet` for seenIds; or gate concurrent access to the same conversationId with a per-conversation lock (simple `ConcurrentHashMap<UUID, ReentrantLock>`)

2. **`EmbeddingProvider` adapter placement — in ai-service or a shared `ai-adapter-gemini` module?**
   - What we know: search-service imports `EmbeddingProvider` port; GeminiEmbeddingAdapter implements it; both ai-service and (future) search-service need it
   - What's unclear: should the adapter live in ai-service (search-service calls ai-service's `/ai/embed` endpoint per ARCHITECTURE.md §2.11 option) OR in a shared Gradle module both services import?
   - Recommendation per ARCHITECTURE.md §2.11: keep adapter in ai-service; search-service calls ai-service's internal REST `/embed` endpoint. This honors the "single port, single adapter" SOLID story. search-service only has the port + client, not the Gemini dep.

3. **`agent-toolset` Spring `@Component` tool beans — how does mcp-server discover them without also pulling in ai-service?**
   - What we know: Phase 9 mcp-server must import agent-toolset module; beans auto-discovered via `@ComponentScan`
   - What's unclear: if Spring AI MCP starter has its own `@ComponentScan` scope that might conflict
   - Recommendation: Explicit `@Import(AgentToolConfiguration.class)` in mcp-server rather than relying on auto-scan; avoids classpath pollution

---

## Sources

### Primary (HIGH confidence)
- github.com/googleapis/java-genai — SDK README, CHANGELOG (1.52.0 release 2026-04-30, AFC patterns, embedContent signature)
- ai.google.dev/gemini-api/docs/models/gemini-3-flash-preview — `gemini-3-flash-preview` model ID confirmed active
- ai.google.dev/gemini-api/docs/embeddings — `gemini-embedding-2` GA, `output_dimensionality`, taskType deprecated for embedding-2
- .planning/phases/08-ai-port-adapter-agent-toolset/08-CONTEXT.md — locked decisions D-01 through D-10
- infra/postgres/init.sh — confirmed `ai` + `search` schemas already provisioned [VERIFIED: codebase grep]
- config-server/src/main/resources/config/api-gateway.yml — SSE route anchor already in place [VERIFIED: codebase grep]
- config-server/src/main/resources/config/*.yml — HikariCP pool sizes per service [VERIFIED: codebase grep]
- .planning/research/STACK.md — google-genai 1.51.0 (→ bump to 1.52.0), Spring AI MCP-only, model fallback ladder
- .planning/research/ARCHITECTURE.md §6.1, §6.2 — hexagonal layout, agent-toolset interface, tool dispatch flow
- .planning/research/PITFALLS.md — Pitfalls #1, #7, #8, #9, #10, #17, #19, #20, #22

### Secondary (MEDIUM confidence)
- ai.google.dev/gemini-api/docs/text-generation — Java SDK Chat examples with `client.chats.create()`, streaming pattern, `gemini-3-flash-preview` confirmed in code samples
- ai.google.dev/gemini-api/docs/function-calling — manual vs automatic FC distinction, tool_config modes (AUTO/ANY/NONE), function call ID field (required for multi-tool turn correlation)
- developers.googleblog.com/building-with-gemini-embedding-2/ — 768/1536 dims recommended; MRL truncation; taskType replacement with task prefix in prompt
- howtodoinjava.com/spring-boot/spring-async-controller-sseemitter/ — SseEmitter executor pattern for Spring Boot

### Tertiary (LOW confidence)
- github.com/spring-projects/spring-framework/issues/33421 — SseEmitter ABBA deadlock in 6.2.x (assumed resolved in 6.3.x / Boot 3.5.14 — LOW because fix not explicitly confirmed in changelog)
- medium.com/google-cloud/using-gemini-function-calling-in-java — uses Vertex AI SDK (not google-genai); FunctionDeclaration builder patterns adapted but class names may differ

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — SDK version and model IDs verified against live sources
- Architecture: HIGH — leverages detailed CONTEXT.md decisions + existing codebase patterns
- Function-calling loop: MEDIUM — Java examples are sparse in official docs; adapted from Python/Go patterns + SDK README
- Pitfalls: HIGH — verified against PITFALLS.md + confirmed sources
- System prompt / Turkish content: MEDIUM — based on CONTEXT.md guidance + Pitfall #20 recommendations

**Research date:** 2026-05-01
**Valid until:** 2026-06-01 for stable items; 2026-05-15 for Gemini model identifiers (Preview tag means names change faster)
