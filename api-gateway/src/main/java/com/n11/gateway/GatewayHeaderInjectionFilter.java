package com.n11.gateway;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Header-injection filter -- STUB for Phase 1, fully implemented in Phase 3.
 *
 * <p><b>Phase 1 (D-14, this plan):</b> the {@code permitAll()} chain
 * ({@link SecurityConfig}) is in effect, so we cannot trust ANY inbound
 * {@code X-User-Id} / {@code X-User-Roles} header from a public client. This filter
 * STRIPS those headers from every inbound request before forwarding. Threat ID:
 * T-01-09 (gateway header injection / skip-auth impersonation). Combined with the
 * Authorization-strip rule from D-09 (the gateway will strip {@code Authorization}
 * BEFORE forwarding starting in Phase 3 once it injects the trusted claims itself),
 * this prevents a public client from forging identity in either direction.
 *
 * <p><b>Phase 3 (TODO):</b> after the JWT chain is wired, this filter must:
 * <ol>
 *   <li>Continue stripping inbound {@code X-User-Id} / {@code X-User-Roles}
 *       (defense in depth -- never trust the wire),</li>
 *   <li>Read the validated JWT principal from the reactive {@code SecurityContext},</li>
 *   <li>Inject trusted {@code X-User-Id} (from {@code sub} claim) and
 *       {@code X-User-Roles} (from {@code roles} claim) BEFORE forwarding,</li>
 *   <li>Strip the inbound {@code Authorization} header so downstream services
 *       don't see the raw JWT (see ARCHITECTURE.md §10 anti-pattern 4 +
 *       D-09 Authorization-strip rule).</li>
 * </ol>
 * Phase 3's planner: REPLACE this stub wholesale; do NOT patch.
 */
@Component
public class GatewayHeaderInjectionFilter implements GlobalFilter, Ordered {

    private static final String HEADER_USER_ID    = "X-User-Id";
    private static final String HEADER_USER_ROLES = "X-User-Roles";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest stripped = exchange.getRequest().mutate()
            .headers(h -> {
                h.remove(HEADER_USER_ID);
                h.remove(HEADER_USER_ROLES);
            })
            .build();
        return chain.filter(exchange.mutate().request(stripped).build());
    }

    @Override
    public int getOrder() {
        // Run AFTER GatewayCorrelationIdFilter so logs of stripped attempts retain
        // the correlation-ID, but BEFORE any downstream forwarding filter.
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
