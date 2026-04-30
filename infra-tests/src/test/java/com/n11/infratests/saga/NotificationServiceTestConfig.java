package com.n11.infratests.saga;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Minimal Spring Boot application for QualFourSagaNotificationTest.
 *
 * <p>Modeled on {@link PaymentServiceTestConfig} (Plan 05-04). Same defensive isolation:
 * explicit scan scope blocking other services' application boot classes
 * from being processed as secondary configuration sources.
 *
 * <p>Differences from PaymentServiceTestConfig:
 * <ul>
 *   <li>Drops the outbox package scan — notification-service is a leaf consumer with
 *       no outbox dependency (Plan 07-01 build.gradle.kts excludes :common-outbox)</li>
 *   <li>Drops scheduled poller support — no @Scheduled methods in notification-service</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = {
    "com.n11.notification",
    "com.n11.events",
    "com.n11.logging"
})
@ComponentScan(
    basePackages = {
        "com.n11.notification",
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
@EntityScan(basePackages = {"com.n11.notification"})
@EnableJpaRepositories(basePackages = {"com.n11.notification"})
class NotificationServiceTestConfig {
    // no-op: all beans come from component scan
}
