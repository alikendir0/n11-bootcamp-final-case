package com.n11.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.n11.agent.AgentTool;
import com.n11.agent.ToolRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = ToolSchemaContractTest.TestApp.class)
class ToolSchemaContractTest {

    @Autowired ToolRegistry registry;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void every_tool_returns_parseable_object_schema() throws Exception {
        for (AgentTool t : registry.all()) {
            String schemaJson = t.parametersJsonSchema();
            assertThat(schemaJson).as("%s schema must not be null", t.name()).isNotNull();
            JsonNode schema = mapper.readTree(schemaJson);
            assertThat(schema.path("type").asText()).as("%s schema must have type=object", t.name()).isEqualTo("object");
            assertThat(schema.has("properties")).as("%s schema must have properties", t.name()).isTrue();
        }
    }

    @Configuration
    @ComponentScan(basePackages = "com.n11.agent")
    static class TestApp { }
}
