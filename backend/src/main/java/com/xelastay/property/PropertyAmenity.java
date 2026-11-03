package com.xelastay.property;

import java.util.UUID;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Amenidad asignada a un alojamiento (tabla property_amenities de V1). */
@Entity
@Table(name = "property_amenities")
public class PropertyAmenity {

    @EmbeddedId
    private PropertyAmenityId id;

    protected PropertyAmenity() {
    }

    public static PropertyAmenity of(UUID propertyId, UUID amenityId) {
        PropertyAmenity assignment = new PropertyAmenity();
        assignment.id = new PropertyAmenityId(propertyId, amenityId);
        return assignment;
    }

    public PropertyAmenityId getId() {
        return id;
    }
}
