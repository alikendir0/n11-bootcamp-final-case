# ai-service

> **Phase 8** — AI Shopping Assistant

Gemini-powered Turkish chat assistant with SSE streaming, function-calling (10 tools), and conversation persistence. The chat assistant can search products, manage the cart, place orders, and generate payment links — all via natural language in Turkish.

## Endpoints

| Verb | Path | Auth | Purpose |
|------|------|------|---------|
| POST | `/chat` | Public (guest) or JWT | Non-streaming chat (fallback) |
| POST | `/chat/stream` | Public (guest) or JWT | SSE token streaming with tool-call indicators |
| GET | `/conversations/{id}` | JWT (X-User-Id) | Replay persisted conversation |

## Architecture

```
Frontend (SSE) --> ChatStreamController --> ChatService --> ChatProvider (port)
                                              |                    |
                                              v                    v
                                        ToolDispatcher      GeminiChatAdapter
                                              |              (or EchoChatProvider)
                                              v
                                        agent-toolset
                                        (10 shared tools)
```

### Function-Calling Loop

1. User message + conversation history sent to Gemini
2. Gemini returns text OR function-call request
3. `ToolDispatcher` validates IDs (no hallucinated UUIDs accepted) and executes the tool
4. Tool result fed back to Gemini
5. Loop repeats (max 6 rounds) until Gemini returns a text response
6. Final text streamed token-by-token via SSE

### SSE Event Types

| Event | Data | Purpose |
|-------|------|---------|
| `token` | `{"text": "..."}` | Streaming text chunk |
| `tool_start` | `{"tool": "search_products", "args": {...}}` | Tool execution indicator |
| `tool_result` | `{"tool": "search_products", "result": {...}}` | Tool result for UI rendering |
| `done` | `{}` | Stream complete |
| `error` | `{"message": "..."}` | Error during generation |

### Conversation Persistence

- Conversations stored in `ai_conversations` + `messages` tables (Postgres `ai` schema)
- Keyed by `user_id` (JWT) or `guest_session_id` (cookie)
- Survives page refresh and navigation
- `GuestSessionStore` generates and manages guest session cookies

## Provider-Agnostic Design (QUAL-08)

The `ai-port` module defines `ChatProvider` and `EmbeddingProvider` as zero-dependency interfaces. Two implementations exist:

| Provider | Bean Name | Purpose |
|----------|-----------|---------|
| `GeminiChatAdapter` | `geminiChatAdapter` | Production — calls Gemini via google-genai SDK |
| `EchoChatProvider` | `echoChatProvider` | Test swap — echoes prompt back; proves SOLID substitutability |

Switch with: `ai.provider=echo` in config.

**Zero Gemini SDK imports** outside `ai-service/src/main/java/.../infrastructure/llm/` — verified by `AiPortContractTest`.

## System Prompt

Turkish-first system prompt forces:
- `dil: tr-TR` — all responses in Turkish
- Tool-use guidance in Turkish ("arac calistirilyor...")
- n11 branding and shopping assistant persona
- Price formatting with TRY and Turkish locale

## Required Environment Variables

| Variable | Description |
|----------|-------------|
| `GEMINI_API_KEY` | Google AI Studio API key |
| `AI_DB_PASSWORD` | PostgreSQL password for `ai_user` |

## Build & Run

```bash
./gradlew :ai-service:jibDockerBuild
docker compose up -d ai-service
```

## Tests

```bash
./gradlew :ai-service:test
```

| Test | Purpose |
|------|---------|
| EchoChatProviderTest | Echo adapter responds correctly |
| EchoProviderActivationTest | `ai.provider=echo` activates EchoChatProvider |
| TurkishSystemPromptTest | System prompt contains Turkish instructions |
| ToolDispatcherIdProvenanceTest | Hallucinated UUIDs are rejected |
| ConversationPersistenceTest | Conversations survive across sessions |
| ChatStreamSseTest | SSE event types are correctly formatted |
| GeminiSmokeTest | Live Gemini API call (requires GEMINI_API_KEY) |
