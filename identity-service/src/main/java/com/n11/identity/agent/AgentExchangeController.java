package com.n11.identity.agent;

import com.n11.identity.agent.dto.AgentExchangeRequest;
import com.n11.identity.agent.dto.AgentTokenResponse;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 9 (AI-13). The auth bridge endpoint MCP server hits to swap an
 * MCP_API_KEY for an internal JWT.
 *
 * <p>Public-but-API-key-validated: IdentitySecurityConfig is anyRequest().permitAll()
 * (Phase 3 / D-15) — the API-key check happens inside AgentExchangeService, NOT in
 * Spring Security. Through the gateway, this maps to /api/v1/identity/agents/exchange.
 *
 * <p>This controller is intentionally thin (PATTERNS.md §Auth controller).
 */
@RestController
@RequestMapping("/agents")
public class AgentExchangeController {

    private final AgentExchangeService service;

    public AgentExchangeController(AgentExchangeService service) {
        this.service = service;
    }

    @PostMapping("/exchange")
    public AgentTokenResponse exchange(@Valid @RequestBody AgentExchangeRequest body) {
        return service.exchange(body);
    }
}
