package com.n11.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.n11.agent.ToolContext;
import com.n11.agent.ToolResult;
import com.n11.agent.http.CartToolClient;
import org.springframework.stereotype.Component;

/**
 * Updates the quantity of a product already in the cart.
 * Uses PATCH /cart/items/{productId} per cart-service contract.
 */
@Component
public class UpdateCartItemTool extends AbstractAgentTool {

    private final CartToolClient client;

    public UpdateCartItemTool(CartToolClient client) {
        this.client = client;
    }

    @Override
    public String name() { return "update_cart_item"; }

    @Override
    public String descriptionTr() {
        return "Sepetteki bir ürünün adedini günceller. Giriş gerektirir.";
    }

    @Override
    public boolean requiresAuth() { return true; }

    @Override
    public String parametersJsonSchema() {
        return "{\"type\":\"object\",\"properties\":{\"productId\":{\"type\":\"string\"},\"qty\":{\"type\":\"integer\",\"minimum\":1}},\"required\":[\"productId\",\"qty\"]}";
    }

    @Override
    protected ToolResult doExecute(ToolContext ctx, JsonNode args) {
        String productId = args.required("productId").asText();
        int qty = args.required("qty").asInt();
        return ToolResult.ok(client.updateItem(ctx.userId(), productId, qty));
    }
}
