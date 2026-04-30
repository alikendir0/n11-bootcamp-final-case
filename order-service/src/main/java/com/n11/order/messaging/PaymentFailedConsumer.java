package com.n11.order.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.n11.events.Envelope;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

import static com.n11.order.messaging.StockReservedConsumer.*;

@Component
public class PaymentFailedConsumer {
    private final OrderSagaService orderSagaService;
    private final ObjectMapper objectMapper;

    public PaymentFailedConsumer(OrderSagaService orderSagaService, ObjectMapper objectMapper) {
        this.orderSagaService = orderSagaService; this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = OrderRabbitConfig.QUEUE_ORDER_PAYMENT_FAILED)
    public void handlePaymentFailed(Message amqpMessage) {
        Envelope envelope = parseEnvelope(amqpMessage, objectMapper, "payment.failed");
        UUID eventId = parseEventId(envelope);
        PaymentFailedPayload payload = parsePayload(envelope, PaymentFailedPayload.class, objectMapper, eventId);
        orderSagaService.processPaymentFailed(eventId, envelope, payload);
    }

    public record PaymentFailedPayload(UUID orderId, UUID paymentId, String reason, String errorCode) {}
}
