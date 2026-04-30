package com.n11.payment.messaging;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology for payment-service (saga-contracts.md §2, §3).
 *
 * <p>Topology:
 * <ul>
 *   <li>{@code inventory.tx} — idempotent re-declaration; payment-service is a consumer here</li>
 *   <li>{@code payments.tx} — durable topic exchange; payment-service is the producer</li>
 *   <li>{@code payment.q.stock-reserved} — bound to inventory.tx/stock.reserved; DLQ configured</li>
 *   <li>{@code payment.q.stock-reserved.dlq} — receives messages after 3 failed deliveries</li>
 * </ul>
 */
@Configuration
public class PaymentRabbitConfig {

    public static final String EXCHANGE_INVENTORY_TX         = "inventory.tx";
    public static final String EXCHANGE_PAYMENTS_TX          = "payments.tx";
    public static final String QUEUE_PAYMENT_STOCK_RESERVED  = "payment.q.stock-reserved";
    public static final String DLQ_PAYMENT_STOCK_RESERVED    = "payment.q.stock-reserved.dlq";

    /** Idempotent re-declaration of inventory.tx (owned by inventory-service). */
    @Bean
    public TopicExchange inventoryExchangeForPayment() {
        return ExchangeBuilder.topicExchange(EXCHANGE_INVENTORY_TX).durable(true).build();
    }

    /** payments.tx — payment-service is the sole producer. */
    @Bean
    public TopicExchange paymentsExchangeForPayment() {
        return ExchangeBuilder.topicExchange(EXCHANGE_PAYMENTS_TX).durable(true).build();
    }

    @Bean
    public Queue qStockReserved() {
        return QueueBuilder.durable(QUEUE_PAYMENT_STOCK_RESERVED)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", DLQ_PAYMENT_STOCK_RESERVED)
                .build();
    }

    @Bean
    public Queue dlqStockReserved() {
        return QueueBuilder.durable(DLQ_PAYMENT_STOCK_RESERVED).build();
    }

    @Bean
    public Binding bStockReserved(Queue qStockReserved, TopicExchange inventoryExchangeForPayment) {
        return BindingBuilder.bind(qStockReserved).to(inventoryExchangeForPayment).with("stock.reserved");
    }
}
