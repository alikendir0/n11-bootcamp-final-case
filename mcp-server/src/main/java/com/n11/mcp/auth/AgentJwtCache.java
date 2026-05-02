package com.n11.mcp.auth;

import org.springframework.stereotype.Component;

/**
 * Phase 9 stub. Plan 09-04 replaces the body with the lazy-exchange + scheduled
 * refresh implementation. This stub exists only so AgentToolMcpRegistration
 * (Plan 09-03) compiles independently in Wave 2.
 *
 * <p>Throwing UnsupportedOperationException at the methods makes accidental
 * production use (without Plan 09-04 landing) fail fast.
 */
@Component
public class AgentJwtCache {

    public String userId() {
        throw new UnsupportedOperationException(
            "AgentJwtCache stub — Plan 09-04 must replace this class with the real implementation");
    }

    public String bearerToken() {
        throw new UnsupportedOperationException(
            "AgentJwtCache stub — Plan 09-04 must replace this class with the real implementation");
    }
}
