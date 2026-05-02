package com.n11.mcp.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Phase 9 (D-07 / D-11). Exchanges the configured MCP_API_KEY for an internal JWT
 * against identity-service. Calls direct via Eureka (lb://identity-service) NOT
 * through api-gateway — the gateway is the trust edge for human-bearer traffic;
 * internal mesh hops use Eureka directly (Phase 6 + 8 internal-REST pattern).
 *
 * <p>Note on RestClient injection: This client deliberately uses the un-intercepted
 * load-balanced builder (NOT the shared tool Builder) so the
 * {@link JwtBearerInterceptor} — which depends on {@link AgentJwtCache} which
 * depends on this client — doesn't form a cycle. Common-logging's correlation-ID
 * interceptor is dropped here; this is a one-shot bootstrap call (~once per 24h
     * after JWT cache). The qualified builder bean is annotated {@code @LoadBalanced}
     * in {@link com.n11.mcp.config.McpRestClientConfig} so lb://identity-service
     * resolves through Spring Cloud LoadBalancer.
 */
@Component
public class AgentJwtClient {

    private static final Logger log = LoggerFactory.getLogger(AgentJwtClient.class);

    private final RestClient restClient;
    private final String apiKey;

    public AgentJwtClient(@Value("${mcp.api-key:}") String apiKey,
                          @Value("${mcp.identity-service.uri:lb://identity-service}") String identityUri,
                          @Qualifier("loadBalancedRestClientBuilder") RestClient.Builder loadBalancedBuilder) {
        this.restClient = loadBalancedBuilder
                .baseUrl(identityUri)
                .build();
        this.apiKey = apiKey;
    }

    /**
     * Performs the API-key → JWT exchange.
     *
     * @return parsed response body
     * @throws IllegalStateException if MCP_API_KEY is empty
     * @throws SecurityException if identity-service returns 401 (invalid/revoked)
     * @throws RuntimeException on transport / 5xx errors
     */
    public AgentTokenResponseDto exchange() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "MCP_API_KEY is empty — cannot perform /agents/exchange. " +
                    "Set the MCP_API_KEY environment variable (see .env.example).");
        }

        log.info("Performing /agents/exchange against identity-service (Eureka direct)");

        try {
            return restClient.post()
                    .uri("/agents/exchange")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("apiKey", apiKey))
                    .retrieve()
                    .body(AgentTokenResponseDto.class);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw new SecurityException(
                        "identity-service rejected MCP_API_KEY (401). The key may be invalid or revoked.", e);
            }
            throw e;
        }
    }
}
