package com.n11.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * n11-clone API Gateway -- Spring Cloud Gateway 2025.0 (Northfields), reactive WebFlux.
 *
 * <p>Phase 1 posture (D-14): {@code permitAll()} security chain, discovery-locator-driven
 * routing, no business services registered yet. Phase 3 flips the chain to JWT validation
 * once identity-service exists.
 *
 * <p>The gateway runtime is REACTIVE (WebFlux). Pitfall #2 is structurally prevented in
 * {@code build.gradle.kts} via {@code configurations.all { exclude(... starter-tomcat); ... }}.
 *
 * <p>The {@code @SpringBootApplication} component-scan is rooted at {@code com.n11.gateway}
 * by default, so the {@code com.n11.logging.CorrelationIdFilter} servlet filter from
 * {@code :common-logging} is NOT picked up here -- which is what we want, because the
 * gateway is reactive. The gateway-side reactive equivalent lives at
 * {@link GatewayCorrelationIdFilter} (a {@code GlobalFilter}, NOT a servlet filter).
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
