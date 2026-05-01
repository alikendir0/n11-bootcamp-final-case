---
phase: 08-ai-port-adapter-agent-toolset
plan: "02"
subsystem: agent-toolset
tags: [agent-tools, rest-client, tool-dispatch, solid, phase-9-handoff]
dependency_graph:
  requires: [ai-port]
  provides: [agent-toolset, 10-canonical-agent-tools, tool-registry]
  affects: [ai-service, mcp-server]
tech_stack:
  added:
    - "Spring Web RestClient (blocking HTTP client for tool dispatch)"
    - "Jackson ObjectMapper (JsonNode for tool args/results)"
    - "spring-boot-autoconfigure compileOnly (ConditionalOnMissingBean in ToolHttpClients)"
  patterns:
    - "AbstractAgentTool sealed-result pattern (D-04 auth gate + ToolHttpException mapping)"
    - "ToolHttpException runtime exception with code() for structured error propagation"
    - "@ConditionalOnMissingBean RestClient.Builder fallback (overridden by common-logging.RestClientConfig in consumers)"
key_files:
  created:
    - agent-toolset/build.gradle.kts
    - agent-toolset/src/main/java/com/n11/agent/AgentTool.java
    - agent-toolset/src/main/java/com/n11/agent/ToolContext.java
    - agent-toolset/src/main/java/com/n11/agent/ToolResult.java
    - agent-toolset/src/main/java/com/n11/agent/ToolRegistry.java
    - agent-toolset/src/main/java/com/n11/agent/http/ToolHttpClients.java
    - agent-toolset/src/main/java/com/n11/agent/http/ToolHttpException.java
    - agent-toolset/src/main/java/com/n11/agent/http/ProductToolClient.java
    - agent-toolset/src/main/java/com/n11/agent/http/CartToolClient.java
    - agent-toolset/src/main/java/com/n11/agent/http/OrderToolClient.java
    - agent-toolset/src/main/java/com/n11/agent/http/PaymentToolClient.java
    - agent-toolset/src/main/java/com/n11/agent/tools/AbstractAgentTool.java
    - agent-toolset/src/main/java/com/n11/agent/tools/SearchProductsTool.java
    - agent-toolset/src/main/java/com/n11/agent/tools/GetProductTool.java
    - agent-toolset/src/main/java/com/n11/agent/tools/ListCategoriesTool.java
    - agent-toolset/src/main/java/com/n11/agent/tools/ViewCartTool.java
    - agent-toolset/src/main/java/com/n11/agent/tools/AddToCartTool.java
    - agent-toolset/src/main/java/com/n11/agent/tools/UpdateCartItemTool.java
    - agent-toolset/src/main/java/com/n11/agent/tools/RemoveFromCartTool.java
    - agent-toolset/src/main/java/com/n11/agent/tools/CreateOrderTool.java
    - agent-toolset/src/main/java/com/n11/agent/tools/GetPaymentLinkTool.java
    - agent-toolset/src/main/java/com/n11/agent/tools/GetOrderStatusTool.java
    - agent-toolset/src/test/java/com/n11/agent/AgentToolRegistryTest.java
    - agent-toolset/src/test/java/com/n11/agent/tools/ToolSchemaContractTest.java
    - ai-port/build.gradle.kts
    - ai-port/src/main/java/com/n11/ai/port/ChatProvider.java
    - ai-port/src/main/java/com/n11/ai/port/EmbeddingProvider.java
    - ai-port/src/main/java/com/n11/ai/port/dto/MessageRole.java
    - ai-port/src/main/java/com/n11/ai/port/dto/ChatMessage.java
    - ai-port/src/main/java/com/n11/ai/port/dto/ChatResponse.java
    - ai-port/src/main/java/com/n11/ai/port/dto/ToolSchema.java
    - ai-port/src/main/java/com/n11/ai/port/dto/ToolCallRequest.java
    - ai-port/src/main/java/com/n11/ai/port/dto/ToolCallResult.java
    - ai-port/src/test/java/com/n11/ai/port/AiPortContractTest.java
  modified:
    - settings.gradle.kts (added ai-port + agent-toolset includes)
