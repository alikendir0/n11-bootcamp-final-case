package com.n11.identity.agent.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * POST /agents/exchange request body. JSON shape: {"apiKey":"plaintext-key"}.
 * The plaintext key is hashed inside the service — never logged, never persisted.
 */
public record AgentExchangeRequest(@NotBlank String apiKey) {}
