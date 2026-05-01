package com.n11.ai.port.dto;

import java.util.List;

public record ChatMessage(
    MessageRole role,
    String content,
    List<ToolCallRequest> toolCalls,
    List<ToolCallResult> toolResults
) {}
