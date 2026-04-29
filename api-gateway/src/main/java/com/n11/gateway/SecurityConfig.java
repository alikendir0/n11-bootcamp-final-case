package com.n11.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtGrantedAuthoritiesConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.List;

/**
 * Reactive Spring Security chain (D-18). Phase 3 flip: from Phase 1's
 * {@code permitAll()} to JWT-validated chain backed by identity-service's
 * JWKS endpoint.
 *
 * <p>Public allowlist mirrors api-contracts.md §3 — login, register, JWKS,
 * GET /products/**, GET /search/**, /chat/**, payments callback. Everything
 * else requires authentication. The {@link GatewayHeaderInjectionFilter}
 * (separate file) is responsible for stripping Authorization and injecting
 * X-User-Id / X-User-Roles after this chain validates the JWT.
 *
 * <p>Threat-model alignment:
 * <ul>
 *   <li>T-3-01 mitigation: jwk-set-uri NOT lb:// (Risk 2). Docker-compose
 *       hostname is the only working URI for NimbusReactiveJwtDecoder's
 *       internal WebClient.</li>
 *   <li>T-3-03 mitigation: JwtTimestampValidator(30s) explicit; default is 60s.</li>
 *   <li>T-01-11 (CORS misconfig) preserved: allowedOrigins is an explicit list,
 *       NOT "*" — required by allowCredentials=true.</li>
 * </ul>
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityFilterChain(ServerHttpSecurity http,
                                                      ReactiveJwtDecoder jwtDecoder,
                                                      ReactiveJwtAuthenticationConverter jwtAuthConverter) {
        http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeExchange(ex -> ex
                // CORS preflight — always permit
                .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // api-contracts.md §3 public allowlist (Day-1 locked):
                .pathMatchers(HttpMethod.POST, "/api/v1/identity/auth/login").permitAll()
                .pathMatchers(HttpMethod.POST, "/api/v1/identity/auth/register").permitAll()
                .pathMatchers(HttpMethod.GET,  "/api/v1/identity/.well-known/jwks.json").permitAll()
                .pathMatchers(HttpMethod.GET,  "/api/v1/products/**").permitAll()
                .pathMatchers(HttpMethod.GET,  "/api/v1/categories/**").permitAll()
                .pathMatchers(HttpMethod.GET,  "/api/v1/inventory/**").permitAll()
                // Admin-only write paths (ROLE_ADMIN required at gateway — defense-in-depth per T-04-03-01)
                .pathMatchers(HttpMethod.POST, "/api/v1/products/**").hasAuthority("ROLE_ADMIN")
                .pathMatchers(HttpMethod.PUT, "/api/v1/products/**").hasAuthority("ROLE_ADMIN")
                .pathMatchers(HttpMethod.DELETE, "/api/v1/products/**").hasAuthority("ROLE_ADMIN")
                .pathMatchers(HttpMethod.GET,  "/api/v1/search/**").permitAll()
                .pathMatchers(HttpMethod.POST, "/api/v1/chat/**").permitAll()
                .pathMatchers(HttpMethod.GET,  "/api/v1/chat/**").permitAll()
                .pathMatchers(HttpMethod.POST, "/api/v1/payments/iyzico/callback").permitAll()

                // Actuator (T-01-04 mitigation: only health/info/gateway exposed via management.endpoints)
                .pathMatchers("/actuator/**").permitAll()

                // Springdoc aggregator
                .pathMatchers("/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**", "/webjars/**").permitAll()

                .anyExchange().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .jwtDecoder(jwtDecoder)
                    .jwtAuthenticationConverter(jwtAuthConverter)
                )
            );
        return http.build();
    }

    @Bean
    public ReactiveJwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri) {
        NimbusReactiveJwtDecoder decoder =
                NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri)
                        .jwsAlgorithm(SignatureAlgorithm.RS256)
                        .build();
        // D-04: 30s clock skew. Default is 60s — must override explicitly.
        // No issuer-uri, so no JwtIssuerValidator; only the timestamp validator.
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(Duration.ofSeconds(30))
        ));
        return decoder;
    }

    /**
     * Risk 5 mitigation: default converter reads `scope`/`scp`; we mint `roles`.
     * Configure to read `roles` claim with empty prefix (claim values are already
     * `ROLE_*` prefixed by JwtIssuerService). Phase 4's hasRole("ADMIN") relies on this.
     */
    @Bean
    public ReactiveJwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter rolesConverter = new JwtGrantedAuthoritiesConverter();
        rolesConverter.setAuthoritiesClaimName("roles");
        rolesConverter.setAuthorityPrefix("");
        ReactiveJwtAuthenticationConverter converter = new ReactiveJwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(
                new ReactiveJwtGrantedAuthoritiesConverterAdapter(rolesConverter));
        return converter;
    }

    /** CORS preserved verbatim from Phase 1. Phase 11 will append the tunnel hostname. */
    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
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
