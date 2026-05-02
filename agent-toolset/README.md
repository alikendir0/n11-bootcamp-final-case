# agent-toolset

> **Phase 8** — 10 Canonical Agent Tools

Shared Gradle module defining the 10 tools that both `ai-service` (chat assistant) and `mcp-server` (external agents) consume. **Single source of truth** — tool definitions are never duplicated.

## Tools

| Tool | Type | Description |
|------|------|-------------|
| `search_products` | read | Search the product catalog with query and pagination |
| `get_product` | read | Get detailed product information by UUID |
| `list_categories` | read | List all product categories |
| `add_to_cart` | mutate | Add a product to the user's cart |
| `view_cart` | read | View the current cart contents |
| `update_cart_item` | mutate | Update quantity of a cart line item |
| `remove_from_cart` | mutate | Remove an item from the cart |
| `create_order` | mutate | Create an order from the current cart |
| `get_payment_link` | read | Get the Iyzico checkout payment URL for an order |
| `get_order_status` | read | Get the current status of an order |

## Architecture

```java
public interface AgentTool {
    String name();
    String description();
    ToolSchema inputSchema();
    ToolResult execute(Map<String, Object> args, ToolContext context);
}
```

- `AbstractAgentTool` — base class with JSON Schema generation and common validation
- `ToolContext` — carries `userId`, `correlationId`, and auth headers for downstream calls
- `ToolResult` — success/error result with serializable data

## HTTP Clients

Each tool delegates to a type-safe HTTP client that calls the respective service via Eureka discovery:

| Client | Target Service |
|--------|---------------|
| `ProductToolClient` | product-service |
| `CartToolClient` | cart-service |
| `OrderToolClient` | order-service |
| `PaymentToolClient` | payment-service |

All clients propagate `X-User-Id` from the `ToolContext`.

## Consumers

| Consumer | How it's used |
|----------|--------------|
| `ai-service` | `ToolDispatcher` invokes tools during Gemini function-calling loop |
| `mcp-server` | `AgentToolMcpRegistration` wraps tools as MCP `ToolCallbackProvider` |

## DRY Enforcement

`McpServerToolsListEqualityTest` in `infra-tests` asserts both consumers expose identical tool catalogs. Adding a tool in one place automatically appears in both surfaces.

```bash
./gradlew :agent-toolset:test
```
]]>
