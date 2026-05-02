package com.n11.agent.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class IdentityToolClient {

    private final RestClient client;
    private final String baseUrl;
    private final ObjectMapper mapper = new ObjectMapper();

    public IdentityToolClient(RestClient.Builder builder,
                              @Value("${app.clients.identity.base-url:http://identity-service:8081}") String baseUrl) {
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.client = builder.build();
    }

    public JsonNode listAddresses(String userId) {
        return execute(() -> client.get()
            .uri(baseUrl + "/addresses")
            .header("X-User-Id", userId)
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .body(String.class));
    }

    private JsonNode execute(java.util.function.Supplier<String> call) {
        try {
            String body = call.get();
            return body == null ? mapper.nullNode() : mapper.readTree(body);
        } catch (RestClientException | java.io.IOException e) {
            throw new ToolHttpException("IDENTITY_UPSTREAM", e.getMessage(), e);
        }
    }

    private static String stripTrailingSlash(String v) {
        if (v == null || v.isBlank()) return "http://identity-service:8081";
        return v.endsWith("/") ? v.substring(0, v.length() - 1) : v;
    }
}