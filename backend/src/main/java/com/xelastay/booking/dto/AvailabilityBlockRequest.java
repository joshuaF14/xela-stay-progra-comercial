package com.xelastay.booking.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Bloqueo de fechas. El intervalo es [startDate, endDate): endDate es el primer día que vuelve a estar libre,
 * igual que el check-out de una reserva.
 */
public record AvailabilityBlockRequest(
        @NotNull(message = "La fecha de inicio es obligatoria") LocalDate startDate,
        @NotNull(message = "La fecha de fin es obligatoria") LocalDate endDate,
        @Size(max = 255, message = "El motivo admite máximo 255 caracteres") String reason) {
}
