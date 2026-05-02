package com.n11.mcp.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtBearerInterceptorTest {

    private AgentJwtCache cache;
    private JwtBearerInterceptor interceptor;
    private HttpRequest request;
    private ClientHttpRequestExecution execution;
    private ClientHttpResponse response;
    private HttpHeaders headers;

    @BeforeEach
    void setUp() throws IOException {
        cache = mock(AgentJwtCache.class);
        interceptor = new JwtBearerInterceptor(cache);

        request = mock(HttpRequest.class);
        headers = new HttpHeaders();
        when(request.getHeaders()).thenReturn(headers);

        execution = mock(ClientHttpRequestExecution.class);
        response = mock(ClientHttpResponse.class);
        when(execution.execute(same(request), any(byte[].class))).thenReturn(response);
    }

    @Test
    void intercepts_with_bearer_auth_header_and_delegates() throws IOException {
        when(cache.bearerToken()).thenReturn("the.signed.jwt");

        ClientHttpResponse actual = interceptor.intercept(request, new byte[0], execution);

        assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer the.signed.jwt");
        verify(execution).execute(same(request), any(byte[].class));
        assertThat(actual).isSameAs(response);
    }

    @Test
    void cache_failure_propagates_and_does_not_delegate() {
        when(cache.bearerToken()).thenThrow(new SecurityException("identity-service down"));

        assertThatThrownBy(() -> interceptor.intercept(request, new byte[0], execution))
                .isInstanceOf(SecurityException.class);
    }
}
