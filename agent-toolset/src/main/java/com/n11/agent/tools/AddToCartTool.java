package com.n11.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.n11.agent.ToolContext;
import com.n11.agent.ToolResult;
import com.n11.agent.http.CartToolClient;
import org.springframework.stereotype.Component;

/**
 * Adds a product to the user's cart (AI-15: chat cart = REST cart, same cart-service row).
 * X-User-Id forwarded from ToolContext so the cart updated here is visible in the storefront.
 */
@Component
public class AddToCartTool extends AbstractAgentTool {

    private final CartToolClient client;

    public AddToCartTool(CartToolClient client) {
        this.client = client;
    }

    @Override
    public String name() { return "add_to_cart"; }

    @Override
    public String descriptionTr() {
        return "Sepete ürün ekler. Giriş gerektirir.";
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
        return ToolResult.ok(client.addItem(ctx.userId(), productId, qty));
    }
}
