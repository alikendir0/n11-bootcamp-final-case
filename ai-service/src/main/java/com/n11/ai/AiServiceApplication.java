package com.n11.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * ai-service — chat assistant + SSE streaming + agent tool dispatch.
 *
 * @EntityScan("com.n11") so JPA entities from any com.n11 package can be
 * discovered if shared modules ever contribute entities (Plan 05-01 lesson).
 */
@SpringBootApplication(scanBasePackages = "com.n11")
@EntityScan(basePackages = "com.n11")
@EnableDiscoveryClient
public class AiServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiServiceApplication.class, args);
    }
}
