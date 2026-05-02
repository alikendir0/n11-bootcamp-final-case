package com.n11.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.n11.agent.ToolContext;
import com.n11.agent.ToolResult;
import com.n11.agent.http.OrderToolClient;
import org.springframework.stereotype.Component;

/**
 * Creates an order from current cart contents.
 * OrderToolClient sends a fresh UUID Idempotency-Key per call (Plan 05-03 dedup contract)
 * ensuring safe retry if Gemini re-invokes the tool after a transient failure.
 */
@Component
public class CreateOrderTool extends AbstractAgentTool {

    private final OrderToolClient client;

    public CreateOrderTool(OrderToolClient client) {
        this.client = client;
    }

    @Override
    public String name() { return "create_order"; }

    @Override
    public String descriptionTr() {
        return "Sepetteki ürünlerden sipariş oluşturur ve ödeme bağlantısı döner. Giriş gerektirir.";
    }

    @Override
    public boolean requiresAuth() { return true; }

    @Override
    public String parametersJsonSchema() {
        return "{\"type\":\"object\",\"properties\":{\"addressId\":{\"type\":\"string\"},\"paymentMethod\":{\"type\":\"string\",\"enum\":[\"CREDIT_CARD\",\"CASH_ON_DELIVERY\"]}},\"required\":[\"addressId\",\"paymentMethod\"]}";
    }

    @Override
    protected ToolResult doExecute(ToolContext ctx, JsonNode args) {
        String addressId = args.required("addressId").asText();
        String paymentMethod = args.required("paymentMethod").asText();
        String apiPaymentMethod = "CREDIT_CARD".equals(paymentMethod) ? "CARD" : paymentMethod;
        return ToolResult.ok(client.createOrder(ctx.userId(), addressId, apiPaymentMethod));
    }
}
