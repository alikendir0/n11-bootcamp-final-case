package com.n11.agent.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/**
 * cart-service client. Every method REQUIRES a non-null userId (cart endpoints
 * require X-User-Id; gateway-injected mesh per CONTEXT D-05). Mutating tools
 * gate on requiresAuth() before calling these methods.
 */
@Component
public class CartToolClient {

    private final RestClient client;
    private final String baseUrl;
    private final ObjectMapper mapper = new ObjectMapper();

    public CartToolClient(RestClient.Builder builder,
                          @Value("${app.clients.cart.base-url:http://cart-service:8084}") String baseUrl) {
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.client = builder.build();
    }

    public JsonNode getCart(String userId) {
        return execute(() -> client.get()
            .uri(baseUrl + "/cart")
            .header("X-User-Id", userId)
            .retrieve()
            .body(String.class));
    }

    public JsonNode addItem(String userId, String productId, int qty) {
        return execute(() -> client.post()
            .uri(baseUrl + "/cart/items")
            .header("X-User-Id", userId)
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("productId", productId, "qty", qty))
            .retrieve()
            .body(String.class));
    }

    public JsonNode updateItem(String userId, String productId, int qty) {
        return execute(() -> client.patch()
            .uri(baseUrl + "/cart/items/" + productId)
            .header("X-User-Id", userId)
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("qty", qty))
            .retrieve()
            .body(String.class));
    }

    public JsonNode removeItem(String userId, String productId) {
        try {
            client.delete()
                .uri(baseUrl + "/cart/items/" + productId)
                .header("X-User-Id", userId)
                .retrieve()
                .toBodilessEntity();
            return mapper.createObjectNode().put("removed", true).put("productId", productId);
        } catch (RestClientException e) {
            throw new ToolHttpException("CART_UPSTREAM", e.getMessage(), e);
        }
    }

    private JsonNode execute(java.util.function.Supplier<String> call) {
        try {
            String body = call.get();
            return body == null ? mapper.nullNode() : mapper.readTree(body);
        } catch (RestClientException | java.io.IOException e) {
            throw new ToolHttpException("CART_UPSTREAM", e.getMessage(), e);
        }
    }

    private static String stripTrailingSlash(String v) {
        if (v == null || v.isBlank()) return "http://cart-service:8084";
        return v.endsWith("/") ? v.substring(0, v.length() - 1) : v;
    }
}
