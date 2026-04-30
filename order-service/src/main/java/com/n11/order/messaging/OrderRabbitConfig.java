package com.n11.order.messaging;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology for order-service (saga-contracts.md §2 + Phase 5 D-08 + CD-08 + CD-09).
 *
 * Owns {@code orders.tx} as producer; consumes from {@code inventory.tx} (stock.reserved,
 * stock.reserve_failed) and {@code payments.tx} (payment.completed, payment.failed).
 */
@Configuration
public class OrderRabbitConfig {

    public static final String EXCHANGE_ORDERS_TX     = "orders.tx";
    public static final String EXCHANGE_INVENTORY_TX  = "inventory.tx";
    public static final String EXCHANGE_PAYMENTS_TX   = "payments.tx";

    public static final String QUEUE_ORDER_STOCK_RESERVED    = "order.q.stock-reserved";
    public static final String QUEUE_ORDER_STOCK_FAILED      = "order.q.stock-failed";
    public static final String QUEUE_ORDER_PAYMENT_COMPLETED = "order.q.payment-completed";
    public static final String QUEUE_ORDER_PAYMENT_FAILED    = "order.q.payment-failed";

    public static final String DLQ_STOCK_RESERVED    = "order.q.stock-reserved.dlq";
    public static final String DLQ_STOCK_FAILED      = "order.q.stock-failed.dlq";
    public static final String DLQ_PAYMENT_COMPLETED = "order.q.payment-completed.dlq";
    public static final String DLQ_PAYMENT_FAILED    = "order.q.payment-failed.dlq";

    @Bean public TopicExchange ordersExchangeForOrder()    { return ExchangeBuilder.topicExchange(EXCHANGE_ORDERS_TX).durable(true).build(); }
    @Bean public TopicExchange inventoryExchangeForOrder() { return ExchangeBuilder.topicExchange(EXCHANGE_INVENTORY_TX).durable(true).build(); }
    @Bean public TopicExchange paymentsExchangeForOrder()  { return ExchangeBuilder.topicExchange(EXCHANGE_PAYMENTS_TX).durable(true).build(); }

    @Bean public Queue qStockReserved()    { return queue(QUEUE_ORDER_STOCK_RESERVED, DLQ_STOCK_RESERVED); }
    @Bean public Queue qStockFailed()      { return queue(QUEUE_ORDER_STOCK_FAILED, DLQ_STOCK_FAILED); }
    @Bean public Queue qPaymentCompleted() { return queue(QUEUE_ORDER_PAYMENT_COMPLETED, DLQ_PAYMENT_COMPLETED); }
    @Bean public Queue qPaymentFailed()    { return queue(QUEUE_ORDER_PAYMENT_FAILED, DLQ_PAYMENT_FAILED); }

    @Bean public Queue dlqStockReserved()    { return QueueBuilder.durable(DLQ_STOCK_RESERVED).build(); }
    @Bean public Queue dlqStockFailed()      { return QueueBuilder.durable(DLQ_STOCK_FAILED).build(); }
    @Bean public Queue dlqPaymentCompleted() { return QueueBuilder.durable(DLQ_PAYMENT_COMPLETED).build(); }
    @Bean public Queue dlqPaymentFailed()    { return QueueBuilder.durable(DLQ_PAYMENT_FAILED).build(); }

    @Bean public Binding bStockReserved(Queue qStockReserved, TopicExchange inventoryExchangeForOrder) {
        return BindingBuilder.bind(qStockReserved).to(inventoryExchangeForOrder).with("stock.reserved");
    }
    @Bean public Binding bStockFailed(Queue qStockFailed, TopicExchange inventoryExchangeForOrder) {
        return BindingBuilder.bind(qStockFailed).to(inventoryExchangeForOrder).with("stock.reserve_failed");
    }
    @Bean public Binding bPaymentCompleted(Queue qPaymentCompleted, TopicExchange paymentsExchangeForOrder) {
        return BindingBuilder.bind(qPaymentCompleted).to(paymentsExchangeForOrder).with("payment.completed");
    }
    @Bean public Binding bPaymentFailed(Queue qPaymentFailed, TopicExchange paymentsExchangeForOrder) {
        return BindingBuilder.bind(qPaymentFailed).to(paymentsExchangeForOrder).with("payment.failed");
    }

    private static Queue queue(String name, String dlq) {
        return QueueBuilder.durable(name)
            .withArgument("x-dead-letter-exchange", "")
            .withArgument("x-dead-letter-routing-key", dlq)
            .build();
    }
}
