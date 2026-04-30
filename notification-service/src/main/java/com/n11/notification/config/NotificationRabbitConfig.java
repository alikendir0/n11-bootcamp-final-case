package com.n11.notification.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology for notification-service (saga-contracts.md §2, §3, §7).
 * 4 main queues × {orders.tx, payments.tx, identity.tx} re-declared idempotently;
 * each main queue has a paired *.dlq via x-dead-letter-exchange="".
 * Bean names suffixed {@code *ForNotification} to disambiguate from inventory-service /
 * cart-service exchange beans when infra-tests loads multiple services on the same
 * classpath (Plan 05-04 STATE.md decision).
 *
 * <p>Topology matrix (saga-contracts.md §2):
 * <ul>
 *   <li>{@code notify.q.order-confirmed} — orders.tx / order.confirmed — order-service producer</li>
 *   <li>{@code notify.q.order-cancelled} — orders.tx / order.cancelled — order-service producer</li>
 *   <li>{@code notify.q.payment-failed}  — payments.tx / payment.failed — payment-service producer</li>
 *   <li>{@code notify.q.user-registered} — identity.tx / user.registered — identity-service producer</li>
 * </ul>
 *
 * <p>Idempotent re-declarations of exchanges owned by other services prevent bind failure
 * when notification-service starts before order-service / payment-service / identity-service
 * (saga-contracts.md §3, Pitfall #5 per RESEARCH.md §Common Pitfalls).
 */
@Configuration
public class NotificationRabbitConfig {

    // ---- Queue name constants (saga-contracts.md §2) ----

    public static final String QUEUE_NOTIFY_ORDER_CONFIRMED  = "notify.q.order-confirmed";
    public static final String QUEUE_NOTIFY_ORDER_CANCELLED  = "notify.q.order-cancelled";
    public static final String QUEUE_NOTIFY_PAYMENT_FAILED   = "notify.q.payment-failed";
    public static final String QUEUE_NOTIFY_USER_REGISTERED  = "notify.q.user-registered";

    // DLQ name constants derived from main queue names
    private static final String DLQ_NOTIFY_ORDER_CONFIRMED  = QUEUE_NOTIFY_ORDER_CONFIRMED + ".dlq";
    private static final String DLQ_NOTIFY_ORDER_CANCELLED  = QUEUE_NOTIFY_ORDER_CANCELLED + ".dlq";
    private static final String DLQ_NOTIFY_PAYMENT_FAILED   = QUEUE_NOTIFY_PAYMENT_FAILED  + ".dlq";
    private static final String DLQ_NOTIFY_USER_REGISTERED  = QUEUE_NOTIFY_USER_REGISTERED + ".dlq";

    // ---- Idempotent exchange re-declarations (service-prefixed bean names per Plan 05-04) ----

    /**
     * Idempotent re-declaration of orders.tx (owned by order-service).
     * Bean name {@code ordersExchangeForNotification} avoids clash with
     * {@code ordersExchange()} in InventoryRabbitConfig and
     * {@code ordersExchangeForCart()} in CartRabbitConfig.
     */
    @Bean
    public TopicExchange ordersExchangeForNotification() {
        return ExchangeBuilder.topicExchange("orders.tx").durable(true).build();
    }

    /**
     * Idempotent re-declaration of payments.tx (owned by payment-service).
     * Bean name {@code paymentsExchangeForNotification} avoids clash with
     * {@code paymentsExchange()} in InventoryRabbitConfig.
     */
    @Bean
    public TopicExchange paymentsExchangeForNotification() {
        return ExchangeBuilder.topicExchange("payments.tx").durable(true).build();
    }

    /**
     * Idempotent re-declaration of identity.tx (owned by identity-service).
     * Required to allow notify.q.user-registered to bind before identity-service starts
     * (RESEARCH.md Pitfall #5).
     */
    @Bean
    public TopicExchange identityExchangeForNotification() {
        return ExchangeBuilder.topicExchange("identity.tx").durable(true).build();
    }

    // ---- notify.q.order-confirmed (orders.tx / order.confirmed) ----

    @Bean
    public Queue notifyOrderConfirmedQueue() {
        return QueueBuilder.durable(QUEUE_NOTIFY_ORDER_CONFIRMED)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", DLQ_NOTIFY_ORDER_CONFIRMED)
                .build();
    }

    @Bean
    public Queue notifyOrderConfirmedDlq() {
        return QueueBuilder.durable(DLQ_NOTIFY_ORDER_CONFIRMED).build();
    }

    @Bean
    public Binding notifyOrderConfirmedBinding(Queue notifyOrderConfirmedQueue,
                                               TopicExchange ordersExchangeForNotification) {
        return BindingBuilder.bind(notifyOrderConfirmedQueue)
                .to(ordersExchangeForNotification).with("order.confirmed");
    }

    // ---- notify.q.order-cancelled (orders.tx / order.cancelled) ----

    @Bean
    public Queue notifyOrderCancelledQueue() {
        return QueueBuilder.durable(QUEUE_NOTIFY_ORDER_CANCELLED)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", DLQ_NOTIFY_ORDER_CANCELLED)
                .build();
    }

    @Bean
    public Queue notifyOrderCancelledDlq() {
        return QueueBuilder.durable(DLQ_NOTIFY_ORDER_CANCELLED).build();
    }

    @Bean
    public Binding notifyOrderCancelledBinding(Queue notifyOrderCancelledQueue,
                                               TopicExchange ordersExchangeForNotification) {
        return BindingBuilder.bind(notifyOrderCancelledQueue)
                .to(ordersExchangeForNotification).with("order.cancelled");
    }

    // ---- notify.q.payment-failed (payments.tx / payment.failed) ----

    @Bean
    public Queue notifyPaymentFailedQueue() {
        return QueueBuilder.durable(QUEUE_NOTIFY_PAYMENT_FAILED)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", DLQ_NOTIFY_PAYMENT_FAILED)
                .build();
    }

    @Bean
    public Queue notifyPaymentFailedDlq() {
        return QueueBuilder.durable(DLQ_NOTIFY_PAYMENT_FAILED).build();
    }

    @Bean
    public Binding notifyPaymentFailedBinding(Queue notifyPaymentFailedQueue,
                                              TopicExchange paymentsExchangeForNotification) {
        return BindingBuilder.bind(notifyPaymentFailedQueue)
                .to(paymentsExchangeForNotification).with("payment.failed");
    }

    // ---- notify.q.user-registered (identity.tx / user.registered) ----

    @Bean
    public Queue notifyUserRegisteredQueue() {
        return QueueBuilder.durable(QUEUE_NOTIFY_USER_REGISTERED)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", DLQ_NOTIFY_USER_REGISTERED)
                .build();
    }

    @Bean
    public Queue notifyUserRegisteredDlq() {
        return QueueBuilder.durable(DLQ_NOTIFY_USER_REGISTERED).build();
    }

    @Bean
    public Binding notifyUserRegisteredBinding(Queue notifyUserRegisteredQueue,
                                               TopicExchange identityExchangeForNotification) {
        return BindingBuilder.bind(notifyUserRegisteredQueue)
                .to(identityExchangeForNotification).with("user.registered");
    }
}
