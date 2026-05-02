package com.n11.mcp.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentJwtCacheTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private AgentJwtClient client;
    private AgentJwtCache cache;

    @BeforeEach
    void setUp() {
        client = mock(AgentJwtClient.class);
        cache = new AgentJwtCache(client);
        // Do NOT call cache.schedule() — tests bypass the scheduler.
    }

    @Test
    void first_userId_call_triggers_exchange_once() {
        UUID sub = UUID.randomUUID();
        String jwt = makeJwtWithSubAndExp(sub.toString(), Instant.now().plusSeconds(86400));
        when(client.exchange()).thenReturn(new AgentTokenResponseDto(jwt, 86400L));

        String actual = cache.userId();

        assertThat(actual).isEqualTo(sub.toString());
        verify(client, times(1)).exchange();
    }

    @Test
    void second_call_within_validity_window_does_NOT_re_exchange() {
        UUID sub = UUID.randomUUID();
        String jwt = makeJwtWithSubAndExp(sub.toString(), Instant.now().plusSeconds(86400));
        when(client.exchange()).thenReturn(new AgentTokenResponseDto(jwt, 86400L));

        cache.userId();
        cache.userId();
        cache.bearerToken();

        verify(client, times(1)).exchange();
    }

    @Test
    void bearerToken_returns_cached_jwt_byte_for_byte() {
        UUID sub = UUID.randomUUID();
        String jwt = makeJwtWithSubAndExp(sub.toString(), Instant.now().plusSeconds(86400));
        when(client.exchange()).thenReturn(new AgentTokenResponseDto(jwt, 86400L));

        assertThat(cache.bearerToken()).isEqualTo(jwt);
    }

    @Test
    void expiry_within_10_minutes_triggers_re_exchange() {
        UUID sub1 = UUID.randomUUID();
        UUID sub2 = UUID.randomUUID();
        String jwt1 = makeJwtWithSubAndExp(sub1.toString(), Instant.now().plusSeconds(60));
        String jwt2 = makeJwtWithSubAndExp(sub2.toString(), Instant.now().plusSeconds(86400));
        when(client.exchange())
                .thenReturn(new AgentTokenResponseDto(jwt1, 60L))
                .thenReturn(new AgentTokenResponseDto(jwt2, 86400L));

        cache.userId();
        String secondId = cache.userId();

        assertThat(secondId).isEqualTo(sub2.toString());
        verify(client, times(2)).exchange();
    }

    @Test
    void exchange_failure_propagates_and_does_not_cache() {
        when(client.exchange()).thenThrow(new SecurityException("401 from identity-service"));

        assertThatThrownBy(() -> cache.userId()).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> cache.userId()).isInstanceOf(SecurityException.class);
        verify(client, times(2)).exchange();
    }

    @Test
    void extractSubject_decodes_jwt_payload_without_signature_verification() {
        UUID expected = UUID.randomUUID();
        String jwt = makeJwtWithSubAndExp(expected.toString(), Instant.now().plusSeconds(86400));

        assertThat(AgentJwtCache.extractSubject(jwt)).isEqualTo(expected.toString());
    }

    @Test
    void extractSubject_rejects_malformed_jwt() {
        assertThatThrownBy(() -> AgentJwtCache.extractSubject("not.a-jwt"))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * Constructs a JWT-ish string (header.payload.fakesignature) sufficient for
     * AgentJwtCache.extractSubject — signature is intentionally garbage because
     * the gateway is the authoritative validator; mcp-server only decodes claims.
     */
    private static String makeJwtWithSubAndExp(String sub, Instant exp) {
        try {
            ObjectNode header = JSON.createObjectNode();
            header.put("alg", "RS256");
            header.put("typ", "JWT");

            ObjectNode payload = JSON.createObjectNode();
            payload.put("sub", sub);
            payload.put("iat", Instant.now().getEpochSecond());
            payload.put("exp", exp.getEpochSecond());

            String h = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(JSON.writeValueAsBytes(header));
            String p = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(JSON.writeValueAsBytes(payload));
            return h + "." + p + ".fake-signature-not-validated-here";
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
