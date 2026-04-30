package com.n11.ai.port.dto;

public record ToolCallRequest(
    String callId,
    String name,
    String argsJson
) {}
