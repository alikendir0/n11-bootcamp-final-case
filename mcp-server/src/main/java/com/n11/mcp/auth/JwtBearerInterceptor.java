package com.n11.mcp.auth;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Phase 9 (D-11). Stamps {@code Authorization: Bearer <cached jwt>} on every
 * outbound RestClient call from agent-toolset HTTP clients (CartToolClient,
 * OrderToolClient, ProductToolClient, PaymentToolClient).
 *
 * <p>Registered via {@link com.n11.mcp.config.McpRestClientConfig}'s
 * RestClient.Builder bean — the toolset's {@code @ConditionalOnMissingBean}
 * fallback is skipped because mcp-server provides a Builder bean.
 *
 * <p>This interceptor must NOT be applied to the AgentJwtClient itself
 * (cycle: cache → client → interceptor → cache). AgentJwtClient builds its
 * RestClient from a SEPARATE {@code @LoadBalanced} Builder that does not
 * carry this interceptor (see {@link com.n11.mcp.config.McpRestClientConfig}).
 */
@Component
public class JwtBearerInterceptor implements ClientHttpRequestInterceptor {

    private final AgentJwtCache cache;

    public JwtBearerInterceptor(AgentJwtCache cache) {
        this.cache = cache;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        request.getHeaders().setBearerAuth(cache.bearerToken());
        return execution.execute(request, body);
    }
}
