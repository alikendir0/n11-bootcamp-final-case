package com.n11.infratests.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.n11.agent.AgentTool;
import com.n11.agent.ToolRegistry;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.TestPropertySource;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 9 SC-1 + SC-4 grading proof at the infra-tests integration level.
 *
 * <p>SC-1 (AI-11): mcp-server's tools/list exposes identical tool names,
 * descriptions, and schemas as ai-service uses — verified here by booting
 * mcp-server's Spring context inside the multi-service infra-tests classpath
 * and asserting bean equality against the shared ToolRegistry.
 *
 * <p>SC-4 (AI-11): mcp-server has zero local tool definitions — verified by
 * {@link #zero_local_tool_definitions_in_mcp_server} via filesystem walk + regex.
 *
 * <p>This test is the regression safety net: future phases (10, 11) re-run it
 * to ensure no drift in the agent-toolset → mcp-server contract.
 */
@SpringBootTest(
    classes = McpServerTestConfig.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@TestPropertySource(properties = {
    // Spring AI MCP server config (mirrors mcp-server.yml; required for adapter to load)
    "spring.ai.mcp.server.protocol=STREAMABLE",
    "spring.ai.mcp.server.stdio=false",
    "spring.ai.mcp.server.streamable-http.mcp-endpoint=/mcp",
    "spring.ai.mcp.server.capabilities.tool=true",
    "spring.ai.mcp.server.capabilities.resource=false",
    "spring.ai.mcp.server.capabilities.prompt=false",
    "spring.ai.mcp.server.name=n11-storefront",
    "spring.ai.mcp.server.version=1.0.0",
    "spring.ai.mcp.server.type=SYNC",
    // Disable autoconfig that would fire on multi-service classpath
    "spring.autoconfigure.exclude="
        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
    // Disable cloud-config / Eureka in tests
    "spring.cloud.config.enabled=false",
    "spring.cloud.discovery.enabled=false",
    "eureka.client.enabled=false",
    // AgentJwtCache scheduler must not run (would attempt /agents/exchange against null target)
    "spring.task.scheduling.enabled=false",
    // Plan 09-04 AgentJwtClient @Value binding — placeholder; tests do not exchange
    "mcp.api-key=test-key-not-actually-exchanged"
})
@ExtendWith(OutputCaptureExtension.class)
class McpServerToolsListEqualityTest {

    private static final ObjectMapper M = new ObjectMapper();
    private static final Pattern LOCAL_AGENT_TOOL_PATTERN = Pattern.compile(
            "class\\s+\\w+\\s+(extends\\s+\\w*AgentTool|implements\\s+\\w*AgentTool)"
                + "|extends\\s+AbstractAgentTool");

    @Autowired ToolCallbackProvider toolCallbackProvider;
    @Autowired ToolRegistry toolRegistry;

    @Test
    void exposes_exactly_11_callbacks() {
        ToolCallback[] callbacks = toolCallbackProvider.getToolCallbacks();
        assertThat(callbacks).hasSize(11);
        assertThat(toolRegistry.all()).hasSize(11);
    }

    @Test
    void callback_names_equal_tool_registry_names_set_equality() {
        Set<String> callbackNames = Arrays.stream(toolCallbackProvider.getToolCallbacks())
                .map(c -> c.getToolDefinition().name())
                .collect(Collectors.toSet());
        Set<String> registryNames = toolRegistry.all().stream()
                .map(AgentTool::name)
                .collect(Collectors.toSet());

        assertThat(callbackNames).isEqualTo(registryNames);
        assertThat(callbackNames).hasSize(toolCallbackProvider.getToolCallbacks().length);
    }

    @Test
    void descriptions_match_descriptionTr_byte_for_byte() {
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
    void inputSchemas_match_after_json_canonicalization() throws Exception {
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

    /**
     * SC-4 structural DRY proof: zero AgentTool implementations in mcp-server source.
     *
     * <p>Walks {@code mcp-server/src/main/java/} and asserts no Java file declares
     * a class extending or implementing AgentTool. The grep gate is implemented in
     * Java (NOT shell) so the test is portable across CI environments.
     */
    @Test
    void zero_local_tool_definitions_in_mcp_server() throws IOException {
        Path mcpSrc = locateMcpServerSrc();

        try (Stream<Path> javaFiles = Files.walk(mcpSrc)) {
            List<Path> offenders = javaFiles
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(this::containsLocalAgentToolDefinition)
                    .toList();

            assertThat(offenders)
                    .as("mcp-server must have ZERO local AgentTool implementations (CLAUDE.md Rule #2 / SC-4)")
                    .isEmpty();
        }
    }

    @Test
    void boot_log_reports_11_registered_tools(CapturedOutput output) {
        assertThat(output.getOut())
                .contains("MCP transport adapter registered 11 tools");
    }

    private boolean containsLocalAgentToolDefinition(Path path) {
        try {
            return LOCAL_AGENT_TOOL_PATTERN.matcher(Files.readString(path)).find();
        } catch (IOException e) {
            throw new IllegalStateException("Failed reading " + path, e);
        }
    }

    private static Path locateMcpServerSrc() {
        Path[] candidates = {
                Paths.get("mcp-server/src/main/java"),
                Paths.get("../mcp-server/src/main/java")
        };
        for (Path p : candidates) {
            if (Files.isDirectory(p)) {
                return p;
            }
        }
        throw new IllegalStateException(
                "Cannot locate mcp-server/src/main/java from working dir " + Paths.get(".").toAbsolutePath());
    }
}
