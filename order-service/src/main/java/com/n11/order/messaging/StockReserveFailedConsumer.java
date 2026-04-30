package com.n11.order.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.n11.events.Envelope;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

import static com.n11.order.messaging.StockReservedConsumer.*;

@Component
public class StockReserveFailedConsumer {
    private final OrderSagaService orderSagaService;
    private final ObjectMapper objectMapper;

    public StockReserveFailedConsumer(OrderSagaService orderSagaService, ObjectMapper objectMapper) {
        this.orderSagaService = orderSagaService; this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = OrderRabbitConfig.QUEUE_ORDER_STOCK_FAILED)
    public void handleStockReserveFailed(Message amqpMessage) {
        Envelope envelope = parseEnvelope(amqpMessage, objectMapper, "stock.reserve_failed");
        UUID eventId = parseEventId(envelope);
        StockReserveFailedPayload payload = parsePayload(envelope, StockReserveFailedPayload.class, objectMapper, eventId);
        orderSagaService.processStockReserveFailed(eventId, envelope, payload);
    }

    public record StockReserveFailedPayload(UUID orderId, String reason, List<FailedItem> failedItems) {
        public record FailedItem(UUID productId, int requestedQty, int availableQty) {}
    }
}
