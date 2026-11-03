package com.xelastay.property;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import com.xelastay.catalog.Location;
import com.xelastay.catalog.PropertyCategory;

/**
 * Alojamiento (tabla properties de V1). Nace en DRAFT; el borrado es lógico (deleted_at). El anfitrión se guarda
 * como UUID simple (profiles.id), igual que en los módulos de reservas y reseñas.
 */
@Entity
@Table(name = "properties")
public class Property {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "host_id", nullable = false, updatable = false)
    private UUID hostId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private PropertyCategory category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @Column(name = "title", nullable = false, length = 180)
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "price_per_night", nullable = false, precision = 12, scale = 2)
    private BigDecimal pricePerNight;

    @Column(name = "currency", nullable = false, length = 3, columnDefinition = "bpchar(3)")
    private String currency;

    @Column(name = "capacity", nullable = false)
    private int capacity;

    @Column(name = "bedrooms", nullable = false)
    private int bedrooms;

    @Column(name = "bathrooms", nullable = false, precision = 3, scale = 1)
    private BigDecimal bathrooms;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PropertyStatus status;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at", columnDefinition = "timestamptz")
    private OffsetDateTime deletedAt;

    protected Property() {
    }

    public static Property draft(UUID hostId, PropertyCategory category, Location location, String title,
            String description, BigDecimal pricePerNight, String currency, int capacity, int bedrooms,
            BigDecimal bathrooms) {
        Property property = new Property();
        property.hostId = hostId;
        property.status = PropertyStatus.DRAFT;
        property.update(category, location, title, description, pricePerNight, currency, capacity, bedrooms,
                bathrooms);
        property.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        property.updatedAt = property.createdAt;
        return property;
    }

    public void update(PropertyCategory category, Location location, String title, String description,
            BigDecimal pricePerNight, String currency, int capacity, int bedrooms, BigDecimal bathrooms) {
        this.category = category;
        this.location = location;
        this.title = title;
        this.description = description;
        this.pricePerNight = pricePerNight;
        this.currency = currency;
        this.capacity = capacity;
        this.bedrooms = bedrooms;
        this.bathrooms = bathrooms;
    }

    public void changeStatus(PropertyStatus status) {
        this.status = status;
    }

    public void markDeleted() {
        this.deletedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public boolean isOwnedBy(UUID userId) {
        return hostId.equals(userId);
    }

    @PreUpdate
    void touch() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public UUID getId() {
        return id;
    }

    public UUID getHostId() {
        return hostId;
    }

    public PropertyCategory getCategory() {
        return category;
    }

    public Location getLocation() {
        return location;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPricePerNight() {
        return pricePerNight;
    }

    public String getCurrency() {
        return currency;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getBedrooms() {
        return bedrooms;
    }

    public BigDecimal getBathrooms() {
        return bathrooms;
    }

    public PropertyStatus getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }
}
