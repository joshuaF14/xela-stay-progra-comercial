package com.xelastay.property;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PropertyImageRepository extends JpaRepository<PropertyImage, UUID> {

    List<PropertyImage> findByPropertyIdOrderByDisplayOrderAscCreatedAtAsc(UUID propertyId);

    /** Imágenes de varios alojamientos: primero la portada y luego por orden (para elegir la de cada tarjeta). */
    @Query("""
            select i from PropertyImage i where i.propertyId in :propertyIds
            order by i.propertyId, i.cover desc, i.displayOrder, i.createdAt
            """)
    List<PropertyImage> findForCovers(@Param("propertyIds") Collection<UUID> propertyIds);

    Optional<PropertyImage> findByIdAndPropertyId(UUID id, UUID propertyId);

    long countByPropertyId(UUID propertyId);

    @Query("select coalesce(max(i.displayOrder), -1) from PropertyImage i where i.propertyId = :propertyId")
    int maxDisplayOrder(@Param("propertyId") UUID propertyId);

    /** Quita la portada actual. Se ejecuta antes de marcar otra, para respetar el índice único de V1. */
    @Modifying(flushAutomatically = true)
    @Query("update PropertyImage i set i.cover = false where i.propertyId = :propertyId and i.cover = true")
    int clearCover(@Param("propertyId") UUID propertyId);
}
