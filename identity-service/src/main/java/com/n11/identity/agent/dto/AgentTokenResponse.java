package com.n11.identity.agent.dto;

/**
 * POST /agents/exchange response body. Mirrors the relevant subset of AuthResponse
 * but omits the user profile summary — agents don't need user-facing fields.
 */
public record AgentTokenResponse(String accessToken, long expiresIn) {}
