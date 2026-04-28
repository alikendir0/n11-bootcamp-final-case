package com.n11.template;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Service-template archetype main class. Cloned by every business service in Phases 3+.
 *
 * <p>scanBasePackages = "com.n11" extends component scan beyond com.n11.template so the
 * sibling :common-error ProblemDetailControllerAdvice (com.n11.error.*) and
 * :common-logging beans (com.n11.logging.*) are picked up. Without this, Boot's default
 * scan would only cover com.n11.template.* and the cross-cutting wires would not register.
 */
@SpringBootApplication(scanBasePackages = "com.n11")
@EnableDiscoveryClient
public class ServiceTemplateApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServiceTemplateApplication.class, args);
    }
}
