package com.n11.ai.port.dto;

import java.util.List;

public record ChatResponse(
    String text,
    List<ToolCallRequest> toolCalls,
    String finishReason
) {}
