package com.n11.eureka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Spring Cloud Netflix Eureka Server — the discovery root for the n11 microservice mesh
 * (ARCH-02). Every business service registers here via the Eureka client; the api-gateway
 * (Plan 01-06) uses the discovery-locator to auto-route to registered services.
 *
 * <p>This server does NOT register against itself and does NOT fetch a registry — see
 * {@code application.yml} for the `register-with-eureka: false` / `fetch-registry: false`
 * lockdown. There is no peer Eureka in Phase 1; multi-instance peer-replication is a
 * Phase 11 hardening item.</p>
 */
@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}
