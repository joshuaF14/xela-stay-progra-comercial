package com.xelastay.messaging.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * El usuario actual inicia (o retoma) la conversación con el anfitrión del alojamiento.
 *
 * @param message primer mensaje, opcional
 */
public record StartConversationRequest(
        @NotNull(message = "El alojamiento es obligatorio") UUID propertyId,
        @Size(max = 2000, message = "El mensaje admite máximo 2000 caracteres") String message) {
}
