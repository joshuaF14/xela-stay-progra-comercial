package com.xelastay.events;

import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

/**
 * Escribe eventos en outbox_events. Solo funciona dentro de una transacción ya abierta
 * (Propagation.MANDATORY): así el evento se guarda o se descarta junto con el cambio de negocio,
 * y nunca se publica algo que no ocurrió.
 */
@Service
public class OutboxService {

    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<Map<String, Object>>() {
    };

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * Registra un evento para publicarlo después del commit.
     *
     * @param eventKey      clave única del evento concreto, p. ej. {@code booking:<id>:CONFIRMED} (ver {@link #eventKey})
     * @param aggregateType tipo de entidad, p. ej. {@link EventTypes#BOOKING_AGGREGATE}
     * @param aggregateId   ID de la entidad
     * @param eventType     tipo de evento y clave de enrutamiento, p. ej. {@link EventTypes#BOOKING_CONFIRMED}
     * @param payload       objeto que se guarda como JSON (p. ej. {@link BookingEventPayload})
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent enqueue(String eventKey, String aggregateType, UUID aggregateId, String eventType,
            Object payload) {
        Assert.hasText(eventKey, "eventKey es obligatorio");
        Assert.hasText(aggregateType, "aggregateType es obligatorio");
        Assert.notNull(aggregateId, "aggregateId es obligatorio");
        Assert.hasText(eventType, "eventType es obligatorio");
        Assert.notNull(payload, "payload es obligatorio");

        Map<String, Object> body = objectMapper.convertValue(payload, PAYLOAD_TYPE);
        return repository.save(OutboxEvent.pending(eventKey, aggregateType, aggregateId, eventType, body));
    }

    /** Clave de deduplicación con el formato {@code <aggregateType>:<id>:<ACCION>}. */
    public static String eventKey(String aggregateType, UUID aggregateId, String action) {
        return aggregateType + ":" + aggregateId + ":" + action;
    }
}
