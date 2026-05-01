package com.n11.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.n11.agent.ToolContext;
import com.n11.agent.ToolResult;
import com.n11.agent.http.CartToolClient;
import org.springframework.stereotype.Component;

/**
 * Displays the user's current cart with totals.
 * Requires auth (D-04): cart-service returns 401 for missing X-User-Id.
 * Auth gate here provides better UX — Gemini suggests login before calling backend.
 */
@Component
public class ViewCartTool extends AbstractAgentTool {

    private final CartToolClient client;

    public ViewCartTool(CartToolClient client) {
        this.client = client;
    }

    @Override
    public String name() { return "view_cart"; }

    @Override
    public String descriptionTr() {
        return "Kullanıcının sepetini ve toplam tutarı gösterir.";
    }

    @Override
    public boolean requiresAuth() { return true; }

    @Override
    public String parametersJsonSchema() {
        return "{\"type\":\"object\",\"properties\":{}}";
    }

    @Override
    protected ToolResult doExecute(ToolContext ctx, JsonNode args) {
        return ToolResult.ok(client.getCart(ctx.userId()));
    }
}
