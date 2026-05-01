package com.n11.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.n11.agent.ToolContext;
import com.n11.agent.ToolResult;
import com.n11.agent.http.CartToolClient;
import org.springframework.stereotype.Component;

/**
 * Removes a product from the cart entirely.
 * Uses DELETE /cart/items/{productId} per cart-service contract.
 */
@Component
public class RemoveFromCartTool extends AbstractAgentTool {

    private final CartToolClient client;

    public RemoveFromCartTool(CartToolClient client) {
        this.client = client;
    }

    @Override
    public String name() { return "remove_from_cart"; }

    @Override
    public String descriptionTr() {
        return "Sepetten bir ürünü kaldırır. Giriş gerektirir.";
    }

    @Override
    public boolean requiresAuth() { return true; }

    @Override
    public String parametersJsonSchema() {
        return "{\"type\":\"object\",\"properties\":{\"productId\":{\"type\":\"string\"}},\"required\":[\"productId\"]}";
    }

    @Override
    protected ToolResult doExecute(ToolContext ctx, JsonNode args) {
        String productId = args.required("productId").asText();
        return ToolResult.ok(client.removeItem(ctx.userId(), productId));
    }
}
