package com.xelastay.property.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Nueva imagen. {@code displayOrder} es opcional (va al final). {@code cover} es opcional; la primera imagen de un
 * alojamiento se vuelve portada automáticamente.
 */
public record PropertyImageRequest(
        @NotBlank(message = "La URL de la imagen es obligatoria")
        @Size(max = 2048, message = "La URL admite máximo 2048 caracteres")
        @Pattern(regexp = "^https://\\S+$", message = "La URL de la imagen debe empezar con https://") String imageUrl,
        @Size(max = 255, message = "La descripción de la imagen admite máximo 255 caracteres") String caption,
        @Min(value = 0, message = "El orden no puede ser negativo") Integer displayOrder,
        Boolean cover) {
}
