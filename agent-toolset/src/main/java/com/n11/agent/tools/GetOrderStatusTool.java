package com.n11.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.n11.agent.ToolContext;
import com.n11.agent.ToolResult;
import com.n11.agent.http.OrderToolClient;
import org.springframework.stereotype.Component;

/**
 * Shows the current status and status timeline of a user's order.
 * Orders are user-scoped so X-User-Id forwarding is required.
 */
@Component
public class GetOrderStatusTool extends AbstractAgentTool {

    private final OrderToolClient client;

    public GetOrderStatusTool(OrderToolClient client) {
        this.client = client;
    }

    @Override
    public String name() { return "get_order_status"; }

    @Override
    public String descriptionTr() {
        return "Bir siparişin güncel durumunu gösterir. Giriş gerektirir.";
    }

    @Override
    public boolean requiresAuth() { return true; }

    @Override
    public String parametersJsonSchema() {
        return "{\"type\":\"object\",\"properties\":{\"orderId\":{\"type\":\"string\"}},\"required\":[\"orderId\"]}";
    }

    @Override
    protected ToolResult doExecute(ToolContext ctx, JsonNode args) {
        String orderId = args.required("orderId").asText();
        return ToolResult.ok(client.getOrderStatus(ctx.userId(), orderId));
    }
}
