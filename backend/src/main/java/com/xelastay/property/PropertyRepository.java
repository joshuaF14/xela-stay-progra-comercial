package com.xelastay.property;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PropertyRepository extends JpaRepository<Property, UUID>, JpaSpecificationExecutor<Property> {

    /** Carga categoría y ubicación en la misma consulta para armar las tarjetas sin N+1. */
    @Override
    @EntityGraph(attributePaths = {"category", "location"})
    Page<Property> findAll(Specification<Property> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"category", "location"})
    Optional<Property> findByIdAndDeletedAtIsNull(UUID id);

    /** true si hay reservas PENDING o CONFIRMED que todavía no terminan (check_out posterior a hoy). */
    @Query(value = """
            SELECT EXISTS (
              SELECT 1 FROM bookings
              WHERE property_id = :propertyId AND status IN ('PENDING', 'CONFIRMED') AND check_out > :today)
            """, nativeQuery = true)
    boolean hasActiveFutureBookings(@Param("propertyId") UUID propertyId, @Param("today") LocalDate today);
}
