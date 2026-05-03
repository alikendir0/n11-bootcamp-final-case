package com.n11.error.springdoc;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.List;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "org.springdoc.core.customizers.OpenApiCustomizer")
public class GatewayServerUrlCustomizer {

    @Bean
    public OpenApiCustomizer gatewayOpenApiCustomizer(Environment env) {
        String host = env.getProperty("GATEWAY_EXTERNAL_HOST", "");
        String scheme = env.getProperty("GATEWAY_EXTERNAL_SCHEME", "http");
        String port = env.getProperty("GATEWAY_EXTERNAL_PORT", "");
        String pathPrefix = env.getProperty("GATEWAY_PATH_PREFIX", "");
        
        final String serverUrl;
        if (!host.isBlank()) {
            serverUrl = scheme + "://" + host
                    + (port.isBlank() ? "" : ":" + port)
                    + (pathPrefix.isBlank() ? "" : pathPrefix);
        } else {
            serverUrl = null;
        }

        return openApi -> {
            if (serverUrl != null) {
                openApi.setServers(List.of(new Server().url(serverUrl).description("API Gateway")));
            }
        };
    }
}