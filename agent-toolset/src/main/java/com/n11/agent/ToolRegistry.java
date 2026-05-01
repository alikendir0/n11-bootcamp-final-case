package com.n11.agent;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Aggregates all @Component AgentTool beans found by Spring component-scan.
 * ai-service auto-discovers; mcp-server (Phase 9) re-uses via @Tool annotation.
 */
@Component
public class ToolRegistry {

    private final Map<String, AgentTool> tools;

    public ToolRegistry(List<AgentTool> beans) {
        this.tools = beans.stream().collect(Collectors.toMap(
            AgentTool::name,
            t -> t,
            (a, b) -> {
                throw new IllegalStateException(
                    "Duplicate AgentTool name: " + a.name() +
                    " (classes: " + a.getClass() + ", " + b.getClass() + ")");
            }
        ));
    }

    public Optional<AgentTool> find(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    public List<AgentTool> all() {
        return List.copyOf(tools.values());
    }
}
