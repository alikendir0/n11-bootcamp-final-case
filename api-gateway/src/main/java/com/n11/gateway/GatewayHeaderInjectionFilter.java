package com.n11.gateway;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Reactive {@link GlobalFilter} (D-19). Phase 3 implementation:
 * <ol>
 *   <li>Strips inbound spoofed {@code X-User-Id} / {@code X-User-Roles}
 *       (defense in depth — never trust the wire).</li>
 *   <li>Reads the validated {@link JwtAuthenticationToken} from the
 *       reactive {@link SecurityContext} (populated by {@link SecurityConfig}'s
 *       {@code oauth2ResourceServer().jwt()} chain).</li>
 *   <li>Injects {@code X-User-Id} (from {@code sub} claim) and
 *       {@code X-User-Roles} (comma-joined from {@code roles} claim).</li>
 *   <li>Strips the inbound {@code Authorization} header so downstream services
 *       never see the raw JWT (api-contracts.md §4 anti-pattern: trust the mesh).</li>
 * </ol>
 *
 * <p>Order: {@code Ordered.HIGHEST_PRECEDENCE + 10} (unchanged from Phase 1
 * stub) — runs AFTER {@link GatewayCorrelationIdFilter} (HIGHEST_PRECEDENCE+5)
 * but BEFORE Spring Cloud Gateway's forwarding filter.
 *
 * <p>Public allowlist paths (login/register/JWKS/products/search/chat/iyzico-callback)
 * skip the JWT path because the SecurityWebFilterChain matches them with permitAll().
 * For those paths the {@code switchIfEmpty} branch fires: still strip spoofed
 * X-User-* headers AND strip Authorization unconditionally (api-contracts.md §4
 * — the Authorization header is the gateway's input contract; downstream services
 * never see it, regardless of whether the inbound path was public or authenticated).
 *
 * <p>Implementation note: the reactive chain returns {@code Mono<ServerHttpRequest>}
 * (not {@code Mono<Void>}) before the terminal {@code chain.filter()} call. This is
 * intentional — {@code switchIfEmpty} on a {@code Mono<Void>} would ALWAYS fire
 * because {@code chain.filter()} completes without emitting a value (it is
 * {@code Mono<Void>}). By operating on {@code Mono<ServerHttpRequest>} first and
 * calling {@code chain.filter()} exactly once in the terminal {@code flatMap},
 * the authenticated path and public path are mutually exclusive.
 */
@Component
public class GatewayHeaderInjectionFilter implements GlobalFilter, Ordered {

    private static final String HEADER_USER_ID    = "X-User-Id";
    private static final String HEADER_USER_ROLES = "X-User-Roles";
    private static final String HEADER_AUTH       = "Authorization";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Build the (potentially mutated) downstream request first, THEN call chain.filter once.
        // This avoids the switchIfEmpty-on-Mono<Void> trap: chain.filter() returns Mono<Void>
        // which completes without emitting a value — switchIfEmpty would always fire.
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(auth -> auth instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .map(jwtAuth -> {
                    // Authenticated path: inject X-User-Id + X-User-Roles, strip Authorization
                    Jwt jwt = jwtAuth.getToken();
                    String userId = jwt.getSubject();
                    List<String> roles = jwt.getClaimAsStringList("roles");
                    String rolesHeader = (roles != null) ? String.join(",", roles) : "";
                    return exchange.getRequest().mutate()
                            .headers(h -> {
                                h.remove(HEADER_USER_ID);     // strip inbound spoofed
                                h.remove(HEADER_USER_ROLES);
                                h.remove(HEADER_AUTH);        // strip raw JWT (api-contracts.md §4)
                                h.set(HEADER_USER_ID, userId);
                                h.set(HEADER_USER_ROLES, rolesHeader);
                            })
                            .build();
                })
                .switchIfEmpty(Mono.fromCallable(() ->
                    // Public path: strip spoofed user headers AND strip Authorization
                    // unconditionally (api-contracts.md §4: the Authorization header is
                    // the gateway's input boundary; downstream services never see it).
                    exchange.getRequest().mutate()
                            .headers(h -> {
                                h.remove(HEADER_USER_ID);
                                h.remove(HEADER_USER_ROLES);
                                h.remove(HEADER_AUTH);
                            })
                            .build()
                ))
                .flatMap(mutatedRequest ->
                    chain.filter(exchange.mutate().request(mutatedRequest).build())
                );
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