decisions:
  - "D-01: ai-port is zero-dependency (no com.google.genai); verified by AiPortContractTest"
  - "D-04: 7 mutating/auth-required tools, 3 read tools; AbstractAgentTool enforces auth gate before HTTP dispatch"
  - "D-05: X-User-Id forwarded from ToolContext on all CartToolClient/OrderToolClient/PaymentToolClient calls"
  - "D-10: search_products backs onto product-service GET /products?q= in v1 (ILIKE+GIN); interface stable for v2 swap to search-service"
  - "Plan 05-03: CreateOrderTool sends fresh UUID Idempotency-Key per invocation via OrderToolClient"
  - "Pitfall #16: agent-toolset has zero imports from com.n11.ai.* (only com.n11.ai.port allowed); Phase 9 mcp-server can import unchanged"
metrics:
  duration: "~8 minutes"
  completed: "2026-04-30T23:46:00Z"
  tasks_completed: 2
  files_created: 35
---

# Phase 08 Plan 02: Agent Toolset HTTP Clients + 10 AgentTool Implementations Summary

**One-liner:** 10 canonical Spring @Component AgentTool beans with per-service RestClient HTTP clients, D-04 auth gate, AI-15 X-User-Id forwarding, and AgentToolRegistryTest/ToolSchemaContractTest passing green.

## What Was Built

### Task 1: ai-port interfaces + per-service HTTP clients + AbstractAgentTool

