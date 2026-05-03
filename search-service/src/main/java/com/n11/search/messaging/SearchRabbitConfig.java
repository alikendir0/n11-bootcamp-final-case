package com.n11.search.messaging;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the search-service queue and binding for product events (saga-contracts.md §2).
 */
@Configuration
public class SearchRabbitConfig {

    public static final String EXCHANGE_PRODUCTS_TX = "products.tx";
    public static final String QUEUE_PRODUCT_EVENTS = "search.q.product-events";
    public static final String ROUTING_KEY_PRODUCT_ANY = "product.*";

    @Bean
    public TopicExchange productsExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE_PRODUCTS_TX).durable(true).build();
    }

    @Bean
    public Queue productEventsQueue() {
        return QueueBuilder.durable(QUEUE_PRODUCT_EVENTS).build();
    }

    @Bean
    public Binding productEventsBinding(Queue productEventsQueue, TopicExchange productsExchange) {
        return BindingBuilder.bind(productEventsQueue).to(productsExchange).with(ROUTING_KEY_PRODUCT_ANY);
    }
}
