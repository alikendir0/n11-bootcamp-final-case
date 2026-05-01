package com.n11.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * MCP server entry point (Phase 9).
 *
 * <p>Re-exposes Phase 8's agent-toolset (10 AgentTool beans in com.n11.agent.tools)
 * over the MCP wire protocol via spring-ai-starter-mcp-server-webmvc 1.1.5.
 * Streamable HTTP (D-02) is the primary transport; stdio is also active per D-01
 * for Claude Desktop demo.
 *
 * <p>scanBasePackages = "com.n11" picks up:
 *   - com.n11.mcp.* (this module — config + auth)
 *   - com.n11.agent.* (agent-toolset — 10 AgentTool beans + ToolRegistry)
 *   - com.n11.error.* (common-error — RFC-7807 advice)
 *   - com.n11.logging.* (common-logging — MDC correlation-ID)
 *
 * <p>NO @EntityScan: mcp-server has no JPA entities.
 *
 * <p>@EnableScheduling: Plan 09-04's AgentJwtCache uses @Scheduled to refresh the
 * exchanged JWT every minute. Required even though Wave 1 itself has no scheduler.
 */
@SpringBootApplication(scanBasePackages = "com.n11")
@EnableDiscoveryClient
@EnableScheduling
public class McpServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(McpServerApplication.class, args);
    }
}
