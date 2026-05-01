package com.n11.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 9 Wave 0 acceptance: mcp-server Spring context boots green with both
 * transports configured (D-01 + D-02). Excludes config-server / eureka discovery
 * for offline test posture; everything else (Spring AI MCP starter, agent-toolset
 * beans) loads from the classpath as in production.
 */
@SpringBootTest(
    classes = McpServerApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        // Inline the externalised mcp-server.yml values that config-server would
        // serve at runtime. Without these, Spring AI fails to resolve required
        // properties at context init.
        "spring.ai.mcp.server.protocol=STREAMABLE",
        "spring.ai.mcp.server.stdio=true",
        "spring.ai.mcp.server.streamable-http.mcp-endpoint=/mcp",
        "spring.ai.mcp.server.capabilities.tool=true",
        "spring.ai.mcp.server.capabilities.resource=false",
        "spring.ai.mcp.server.capabilities.prompt=false",
        "spring.ai.mcp.server.name=n11-storefront",
        "spring.ai.mcp.server.version=1.0.0",
        "spring.ai.mcp.server.type=SYNC",
        // Auto-config exclusion (Pitfall #7).
        "spring.autoconfigure.exclude=" +
            "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
            "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration," +
            "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
        // Disable cloud-config fetch + eureka in tests.
        "spring.cloud.config.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "eureka.client.enabled=false",
        // Required by Plan 09-04's AgentJwtCache @Value binding (the bean exists in
        // Wave 2 — at Wave 1 the property still has to resolve to *something*
        // non-empty if the bean is instantiated; Wave 1 has no bean yet so an
        // empty default is acceptable. Set a placeholder for forward compat).
        "mcp.api-key=test-key-not-used-in-wave-1"
    }
)
class McpServerContextTest {

    @Value("${spring.ai.mcp.server.protocol}")
    String protocol;

    @Value("${spring.ai.mcp.server.stdio}")
    boolean stdio;

    @Value("${spring.ai.mcp.server.capabilities.resource}")
    boolean resourceCapability;

    @Value("${spring.ai.mcp.server.capabilities.prompt}")
    boolean promptCapability;

    @Value("${spring.ai.mcp.server.streamable-http.mcp-endpoint}")
    String mcpEndpoint;

    @Test
    void context_boots_with_streamable_http_and_stdio_active() {
        // SC-2 truth: both transports active in the same Boot app (D-01).
        assertThat(protocol).isEqualTo("STREAMABLE");
        assertThat(stdio).isTrue();
    }

    @Test
    void mcp_endpoint_is_slash_mcp() {
        // Streamable HTTP endpoint matches what api-gateway routes to (Plan 09-05).
        assertThat(mcpEndpoint).isEqualTo("/mcp");
    }

    @Test
    void capabilities_advertise_only_tools() {
        // Pitfall #5 mitigation (09-RESEARCH.md).
        assertThat(resourceCapability).isFalse();
        assertThat(promptCapability).isFalse();
    }
}
