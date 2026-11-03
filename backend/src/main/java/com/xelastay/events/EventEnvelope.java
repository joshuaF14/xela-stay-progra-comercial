package com.xelastay.events;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Mensaje que viaja por RabbitMQ (JSON). Los consumidores usan {@code eventKey} para ser idempotentes.
 */
public record EventEnvelope(
        UUID eventId,
        String eventKey,
        String eventType,
        String aggregateType,
        UUID aggregateId,
        Instant occurredAt,
        Map<String, Object> payload) {

    public static EventEnvelope from(OutboxEvent event) {
        return new EventEnvelope(
                event.getId(),
                event.getEventKey(),
                event.getEventType(),
                event.getAggregateType(),
                event.getAggregateId(),
                event.getCreatedAt(),
                event.getPayload());
    }
}
