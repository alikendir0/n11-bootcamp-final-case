package com.n11.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.n11.agent.ToolContext;
import com.n11.agent.ToolResult;
import com.n11.agent.http.ProductToolClient;
import org.springframework.stereotype.Component;

/**
 * Searches the product catalogue by free-text query (PROD-04 ILIKE+GIN backing).
 * In v1 this calls product-service GET /products?q= directly per D-10.
 * In v2, backing will switch to search-service (pgvector/semantic) without changing
 * this interface — the stable v1->v2 transition contract.
 */
@Component
public class SearchProductsTool extends AbstractAgentTool {

    private final ProductToolClient client;

    public SearchProductsTool(ProductToolClient client) {
        this.client = client;
    }

    @Override
    public String name() { return "search_products"; }

    @Override
    public String descriptionTr() {
        return "Ürün kataloğunda metin araması yapar. Önce bu araçla ara, sonra sonuçlardan gelen ID'leri kullan.";
    }

    @Override
    public boolean requiresAuth() { return false; }

    @Override
    public String parametersJsonSchema() {
        return "{\"type\":\"object\",\"properties\":{\"q\":{\"type\":\"string\"},\"categoryId\":{\"type\":\"string\"},\"page\":{\"type\":\"integer\",\"minimum\":0,\"default\":0},\"size\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":50,\"default\":10}}}";
    }

    @Override
    protected ToolResult doExecute(ToolContext ctx, JsonNode args) {
        String q = args.path("q").asText(null);
        String categoryId = args.path("categoryId").asText(null);
        int page = args.path("page").asInt(0);
        int size = args.path("size").asInt(10);
        return ToolResult.ok(client.searchProducts(q, categoryId, page, size));
    }
}
