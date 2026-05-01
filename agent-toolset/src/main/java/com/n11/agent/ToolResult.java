package com.n11.agent;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Sealed result type. Gemini sees Err results as tool-result messages and
 * formulates a Turkish apology (Pitfall #6).
 */
public sealed interface ToolResult permits ToolResult.Ok, ToolResult.Err {

    record Ok(JsonNode data) implements ToolResult {}

    record Err(String code, String message) implements ToolResult {}

    static Ok ok(JsonNode data) { return new Ok(data); }
    static Err err(String code, String message) { return new Err(code, message); }
}
