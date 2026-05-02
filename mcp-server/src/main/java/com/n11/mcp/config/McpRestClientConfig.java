package com.n11.mcp.config;

import com.n11.mcp.auth.JwtBearerInterceptor;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

/**
 * Phase 9 (D-11). Two RestClient.Builder beans, both with @LoadBalanced for
 * Spring Cloud LoadBalancer (Eureka lb:// resolution):
 *
 * <ol>
 *   <li>{@link #toolRestClientBuilder} — DEFAULT. Carries the
 *       {@link JwtBearerInterceptor}. Used by agent-toolset's ToolHttpClients
 *       for outbound tool calls to cart/order/product/payment services. Marked
 *       {@code @Primary} so agent-toolset's @ConditionalOnMissingBean Builder
 *       fallback is skipped at startup.</li>
 *   <li>{@link #loadBalancedRestClientBuilder} — for AgentJwtClient. NO
 *       JwtBearerInterceptor (would form a cycle: cache → client → interceptor
 *       → cache). Distinguished by explicit @Qualifier on AgentJwtClient.</li>
 * </ol>
 *
 * <p><b>Cycle prevention:</b> AgentJwtClient receives the un-intercepted builder
 * by explicit {@code @Qualifier("loadBalancedRestClientBuilder")}. The tool
 * builder is the primary candidate for agent-toolset clients and carries the
 * cached JWT bearer interceptor.
 */
@Configuration
public class McpRestClientConfig {

    /**
     * common-logging auto-config also contributes a @Primary RestClient.Builder.
     * mcp-server needs exactly one primary builder: the tool builder carrying the
     * JWT interceptor. Normalize primaries at bean-definition time so generic
     * agent-toolset constructor injection is deterministic while AgentJwtClient
     * still receives the un-intercepted builder by explicit qualifier.
     */
    @Bean
    static BeanFactoryPostProcessor restClientBuilderPrimaryNormalizer() {
        return beanFactory -> {
            if (beanFactory.containsBeanDefinition("correlationIdAwareRestClientBuilder")) {
                beanFactory.getBeanDefinition("correlationIdAwareRestClientBuilder").setPrimary(false);
            }
            if (beanFactory.containsBeanDefinition("loadBalancedRestClientBuilder")) {
                beanFactory.getBeanDefinition("loadBalancedRestClientBuilder").setPrimary(false);
            }
            if (beanFactory.containsBeanDefinition("toolRestClientBuilder")) {
                beanFactory.getBeanDefinition("toolRestClientBuilder").setPrimary(true);
            }
        };
    }

    /**
     * Builder used by agent-toolset HTTP clients. Carries JwtBearerInterceptor.
     */
    @Bean
    @Primary
    @LoadBalanced
    public RestClient.Builder toolRestClientBuilder(JwtBearerInterceptor jwtBearerInterceptor) {
        return RestClient.builder()
                .requestInterceptor(jwtBearerInterceptor);
    }

    /**
     * Builder used ONLY by AgentJwtClient. NO JwtBearerInterceptor.
     */
    @Bean
    @LoadBalanced
    public RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }
}
