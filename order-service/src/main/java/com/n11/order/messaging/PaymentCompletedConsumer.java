package com.n11.order.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.n11.events.Envelope;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

import static com.n11.order.messaging.StockReservedConsumer.*;

@Component
public class PaymentCompletedConsumer {
    private final OrderSagaService orderSagaService;
    private final ObjectMapper objectMapper;

    public PaymentCompletedConsumer(OrderSagaService orderSagaService, ObjectMapper objectMapper) {
        this.orderSagaService = orderSagaService; this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = OrderRabbitConfig.QUEUE_ORDER_PAYMENT_COMPLETED)
    public void handlePaymentCompleted(Message amqpMessage) {
        Envelope envelope = parseEnvelope(amqpMessage, objectMapper, "payment.completed");
        UUID eventId = parseEventId(envelope);
        PaymentCompletedPayload payload = parsePayload(envelope, PaymentCompletedPayload.class, objectMapper, eventId);
        orderSagaService.processPaymentCompleted(eventId, envelope, payload);
    }

    public record PaymentCompletedPayload(UUID orderId, UUID paymentId, String iyzicoPaymentId, BigDecimal amount, String currency) {}
}
