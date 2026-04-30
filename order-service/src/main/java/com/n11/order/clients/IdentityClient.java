package com.n11.order.clients;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Component
public class IdentityClient {
    private final RestClient restClient;
    private final String baseUrl;

    public IdentityClient(RestClient.Builder builder,
                          @Value("${app.clients.identity.base-url:http://identity-service:8081}") String baseUrl) {
        this.restClient = builder.build(); this.baseUrl = baseUrl;
    }

    /** GET /addresses/{id} with X-User-Id; identity-service enforces ownership and returns 404 if not owned by caller. */
    public AddressSnapshot getAddress(UUID addressId, UUID userId) {
        try {
            AddressSnapshot a = restClient.get()
                .uri(baseUrl + "/addresses/{id}", addressId)
                .header("X-User-Id", userId.toString())
                .retrieve().body(AddressSnapshot.class);
            if (a == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Adres bulunamadı: " + addressId);
            return a;
        } catch (HttpClientErrorException.NotFound nf) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Adres bulunamadı: " + addressId);
        }
    }
}
