package com.n11.identity.outbox;

import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the {@code identity.tx} topic exchange (D-12, saga-contracts.md §2).
 * Phase 7's notification-service binds {@code notify.q.user-registered} to this
 * exchange with routing key {@code user.registered}; Phase 3 only declares.
 *
 * <p>NOTE: durable=true so the exchange survives broker restart (matches the
 * existing orders.tx / inventory.tx / payments.tx style from saga-contracts.md §2).
 */
@Configuration
public class IdentityRabbitConfig {

    public static final String EXCHANGE_IDENTITY_TX = "identity.tx";

    @Bean
    public TopicExchange identityExchange() {
        return ExchangeBuilder
                .topicExchange(EXCHANGE_IDENTITY_TX)
                .durable(true)
                .build();
    }
}
