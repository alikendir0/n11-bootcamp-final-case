package com.n11.ai.domain.tools;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * D-08 belt-and-braces ID provenance. Centralized in ai-service ToolDispatcher
 * (NOT in tool implementations) so mcp-server can reuse agent-toolset without
 * dragging this logic — it owns its own analog.
 */
@Service
public class IdProvenanceService {

    private static final Pattern ID_KEY = Pattern.compile(".*[iI]d$");
    private static final int MIN_ID_LEN = 8;

    public String validateOrReject(String toolName, JsonNode args, Set<String> seenIds) {
        if (args == null || !args.isObject()) return null;
        Iterator<Map.Entry<String, JsonNode>> fields = args.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (!ID_KEY.matcher(field.getKey()).matches()) continue;
            if (!field.getValue().isTextual()) continue;
            String id = field.getValue().asText();
            if (id.length() >= MIN_ID_LEN && !seenIds.contains(id)) {
                return "'" + field.getKey() + "' değeri '" + id + "' önceki araç sonuçlarında görülmedi. " +
                       "Önce search_products veya get_product kullanarak geçerli bir ID al.";
            }
        }
        return null;
    }

    public void extractAndRegister(JsonNode data, Set<String> seenIds) {
        if (data == null) return;
        // Walk for all string values whose key matches ID_KEY (or is plain "id")
        walk(data, seenIds);
    }

    private void walk(JsonNode node, Set<String> seenIds) {
        if (node == null) return;
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                if ((ID_KEY.matcher(e.getKey()).matches() || "id".equals(e.getKey()))
                    && e.getValue().isTextual()) {
                    String v = e.getValue().asText();
                    if (v.length() >= MIN_ID_LEN) seenIds.add(v);
                }
                walk(e.getValue(), seenIds);
            }
        } else if (node.isArray()) {
            node.forEach(child -> walk(child, seenIds));
        }
    }
}
