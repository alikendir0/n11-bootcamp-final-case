package com.n11.order.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.n11.events.Envelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Component
public class StockReservedConsumer {
    private static final Logger LOG = LoggerFactory.getLogger(StockReservedConsumer.class);
    private final OrderSagaService orderSagaService;
    private final ObjectMapper objectMapper;

    public StockReservedConsumer(OrderSagaService orderSagaService, ObjectMapper objectMapper) {
        this.orderSagaService = orderSagaService; this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = OrderRabbitConfig.QUEUE_ORDER_STOCK_RESERVED)
    public void handleStockReserved(Message amqpMessage) {
        Envelope envelope = parseEnvelope(amqpMessage, objectMapper, "stock.reserved");
        UUID eventId = parseEventId(envelope);
        StockReservedPayload payload = parsePayload(envelope, StockReservedPayload.class, objectMapper, eventId);
        orderSagaService.processStockReserved(eventId, envelope, payload);
    }

    public record StockReservedPayload(UUID orderId, UUID reservationId, List<Item> reservedItems) {
        public record Item(UUID productId, int qty) {}
    }

    // Shared helpers — package-private static methods reused by all consumers in this package
    static Envelope parseEnvelope(Message m, ObjectMapper om, String expected) {
        String body = new String(m.getBody(), StandardCharsets.UTF_8);
        try { return om.readValue(body, Envelope.class); }
        catch (Exception e) {
            LOG.error("order.consumer({}): malformed envelope. body={}", expected, body, e);
            throw new AmqpRejectAndDontRequeueException("Malformed saga envelope", e);
        }
    }

    static UUID parseEventId(Envelope e) {
        try { return UUID.fromString(e.eventId()); }
        catch (Exception ex) {
            LOG.error("order.consumer: invalid eventId '{}'", e.eventId(), ex);
            throw new AmqpRejectAndDontRequeueException("Invalid eventId: " + e.eventId(), ex);
        }
    }

    static <T> T parsePayload(Envelope envelope, Class<T> type, ObjectMapper om, UUID eventId) {
        if (envelope.payload() == null || envelope.payload().isNull()) {
            LOG.error("order.consumer: null payload for event {}", eventId);
            throw new AmqpRejectAndDontRequeueException("Null payload for event " + eventId);
        }
        try {
            T p = om.treeToValue(envelope.payload(), type);
            if (p == null) throw new AmqpRejectAndDontRequeueException("Deserialized payload is null for event " + eventId);
            return p;
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            LOG.error("order.consumer: cannot parse payload for event {}", eventId, ex);
            throw new AmqpRejectAndDontRequeueException("Cannot deserialize payload for event " + eventId, ex);
        }
    }
}
