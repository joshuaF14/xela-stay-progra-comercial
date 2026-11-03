package com.xelastay.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelBookingRequest(
        @NotBlank(message = "El motivo de cancelación es obligatorio")
        @Size(max = 500, message = "El motivo admite máximo 500 caracteres") String reason) {
}
