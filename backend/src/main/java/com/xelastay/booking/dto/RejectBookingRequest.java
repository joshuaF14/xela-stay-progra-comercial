package com.xelastay.booking.dto;

import jakarta.validation.constraints.Size;

/** Cuerpo opcional al rechazar una reserva. */
public record RejectBookingRequest(
        @Size(max = 500, message = "El motivo admite máximo 500 caracteres") String reason) {
}
