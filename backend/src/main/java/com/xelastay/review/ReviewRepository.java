package com.xelastay.review;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    boolean existsByBookingId(UUID bookingId);

    Page<Review> findByPropertyId(UUID propertyId, Pageable pageable);

    /** Cantidad y suma de calificaciones de un alojamiento, en una sola consulta (sin cargar las reseñas). */
    @Query("""
            SELECT COUNT(r) AS reviewCount, SUM(r.rating) AS ratingSum
            FROM Review r
            WHERE r.propertyId = :propertyId
            """)
    RatingTotals totalsByPropertyId(@Param("propertyId") UUID propertyId);

    /** Proyección de {@link #totalsByPropertyId}; ratingSum es null cuando no hay reseñas. */
    interface RatingTotals {

        Long getReviewCount();

        Long getRatingSum();
    }
}
