package com.n11.agent;

import java.util.Set;

/**
 * Per-call context (D-05 / D-08). Tools receive nothing else from ai-service.
 *
 * @param userId      X-User-Id forwarded by gateway; null for guests (D-03)
 * @param correlationId for MDC propagation through outbound HTTP
 * @param seenIds     Pitfall #10: IDs seen in prior tool results this conversation
 */
public record ToolContext(
    String userId,
    String correlationId,
    Set<String> seenIds
) {}
