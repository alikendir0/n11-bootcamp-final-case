package com.n11.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

/**
 * Reactive slice test for {@link SecurityConfig}. Uses {@link WebFluxTest}
 * to wire only the security chain + a stub controller — no full Spring Boot
 * boot, no Eureka, no JWKS fetch. The {@code mockJwt()} configurer fakes a
 * validated JWT so the chain authorises the request without needing the
 * NimbusReactiveJwtDecoder to actually call identity-service.
 *
 * <p>Key invariants verified:
 * <ul>
 *   <li>T-3-02 (spoofing): unauthenticated request to protected route → 401</li>
 *   <li>Public allowlist: no 401 for listed paths (login/register/JWKS/etc.)</li>
 *   <li>T-3-03: authenticated request with valid JWT → NOT rejected (not 401)</li>
 *   <li>CORS preflight: OPTIONS → NOT rejected by security (not 401)</li>
 * </ul>
 *
 * <p>Note: in @WebFluxTest on an api-gateway module, Spring Cloud Gateway's routing
 * may return 404 for stub controller paths — the tests verify the *security decision*
 * (401 vs not-401), not the full routing stack.
 */
@WebFluxTest(controllers = SecurityConfigTest.StubController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
    "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:0/.well-known/jwks.json"
})
class SecurityConfigTest {

    @Autowired WebTestClient webTestClient;

    @Test
    void unauthenticatedRequestToProtectedRouteReturns401() {
        // Protected route without Bearer token — the security chain MUST return 401 (T-3-02).
        webTestClient.get().uri("/api/v1/orders/anything")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void publicAllowlistLoginRouteNotRejectedBySecurityWithoutBearer() {
        // POST /api/v1/identity/auth/login is in the public allowlist — must NOT require Bearer.
        // Security returns anything except 401; 404 from gateway routing is acceptable in slice test.
        webTestClient.post().uri("/api/v1/identity/auth/login")
                .exchange()
                .expectStatus().value(status ->
                    assertThat(status)
                        .as("Login route must be permitted by security (no 401)")
                        .isNotEqualTo(HttpStatus.UNAUTHORIZED.value()));
    }

    @Test
    void publicAllowlistRegisterRouteNotRejectedBySecurityWithoutBearer() {
        // POST /api/v1/identity/auth/register is in the public allowlist.
        webTestClient.post().uri("/api/v1/identity/auth/register")
                .exchange()
                .expectStatus().value(status ->
                    assertThat(status)
                        .as("Register route must be permitted by security (no 401)")
                        .isNotEqualTo(HttpStatus.UNAUTHORIZED.value()));
    }

    @Test
    void publicAllowlistAgentExchangeRouteNotRejectedBySecurityWithoutBearer() {
        // MCP clients need this public exchange to turn MCP_API_KEY into a normal JWT.
        webTestClient.post().uri("/api/v1/identity/agents/exchange")
                .exchange()
                .expectStatus().value(status ->
                    assertThat(status)
                        .as("Agent exchange route must be permitted by security (no 401)")
                        .isNotEqualTo(HttpStatus.UNAUTHORIZED.value()));
    }

    @Test
    void publicAllowlistIyzicoCallbackGetRouteNotRejectedBySecurityWithoutBearer() {
        webTestClient.get().uri("/api/v1/payments/iyzico/callback?token=test-token")
                .exchange()
                .expectStatus().value(status ->
                    assertThat(status)
                        .as("Iyzico callback GET must be permitted by security (no 401/403)")
                        .isNotIn(HttpStatus.UNAUTHORIZED.value(), HttpStatus.FORBIDDEN.value()));
    }

    @Test
    void authenticatedRequestWithValidJwtNotRejectedBySecurity() {
        // With a valid mocked JWT, a protected route must pass the security chain.
        // Security accepting = not 401. The route may return 404 from gateway routing.
        webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt
                .subject("11111111-1111-1111-1111-111111111111")
                .claim("roles", java.util.List.of("ROLE_USER"))
        ))
                .get().uri("/api/v1/orders/test")
                .exchange()
                .expectStatus().value(status ->
                    assertThat(status)
                        .as("Authenticated request must not be rejected by security (not 401)")
                        .isNotEqualTo(HttpStatus.UNAUTHORIZED.value()));
    }

    @Test
    void corsPreflightNotRejectedBySecurity() {
        // OPTIONS preflight is in the security allowlist (.pathMatchers(OPTIONS, "/**").permitAll()).
        // The security layer must NOT return 401 for preflight requests.
        webTestClient.options().uri("/api/v1/orders/test")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET")
                .exchange()
                .expectStatus().value(status ->
                    assertThat(status)
                        .as("CORS preflight must not be rejected by Spring Security (not 401)")
                        .isNotEqualTo(HttpStatus.UNAUTHORIZED.value()));
    }

    @Test
    void corsConfigurationAllowsLanFrontendOrigin() {
        CorsConfigurationSource source = new SecurityConfig().corsConfigurationSource();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/categories").build());

        CorsConfiguration configuration = source.getCorsConfiguration(exchange);

        assertThat(configuration).isNotNull();
        assertThat(configuration.checkOrigin("http://192.168.1.46:8083"))
                .isEqualTo("http://192.168.1.46:8083");
    }

    @Test
    void corsConfigurationAllowsIyzicoSandboxCallbackOrigin() {
        CorsConfigurationSource source = new SecurityConfig().corsConfigurationSource();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/payments/iyzico/callback").build());

        CorsConfiguration configuration = source.getCorsConfiguration(exchange);

        assertThat(configuration).isNotNull();
        assertThat(configuration.checkOrigin("https://sandbox-cpp.iyzipay.com"))
                .isEqualTo("https://sandbox-cpp.iyzipay.com");
        assertThat(configuration.checkOrigin("https://sandbox-cpp.iyzico.com"))
                .isEqualTo("https://sandbox-cpp.iyzico.com");
        assertThat(configuration.checkOrigin("https://sandbox.iyzipay.com"))
                .isEqualTo("https://sandbox.iyzipay.com");
        assertThat(configuration.checkOrigin("https://sandbox-cpp.iyzipay.com.tr"))
                .isEqualTo("https://sandbox-cpp.iyzipay.com.tr");
    }

    // Minimal stub — wired for slice test context.
    @RestController
    @RequestMapping("/api/v1")
    static class StubController {
        @GetMapping("/orders/anything")
        String protectedRoute() { return "ok"; }
    }
}
