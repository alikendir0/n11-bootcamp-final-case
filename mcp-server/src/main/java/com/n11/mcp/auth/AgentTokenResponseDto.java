package com.n11.mcp.auth;

/**
 * Wire-format match for identity-service AgentTokenResponse (Plan 09-02 Task 3).
 * Local copy avoids cross-module coupling — mcp-server depends only on the wire
 * contract, not on identity-service classpath.
 */
public record AgentTokenResponseDto(String accessToken, long expiresIn) {}
