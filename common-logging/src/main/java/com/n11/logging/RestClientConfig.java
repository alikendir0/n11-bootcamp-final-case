package com.n11.logging;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestClient;

/**
 * Wire #2B of 5: outbound HTTP correlation-ID propagation (builder half).
 *
 * Provides a RestClient.Builder bean with CorrelationIdRestClientInterceptor
 * pre-installed. Consumer services autowire RestClient.Builder and call .build()
 * to get a propagation-aware RestClient.
 *
 * Registered via META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports.
 * The @Import pulls in the helper @Component beans (CorrelationIdRestClientInterceptor,
 * CorrelationIdFilter) explicitly so consumers do NOT need @ComponentScan to cover
 * com.n11.logging — the auto-config is fully self-contained.
 */
@Configuration(proxyBeanMethods = false)
@Import({ CorrelationIdRestClientInterceptor.class, CorrelationIdFilter.class })
public class RestClientConfig {

    @Bean
    public RestClient.Builder correlationIdAwareRestClientBuilder(
            CorrelationIdRestClientInterceptor interceptor) {
        return RestClient.builder().requestInterceptor(interceptor);
    }
}
