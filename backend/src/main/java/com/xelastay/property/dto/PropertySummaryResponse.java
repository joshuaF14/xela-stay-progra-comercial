package com.xelastay.property.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.xelastay.catalog.dto.LocationResponse;
import com.xelastay.property.PropertyStatus;

/**
 * Tarjeta de alojamiento para listados y mapa.
 *
 * @param coverImageUrl portada (o la primera imagen); null si no tiene imágenes
 * @param averageRating promedio de reseñas con 2 decimales; null si no tiene reseñas
 */
public record PropertySummaryResponse(
        UUID id,
        UUID hostId,
        String title,
        String description,
        BigDecimal pricePerNight,
        String currency,
        int capacity,
        int bedrooms,
        BigDecimal bathrooms,
        PropertyStatus status,
        CategoryRef category,
        LocationResponse location,
        String coverImageUrl,
        BigDecimal averageRating,
        long reviewCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public record CategoryRef(UUID id, String name) {
    }
}
