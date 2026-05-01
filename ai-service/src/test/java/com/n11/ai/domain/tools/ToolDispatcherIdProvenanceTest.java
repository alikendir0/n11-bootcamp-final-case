package com.n11.ai.domain.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.n11.agent.AgentTool;
import com.n11.agent.ToolContext;
import com.n11.agent.ToolRegistry;
import com.n11.agent.ToolResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class ToolDispatcherIdProvenanceTest {

    private final ObjectMapper json = new ObjectMapper();

    private ToolDispatcher dispatcher(AgentTool... tools) {
        ToolRegistry registry = new ToolRegistry(List.of(tools));
        return new ToolDispatcher(registry, new IdProvenanceService());
    }

    @Test
    void hallucinated_productId_returns_UNKNOWN_ID_error() throws Exception {
        AgentTool addToCart = stubTool("add_to_cart", true,
            ToolResult.ok(json.createObjectNode().put("ok", true)));
        ToolDispatcher d = dispatcher(addToCart);

        Set<String> seenIds = ConcurrentHashMap.newKeySet();
        // No prior search_products result -- productId 'prod-12345678' is hallucinated.
        String args = "{\"productId\":\"prod-12345678\",\"qty\":1}";
        ToolResult r = d.dispatch("add_to_cart", args, "user-1", "corr-1", seenIds);

        assertThat(r).isInstanceOf(ToolResult.Err.class);
        ToolResult.Err err = (ToolResult.Err) r;
        assertThat(err.code()).isEqualTo("UNKNOWN_ID");
        assertThat(err.message()).contains("productId");
    }

    @Test
    void valid_productId_after_search_passes_provenance() throws Exception {
        AgentTool addToCart = stubTool("add_to_cart", true,
            ToolResult.ok(json.createObjectNode().put("ok", true)));
        ToolDispatcher d = dispatcher(addToCart);

        Set<String> seenIds = ConcurrentHashMap.newKeySet();
        seenIds.add("prod-12345678");      // simulates earlier search_products result

        String args = "{\"productId\":\"prod-12345678\",\"qty\":1}";
        ToolResult r = d.dispatch("add_to_cart", args, "user-1", "corr-1", seenIds);
        assertThat(r).isInstanceOf(ToolResult.Ok.class);
    }

    @Test
    void mutating_tool_without_userId_returns_AUTH_REQUIRED() {
        AgentTool addToCart = stubTool("add_to_cart", true, ToolResult.ok(json.createObjectNode()));
        ToolDispatcher d = dispatcher(addToCart);

        ToolResult r = d.dispatch("add_to_cart", "{}", null, "corr", ConcurrentHashMap.newKeySet());

        assertThat(r).isInstanceOf(ToolResult.Err.class);
        assertThat(((ToolResult.Err) r).code()).isEqualTo("AUTH_REQUIRED");
    }

    @Test
    void successful_Ok_extracts_ids_into_seenIds() throws Exception {
        ObjectNode data = json.createObjectNode();
        data.putArray("items")
            .addObject().put("productId", "prod-aaaaaaaa").put("qty", 1);
        AgentTool searchProducts = stubTool("search_products", false, ToolResult.ok(data));
        ToolDispatcher d = dispatcher(searchProducts);

        Set<String> seenIds = ConcurrentHashMap.newKeySet();
        d.dispatch("search_products", "{\"q\":\"x\"}", null, "corr", seenIds);
        assertThat(seenIds).contains("prod-aaaaaaaa");
    }

    private AgentTool stubTool(String name, boolean requiresAuth, ToolResult fixed) {
        return new AgentTool() {
            @Override public String name() { return name; }
            @Override public String descriptionTr() { return "stub"; }
            @Override public boolean requiresAuth() { return requiresAuth; }
            @Override public String parametersJsonSchema() { return "{\"type\":\"object\"}"; }
            @Override public ToolResult execute(ToolContext ctx, JsonNode args) { return fixed; }
        };
    }
}
