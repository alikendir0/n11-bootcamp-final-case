package com.n11.logging;

import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Wire #2A of 5: outbound HTTP correlation-ID propagation (interceptor half).
 *
 * Reads the current MDC correlationId and adds it as X-Correlation-Id on
 * outgoing RestClient requests. Used by RestClientConfig.
 */
@Component
public class CorrelationIdRestClientInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest req, byte[] body,
                                        ClientHttpRequestExecution exec) throws IOException {
        String cid = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (cid != null && !cid.isBlank()) {
            req.getHeaders().add(CorrelationIdFilter.HEADER, cid);
        }
        return exec.execute(req, body);
    }
}
