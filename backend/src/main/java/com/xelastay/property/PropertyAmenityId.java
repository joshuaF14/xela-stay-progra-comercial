package com.xelastay.property;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Llave compuesta de property_amenities: (property_id, amenity_id). */
@Embeddable
public class PropertyAmenityId implements Serializable {

    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    @Column(name = "amenity_id", nullable = false)
    private UUID amenityId;

    protected PropertyAmenityId() {
    }

    public PropertyAmenityId(UUID propertyId, UUID amenityId) {
        this.propertyId = propertyId;
        this.amenityId = amenityId;
    }

    public UUID getPropertyId() {
        return propertyId;
    }

    public UUID getAmenityId() {
        return amenityId;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PropertyAmenityId that
                && Objects.equals(propertyId, that.propertyId)
                && Objects.equals(amenityId, that.amenityId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(propertyId, amenityId);
    }
}
