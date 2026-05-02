# ai-port

> **Phase 8** — Provider-Agnostic LLM Abstraction

Zero-dependency Gradle module defining the `ChatProvider` and `EmbeddingProvider` ports with neutral DTOs. **No Gemini SDK types leak into this module.** This is the SOLID centerpiece of the project — the abstraction that graders inspect for QUAL-08.

## The SOLID Thesis

> *"If the abstraction leaks, the entire grading thesis collapses."* — CLAUDE.md Rule #1

This module proves **Dependency Inversion** and **Open-Closed Principle**:
- `ChatProvider` and `EmbeddingProvider` are interfaces with neutral DTOs
- Implementations live **outside** this module (in `ai-service`)
- Swapping LLM providers (Gemini → OpenAI → Claude) requires only a new adapter, zero changes to ports or consumers

## Interfaces

### ChatProvider

```java
public interface ChatProvider {
    ChatResponse chat(List<ChatMessage> messages, List<ToolSchema> tools);
}
```

### EmbeddingProvider

```java
public interface EmbeddingProvider {
    float[] embed(String text, int outputDims);
}
```

## DTOs (Neutral — No SDK Types)

| DTO | Purpose |
|-----|---------|
| `ChatMessage` | Role + content (text or tool-call/result) |
| `MessageRole` | `USER`, `MODEL`, `TOOL_CALL`, `TOOL_RESULT` |
| `ChatResponse` | Text response or tool-call requests |
| `ToolCallRequest` | Tool name + JSON args |
| `ToolCallResult` | Tool name + JSON result |
| `ToolSchema` | Tool name + description + JSON Schema for args |

## Implementations

| Adapter | Module | Purpose |
|---------|--------|---------|
| `GeminiChatAdapter` | ai-service | Production chat via google-genai 1.52.0 |
| `GeminiEmbeddingAdapter` | ai-service | Production embeddings via gemini-embedding-2 |
| `EchoChatProvider` | ai-service | Test swap — echoes prompt back |
| No-op stub | search-service | Zero-vector deterministic stub |

## Dependency Guard

`AiPortContractTest` (in this module) asserts:
- Zero `com.google.genai` imports in ai-port source
- All DTOs are `record` types or plain Java (no SDK wrappers)
- The module compiles with zero external dependencies beyond JDK

```bash
./gradlew :ai-port:test
```
]]>
