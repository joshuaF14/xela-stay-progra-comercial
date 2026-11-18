package com.xelastay.review.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Resumen de las reseñas de un alojamiento.
 *
 * @param averageRating promedio de 1 a 5 con 2 decimales (igual que la vista reporting_property_ratings de V1);
 *                      null si no tiene reseñas
 */
public record ReviewSummaryResponse(UUID propertyId, long reviewCount, BigDecimal averageRating) {
}
