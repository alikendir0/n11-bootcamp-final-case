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
 * <p>The gateway intentionally does NOT depend on {@code :common-logging}: its
 * {@code AutoConfiguration.imports} pulls in a servlet filter that fails to
 * load on the reactive WebFlux runtime (jakarta.servlet API correctly absent
 * thanks to Pitfall #2 lockdown in {@code build.gradle.kts}). The gateway-side
 * reactive equivalent lives at {@link GatewayCorrelationIdFilter} (a
 * {@code GlobalFilter}, NOT a servlet filter); the two systems stay in sync via
 * the shared {@code X-Correlation-Id} wire-format header name.
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
