package com.n11.order.clients;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Component
public class ProductClient {
    private final RestClient restClient;
    private final String baseUrl;

    public ProductClient(RestClient.Builder builder,
                         @Value("${app.clients.product.base-url:http://product-service:8082}") String baseUrl) {
        this.restClient = builder.build(); this.baseUrl = baseUrl;
    }

    public ProductSnapshot fetchSnapshot(UUID productId) {
        try {
            ProductSnapshot snap = restClient.get()
                .uri(baseUrl + "/products/{id}", productId)
                .retrieve().body(ProductSnapshot.class);
            if (snap == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ürün bulunamadı: " + productId);
            return snap;
        } catch (HttpClientErrorException.NotFound nf) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ürün bulunamadı: " + productId);
        }
    }
}
