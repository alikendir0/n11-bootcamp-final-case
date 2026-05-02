package com.n11.error.springdoc;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GatewayServerUrlCustomizer implements OpenApiCustomizer {

    private final String serverUrl;

    public GatewayServerUrlCustomizer(Environment env) {
        String host = env.getProperty("GATEWAY_EXTERNAL_HOST", "");
        String scheme = env.getProperty("GATEWAY_EXTERNAL_SCHEME", "http");
        String port = env.getProperty("GATEWAY_EXTERNAL_PORT", "");
        String pathPrefix = env.getProperty("GATEWAY_PATH_PREFIX", "");
        if (!host.isBlank()) {
            this.serverUrl = scheme + "://" + host
                    + (port.isBlank() ? "" : ":" + port)
                    + (pathPrefix.isBlank() ? "" : pathPrefix);
        } else {
            this.serverUrl = null;
        }
    }

    @Override
    public void customise(OpenAPI openApi) {
        if (serverUrl != null) {
            openApi.setServers(List.of(new Server().url(serverUrl).description("API Gateway")));
        }
    }
}