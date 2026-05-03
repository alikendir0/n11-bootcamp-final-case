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
        
        if (q != null && !q.isBlank() && (categoryId == null || categoryId.isBlank())) {
            try {
                JsonNode uuids = client.searchProductsSemantic(q, size);
                if (uuids != null && uuids.isArray() && !uuids.isEmpty()) {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    com.fasterxml.jackson.databind.node.ArrayNode results = mapper.createArrayNode();
                    for (JsonNode uuid : uuids) {
                        try {
                            results.add(client.getProduct(uuid.asText()));
                        } catch (Exception e) {
                            // ignore missing products
                        }
                    }
                    if (!results.isEmpty()) {
                        com.fasterxml.jackson.databind.node.ObjectNode pageResult = mapper.createObjectNode();
                        pageResult.set("content", results);
                        pageResult.put("totalElements", results.size());
                        pageResult.put("totalPages", 1);
                        pageResult.put("number", 0);
                        pageResult.put("size", size);
                        return ToolResult.ok(pageResult);
                    }
                }
            } catch (Exception e) {
                // Semantic search failed, fallback to text search
            }
        }
        
        return ToolResult.ok(client.searchProducts(q, categoryId, page, size));
    }
}
