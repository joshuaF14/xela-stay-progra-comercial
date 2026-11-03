package com.xelastay.events;

import jakarta.validation.constraints.Min;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Parámetros del publicador del outbox (prefijo xelastay.events).
 * El intervalo de publicación se lee directamente en {@link OutboxPublisher} (publish-interval-ms).
 *
 * @param batchSize   eventos por ciclo de publicación
 * @param maxAttempts intentos fallidos tras los que un evento queda FAILED y ya no se reintenta
 */
@Validated
@ConfigurationProperties("xelastay.events")
public record EventsProperties(
        @DefaultValue("20") @Min(1) int batchSize,
        @DefaultValue("10") @Min(1) int maxAttempts) {
}
