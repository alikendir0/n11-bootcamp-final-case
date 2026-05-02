package com.n11.ai.port.dto;

public record ToolCallResult(
    String callId,
    String functionName,
    String resultJson,
    boolean isError
) {}
