package com.n11.gateway;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Reactive security chain. Phase 1: {@code permitAll()} (D-14) with CORS for the Phase 10
 * frontend dev origin. Phase 3 will swap {@code permitAll()} for a JWT-validated chain
 * ({@code oauth2ResourceServer().jwt()} against identity-service's JWKS).
 *
 * <p>NOTE: this file MUST be REPLACED -- not patched -- in Phase 3, because the JWT
 * chain change is structural enough that "edit and hope" is risky. Phase 3's planner
 * should expect to delete + rewrite this file. The header-strip semantics in
 * {@link GatewayHeaderInjectionFilter} stay unchanged (defense in depth -- never trust
 * the wire even with auth on).
 *
 * <p>Threat-model alignment:
 * <ul>
 *   <li>T-01-04 (actuator info-disclosure): exposure list lives in
 *       {@code config-server/.../config/api-gateway.yml}, not here.</li>
 *   <li>T-01-11 (CORS misconfig): {@code allowedOrigins} is an explicit list
 *       (NOT {@code "*"} -- forbidden combo with {@code allowCredentials=true}).
 *       Phase 11 expands the list with the public tunnel hostname.</li>
 * </ul>
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityFilterChain(ServerHttpSecurity http) {
        http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeExchange(ex -> ex.anyExchange().permitAll());
        return http.build();
    }

    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        // Phase 10 frontend dev origin -- Vite default (per CLAUDE.md frontend stack).
        // Phase 11 will expand this list with the public tunnel hostname (Cloudflare
        // Tunnel / ngrok) used for the demo URL.
        cfg.setAllowedOrigins(List.of("http://localhost:5173"));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setExposedHeaders(List.of("X-Correlation-Id"));
        cfg.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", cfg);
        return src;
    }
}
