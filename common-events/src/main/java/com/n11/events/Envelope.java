package com.n11.events;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

/**
 * Saga event envelope per ARCHITECTURE.md §3.4 and .planning/saga-contracts.md §1.
 *
 * Every saga event published on RabbitMQ is wrapped in this 8-field envelope.
 * The corresponding JSON-Schema is /saga-schemas/envelope.schema.json (copied
 * from .planning/saga-contracts/envelope.schema.json into the classpath at
 * build time so AbstractEventSchemaTest can validate against it).
 *
 * @param eventId        unique per event — primary idempotency key
 * @param eventType      dotted (e.g. "order.created", "stock.reserved")
 * @param eventVersion   currently 1 for all events
 * @param occurredAt     UTC RFC3339 timestamp of the business event
 * @param correlationId  saga-wide; equals the orderId of the saga-initiating event
 * @param causationId    eventId of the event that caused this one; null on saga roots
 * @param producer       service name (e.g. "order-service")
 * @param payload        type-specific payload — schema lives in saga-schemas/{eventType}.schema.json
 */
public record Envelope(
    String eventId,
    String eventType,
    int eventVersion,
    Instant occurredAt,
    String correlationId,
    String causationId,
    String producer,
    JsonNode payload
) {}
