package com.n11.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Slice test for {@link GatewayHeaderInjectionFilter}. Captures the mutated
 * downstream request via a stub {@link GatewayFilterChain} and asserts on
 * the headers visible to the next filter.
 *
 * <p>Two scenarios:
 * <ol>
 *   <li>Authenticated path: JwtAuthenticationToken in context →
 *       Authorization stripped, X-User-Id + X-User-Roles injected from claims.</li>
 *   <li>Public path (empty SecurityContext / no JWT): spoofed X-User-* stripped,
 *       Authorization ALSO stripped (api-contracts §4 unconditional rule).</li>
 * </ol>
 */
class GatewayHeaderInjectionFilterTest {

    private final GatewayHeaderInjectionFilter filter = new GatewayHeaderInjectionFilter();

    @Test
    void authenticatedPathStripsAuthorizationAndInjectsHeaders() {
        // Build an inbound request with a Bearer token + spoofed X-User-Id
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/identity/auth/me")
                .header("Authorization", "Bearer eyJhbGciOiJSUzI1NiJ9.spoofedpayload")
                .header("X-User-Id", "spoofed-user-id")
                .header("X-User-Roles", "ROLE_ADMIN")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // Build a JwtAuthenticationToken with a real sub + roles claim
        Jwt jwt = new Jwt(
                "fake-token-value",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "RS256"),
                Map.of(
                        "sub", "11111111-1111-1111-1111-111111111111",
                        "roles", List.of("ROLE_USER", "ROLE_ADMIN")
                )
        );
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt);
        SecurityContext securityContext = new SecurityContextImpl(auth);

        // Capture the request that flows downstream
        AtomicReference<ServerWebExchange> downstreamCapture = new AtomicReference<>();
        GatewayFilterChain chain = e -> {
            downstreamCapture.set(e);
            return Mono.empty();
        };

        // Write the security context into the Reactor context so the filter's
        // ReactiveSecurityContextHolder.getContext() call can find it.
        filter.filter(exchange, chain)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext)))
                .block();

        HttpHeaders downstreamHeaders = downstreamCapture.get().getRequest().getHeaders();
        assertThat(downstreamHeaders.getFirst("Authorization")).isNull();   // STRIPPED
        assertThat(downstreamHeaders.getFirst("X-User-Id"))
                .isEqualTo("11111111-1111-1111-1111-111111111111");          // INJECTED from sub
        assertThat(downstreamHeaders.getFirst("X-User-Roles"))
                .isEqualTo("ROLE_USER,ROLE_ADMIN");                          // INJECTED from claim
    }

    @Test
    void publicPathStillStripsAuthorizationUnconditionally() {
        // Public path (e.g., /auth/register) — no auth in context
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/v1/identity/auth/register")
                .header("Authorization", "Bearer attacker-supplied-token")
                .header("X-User-Id", "spoofed-user-id")
                .header("X-User-Roles", "ROLE_ADMIN")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicReference<ServerWebExchange> downstreamCapture = new AtomicReference<>();
        GatewayFilterChain chain = e -> {
            downstreamCapture.set(e);
            return Mono.empty();
        };

        // No security context — switchIfEmpty branch fires
        filter.filter(exchange, chain).block();

        HttpHeaders downstreamHeaders = downstreamCapture.get().getRequest().getHeaders();
        // Spoofed X-User-* stripped (defense in depth)
        assertThat(downstreamHeaders.getFirst("X-User-Id")).isNull();
        assertThat(downstreamHeaders.getFirst("X-User-Roles")).isNull();
        // Authorization ALSO stripped — api-contracts §4 unconditional rule
        assertThat(downstreamHeaders.getFirst("Authorization")).isNull();
    }

    @Test
    void filterOrderIsHighestPrecedencePlus10() {
        assertThat(filter.getOrder()).isEqualTo(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 10);
    }
}
