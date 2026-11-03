package com.xelastay.events;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Publica periódicamente los eventos PENDING/FAILED del outbox.
 * <p>
 * Cada ciclo abre una transacción, bloquea un lote con FOR UPDATE SKIP LOCKED, lo envía a RabbitMQ y marca
 * cada evento como PUBLISHED, o suma un intento y lo marca FAILED. Si RabbitMQ no está disponible, corta el
 * lote sin gastar intentos. La entrega es "al menos una vez": los consumidores deben ser idempotentes.
 */
@Component
@ConditionalOnProperty(prefix = "xelastay.events", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository repository;
    private final EventSender sender;
    private final EventsProperties properties;
    private final TransactionTemplate transactionTemplate;

    public OutboxPublisher(OutboxEventRepository repository, EventSender sender, EventsProperties properties,
            PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.sender = sender;
        this.properties = properties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Scheduled(initialDelayString = "${xelastay.events.publish-interval-ms:5000}",
            fixedDelayString = "${xelastay.events.publish-interval-ms:5000}")
    public void publishPending() {
        Integer published = transactionTemplate.execute(status -> publishBatch());
        if (published != null && published > 0) {
            log.debug("Outbox: {} eventos publicados", published);
        }
    }

    private int publishBatch() {
        List<OutboxEvent> batch = repository.lockNextBatch(properties.maxAttempts(), properties.batchSize());
        int published = 0;
        for (OutboxEvent event : batch) {
            try {
                sender.send(event);
                event.markPublished(Instant.now());
                published++;
            } catch (AmqpConnectException ex) {
                log.warn("RabbitMQ no está disponible; los eventos pendientes se reintentarán en el próximo ciclo");
                break;
            } catch (RuntimeException ex) {
                event.markFailed();
                log.warn("No se pudo publicar el evento {} (intento {} de {}): {}", event.getEventKey(),
                        event.getAttempts(), properties.maxAttempts(), ex.getMessage());
            }
        }
        return published;
    }
}
