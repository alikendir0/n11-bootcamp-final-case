---
phase: 08-ai-port-adapter-agent-toolset
plan: "04"
subsystem: ai-service
tags: [chat, sse, tool-dispatch, conversation-persistence, turkish-prompt, provenance, hexagonal]
dependency_graph:
  requires: [08-02, 08-03]
  provides: [ai-service-runtime, chat-endpoint, sse-stream-endpoint, tool-dispatch-loop]
  affects: [mcp-server, api-gateway-routing]
tech_stack:
  added:
    - SseEmitter (Spring MVC) for server-sent events streaming
    - Testcontainers PostgreSQL for persistence integration tests
    - @WebMvcTest slice + @MockitoBean for SSE controller isolation
  patterns:
    - Manual function-calling loop (D-06) — ai-service owns the tool call cycle, not SDK
    - D-08 belt-and-braces: IdProvenanceService seenIds set + backend validation
    - Native INSERT ... ON CONFLICT DO NOTHING for client-assigned UUID JPA entities
    - Live mutable list (liveHistory) alongside DB persistence for in-session turns
    - SseEmitter + CachedThreadPool executor for non-blocking SSE streaming
    - @Primary on authoritative RestClient.Builder to resolve @ConditionalOnMissingBean race
key_files:
  created:
    - ai-service/src/main/java/com/n11/ai/domain/chat/ChatService.java
    - ai-service/src/main/java/com/n11/ai/domain/chat/Conversation.java
    - ai-service/src/main/java/com/n11/ai/domain/chat/Message.java
    - ai-service/src/main/java/com/n11/ai/domain/chat/MessageRoleEntity.java
    - ai-service/src/main/java/com/n11/ai/domain/chat/SystemPromptProvider.java
    - ai-service/src/main/java/com/n11/ai/domain/tools/IdProvenanceService.java
    - ai-service/src/main/java/com/n11/ai/domain/tools/ToolDispatcher.java
    - ai-service/src/main/java/com/n11/ai/application/ChatUseCase.java
    - ai-service/src/main/java/com/n11/ai/application/ConversationStateService.java
    - ai-service/src/main/java/com/n11/ai/application/ConversationStore.java
    - ai-service/src/main/java/com/n11/ai/application/GuestSessionStore.java
    - ai-service/src/main/java/com/n11/ai/infrastructure/persistence/ConversationRepository.java
    - ai-service/src/main/java/com/n11/ai/infrastructure/persistence/MessageRepository.java
    - ai-service/src/main/java/com/n11/ai/interfaces/rest/ChatController.java
    - ai-service/src/main/java/com/n11/ai/interfaces/rest/dto/ChatRequest.java
    - ai-service/src/main/java/com/n11/ai/interfaces/rest/dto/ChatReply.java
    - ai-service/src/main/java/com/n11/ai/interfaces/rest/ConversationReplayController.java
    - ai-service/src/main/java/com/n11/ai/interfaces/sse/ChatStreamController.java
    - ai-service/src/main/java/com/n11/ai/interfaces/sse/SseEvents.java
    - ai-service/src/main/java/com/n11/ai/interfaces/error/AiErrorAdvice.java
    - ai-service/src/main/resources/prompts/system-prompt-tr.txt
    - ai-service/src/test/java/com/n11/ai/application/ConversationPersistenceTest.java
    - ai-service/src/test/java/com/n11/ai/domain/chat/TurkishSystemPromptTest.java
    - ai-service/src/test/java/com/n11/ai/domain/tools/ToolDispatcherIdProvenanceTest.java
    - ai-service/src/test/java/com/n11/ai/interfaces/sse/ChatStreamSseTest.java
  modified:
    - common-logging/src/main/java/com/n11/logging/RestClientConfig.java
