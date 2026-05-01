package com.n11.ai.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ChatRequest(
    @NotNull UUID conversationId,        // frontend generates a UUID and persists in localStorage (D-03)
    @NotBlank String message
) {}
