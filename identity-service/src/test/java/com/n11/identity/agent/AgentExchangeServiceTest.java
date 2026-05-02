package com.n11.identity.agent;

import com.n11.identity.agent.dto.AgentExchangeRequest;
import com.n11.identity.agent.dto.AgentTokenResponse;
import com.n11.identity.auth.JwtIssuerService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentExchangeServiceTest {

    private AgentApiKeyRepository repository;
    private JwtIssuerService jwtIssuerService;
    private AgentExchangeService service;

    @BeforeEach
    void setUp() {
        repository = mock(AgentApiKeyRepository.class);
        jwtIssuerService = mock(JwtIssuerService.class);
        service = new AgentExchangeService(repository, jwtIssuerService);
        when(jwtIssuerService.tokenLifetimeSeconds()).thenReturn(86400L);
    }

    @Test
    void exchange_with_valid_key_mints_jwt_bound_to_real_user_id() {
        UUID userId = UUID.randomUUID();
        String plaintext = "demo-plaintext-key";
        String hash = AgentExchangeService.sha256Hex(plaintext);
        AgentApiKey row = new AgentApiKey(hash, "demo-agent", userId, Instant.now());
        when(repository.findByApiKeyHashAndRevokedAtIsNull(hash)).thenReturn(Optional.of(row));
        when(jwtIssuerService.issue(eq(userId), eq("demo-agent"), eq("Agent"), eq(List.of("ROLE_USER"))))
                .thenReturn("the.signed.jwt");

        AgentTokenResponse resp = service.exchange(new AgentExchangeRequest(plaintext));

        assertThat(resp.accessToken()).isEqualTo("the.signed.jwt");
        assertThat(resp.expiresIn()).isEqualTo(86400L);
        verify(repository).updateLastUsed(eq(hash), any(Instant.class));
        verify(jwtIssuerService).issue(eq(userId), eq("demo-agent"), eq("Agent"), eq(List.of("ROLE_USER")));
    }

    @Test
    void exchange_with_unknown_key_returns_401() {
        when(repository.findByApiKeyHashAndRevokedAtIsNull(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.exchange(new AgentExchangeRequest("nope")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(repository, never()).updateLastUsed(any(), any());
        verify(jwtIssuerService, never()).issue(any(), any(), any(), any());
    }

    @Test
    void exchange_with_revoked_key_returns_401() {
        when(repository.findByApiKeyHashAndRevokedAtIsNull(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.exchange(new AgentExchangeRequest("revoked")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void successful_exchange_updates_last_used_once() {
        UUID userId = UUID.randomUUID();
        String plaintext = "audit-key";
        String hash = AgentExchangeService.sha256Hex(plaintext);
        AgentApiKey row = new AgentApiKey(hash, "audit-agent", userId, Instant.now());
        when(repository.findByApiKeyHashAndRevokedAtIsNull(hash)).thenReturn(Optional.of(row));
        when(jwtIssuerService.issue(any(), any(), any(), any())).thenReturn("jwt");

        service.exchange(new AgentExchangeRequest(plaintext));

        verify(repository).updateLastUsed(eq(hash), any(Instant.class));
    }

    @Test
    void sha256_is_deterministic_and_64_hex_chars() {
        String h1 = AgentExchangeService.sha256Hex("hello");
        String h2 = AgentExchangeService.sha256Hex("hello");

        assertThat(h1).isEqualTo(h2).hasSize(64).matches("[0-9a-f]{64}");
    }
}
