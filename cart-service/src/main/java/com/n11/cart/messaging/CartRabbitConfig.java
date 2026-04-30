package com.n11.cart.messaging;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology for cart-service (saga-contracts.md §2 + D-07).
 *
 * <p>Topology:
 * <ul>
 *   <li>{@code orders.tx} — durable topic exchange (idempotent re-declaration; order-service owns it)</li>
 *   <li>{@code cart.q.order-confirmed} — bound to orders.tx/order.confirmed; DLQ configured</li>
 *   <li>{@code cart.q.order-confirmed.dlq} — receives messages after 3 failed deliveries</li>
 * </ul>
 */
@Configuration
public class CartRabbitConfig {

    public static final String EXCHANGE_ORDERS_TX         = "orders.tx";
    public static final String QUEUE_CART_ORDER_CONFIRMED  = "cart.q.order-confirmed";
    public static final String DLQ_CART_ORDER_CONFIRMED    = "cart.q.order-confirmed.dlq";
    public static final String ROUTING_KEY                 = "order.confirmed";

    /**
     * Idempotent re-declaration of orders.tx. This exchange is owned by order-service (Phase 5 Plan 03);
     * declaring it here ensures the queue can bind before order-service starts.
     * Bean name {@code ordersExchangeForCart} is unique-per-service to avoid clashes
     * with inventory-service's {@code ordersExchange} bean (each runs in its own application context).
     */
    @Bean
    public TopicExchange ordersExchangeForCart() {
        return ExchangeBuilder.topicExchange(EXCHANGE_ORDERS_TX).durable(true).build();
    }

    @Bean
    public Queue orderConfirmedQueue() {
        return QueueBuilder.durable(QUEUE_CART_ORDER_CONFIRMED)
            .withArgument("x-dead-letter-exchange", "")
            .withArgument("x-dead-letter-routing-key", DLQ_CART_ORDER_CONFIRMED)
            .build();
    }

    @Bean
    public Queue orderConfirmedDlq() {
        return QueueBuilder.durable(DLQ_CART_ORDER_CONFIRMED).build();
    }

    @Bean
    public Binding orderConfirmedBinding(Queue orderConfirmedQueue, TopicExchange ordersExchangeForCart) {
        return BindingBuilder.bind(orderConfirmedQueue).to(ordersExchangeForCart).with(ROUTING_KEY);
    }
}
