package com.n11.infratests.mcp;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * Phase 9 (Plan 09-06). Multi-service classpath disambiguation for mcp-server
 * + agent-toolset + common-* loaded inside the infra-tests umbrella.
 *
 * <p>Plan 05-04 lesson applied:
 * <ul>
 *   <li>{@code excludeFilters} blocks all foreign {@code @SpringBootApplication}
 *       classes (e.g. AiServiceApplication, OrderServiceApplication, etc.) so
 *       their {@code scanBasePackages = "com.n11"} does not expand to the entire
 *       infra-tests classpath and pull in JPA / AMQP autoconfig that mcp-server
 *       doesn't need.</li>
 *   <li>NO {@code @EntityScan} / {@code @EnableJpaRepositories} — mcp-server has
 *       zero JPA entities.</li>
 *   <li>NO {@code @EnableScheduling} on this test config — AgentJwtCache's
 *       minute-tick scheduler from Plan 09-04 would attempt /agents/exchange
 *       at test boot. The {@code spring.task.scheduling.enabled=false} property
 *       in McpServerToolsListEqualityTest disables the scheduler.</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = {
    "com.n11.mcp",       // mcp-server config + auth packages
    "com.n11.agent",     // agent-toolset (10 tools + ToolRegistry + ToolHttpClients)
    "com.n11.error",     // common-error
    "com.n11.logging"    // common-logging
})
@ComponentScan(
    basePackages = {
        "com.n11.mcp",
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
public class McpServerTestConfig {
    // no-op: all beans come from component scan
}
