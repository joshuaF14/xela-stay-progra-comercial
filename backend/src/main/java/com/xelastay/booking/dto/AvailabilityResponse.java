package com.xelastay.booking.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Noches ocupadas de un alojamiento entre {@code from} y {@code to} (ambas incluidas). Una fecha ocupada
 * significa que esa noche no se puede reservar; no se indica si es por reserva o por bloqueo del anfitrión.
 */
public record AvailabilityResponse(UUID propertyId, LocalDate from, LocalDate to, List<LocalDate> occupiedDates) {
}
