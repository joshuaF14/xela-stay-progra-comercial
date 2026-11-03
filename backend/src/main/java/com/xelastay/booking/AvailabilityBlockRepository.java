package com.xelastay.booking;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AvailabilityBlockRepository extends JpaRepository<AvailabilityBlock, UUID> {

    List<AvailabilityBlock> findByPropertyIdOrderByStartDateAsc(UUID propertyId);

    Optional<AvailabilityBlock> findByIdAndPropertyId(UUID id, UUID propertyId);

    /** Bloqueos de la propiedad que se cruzan con [startDate, endDate). */
    @Query("""
            select a from AvailabilityBlock a
            where a.propertyId = :propertyId and a.startDate < :endDate and a.endDate > :startDate
            order by a.startDate
            """)
    List<AvailabilityBlock> findOverlapping(@Param("propertyId") UUID propertyId,
            @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}
