package com.n11.mcp.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.n11.agent.AgentTool;
import com.n11.agent.ToolRegistry;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 9 SC-1 / SC-4 grading proof (unit-level). The integration-level proof
 * lives in infra-tests (Plan 09-06) — this test runs faster (no Postgres) and
 * gates every commit in mcp-server.
 *
 * <p>Boots the full mcp-server context with stub {@code mcp.api-key} so
 * AgentJwtCache (whether stub from this plan or real from Plan 09-04) can
 * resolve its @Value binding. The test does NOT call any tool — it only
 * inspects the registered ToolCallback metadata.
 */
@SpringBootTest(
    classes = com.n11.mcp.McpServerApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "spring.ai.mcp.server.protocol=STREAMABLE",
        "spring.ai.mcp.server.stdio=false",
        "spring.ai.mcp.server.streamable-http.mcp-endpoint=/mcp",
        "spring.ai.mcp.server.capabilities.tool=true",
        "spring.ai.mcp.server.capabilities.resource=false",
        "spring.ai.mcp.server.capabilities.prompt=false",
        "spring.ai.mcp.server.name=n11-storefront",
        "spring.ai.mcp.server.version=1.0.0",
        "spring.ai.mcp.server.type=SYNC",
        "spring.autoconfigure.exclude=" +
            "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
            "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration," +
            "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
        "spring.cloud.config.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "eureka.client.enabled=false",
        "mcp.api-key=test-key-not-actually-exchanged"
    }
)
class AgentToolMcpRegistrationTest {

    @Autowired ToolCallbackProvider toolCallbackProvider;
    @Autowired ToolRegistry toolRegistry;

    private static final ObjectMapper M = new ObjectMapper();

    @Test
    void exposes_exactly_11_callbacks() {
        ToolCallback[] callbacks = toolCallbackProvider.getToolCallbacks();
        assertThat(callbacks).hasSize(11);
        assertThat(toolRegistry.all()).hasSize(11);
    }

    @Test
    void callback_names_match_tool_registry_names_exactly() {
        Set<String> callbackNames = Arrays.stream(toolCallbackProvider.getToolCallbacks())
                .map(c -> c.getToolDefinition().name())
                .collect(Collectors.toSet());
        Set<String> registryNames = toolRegistry.all().stream()
                .map(AgentTool::name)
                .collect(Collectors.toSet());
        assertThat(callbackNames).isEqualTo(registryNames);
    }

    @Test
    void descriptions_match_descriptionTr() {
        Map<String, AgentTool> byName = toolRegistry.all().stream()
                .collect(Collectors.toMap(AgentTool::name, t -> t));

        for (ToolCallback cb : toolCallbackProvider.getToolCallbacks()) {
            String name = cb.getToolDefinition().name();
            AgentTool source = byName.get(name);
            assertThat(source).as("source tool for %s", name).isNotNull();
            assertThat(cb.getToolDefinition().description())
                    .as("description for %s", name)
                    .isEqualTo(source.descriptionTr());
        }
    }

    @Test
    void inputSchemas_match_parametersJsonSchema_byte_for_byte_after_canonicalization() throws Exception {
        Map<String, AgentTool> byName = toolRegistry.all().stream()
                .collect(Collectors.toMap(AgentTool::name, t -> t));

        for (ToolCallback cb : toolCallbackProvider.getToolCallbacks()) {
            String name = cb.getToolDefinition().name();
            AgentTool source = byName.get(name);

            JsonNode expected = M.readTree(source.parametersJsonSchema());
            JsonNode actual = M.readTree(cb.getToolDefinition().inputSchema());

            assertThat(actual).as("inputSchema for %s", name).isEqualTo(expected);
        }
    }
}
