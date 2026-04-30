package com.n11.infratests.saga;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Minimal Spring Boot application for the order side of
 * {@link OrderPaymentFailureCompensationE2ETest}.
 *
 * <p>Mirrors {@link InventoryCompensationTestConfig} and {@link PaymentServiceTestConfig}
 * isolation: explicit {@code scanBasePackages} plus {@code excludeFilters} blocking other
 * services' {@code @SpringBootApplication}-annotated classes from being processed as
 * secondary configuration sources. Without this, {@code OrderServiceApplication}'s
 * {@code @EntityScan("com.n11")} would expand entity scope to identity / inventory /
 * payment entities and break schema isolation.
 *
 * <p>Component scope: order-service messaging + order + outbox + clients + idempotency
 * plus the shared {@code com.n11.outbox}, {@code com.n11.events}, and {@code com.n11.logging}
 * modules. Saga consumers (PaymentFailedConsumer, PaymentCompletedConsumer,
 * StockReservedConsumer, StockReserveFailedConsumer) all live under
 * {@code com.n11.order.messaging} so they boot here automatically.
 */
@SpringBootApplication(scanBasePackages = {
    "com.n11.order",
    "com.n11.outbox",
    "com.n11.events",
    "com.n11.logging"
})
@ComponentScan(
    basePackages = {
        "com.n11.order",
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
@EntityScan(basePackages = {"com.n11.order", "com.n11.outbox"})
@EnableJpaRepositories(basePackages = {"com.n11.order"})
class OrderPaymentFailureTestConfig {
    // no-op: all beans come from component scan
}
