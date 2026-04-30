package com.n11.payment.order;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

@Component
public class OrderPaymentContextClient {

    private final RestClient restClient;

    public OrderPaymentContextClient(RestClient.Builder builder,
                                     @Value("${app.clients.order.base-url:http://order-service:8085}") String baseUrl) {
        this.restClient = builder.baseUrl(stripTrailingSlash(baseUrl)).build();
    }

    public OrderPaymentContext getPaymentContext(UUID orderId) {
        try {
            return restClient.get()
                .uri("/internal/orders/{orderId}/payment-context", orderId)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                    throw new PaymentInitializationException("payment context unavailable for order " + orderId);
                })
                .body(OrderPaymentContext.class);
        } catch (PaymentInitializationException e) {
            throw e;
        } catch (RestClientException e) {
            throw new PaymentInitializationException("payment context unavailable for order " + orderId, e);
        }
    }

    private static String stripTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://order-service:8085";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
