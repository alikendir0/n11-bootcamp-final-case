package com.n11.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.n11.agent.AgentTool;
import com.n11.agent.ToolContext;
import com.n11.agent.ToolResult;
import com.n11.agent.http.ToolHttpException;

/**
 * Shared base: D-04 auth check + ToolHttpException -> ToolResult.Err mapping.
 * Concrete subclasses implement name(), descriptionTr(), requiresAuth(),
 * parametersJsonSchema(), and doExecute(ctx, args).
 *
 * Tools NEVER throw out of execute(); errors come back as ToolResult.Err so
 * Gemini sees them as tool-result messages (Pitfall #6) and replies in Turkish.
 */
public abstract class AbstractAgentTool implements AgentTool {

    protected abstract ToolResult doExecute(ToolContext ctx, JsonNode args);

    @Override
    public final ToolResult execute(ToolContext ctx, JsonNode args) {
        if (requiresAuth() && (ctx.userId() == null || ctx.userId().isBlank())) {
            return ToolResult.err("AUTH_REQUIRED",
                "Bu işlem için giriş yapman gerekiyor. Giriş yaptıktan sonra tekrar deneyelim.");
        }
        try {
            return doExecute(ctx, args);
        } catch (ToolHttpException e) {
            return ToolResult.err(e.code(),
                "Araç çalıştırılırken hata oluştu: " + safeMessage(e.getMessage()));
        } catch (RuntimeException e) {
            return ToolResult.err("TOOL_ERROR",
                "Araç çalıştırılırken beklenmeyen bir hata oluştu.");
        }
    }

    private static String safeMessage(String raw) {
        if (raw == null) return "";
        // Strip stack-trace fragments / SQL fragments from upstream
        return raw.length() > 200 ? raw.substring(0, 200) + "…" : raw;
    }
}
