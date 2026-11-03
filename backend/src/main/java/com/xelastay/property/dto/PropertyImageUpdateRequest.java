package com.xelastay.property.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** Cambios parciales de una imagen: los campos ausentes no cambian. {@code cover: true} la vuelve portada. */
public record PropertyImageUpdateRequest(
        @Size(max = 255, message = "La descripción de la imagen admite máximo 255 caracteres") String caption,
        @Min(value = 0, message = "El orden no puede ser negativo") Integer displayOrder,
        Boolean cover) {
}
