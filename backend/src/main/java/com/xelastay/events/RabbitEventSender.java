package com.xelastay.events;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Publica en {@link EventTopology#EXCHANGE} con el tipo de evento como clave de enrutamiento y espera
 * la confirmación del broker (publisher confirms). Si no llega, lanza excepción y el evento queda FAILED.
 */
@Component
@ConditionalOnProperty(prefix = "xelastay.events", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RabbitEventSender implements EventSender {

    private static final long CONFIRM_TIMEOUT_MS = 5_000;

    private final RabbitTemplate rabbitTemplate;

    public RabbitEventSender(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void send(OutboxEvent event) {
        EventEnvelope envelope = EventEnvelope.from(event);
        rabbitTemplate.invoke(operations -> {
            operations.convertAndSend(EventTopology.EXCHANGE, event.getEventType(), envelope, message -> {
                message.getMessageProperties().setMessageId(event.getEventKey());
                message.getMessageProperties().setType(event.getEventType());
                return message;
            });
            operations.waitForConfirmsOrDie(CONFIRM_TIMEOUT_MS);
            return null;
        });
    }
}
