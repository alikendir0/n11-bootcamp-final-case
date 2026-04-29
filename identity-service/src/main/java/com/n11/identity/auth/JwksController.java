package com.n11.identity.auth;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * JWKS endpoint per RFC 7517 — the gateway fetches this at validation time
 * (CONTEXT D-03; success criterion #4). Only the public material is served;
 * {@link RSAKey#toPublicJWK()} strips the private key.
 *
 * <p>Path: GET /.well-known/jwks.json (api-contracts.md §1).
 * Spring Cloud Gateway routes /api/v1/identity/.well-known/jwks.json to this
 * service's /.well-known/jwks.json (the /api/v1/identity/** prefix is stripped
 * by the discovery-locator's lower-case-service-id rule + the gateway's
 * inbuilt path-rewriting).
 */
@RestController
public class JwksController {

    private final RSAKey rsaJwk;

    public JwksController(RSAKey rsaJwk) {
        this.rsaJwk = rsaJwk;
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        // toPublicJWK() strips the private key — CRITICAL. Never serve the raw rsaJwk.
        JWKSet publicSet = new JWKSet(rsaJwk.toPublicJWK());
        return publicSet.toJSONObject();
    }
}
