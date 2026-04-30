---
phase: 08-ai-port-adapter-agent-toolset
plan: "03"
subsystem: ai-service.infrastructure.llm
tags: [gemini, llm-adapter, solid-demo, echo-provider, pitfall-7, pitfall-1]
dependency_graph:
  requires: [08-01]
  provides: [GeminiChatAdapter, GeminiEmbeddingAdapter, EchoChatProvider, UPSTREAM_LLM_ERROR]
  affects: [ai-service, common-error]
tech_stack:
  added:
    - google-genai 1.52.0 (ai-service only — Pitfall #7 sealed boundary)
  patterns:
    - ConditionalOnProperty for provider swap (echo vs gemini)
    - ApplicationReadyEvent model probe + fallback chain (Pitfall #1)
    - Package-sealed Gemini type mapping (GeminiTypeMapper, Pitfall #7)
    - Task-prefix embedding pattern (replaces deprecated taskType enum)
key_files:
  created:
    - ai-port/build.gradle.kts
    - ai-port/src/main/java/com/n11/ai/port/ChatProvider.java
    - ai-port/src/main/java/com/n11/ai/port/EmbeddingProvider.java
    - ai-port/src/main/java/com/n11/ai/port/dto/MessageRole.java
    - ai-port/src/main/java/com/n11/ai/port/dto/ChatMessage.java
    - ai-port/src/main/java/com/n11/ai/port/dto/ChatResponse.java
    - ai-port/src/main/java/com/n11/ai/port/dto/ToolSchema.java
    - ai-port/src/main/java/com/n11/ai/port/dto/ToolCallRequest.java
    - ai-port/src/main/java/com/n11/ai/port/dto/ToolCallResult.java
    - ai-service/src/main/java/com/n11/ai/AiServiceApplication.java
    - ai-service/src/main/java/com/n11/ai/infrastructure/llm/EchoChatProvider.java
    - ai-service/src/main/java/com/n11/ai/infrastructure/llm/GeminiChatAdapter.java
    - ai-service/src/main/java/com/n11/ai/infrastructure/llm/GeminiEmbeddingAdapter.java
    - ai-service/src/main/java/com/n11/ai/infrastructure/llm/GeminiTypeMapper.java
    - ai-service/src/main/java/com/n11/ai/infrastructure/llm/GeminiUpstreamException.java
    - ai-service/src/test/java/com/n11/ai/infrastructure/llm/EchoChatProviderTest.java
    - ai-service/src/test/java/com/n11/ai/infrastructure/llm/EchoProviderActivationTest.java
    - ai-service/src/test/java/com/n11/ai/infrastructure/llm/GeminiSmokeTest.java
  modified:
    - common-error/src/main/java/com/n11/error/ApiErrorCode.java
    - settings.gradle.kts
decisions:
  - "SDK API deviation: client.models.get() requires GetModelConfig second arg — not single-arg as shown in RESEARCH. Adapted with GetModelConfig.builder().build()."
  - "SDK API deviation: GenerateContentResponse has convenience method functionCalls() returning ImmutableList<FunctionCall> — used instead of manual candidate/part traversal."
  - "SDK API deviation: EmbedContentResponse.embeddings() returns Optional<List<ContentEmbedding>> — access guarded with .orElse(List.of())."
  - "GeminiTypeMapper uses Part.fromFunctionCall(name, argsMap) static factory instead of Part.builder().functionCall(...).build() — same semantics, cleaner API."
  - "GeminiTypeMapper uses Part.fromFunctionResponse(callId, resultMap) static factory — confirmed in SDK 1.52.0."
  - "TOOL role messages mapped to 'user' role (not 'tool') per google-genai SDK conventions for multi-turn function calling."
metrics:
  duration_minutes: 11
  tasks_completed: 2
  files_created: 19
  files_modified: 2
  completed_date: "2026-04-30"
---

# Phase 08 Plan 03: Gemini Adapters + Echo Provider + AI Error Codes Summary

One-liner: Sealed Gemini adapter package (GeminiChatAdapter + GeminiEmbeddingAdapter + GeminiTypeMapper) with EchoChatProvider SOLID demo, verified against google-genai 1.52.0 actual API surface.

## What Was Built

### Task 1: ApiErrorCode + EchoChatProvider + Tests (commit 18b4ad0)

**ApiErrorCode extended (common-error):**
Three new entries added after `INTERNAL`: `UPSTREAM_LLM_ERROR`, `TOOL_VALIDATION_FAILED`, `RATE_LIMITED`. These cover the AI failure surface (Pitfalls #5/#6/#10). Existing 5 entries unchanged.

**EchoChatProvider (ai-service):**
The SOLID demonstration artifact. 59 lines including Javadoc. Zero Gemini imports. Activated by `@ConditionalOnProperty(name = "ai.provider", havingValue = "echo")`. Echoes the last user message in chat() and chatStream(). Turkish fallback for empty history: "(boş mesaj)".

**EchoProviderActivationTest:**
SpringBootTest with `MinimalApp` inner class scanned from `EchoChatProvider.class`. Properties: `ai.provider=echo`, Spring Cloud disabled, datasource/JPA/Flyway excluded. Verifies `chatProvider` bean is `EchoChatProvider.class` — the canonical grader-runnable SOLID proof test.

**ai-port module bootstrapped (for local compilation — 08-01 provides authoritative version post-merge):**
Zero-dep `java-library` module. All 7 neutral DTOs as Java records: `MessageRole`, `ChatMessage`, `ChatResponse`, `ToolSchema`, `ToolCallRequest`, `ToolCallResult`, plus `ChatProvider` and `EmbeddingProvider` interfaces. No `com.google.genai` anywhere.

### Task 2: GeminiTypeMapper + GeminiChatAdapter + GeminiEmbeddingAdapter + GeminiSmokeTest (commit b4753a2)

**google-genai 1.52.0 API surface verification (per CLAUDE.md Rule #4):**
JAR inspected via `javap` from Gradle cache. Key findings documented in Deviations section below.

**GeminiChatAdapter:**
- `@ConditionalOnProperty(name = "ai.provider", havingValue = "gemini", matchIfMissing = true)` — default provider
- Constructor: `@Value("${ai.gemini.api-key:}")` — empty default prevents startup failure without key
- `verifyModel()` @EventListener(ApplicationReadyEvent.class): calls `client.models.get(model, GetModelConfig.builder().build())` — probes primary model, falls back to `gemini-2.5-flash` on exception (Pitfall #1)
- `chat()`: delegates to `mapper.toGeminiContents()` → `generateContent()` → `mapper.fromGeminiResponse()`
- `chatStream()`: uses `generateContentStream()` with try-with-resources on `ResponseStream<GenerateContentResponse>`
- `buildConfig()`: creates `GenerateContentConfig` with tools list if non-empty

**GeminiEmbeddingAdapter:**
- Task prefix pattern: `"task: retrieval document | content: " + text` (replaces deprecated taskType enum)
- `EmbedContentConfig.builder().outputDimensionality(outputDims).build()`
- Guards `embeddings().orElse(List.of())` and `values().orElse(List.of())`

**GeminiTypeMapper (package-private, not public):**
- `toGeminiContents()`: USER→`Content.fromParts(Part.fromText(...))`, ASSISTANT→builder with text + function calls via `Part.fromFunctionCall(name, argsMap)`, TOOL→`Part.fromFunctionResponse(callId, resultMap)` with role "user"
- `toGeminiTools()`: maps `ToolSchema` to `FunctionDeclaration` with `Schema.builder().type(new Type(Type.Known.OBJECT)).properties(...).required(...)` 
- `fromGeminiResponse()`: uses `response.functionCalls()` convenience method; falls back to `response.text()` and `response.finishReason()`

**GeminiUpstreamException:**
Package-private runtime exception. Plan 04 will catch this in `@ExceptionHandler` and translate to `ApiErrorCode.UPSTREAM_LLM_ERROR` ProblemDetail.

**GeminiSmokeTest:**
`@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")` — skips cleanly when key absent.

## Pitfall #7 Boundary Verification

```
grep -rln '^import com.google.genai' ai-service/src/main/java/com/n11/ai/ 
```
Returns exactly 3 files:
1. `GeminiChatAdapter.java`
2. `GeminiEmbeddingAdapter.java`
3. `GeminiTypeMapper.java`

Zero Gemini imports outside `infrastructure/llm/`. EchoChatProvider has 0 Gemini imports. ai-port has 0 Gemini dependencies.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] models.get() requires GetModelConfig second argument**
- **Found during:** Task 2 — SDK API inspection via `javap`
- **Issue:** RESEARCH.md showed `client.models.get(modelId)` as single-arg. Actual SDK 1.52.0 has only `get(String, GetModelConfig)` — no single-arg overload.
- **Fix:** `client.models.get(primary, GetModelConfig.builder().build())` — empty config is valid.
- **Files modified:** GeminiChatAdapter.java
- **Impact:** None on behavior — empty GetModelConfig uses SDK defaults.

**2. [Rule 1 - Bug] GenerateContentResponse.candidates() returns Optional, not List**
- **Found during:** Task 2 — SDK API inspection
- **Issue:** Plan's GeminiTypeMapper code traversed `response.candidates().isEmpty()` / `.get(0).content()` directly. Actual SDK: `candidates()` is `Optional<List<Candidate>>`.
- **Fix:** Used the SDK's `response.functionCalls()` convenience method (returns `ImmutableList<FunctionCall>`) and `response.text()` / `response.finishReason()` helpers — much simpler than manual traversal.
- **Files modified:** GeminiTypeMapper.java
- **Impact:** Cleaner, less error-prone code. Semantics identical.

**3. [Rule 1 - Bug] Map wildcard type inference in asPropertiesMap()**
- **Found during:** Task 2 — compilation error
- **Issue:** `Map<?,?>.getOrDefault("type", "string")` fails type inference for the `String` default value.
- **Fix:** Added unchecked cast `(Map<Object, Object>)` after pattern-match on `Map<?,?>`.
- **Files modified:** GeminiTypeMapper.java

**4. [Rule 2 - Missing] EmbedContentResponse null guards**
- **Found during:** Task 2 — SDK inspection showing Optional return types
- **Issue:** Plan's adapter accessed `.embeddings().get(0).values()` without Optional handling.
- **Fix:** Added `.orElse(List.of())` guards for `embeddings()` and `values()`, plus exception throw if embeddings list is empty.
- **Files modified:** GeminiEmbeddingAdapter.java

**5. [Rule 3 - Blocking] ai-port and ai-service modules not present in worktree**
- **Found during:** Setup — parallel execution worktree starts from edd90ff (pre-08-01)
- **Issue:** Plan 08-03 depends on 08-01's ai-port module. Running in parallel worktree, 08-01's files don't exist yet.
- **Fix:** Bootstrapped ai-port DTOs and interfaces locally (matching 08-01-PLAN.md spec exactly). Post-merge gate validates integration. settings.gradle.kts updated to include ai-port and ai-service.
- **Files created:** All ai-port/*.java files, settings.gradle.kts addition

## Actual google-genai 1.52.0 Method Signatures (for Plan 04)

```java
// Client
Client client = Client.builder().apiKey(apiKey).build();

// Models.get (DIFFERS from RESEARCH — requires GetModelConfig):
Model model = client.models.get(modelId, GetModelConfig.builder().build());

// Models.generateContent (matches RESEARCH):
GenerateContentResponse response = client.models.generateContent(
    modelId, List<Content> history, GenerateContentConfig config);

// Models.generateContentStream (matches RESEARCH):
ResponseStream<GenerateContentResponse> stream = client.models.generateContentStream(
    modelId, List<Content> history, GenerateContentConfig config);

// GenerateContentResponse convenience methods (RICHER than RESEARCH):
ImmutableList<FunctionCall> calls = response.functionCalls();  // NEW
String text = response.text();                                  // NEW
FinishReason reason = response.finishReason();                  // NEW
Optional<List<Candidate>> candidates = response.candidates();   // Optional, not List

// EmbedContentResponse:
Optional<List<ContentEmbedding>> embeddings = response.embeddings();
Optional<List<Float>> values = embedding.values();

// Part static factories (MATCHES RESEARCH):
Part.fromText(String text)
Part.fromFunctionCall(String name, Map<String,Object> args)
Part.fromFunctionResponse(String name, Map<String,Object> response, FunctionResponsePart... parts)

// Content static factories (MATCHES RESEARCH):
Content.fromParts(Part... parts)
Content.builder().role("model").parts(List<Part>).build()

// Schema builder:
Schema.builder().type(new Type(Type.Known.OBJECT)).properties(Map<String,Schema>).required(List<String>).build()
// Note: Type is NOT an enum — new Type(Type.Known.OBJECT) is the constructor pattern

// Tool builder:
Tool.builder().functionDeclarations(List<FunctionDeclaration>).build()
FunctionDeclaration.builder().name(s).description(s).parameters(Schema).build()

// GenerateContentConfig:
GenerateContentConfig.builder().tools(List<Tool>).build()

// EmbedContentConfig:
EmbedContentConfig.builder().outputDimensionality(int).build()
```

## Recommended Plan 04 Entry-Point Reads

Plan 04 will build `ChatService` with the manual function-calling loop (D-06). Entry-point reads:
1. `ai-service/src/main/java/com/n11/ai/infrastructure/llm/GeminiChatAdapter.java` — understand the `ChatProvider` contract and how `ChatResponse` signals text-vs-toolCalls
2. `ai-service/src/main/java/com/n11/ai/infrastructure/llm/GeminiTypeMapper.java` — understand how ToolCallResult flows back into TOOL role messages
3. `ai-port/src/main/java/com/n11/ai/port/dto/ChatResponse.java` — `text != null` → terminal; `toolCalls` non-empty → loop continues
4. `.planning/phases/08-ai-port-adapter-agent-toolset/08-CONTEXT.md` D-06, D-07, D-08 — manual loop + SSE event names + seenIds provenance
5. `ai-service/src/test/java/com/n11/ai/infrastructure/llm/EchoProviderActivationTest.java` — use as template for ChatService integration tests (swap EchoChatProvider to avoid real Gemini calls)

## Self-Check: PASSED

Files verified:
- FOUND: ai-service/src/main/java/com/n11/ai/infrastructure/llm/EchoChatProvider.java
- FOUND: ai-service/src/main/java/com/n11/ai/infrastructure/llm/GeminiChatAdapter.java
- FOUND: ai-service/src/main/java/com/n11/ai/infrastructure/llm/GeminiEmbeddingAdapter.java
- FOUND: ai-service/src/main/java/com/n11/ai/infrastructure/llm/GeminiTypeMapper.java
- FOUND: ai-service/src/main/java/com/n11/ai/infrastructure/llm/GeminiUpstreamException.java
- FOUND: common-error/src/main/java/com/n11/error/ApiErrorCode.java (8 entries confirmed)

Commits verified:
- FOUND: 18b4ad0 (Task 1 — EchoChatProvider + ApiErrorCode)
- FOUND: b4753a2 (Task 2 — GeminiChatAdapter + adapters)
