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
 *   <li>{@code orders.tx} — durable topic exchange (idempotent re-declaration; order-service owns it)</li>
 *   <li>{@code payments.tx} — durable topic exchange (idempotent re-declaration; payment-service owns it)</li>
 *   <li>{@code inventory.q.order-created} — bound to orders.tx/order.created; DLQ configured</li>
 *   <li>{@code inventory.q.payment-failed} — bound to payments.tx/payment.failed (CD-08 compensation)</li>
 *   <li>{@code inventory.q.order-cancelled} — bound to orders.tx/order.cancelled (CD-09 compensation)</li>
 * </ul>
 */
@Configuration
public class InventoryRabbitConfig {

    public static final String EXCHANGE_INVENTORY_TX        = "inventory.tx";
    public static final String EXCHANGE_ORDERS_TX           = "orders.tx";
    public static final String EXCHANGE_PAYMENTS_TX         = "payments.tx";
    public static final String QUEUE_INVENTORY_ORDERS       = "inventory.q.order-created";
    public static final String DLQ_INVENTORY_ORDERS         = "inventory.q.order-created.dlq";
    public static final String QUEUE_INV_PAYMENT_FAILED     = "inventory.q.payment-failed";
    public static final String DLQ_INV_PAYMENT_FAILED       = "inventory.q.payment-failed.dlq";
    public static final String QUEUE_INV_ORDER_CANCELLED    = "inventory.q.order-cancelled";
    public static final String DLQ_INV_ORDER_CANCELLED      = "inventory.q.order-cancelled.dlq";

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

    /**
     * Idempotent re-declaration of payments.tx. This exchange is owned by payment-service
     * (Phase 5); declaring it here ensures inventory can subscribe for CD-08 compensation.
     */
    @Bean
    public TopicExchange paymentsExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE_PAYMENTS_TX).durable(true).build();
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

    // ---- CD-08: inventory.q.payment-failed (compensation — release stock on payment failure) ----

    @Bean
    public Queue paymentFailedQueue() {
        return QueueBuilder.durable(QUEUE_INV_PAYMENT_FAILED)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", DLQ_INV_PAYMENT_FAILED)
                .build();
    }

    @Bean
    public Queue paymentFailedDlq() {
        return QueueBuilder.durable(DLQ_INV_PAYMENT_FAILED).build();
    }

    @Bean
    public Binding paymentFailedBinding(Queue paymentFailedQueue, TopicExchange paymentsExchange) {
        return BindingBuilder.bind(paymentFailedQueue).to(paymentsExchange).with("payment.failed");
    }

    // ---- CD-09: inventory.q.order-cancelled (compensation — release stock on user cancel) ----

    @Bean
    public Queue orderCancelledQueue() {
        return QueueBuilder.durable(QUEUE_INV_ORDER_CANCELLED)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", DLQ_INV_ORDER_CANCELLED)
                .build();
    }

    @Bean
    public Queue orderCancelledDlq() {
        return QueueBuilder.durable(DLQ_INV_ORDER_CANCELLED).build();
    }

    @Bean
    public Binding orderCancelledBinding(Queue orderCancelledQueue, TopicExchange ordersExchange) {
        return BindingBuilder.bind(orderCancelledQueue).to(ordersExchange).with("order.cancelled");
    }
}
