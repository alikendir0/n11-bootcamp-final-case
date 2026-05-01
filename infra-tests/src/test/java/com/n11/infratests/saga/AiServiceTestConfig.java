package com.n11.infratests.saga;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Minimal Spring Boot application for ai-service infra-tests scenarios.
 *
 * Same fix strategy as PaymentServiceTestConfig (Plan 05-04 lesson):
 *  - excludeFilters block all @SpringBootApplication classes (prevents
 *    AiServiceApplication's scanBasePackages="com.n11" from expanding the
 *    component scan to the whole codebase, pulling in identity / inventory /
 *    payment beans that would clash on shared classnames).
 *  - @EntityScan restricted to com.n11.ai (ai_conversations, messages).
 *  - @EnableJpaRepositories restricted to com.n11.ai.
 *  - NO @EnableScheduling (ai-service has no scheduled jobs in v1; the
 *    GuestSessionStore evictor uses a private ScheduledExecutorService not
 *    Spring's @Scheduled).
 */
@SpringBootApplication(scanBasePackages = {
    "com.n11.ai",       // ai-service domain + application + infrastructure + interfaces
    "com.n11.agent",    // agent-toolset (10 tools + ToolRegistry)
    "com.n11.error",    // common-error
    "com.n11.logging"   // common-logging
})
@ComponentScan(
    basePackages = {
        "com.n11.ai",
        "com.n11.agent",
        "com.n11.error",
        "com.n11.logging"
    },
    excludeFilters = {
        @ComponentScan.Filter(
            type = FilterType.ANNOTATION,
            classes = org.springframework.boot.autoconfigure.SpringBootApplication.class
        )
    }
)
@EntityScan(basePackages = {"com.n11.ai"})
@EnableJpaRepositories(basePackages = {"com.n11.ai"})
public class AiServiceTestConfig {
    // no-op: all beans come from component scan
}
