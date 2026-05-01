package com.n11.agent.http;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Provides a default RestClient.Builder if no parent module supplies one.
 * In ai-service / mcp-server, common-logging.RestClientConfig contributes a
 * pre-wired Builder with CorrelationIdRestClientInterceptor — that wins by
 * @ConditionalOnMissingBean.
 */
@Configuration
public class ToolHttpClients {

    @Bean
    @ConditionalOnMissingBean
    RestClient.Builder toolRestClientBuilder() {
        return RestClient.builder();
    }
}
