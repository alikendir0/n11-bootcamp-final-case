package com.n11.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.n11.agent.ToolContext;
import com.n11.agent.ToolResult;
import com.n11.agent.http.ProductToolClient;
import org.springframework.stereotype.Component;

/**
 * Lists all product categories. Useful for guided browsing and narrowing search_products calls.
 */
@Component
public class ListCategoriesTool extends AbstractAgentTool {

    private final ProductToolClient client;

    public ListCategoriesTool(ProductToolClient client) {
        this.client = client;
    }

    @Override
    public String name() { return "list_categories"; }

    @Override
    public String descriptionTr() {
        return "Tüm ürün kategorilerini listeler.";
    }

    @Override
    public boolean requiresAuth() { return false; }

    @Override
    public String parametersJsonSchema() {
        return "{\"type\":\"object\",\"properties\":{}}";
    }

    @Override
    protected ToolResult doExecute(ToolContext ctx, JsonNode args) {
        return ToolResult.ok(client.listCategories());
    }
}
