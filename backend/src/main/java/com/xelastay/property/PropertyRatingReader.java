package com.xelastay.property;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.stereotype.Repository;

/** Promedio y cantidad de reseñas desde la vista reporting_property_ratings de V1. */
@Repository
public class PropertyRatingReader {

    /** @param average null si el alojamiento no tiene reseñas */
    public record Rating(BigDecimal average, long count) {
        public static final Rating NONE = new Rating(null, 0);
    }

    @PersistenceContext
    private EntityManager entityManager;

    public Map<UUID, Rating> find(Collection<UUID> propertyIds) {
        if (propertyIds.isEmpty()) {
            return Map.of();
        }
        List<?> rows = entityManager.createNativeQuery("""
                SELECT property_id, review_count, average_rating
                FROM reporting_property_ratings WHERE property_id IN (:ids)
                """)
                .setParameter("ids", propertyIds)
                .getResultList();
        Map<UUID, Rating> ratings = new HashMap<>();
        for (Object row : rows) {
            Object[] columns = (Object[]) row;
            UUID id = columns[0] instanceof UUID uuid ? uuid : UUID.fromString(columns[0].toString());
            long count = ((Number) columns[1]).longValue();
            BigDecimal average = columns[2] == null ? null : new BigDecimal(columns[2].toString());
            ratings.put(id, new Rating(average, count));
        }
        return ratings;
    }
}
