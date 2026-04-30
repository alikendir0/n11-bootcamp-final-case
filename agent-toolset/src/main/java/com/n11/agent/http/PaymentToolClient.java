package com.n11.agent.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class PaymentToolClient {

    private final RestClient client;
    private final String baseUrl;
    private final ObjectMapper mapper = new ObjectMapper();

    public PaymentToolClient(RestClient.Builder builder,
                             @Value("${app.clients.payment.base-url:http://payment-service:8086}") String baseUrl) {
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.client = builder.build();
    }

    public JsonNode getPaymentForOrder(String userId, String orderId) {
        try {
            String body = client.get()
                .uri(baseUrl + "/payments/" + orderId)
                .header("X-User-Id", userId)
                .retrieve()
                .body(String.class);
            return body == null ? mapper.nullNode() : mapper.readTree(body);
        } catch (RestClientException | java.io.IOException e) {
            throw new ToolHttpException("PAYMENT_UPSTREAM", e.getMessage(), e);
        }
    }

    private static String stripTrailingSlash(String v) {
        if (v == null || v.isBlank()) return "http://payment-service:8086";
        return v.endsWith("/") ? v.substring(0, v.length() - 1) : v;
    }
}
