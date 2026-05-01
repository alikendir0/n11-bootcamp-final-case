package com.n11.agent.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;
import java.util.UUID;

@Component
public class OrderToolClient {

    private final RestClient client;
    private final String baseUrl;
    private final ObjectMapper mapper = new ObjectMapper();

    public OrderToolClient(RestClient.Builder builder,
                           @Value("${app.clients.order.base-url:http://order-service:8085}") String baseUrl) {
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.client = builder.build();
    }

    public JsonNode createOrder(String userId, String addressId, String paymentMethod) {
        UUID idempotencyKey = UUID.randomUUID();      // Plan 05-03: per-call dedup
        return execute(() -> client.post()
            .uri(baseUrl + "/orders")
            .header("X-User-Id", userId)
            .header("Idempotency-Key", idempotencyKey.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("addressId", addressId, "paymentMethod", paymentMethod))
            .retrieve()
            .body(String.class));
    }

    public JsonNode getOrderStatus(String userId, String orderId) {
        return execute(() -> client.get()
            .uri(baseUrl + "/orders/" + orderId)
            .header("X-User-Id", userId)
            .retrieve()
            .body(String.class));
    }

    private JsonNode execute(java.util.function.Supplier<String> call) {
        try {
            String body = call.get();
            return body == null ? mapper.nullNode() : mapper.readTree(body);
        } catch (RestClientException | java.io.IOException e) {
            throw new ToolHttpException("ORDER_UPSTREAM", e.getMessage(), e);
        }
    }

    private static String stripTrailingSlash(String v) {
        if (v == null || v.isBlank()) return "http://order-service:8085";
        return v.endsWith("/") ? v.substring(0, v.length() - 1) : v;
    }
}
