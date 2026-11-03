package com.xelastay.property.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** Lista completa de amenidades del alojamiento (reemplaza la actual; una lista vacía las quita todas). */
public record PropertyAmenitiesRequest(
        @NotNull(message = "amenityIds es obligatorio")
        List<@NotNull(message = "El id de amenidad no puede ser nulo") UUID> amenityIds) {
}
