package com.n11.agent.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Read-only product-service client used by search_products / get_product /
 * list_categories tools. No X-User-Id required (PROD-* endpoints are public).
 */
@Component
public class ProductToolClient {

    private final RestClient client;
    private final String baseUrl;
    private final ObjectMapper mapper = new ObjectMapper();

    public ProductToolClient(RestClient.Builder builder,
                             @Value("${app.clients.product.base-url:http://product-service:8082}") String baseUrl) {
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.client = builder.build();
    }

    public JsonNode searchProducts(String q, String categoryId, int page, int size) {
        StringBuilder uri = new StringBuilder(baseUrl).append("/products?page=").append(page).append("&size=").append(size);
        if (q != null && !q.isBlank()) uri.append("&q=").append(java.net.URLEncoder.encode(q, java.nio.charset.StandardCharsets.UTF_8));
        if (categoryId != null && !categoryId.isBlank()) uri.append("&categoryId=").append(categoryId);
        return getAsJson(uri.toString());
    }

    public JsonNode getProduct(String productId) {
        return getAsJson(baseUrl + "/products/" + productId);
    }

    public JsonNode listCategories() {
        return getAsJson(baseUrl + "/categories");
    }

    private JsonNode getAsJson(String url) {
        try {
            String body = client.get().uri(url).retrieve().body(String.class);
            return body == null ? mapper.nullNode() : mapper.readTree(body);
        } catch (RestClientException | java.io.IOException e) {
            throw new ToolHttpException("PRODUCT_UPSTREAM", e.getMessage(), e);
        }
    }

    private static String stripTrailingSlash(String v) {
        if (v == null || v.isBlank()) return "http://product-service:8082";
        return v.endsWith("/") ? v.substring(0, v.length() - 1) : v;
    }
}
