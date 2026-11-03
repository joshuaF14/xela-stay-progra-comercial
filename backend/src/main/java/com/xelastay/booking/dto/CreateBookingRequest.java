package com.xelastay.booking.dto;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Solicitud de reserva. Precio, subtotal y total los calcula el servidor; no se aceptan desde el cliente.
 *
 * @param idempotencyKey opcional; si se repite la solicitud con la misma clave (p. ej. un reintento por mala
 *                       conexión) se devuelve la reserva ya creada en lugar de duplicarla
 */
public record CreateBookingRequest(
        @NotNull(message = "El alojamiento es obligatorio") UUID propertyId,
        @NotNull(message = "La fecha de entrada es obligatoria") LocalDate checkIn,
        @NotNull(message = "La fecha de salida es obligatoria") LocalDate checkOut,
        @NotNull(message = "La cantidad de huéspedes es obligatoria")
        @Min(value = 1, message = "Debe haber al menos 1 huésped")
        @Max(value = 100, message = "Cantidad de huéspedes no válida") Integer guestsCount,
        @Size(max = 100, message = "La clave de idempotencia admite máximo 100 caracteres") String idempotencyKey) {
}
