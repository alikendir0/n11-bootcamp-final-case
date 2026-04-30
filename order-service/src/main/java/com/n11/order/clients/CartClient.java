package com.n11.order.clients;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Component
public class CartClient {
    private final RestClient restClient;
    private final String baseUrl;

    public CartClient(RestClient.Builder builder,
                      @Value("${app.clients.cart.base-url:http://cart-service:8084}") String baseUrl) {
        this.restClient = builder.build();
        this.baseUrl = baseUrl;
    }

    /** GET /cart with X-User-Id propagated; returns 4xx-mapped exceptions. */
    public CartView getCart(UUID userId) {
        CartView body = restClient.get()
            .uri(baseUrl + "/cart")
            .header("X-User-Id", userId.toString())
            .retrieve()
            .body(CartView.class);
        if (body == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sepet bulunamadı");
        return body;
    }
}
