package com.xelastay.booking;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Fechas que el anfitrión cierra a reservas (tabla availability_blocks de V1). Igual que las reservas,
 * el intervalo es [startDate, endDate): endDate es el primer día libre.
 */
@Entity
@Table(name = "availability_blocks")
public class AvailabilityBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "reason", length = 255)
    private String reason;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private Instant createdAt;

    protected AvailabilityBlock() {
    }

    public static AvailabilityBlock create(UUID propertyId, LocalDate startDate, LocalDate endDate, String reason,
            UUID createdBy) {
        AvailabilityBlock block = new AvailabilityBlock();
        block.propertyId = propertyId;
        block.startDate = startDate;
        block.endDate = endDate;
        block.reason = reason;
        block.createdBy = createdBy;
        block.createdAt = Instant.now();
        return block;
    }

    public void update(LocalDate startDate, LocalDate endDate, String reason) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.reason = reason;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPropertyId() {
        return propertyId;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public String getReason() {
        return reason;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
