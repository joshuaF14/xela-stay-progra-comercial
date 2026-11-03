package com.xelastay.booking;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Activa las tareas programadas para {@link PendingBookingExpirer}. Va aparte de RabbitConfig para que el
 * vencimiento siga corriendo aunque los eventos estén apagados ({@code EVENTS_ENABLED=false}).
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "xelastay.bookings.expiration", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class BookingExpirationConfig {
}
