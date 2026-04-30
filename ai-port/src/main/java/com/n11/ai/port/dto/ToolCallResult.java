package com.n11.ai.port.dto;

public record ToolCallResult(
    String callId,
    String resultJson,
    boolean isError
) {}
