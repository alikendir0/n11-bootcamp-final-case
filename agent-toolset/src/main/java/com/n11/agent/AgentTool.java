package com.n11.agent;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Provider-agnostic agent tool contract (D-04 / D-08 / Pitfall #16).
 *
 * Two consumers: ai-service ToolDispatcher (Phase 8) and mcp-server
 * (Phase 9) via Spring AI MCP @Tool annotation. Tools MUST NOT depend
 * on ai-service-specific beans (no SseEmitter, no ConversationRepository).
 */
public interface AgentTool {
    /** English, stable across v1->v2 (e.g. "search_products"). */
    String name();

    /** Turkish description shown to the LLM (Pitfall #20). */
    String descriptionTr();

    /** D-04: mutating tools return AUTH_REQUIRED for guests. */
    boolean requiresAuth();

    /** JSON Schema string for the function parameters. */
    String parametersJsonSchema();

    /** Execute the tool with parsed args + context. */
    ToolResult execute(ToolContext ctx, JsonNode args);
}
