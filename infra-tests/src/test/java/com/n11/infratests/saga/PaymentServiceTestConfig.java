package com.n11.infratests.saga;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Minimal Spring Boot application for the SagaHappyPathE2ETest.
 *
 * <p>Uses {@code @SpringBootApplication} (which is {@code @SpringBootConfiguration}) so that
 * {@code SpringBootContextLoader} recognises this class as the primary source and does NOT fall
 * back to scanning the classpath for other {@code @SpringBootApplication} classes.
 *
 * <p>Root cause of classpath conflicts: the infra-tests module includes identity-service,
 * inventory-service, and payment-service on its classpath for ArchUnit. Each of those has a
 * {@code @SpringBootApplication(scanBasePackages="com.n11")} class.
 * {@code PaymentServiceTestConfig} component-scans {@code com.n11.payment} — which is also
 * where {@code PaymentServiceApplication} lives. When Spring processes
 * {@code PaymentServiceApplication} as a found {@code @Configuration}, its own
 * {@code @EntityScan("com.n11")} and {@code scanBasePackages="com.n11"} expand the scan
 * to the entire codebase, pulling in identity/inventory entities and beans.
 *
 * <p>Fix strategy:
 * <ol>
 *   <li>{@link ComponentScan} with explicit {@code excludeFilters} that blocks all
 *       {@code @SpringBootApplication}-annotated classes from being processed as secondary
 *       configuration sources (PaymentServiceApplication, IdentityServiceApplication,
 *       InventoryServiceApplication).</li>
 *   <li>{@link EntityScan} restricted to payment + common-outbox packages only.</li>
 *   <li>{@link EnableJpaRepositories} restricted to payment package — overrides the auto-
 *       configured {@code @EnableJpaRepositories} that would otherwise follow the entity-scan
 *       packages and register identity/inventory repository beans.</li>
 * </ol>
 */
@SpringBootApplication(scanBasePackages = {
    "com.n11.payment",   // payment-service application + messaging + outbox + health
    "com.n11.outbox",    // common-outbox AbstractOutboxPoller, OutboxMessagePostProcessor
    "com.n11.events",    // common-events RabbitRetryConfig
    "com.n11.logging"    // common-logging RabbitTemplateConfig, CorrelationIdFilter
})
@ComponentScan(
    basePackages = {
        "com.n11.payment",
        "com.n11.outbox",
        "com.n11.events",
        "com.n11.logging"
    },
    excludeFilters = {
        // Exclude ALL @SpringBootApplication classes found in the scan path.
        // Without this, PaymentServiceApplication (in com.n11.payment) is picked up as a
        // @Configuration class and its @EntityScan("com.n11") / scanBasePackages="com.n11"
        // expand entity+component scanning to the entire codebase, pulling in
        // IdentityServiceApplication, InventoryServiceApplication, and all their beans.
        @ComponentScan.Filter(
            type = FilterType.ANNOTATION,
            classes = org.springframework.boot.autoconfigure.SpringBootApplication.class
        )
    }
)
@EntityScan(basePackages = {"com.n11.payment", "com.n11.outbox"})
@EnableJpaRepositories(basePackages = {"com.n11.payment"})
@EnableScheduling
class PaymentServiceTestConfig {
    // no-op: all beans come from component scan
}
