package com.xelastay.catalog;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Ubicación de un alojamiento (tabla locations de V1). Latitud y longitud son opcionales (NUMERIC(10,7)). */
@Entity
@Table(name = "locations")
public class Location {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "department", nullable = false, length = 100)
    private String department;

    @Column(name = "municipality", nullable = false, length = 120)
    private String municipality;

    @Column(name = "address", nullable = false, columnDefinition = "text")
    private String address;

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private OffsetDateTime createdAt;

    protected Location() {
    }

    public static Location create(String department, String municipality, String address, BigDecimal latitude,
            BigDecimal longitude) {
        Location location = new Location();
        location.update(department, municipality, address, latitude, longitude);
        location.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        return location;
    }

    public void update(String department, String municipality, String address, BigDecimal latitude,
            BigDecimal longitude) {
        this.department = department;
        this.municipality = municipality;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public UUID getId() {
        return id;
    }

    public String getDepartment() {
        return department;
    }

    public String getMunicipality() {
        return municipality;
    }

    public String getAddress() {
        return address;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
