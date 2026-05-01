package com.n11.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.n11.agent.ToolContext;
import com.n11.agent.ToolResult;
import com.n11.agent.http.ProductToolClient;
import org.springframework.stereotype.Component;

/**
 * Fetches full product details (price, stock, description) for a known productId.
 * productId must come from a prior search_products tool result (D-08 Pitfall #10 provenance).
 */
@Component
public class GetProductTool extends AbstractAgentTool {

    private final ProductToolClient client;

    public GetProductTool(ProductToolClient client) {
        this.client = client;
    }

    @Override
    public String name() { return "get_product"; }

    @Override
    public String descriptionTr() {
        return "Bir ürünün tam detaylarını getirir (fiyat, stok, açıklama).";
    }

    @Override
    public boolean requiresAuth() { return false; }

    @Override
    public String parametersJsonSchema() {
        return "{\"type\":\"object\",\"properties\":{\"productId\":{\"type\":\"string\",\"description\":\"Önce search_products ile elde edilmiş ürün ID'si\"}},\"required\":[\"productId\"]}";
    }

    @Override
    protected ToolResult doExecute(ToolContext ctx, JsonNode args) {
        String productId = args.required("productId").asText();
        return ToolResult.ok(client.getProduct(productId));
    }
}