decisions:
  - "D-06 manual function-calling loop: ChatService.MAX_TOOL_LOOPS=6 with explicit looping, no SDK AFC; prevents invisible retry and enables seenIds provenance checking on every dispatch"
  - "D-08 IdProvenanceService: pattern-based ID field detection (.*[iI]d) + seenIds Set; hallucinated IDs rejected with UNKNOWN_ID before reaching any service backend"
  - "Native INSERT ... ON CONFLICT DO NOTHING replaces Spring Data save() for client-assigned UUIDs to avoid StaleObjectStateException from JPA merge() path"
  - "liveHistory mutable list tracks current-session turns not yet reflected in DB snapshot; history() returns this live list so EchoChatProvider/GeminiChatAdapter see correct turn order"
  - "@WebMvcTest + @MockitoBean replaces @SpringBootTest for SSE tests to avoid executor thread DB connection failures in test containers"
  - "GuestSessionStore: ConcurrentHashMap + 60-min idle TTL + background eviction every 5 min; no DB row written for guests (D-03)"
metrics:
  duration: "~3 hours"
  completed: "2026-05-01"
  tasks_completed: 3
  files_created: 25
  files_modified: 1
---

# Phase 08 Plan 04: Chat Orchestration + Persistence + REST/SSE Endpoints Summary

Manual function-calling loop (D-06) with D-08 ID provenance, dual-path conversation persistence (Postgres authed / in-memory guests), Turkish system prompt, sync REST and SSE streaming endpoints.

## What Was Built

This plan is the runtime heart of ai-service — everything in Plans 02 and 03 was infrastructure; this plan wires it together into working endpoints.

**Task 1: JPA entities + ConversationStore + GuestSessionStore (commit 95b9060)**
- `Conversation` (@Entity ai_conversations): stores userId, seenIdsJson for D-08 provenance per conversation
- `Message` (@Entity messages): role, content, toolCallJson, toolResultJson, sequenceNo
- `MessageRoleEntity` enum: USER/ASSISTANT/TOOL — JPA-layer isolation from port's MessageRole
- `ConversationRepository`: `insertIfAbsent` (native INSERT ON CONFLICT DO NOTHING) + `touchAndUpdateSeenIds`
- `MessageRepository`: `findByConversationIdOrderBySequenceNoAsc`
- `GuestSessionStore`: ConcurrentHashMap with 60-min idle TTL, scheduled eviction every 5 min (D-03)
- `ConversationStore` interface: unified session view for both authed and guest paths
- `ConversationStateService`: `AuthedStore` (DB-backed with `liveHistory`) + `GuestStore` (in-memory)
- `ConversationPersistenceTest`: authed conversation persists to Postgres; guest does not touch DB

