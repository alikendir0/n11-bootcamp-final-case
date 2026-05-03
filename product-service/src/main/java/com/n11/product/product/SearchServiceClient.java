package com.n11.product.product;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.UUID;

@Component
public class SearchServiceClient {
    private static final Logger log = LoggerFactory.getLogger(SearchServiceClient.class);
    
    private final RestClient client;
    private final String searchServiceUrl;

    public SearchServiceClient(RestClient.Builder builder,
                               @Value("${app.clients.search.base-url:http://search-service:8089}") String searchServiceUrl) {
        this.searchServiceUrl = searchServiceUrl.endsWith("/") ? searchServiceUrl.substring(0, searchServiceUrl.length() - 1) : searchServiceUrl;
        this.client = builder.build();
    }

    public List<UUID> search(String q, int limit) {
        try {
            String url = searchServiceUrl + "/search?q=" + java.net.URLEncoder.encode(q, java.nio.charset.StandardCharsets.UTF_8) + "&limit=" + limit;
            return client.get()
                    .uri(url)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<UUID>>() {});
        } catch (RestClientException e) {
            log.error("Failed to query search-service for '{}': {}", q, e.getMessage());
            return null; // Signals fallback
        }
    }
}