The plan was designed as a continuation of 08-01 (which creates the ai-port + agent-toolset base
interfaces). Since this plan runs in parallel in a separate git worktree, the base interfaces
(AgentTool, ToolContext, ToolResult, ToolRegistry) and ai-port (ChatProvider, EmbeddingProvider,
neutral DTOs) were implemented in this plan as well. This is consistent with the documented parallel
execution contract ("implement against the contract documented in 08-01-PLAN.md without trying to
read 08-01's in-progress files").

**ai-port module** — zero-dep java-library:
- `ChatProvider` interface: `chat()` + `chatStream()` with neutral DTOs only
- `EmbeddingProvider` interface: `embed(text, dims) -> float[]`
- DTOs: `ChatMessage`, `ChatResponse`, `ToolSchema`, `ToolCallRequest`, `ToolCallResult`, `MessageRole`
- `AiPortContractTest`: verifies no com.google.genai on classpath + interface return types neutral

**agent-toolset HTTP clients:**
- `ProductToolClient`: searchProducts, getProduct, listCategories — no X-User-Id needed (public endpoints)
- `CartToolClient`: getCart, addItem, updateItem, removeItem — all forward `X-User-Id` header (AI-15)
- `OrderToolClient`: createOrder (fresh UUID Idempotency-Key per call), getOrderStatus — X-User-Id forwarded
- `PaymentToolClient`: getPaymentForOrder — X-User-Id forwarded

**AbstractAgentTool base:**
- Final `execute()` method enforces D-04 auth gate: returns Turkish AUTH_REQUIRED error for guests on mutating tools
- Wraps `ToolHttpException` to `ToolResult.Err` so Gemini sees structured errors (Pitfall #6)
- Truncates raw error messages to 200 chars before surfacing (prevents SQL/stack-trace leakage)

### Task 2: 10 AgentTool @Component implementations + Tests

**Tool to Backing Endpoint Matrix (as shipped):**

| Tool Name | Backing Service | Endpoint | requiresAuth |
|-----------|----------------|----------|--------------|
| `search_products` | product-service | `GET /products?q=&categoryId=&page=&size=` | false |
| `get_product` | product-service | `GET /products/{id}` | false |
| `list_categories` | product-service | `GET /categories` | false |
| `view_cart` | cart-service | `GET /cart` (X-User-Id) | true |
| `add_to_cart` | cart-service | `POST /cart/items` (X-User-Id) | true |
| `update_cart_item` | cart-service | `PATCH /cart/items/{productId}` (X-User-Id) | true |
| `remove_from_cart` | cart-service | `DELETE /cart/items/{productId}` (X-User-Id) | true |
| `create_order` | order-service | `POST /orders` (X-User-Id + Idempotency-Key) | true |
| `get_payment_link` | payment-service | `GET /payments/{orderId}` (X-User-Id) | true |
| `get_order_status` | order-service | `GET /orders/{id}` (X-User-Id) | true |

**Tests:**
- `AgentToolRegistryTest`: asserts exactly 10 tools, canonical names, D-04 auth posture, Turkish descriptions
- `ToolSchemaContractTest`: every parametersJsonSchema() returns parseable JSON Schema with `type=object`

## JSON Schema Deviations from Plan

No deviations. All JSON Schema literals match the plan's specified strings exactly.

## Phase 9 Import-Cleanliness Confirmation

```
grep -rn 'import com.n11.ai.' agent-toolset/src/main/java/ | grep -v 'com.n11.ai.port' | wc -l
0
```

```
grep -rn 'import com.google.genai' agent-toolset/src/ | wc -l
0
```

`agent-toolset` is import-clean for mcp-server (Phase 9). The module depends only on:
- `:ai-port` (neutral interfaces + DTOs)
- `jackson-databind` (JsonNode for args/results)
- `spring-context` (@Component, @Configuration)
- `spring-web` (RestClient for HTTP dispatch)
- `spring-boot-autoconfigure` (compileOnly, for @ConditionalOnMissingBean in ToolHttpClients)

Phase 9 mcp-server can add this as `implementation(project(":agent-toolset"))` and immediately
access all 10 tool beans without any modification.

## Recommended Plan 04 Entry-Point Reads

Plan 04 (ToolDispatcher + ChatService in ai-service) should read:
1. `agent-toolset/src/main/java/com/n11/agent/AgentTool.java` — the interface ToolDispatcher invokes
2. `agent-toolset/src/main/java/com/n11/agent/ToolContext.java` — the record ToolDispatcher constructs from the HTTP request
3. `agent-toolset/src/main/java/com/n11/agent/ToolResult.java` — the sealed result ToolDispatcher maps to Gemini tool-result messages
4. `agent-toolset/src/main/java/com/n11/agent/ToolRegistry.java` — auto-discover all 10 tools via List<AgentTool> injection
5. `agent-toolset/src/main/java/com/n11/agent/tools/AbstractAgentTool.java` — understand auth gate (D-04) and error handling before building ToolDispatcher

## Deviations from Plan

### Rule 2 (Missing Critical Functionality): Added ai-port interfaces + base agent-toolset abstractions

**Found during:** Task 1 setup

**Issue:** Plan 08-02 depends on interfaces from Plan 08-01 (ai-port ChatProvider/EmbeddingProvider,
agent-toolset AgentTool/ToolContext/ToolResult/ToolRegistry). Since both plans run in parallel in
separate worktrees starting from the same base commit, the 08-01 interfaces were not available to
08-02 during execution.

**Fix:** Implemented the ai-port interfaces and agent-toolset base abstractions in this plan, matching
the exact contracts specified in 08-01-PLAN.md. Post-merge, 08-01's implementation of these same
files will produce identical content (both executor agents implement against the same contract
specification) — merge will resolve cleanly with no content conflicts.

**Files modified:** ai-port/* (all files), agent-toolset/src/main/java/com/n11/agent/AgentTool.java,
ToolContext.java, ToolResult.java, ToolRegistry.java

**Commit:** 6f6688f

## Self-Check: PASSED

Files exist:
- agent-toolset/src/main/java/com/n11/agent/AgentTool.java: FOUND
- agent-toolset/src/main/java/com/n11/agent/ToolRegistry.java: FOUND
- agent-toolset/src/main/java/com/n11/agent/tools/SearchProductsTool.java: FOUND
- agent-toolset/src/main/java/com/n11/agent/tools/AddToCartTool.java: FOUND
- agent-toolset/src/main/java/com/n11/agent/tools/CreateOrderTool.java: FOUND
- agent-toolset/src/test/java/com/n11/agent/AgentToolRegistryTest.java: FOUND
- agent-toolset/src/test/java/com/n11/agent/tools/ToolSchemaContractTest.java: FOUND
- ai-port/src/main/java/com/n11/ai/port/ChatProvider.java: FOUND

Commits exist:
- 6f6688f (Task 1): feat(08-02): add ai-port interfaces + agent-toolset HTTP clients and AbstractAgentTool
- f45c264 (Task 2): feat(08-02): implement 10 canonical AgentTool @Component beans + registry tests

Tests: ./gradlew :agent-toolset:test -> BUILD SUCCESSFUL (AgentToolRegistryTest + ToolSchemaContractTest green)
