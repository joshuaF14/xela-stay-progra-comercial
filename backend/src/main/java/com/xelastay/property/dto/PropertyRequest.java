package com.xelastay.property.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.xelastay.catalog.dto.LocationRequest;

/**
 * Alta o edición de un alojamiento. La ubicación va como {@code locationId} (una existente) o como
 * {@code location} (se crea en la misma transacción), exactamente una de las dos. {@code currency} es opcional
 * (GTQ por defecto).
 */
public record PropertyRequest(
        @NotNull(message = "La categoría es obligatoria") UUID categoryId,
        UUID locationId,
        @Valid LocationRequest location,
        @NotBlank(message = "El título es obligatorio")
        @Size(max = 180, message = "El título admite máximo 180 caracteres") String title,
        @Size(max = 5000, message = "La descripción admite máximo 5000 caracteres") String description,
        @NotNull(message = "El precio por noche es obligatorio")
        @DecimalMin(value = "0.01", message = "El precio por noche debe ser mayor que 0")
        @Digits(integer = 10, fraction = 2, message = "El precio admite 2 decimales") BigDecimal pricePerNight,
        @Pattern(regexp = "^[A-Z]{3}$", message = "La moneda es un código ISO de 3 letras (GTQ, USD)") String currency,
        @NotNull(message = "La capacidad es obligatoria")
        @Min(value = 1, message = "La capacidad mínima es 1")
        @Max(value = 100, message = "La capacidad máxima es 100") Integer capacity,
        @NotNull(message = "Las habitaciones son obligatorias")
        @Min(value = 0, message = "Las habitaciones no pueden ser negativas") Integer bedrooms,
        @NotNull(message = "Los baños son obligatorios")
        @DecimalMin(value = "0", message = "Los baños no pueden ser negativos")
        @DecimalMax(value = "99.9", message = "Los baños admiten máximo 99.9")
        @Digits(integer = 2, fraction = 1, message = "Los baños admiten 1 decimal (p. ej. 1.5)") BigDecimal bathrooms) {

    @AssertTrue(message = "Envíe locationId o location, exactamente uno")
    public boolean isLocationProvidedOnce() {
        return (locationId == null) != (location == null);
    }
}