**Task 2: ToolDispatcher + IdProvenanceService + ChatService + SystemPrompt (commit 97773be)**
- `IdProvenanceService`: regex-based ID field detection, seenIds validation (D-08 Pitfall #10)
- `ToolDispatcher`: D-04 auth gate (requiresAuth check) + D-08 provenance gate before any tool invocation
- `ChatService`: MAX_TOOL_LOOPS=6 manual loop; `chat()` sync path; `handleStream()` SSE path
- `SystemPromptProvider`: loads `prompts/system-prompt-tr.txt` from classpath at startup
- `system-prompt-tr.txt`: DİL KURALLARI (Turkish only), ARAÇ KULLANIMI, KATEGORİLER, KISITLAMALAR sections; UNKNOWN_ID rule baked in
- `ChatUseCase`: thin facade wrapping ChatService + ConversationStateService
- Tests: `ToolDispatcherIdProvenanceTest` (4 unit tests), `TurkishSystemPromptTest` (structural + 5-turn echo)

**Task 3: REST + SSE interfaces + error advice (commit 2ba987c)**
- `ChatController`: POST /chat, optional userId (guest-friendly per D-03)
- `ConversationReplayController`: GET /conversations/{id}, requiresAuth
- `ChatStreamController`: POST /chat/stream, SseEmitter(0L) + CachedThreadPool executor, MDC propagation
- `SseEvents`: typed SSE event name constants (delta, tool_call, tool_result, done, error)
- `AiErrorAdvice`: GeminiUpstreamException -> BAD_GATEWAY + UPSTREAM_LLM_ERROR ProblemDetail
- `ChatStreamSseTest`: @WebMvcTest + @MockitoBean stub verifies SSE wire format

## Test Results

```
BUILD SUCCESSFUL
ai-service tests: all pass
  - ConversationPersistenceTest (Testcontainers PostgreSQL)
  - TurkishSystemPromptTest (EchoChatProvider 5-turn assertion)
  - ToolDispatcherIdProvenanceTest (4 unit tests)
  - ChatStreamSseTest (@WebMvcTest SSE wire format)
```

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Added @Primary to correlationIdAwareRestClientBuilder in RestClientConfig**
- **Found during:** Task 1 (ConversationPersistenceTest)
- **Issue:** `NoUniqueBeanDefinitionException` — two `RestClient.Builder` beans: `correlationIdAwareRestClientBuilder` (common-logging) and `toolRestClientBuilder` (agent-toolset @ConditionalOnMissingBean). Both are regular @Configuration classes that evaluate simultaneously, so @ConditionalOnMissingBean doesn't suppress the second bean.
- **Fix:** Added `@Primary` to `correlationIdAwareRestClientBuilder` in `common-logging/src/main/java/com/n11/logging/RestClientConfig.java`
- **Files modified:** `common-logging/src/main/java/com/n11/logging/RestClientConfig.java`
- **Commit:** 95b9060

**2. [Rule 1 - Bug] Native INSERT ON CONFLICT DO NOTHING to avoid StaleObjectStateException**
- **Found during:** Task 1 (ConversationPersistenceTest)
- **Issue:** Spring Data JPA `save()` calls `merge()` when the entity UUID was assigned before save, causing an UPDATE on a non-existent row. Hibernate sees 0 rows affected and throws `StaleObjectStateException`.
- **Fix:** `ConversationRepository.insertIfAbsent()` uses native SQL `INSERT INTO ai_conversations ... ON CONFLICT (id) DO NOTHING`. Post-insert, `findById()` loads the entity normally.
- **Files modified:** `ai-service/src/main/java/com/n11/ai/infrastructure/persistence/ConversationRepository.java`
- **Commit:** 95b9060

**3. [Rule 1 - Bug] liveHistory mutable list for in-session history tracking**
- **Found during:** Task 2 (TurkishSystemPromptTest)
- **Issue:** `AuthedStore.history()` returned `persistedHistory` loaded in constructor. Appended messages (user turn, assistant turn) weren't reflected, so `EchoChatProvider` saw the system prompt as the most recent USER message and echoed it instead of the user's actual input. TurkishSystemPromptTest 5-turn assertion failed.
- **Fix:** Renamed `persistedHistory` to `liveHistory` as a mutable `ArrayList<Message>`; all `appendXxx` methods add the message to `liveHistory` in addition to persisting to DB. `history()` returns `liveHistory`.
- **Files modified:** `ai-service/src/main/java/com/n11/ai/application/ConversationStateService.java`
- **Commit:** 97773be

**4. [Rule 3 - Blocking] @WebMvcTest slice to avoid SSE executor thread DB failures**
- **Found during:** Task 3 (ChatStreamSseTest)
- **Issue:** `@SpringBootTest` + `Testcontainers` caused "Async result not set during timeToWait=0" because the `executor` background thread's JPA operations hit DB connection errors (EOFException/SQLSTATE 08006) before `emitter.send()` could complete.
- **Fix:** Changed test to `@WebMvcTest(ChatStreamController.class)` + `@MockitoBean ChatUseCase` + `doAnswer` stub that directly invokes the emit `BiConsumer`. No DB, no Testcontainers, pure SSE wire format verification.
- **Files modified:** `ai-service/src/test/java/com/n11/ai/interfaces/sse/ChatStreamSseTest.java`
- **Commit:** 2ba987c

## Known Stubs

None. All endpoints are wired to real implementations. EchoChatProvider is the test double for ChatProvider — it is not a stub in the UI-visible sense; it is the substitutability demonstration required by CLAUDE.md Rule #1. Real Gemini invocation happens in `GeminiChatAdapter` (Plan 03) when `ai.provider=gemini`.

## Threat Flags

| Flag | File | Description |
|------|------|-------------|
| threat_flag: missing-auth-on-stream-endpoint | ChatStreamController.java | POST /chat/stream accepts null userId (guest mode D-03) — upstream gateway must enforce auth policy per route; no gateway-level auth check is validated in this plan |

## Self-Check: PASSED
