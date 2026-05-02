package com.n11.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.n11.agent.ToolContext;
import com.n11.agent.ToolResult;
import com.n11.agent.http.IdentityToolClient;
import org.springframework.stereotype.Component;

@Component
public class ListAddressesTool extends AbstractAgentTool {

    private final IdentityToolClient client;

    public ListAddressesTool(IdentityToolClient client) {
        this.client = client;
    }

    @Override
    public String name() { return "list_addresses"; }

    @Override
    public String descriptionTr() {
        return "Kullanıcının kayıtlı teslimat adreslerini listeler. Sipariş oluşturmak için adresID gereklidir.";
    }

    @Override
    public boolean requiresAuth() { return true; }

    @Override
    public String parametersJsonSchema() {
        return "{\"type\":\"object\",\"properties\":{},\"required\":[]}";
    }

    @Override
    protected ToolResult doExecute(ToolContext ctx, JsonNode args) {
        if (ctx.userId() == null || ctx.userId().isBlank()) {
            return ToolResult.err("AUTH_REQUIRED",
                "Adresleri görmek için giriş yapman gerekiyor.");
        }
        JsonNode result = client.listAddresses(ctx.userId());
        return ToolResult.ok(result);
    }
}