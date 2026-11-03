package com.xelastay.catalog.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Ubicación. Latitud y longitud son opcionales, pero van juntas; se guardan con 7 decimales.
 */
public record LocationRequest(
        @NotBlank(message = "El departamento es obligatorio")
        @Size(max = 100, message = "El departamento admite máximo 100 caracteres") String department,
        @NotBlank(message = "El municipio es obligatorio")
        @Size(max = 120, message = "El municipio admite máximo 120 caracteres") String municipality,
        @NotBlank(message = "La dirección es obligatoria")
        @Size(max = 500, message = "La dirección admite máximo 500 caracteres") String address,
        @DecimalMin(value = "-90", message = "La latitud debe estar entre -90 y 90")
        @DecimalMax(value = "90", message = "La latitud debe estar entre -90 y 90") BigDecimal latitude,
        @DecimalMin(value = "-180", message = "La longitud debe estar entre -180 y 180")
        @DecimalMax(value = "180", message = "La longitud debe estar entre -180 y 180") BigDecimal longitude) {

    @AssertTrue(message = "Latitud y longitud se envían juntas o ninguna")
    public boolean isCoordinatesPaired() {
        return (latitude == null) == (longitude == null);
    }
}
