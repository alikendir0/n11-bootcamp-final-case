package com.n11.product.config;

import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the {@code products.tx} topic exchange (saga-contracts.md §2).
 * Phase 4 declares only the exchange — no producers run in this phase.
 * Phase 8's search-service binds {@code search.q.product-events} with routing key
 * pattern {@code product.*}; until then RabbitMQ retains the exchange harmlessly.
 *
 * <p>Idempotent: the @Bean declaration is safe to re-apply; RabbitMQ's
 * exchangeDeclare is idempotent for matching parameters (durable=true topic).
 */
@Configuration
public class ProductRabbitConfig {

    public static final String EXCHANGE_PRODUCTS_TX = "products.tx";

    @Bean
    public TopicExchange productsExchange() {
        return ExchangeBuilder
                .topicExchange(EXCHANGE_PRODUCTS_TX)
                .durable(true)
                .build();
    }
}
