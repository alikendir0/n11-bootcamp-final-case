package com.n11.ai.domain.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.n11.agent.AgentTool;
import com.n11.agent.ToolContext;
import com.n11.agent.ToolRegistry;
import com.n11.agent.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class ToolDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ToolDispatcher.class);

    private final ToolRegistry registry;
    private final IdProvenanceService provenance;
    private final ObjectMapper json = new ObjectMapper();

    public ToolDispatcher(ToolRegistry registry, IdProvenanceService provenance) {
        this.registry = registry;
        this.provenance = provenance;
    }

    public ToolResult dispatch(String toolName, String argsJson,
                               String userId, String correlationId, Set<String> seenIds) {
        AgentTool tool = registry.find(toolName).orElse(null);
        if (tool == null) {
            return ToolResult.err("UNKNOWN_TOOL",
                "Bilinmeyen araç adı: '" + toolName + "'.");
        }

        // D-04 auth check (BEFORE provenance — cheaper rejection first)
        if (tool.requiresAuth() && (userId == null || userId.isBlank())) {
            return ToolResult.err("AUTH_REQUIRED",
                "Bu işlem için giriş yapman gerekiyor. Giriş yaptıktan sonra tekrar deneyelim.");
        }

        JsonNode args;
        try {
            args = (argsJson == null || argsJson.isBlank())
                ? json.createObjectNode()
                : json.readTree(argsJson);
        } catch (Exception e) {
            return ToolResult.err("TOOL_VALIDATION_FAILED",
                "Araç argümanları geçersiz JSON: " + e.getMessage());
        }

        // D-08 Pitfall #10 ID provenance
        String provenanceError = provenance.validateOrReject(toolName, args, seenIds);
        if (provenanceError != null) {
            log.info("ToolDispatcher: rejected {} due to UNKNOWN_ID: {}", toolName, provenanceError);
            return ToolResult.err("UNKNOWN_ID", provenanceError);
        }

        ToolContext ctx = new ToolContext(userId, correlationId, seenIds);
        ToolResult result = tool.execute(ctx, args);

        if (result instanceof ToolResult.Ok ok) {
            provenance.extractAndRegister(ok.data(), seenIds);
        }

        return result;
    }
}
