package com.n11.ai.port.dto;

public record ToolSchema(
    String name,
    String descriptionTr,
    String parametersJsonSchema
) {}
