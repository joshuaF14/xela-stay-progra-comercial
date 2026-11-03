package com.xelastay.property;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Filtros opcionales de GET /api/v1/properties. {@code amenityIds}: el alojamiento debe tenerlas todas. */
public record PropertySearch(
        String department,
        String municipality,
        UUID categoryId,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Integer guests,
        List<UUID> amenityIds,
        String q) {
}
