package com.n11.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.n11.agent.ToolContext;
import com.n11.agent.ToolResult;
import com.n11.agent.http.PaymentToolClient;
import org.springframework.stereotype.Component;

/**
 * Retrieves the Iyzico payment page URL for an existing order.
 * The paymentPageUrl in the response is the link the chat surfaces to the user.
 */
@Component
public class GetPaymentLinkTool extends AbstractAgentTool {

    private final PaymentToolClient client;

    public GetPaymentLinkTool(PaymentToolClient client) {
        this.client = client;
    }

    @Override
    public String name() { return "get_payment_link"; }

    @Override
    public String descriptionTr() {
        return "Mevcut siparişin ödeme bağlantısını getirir. Giriş gerektirir.";
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
        return ToolResult.ok(client.getPaymentForOrder(ctx.userId(), orderId));
    }
}
