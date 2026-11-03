package com.xelastay.property;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PropertyAmenityRepository extends JpaRepository<PropertyAmenity, PropertyAmenityId> {

    @Query("select pa.id.amenityId from PropertyAmenity pa where pa.id.propertyId = :propertyId")
    List<UUID> findAmenityIds(@Param("propertyId") UUID propertyId);

    @Modifying(flushAutomatically = true)
    @Query("delete from PropertyAmenity pa where pa.id.propertyId = :propertyId")
    int deleteByPropertyId(@Param("propertyId") UUID propertyId);
}
