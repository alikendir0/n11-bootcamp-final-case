package com.n11.inventory.messaging;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology for inventory-service (saga-contracts.md §2, §3, §5.2).
 *
 * <p>Topology:
 * <ul>
 *   <li>{@code inventory.tx} — durable topic exchange (inventory-service is the producer)</li>
 *   <li>{@code orders.tx} — durable topic exchange (idempotent re-declaration; order-service owns it in Phase 5)</li>
 *   <li>{@code inventory.q.order-created} — bound to orders.tx/order.created; DLQ configured</li>
 *   <li>{@code inventory.q.order-created.dlq} — receives messages after 3 failed deliveries</li>
 * </ul>
 */
@Configuration
public class InventoryRabbitConfig {

    public static final String EXCHANGE_INVENTORY_TX  = "inventory.tx";
    public static final String EXCHANGE_ORDERS_TX     = "orders.tx";
    public static final String QUEUE_INVENTORY_ORDERS = "inventory.q.order-created";
    public static final String DLQ_INVENTORY_ORDERS   = "inventory.q.order-created.dlq";

    @Bean
    public TopicExchange inventoryExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE_INVENTORY_TX).durable(true).build();
    }

    /**
     * Idempotent re-declaration of orders.tx. This exchange is owned by order-service
     * (Phase 5); declaring it here ensures the queue can bind before order-service starts.
     */
    @Bean
    public TopicExchange ordersExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE_ORDERS_TX).durable(true).build();
    }

    @Bean
    public Queue orderCreatedQueue() {
        return QueueBuilder.durable(QUEUE_INVENTORY_ORDERS)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", DLQ_INVENTORY_ORDERS)
                .build();
    }

    @Bean
    public Queue orderCreatedDlq() {
        return QueueBuilder.durable(DLQ_INVENTORY_ORDERS).build();
    }

    @Bean
    public Binding orderCreatedBinding(Queue orderCreatedQueue, TopicExchange ordersExchange) {
        return BindingBuilder.bind(orderCreatedQueue).to(ordersExchange).with("order.created");
    }
}
