package com.n11.mcp.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.n11.agent.AgentTool;
import com.n11.agent.ToolContext;
import com.n11.agent.ToolRegistry;
import com.n11.agent.ToolResult;
import com.n11.mcp.auth.AgentJwtCache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Phase 9 (D-09 / SC-1 / SC-4 / CLAUDE.md Rule #2). Adapter that exposes the
 * Phase 8 agent-toolset over Spring AI's MCP wire surface — without ever
 * defining a tool here. The adapter's existence is the structural DRY proof:
 * the mcp-server module contains no local classes that implement the shared
 * tool contract, and the McpServerToolsListEqualityTest (Plan 09-06) asserts
 * byte equality of names + schemas against ToolRegistry.
 *
 * <p>Per-call ToolContext shape (D-10 / D-13):
 * <ul>
 *   <li>{@code userId}: from {@link AgentJwtCache#userId()} — the JWT sub claim
 *       (= bound real user_id from /agents/exchange, D-06).</li>
 *   <li>{@code correlationId}: MDC value (set by common-logging) or fresh UUID.</li>
 *   <li>{@code seenIds}: empty {@code Set.of()} — D-13 explicitly drops the
 *       conversation-scoped provenance check on the MCP surface; backing services
 *       are the authoritative ID gate via 404 → ToolResult.Err mapping.</li>
 * </ul>
 *
 * <p>Schema honoring: AgentTool.parametersJsonSchema() returns a JSON Schema
 * String. We pass it verbatim into {@link ToolDefinition#builder()} via
 * {@code inputSchema(String)}. {@code FunctionToolCallback.inputType(JsonNode.class)}
 * tells Spring AI to deserialize MCP tool-call args into a Jackson JsonNode and
 * NOT regenerate a schema from reflection (Pitfall #3 in 09-RESEARCH.md).
 */
@Configuration
public class
AgentToolMcpRegistration {

    private static final Logger log = LoggerFactory.getLogger(AgentToolMcpRegistration.class);

    private final ToolRegistry toolRegistry;
    private final AgentJwtCache jwtCache;

    public AgentToolMcpRegistration(ToolRegistry toolRegistry, AgentJwtCache jwtCache) {
        this.toolRegistry = toolRegistry;
        this.jwtCache = jwtCache;
    }

    @Bean
    public ToolCallbackProvider agentTools() {
        List<ToolCallback> callbacks = toolRegistry.all().stream()
                .map(this::wrap)
                .toList();
        log.info("MCP transport adapter registered {} tools: {}",
                callbacks.size(),
                callbacks.stream().map(c -> c.getToolDefinition().name()).toList());
        return ToolCallbackProvider.from(callbacks);
    }

    private ToolCallback wrap(AgentTool tool) {
        return FunctionToolCallback
                .builder(tool.name(), (JsonNode args) -> invoke(tool, args))
                .description(tool.descriptionTr())
                .inputSchema(tool.parametersJsonSchema())
                .inputType(JsonNode.class)
                .build();
    }

    private ToolResult invoke(AgentTool tool, JsonNode args) {
        String userId = jwtCache.userId();
        String correlationId = MDC.get("correlationId");
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        ToolContext ctx = new ToolContext(userId, correlationId, Set.of());
        return tool.execute(ctx, args);
    }
}
