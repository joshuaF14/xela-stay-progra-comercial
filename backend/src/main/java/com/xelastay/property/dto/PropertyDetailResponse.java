package com.xelastay.property.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.xelastay.catalog.dto.AmenityResponse;
import com.xelastay.catalog.dto.LocationResponse;
import com.xelastay.property.PropertyStatus;
import com.xelastay.property.dto.PropertySummaryResponse.CategoryRef;

/** Detalle de un alojamiento: tarjeta más imágenes ordenadas, amenidades y anfitrión. */
public record PropertyDetailResponse(
        UUID id,
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
        HostRef host,
        List<PropertyImageResponse> images,
        List<AmenityResponse> amenities,
        BigDecimal averageRating,
        long reviewCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public record HostRef(UUID id, String fullName) {
    }
}
