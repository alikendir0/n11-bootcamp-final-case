package com.n11.infratests.saga;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Minimal Spring Boot application for the inventory side of
 * {@link PaymentFailureCompensationE2ETest}.
 *
 * <p>Mirrors {@link PaymentServiceTestConfig}'s isolation strategy: explicit scan
 * scope plus {@code excludeFilters} blocking other services'
 * {@code @SpringBootApplication} classes. Without this, {@code com.n11.*} component
 * scans expand transitively and pull identity / order / payment beans into the
 * inventory context, breaking schema and bean-name uniqueness.
 *
 * <p>Component scope is intentionally narrow: inventory-service messaging +
 * reservation + stock + outbox plus the shared {@code com.n11.outbox},
 * {@code com.n11.events}, and {@code com.n11.logging} modules. Saga consumers
 * (PaymentFailedConsumer, OrderCancelledConsumer, OrderCreatedConsumer) all live
 * under {@code com.n11.inventory.messaging} so they boot here automatically.
 */
@SpringBootApplication(scanBasePackages = {
    "com.n11.inventory",
    "com.n11.outbox",
    "com.n11.events",
    "com.n11.logging"
})
@ComponentScan(
    basePackages = {
        "com.n11.inventory",
        "com.n11.outbox",
        "com.n11.events",
        "com.n11.logging"
    },
    excludeFilters = {
        @ComponentScan.Filter(
            type = FilterType.ANNOTATION,
            classes = org.springframework.boot.autoconfigure.SpringBootApplication.class
        )
    }
)
@EntityScan(basePackages = {"com.n11.inventory", "com.n11.outbox"})
@EnableJpaRepositories(basePackages = {"com.n11.inventory"})
class InventoryCompensationTestConfig {
    // no-op: all beans come from component scan
}
