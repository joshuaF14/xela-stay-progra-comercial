package com.xelastay.events;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Contenido (payload) de los eventos de reservas. Lo arma el módulo de reservas al escribir en el
 * outbox y lo leen los consumidores. Incluye lo necesario para notificar sin consultar otras tablas.
 *
 * @param status estado de la reserva después del cambio (PENDING, CONFIRMED, REJECTED, CANCELLED, COMPLETED)
 * @param reason motivo de cancelación o rechazo; null si no aplica
 */
public record BookingEventPayload(
        UUID bookingId,
        UUID propertyId,
        String propertyTitle,
        UUID guestId,
        UUID hostId,
        LocalDate checkIn,
        LocalDate checkOut,
        String status,
        String reason) {
}
