package com.n11.cart.product;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Sync REST client for product-service. Used by CartService for D-01 snapshot fetch on add/update.
 *
 * <p>URL is product-service direct (NOT via gateway): cart-service is INSIDE the Docker network
 * and product-service is Eureka-registered. Direct calls skip gateway hops, reduce latency, and
 * the correlation-ID propagation is handled by common-logging.RestClientConfig's interceptor
 * (wires the X-Correlation-Id header from MDC automatically).
 */
@Component
public class ProductClient {

    private final RestClient restClient;
    private final String productBaseUrl;

    public ProductClient(RestClient.Builder builder,
                         @Value("${app.clients.product.base-url:http://product-service:8082}") String productBaseUrl) {
        this.restClient = builder.build();
        this.productBaseUrl = productBaseUrl;
    }

    /**
     * Fetch product snapshot for cart line (D-01).
     *
     * @throws ResponseStatusException 404 if product not found (caller maps to 404 to client),
     *                                  5xx-passthrough on transient failures.
     */
    public ProductSnapshot fetchSnapshot(UUID productId) {
        try {
            ProductSnapshot snapshot = restClient.get()
                .uri(productBaseUrl + "/products/{id}", productId)
                .retrieve()
                .body(ProductSnapshot.class);
            if (snapshot == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ürün bulunamadı: " + productId);
            }
            return snapshot;
        } catch (HttpClientErrorException.NotFound nf) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ürün bulunamadı: " + productId);
        }
    }
}
