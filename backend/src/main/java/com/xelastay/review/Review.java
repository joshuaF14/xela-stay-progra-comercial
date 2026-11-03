package com.xelastay.review;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * Reseña de una estancia (tabla reviews de V1). booking_id es único: una reseña por reserva. property_id se
 * copia siempre de la reserva para que no pueda diferir de bookings.property_id (observación 10 de PENDIENTES).
 */
@Entity
@Table(name = "reviews")
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    @Column(name = "reviewer_id", nullable = false)
    private UUID reviewerId;

    @Column(name = "rating", nullable = false)
    private int rating;

    @Column(name = "comment", columnDefinition = "text")
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamptz")
    private Instant updatedAt;

    protected Review() {
    }

    public static Review create(UUID bookingId, UUID propertyId, UUID reviewerId, int rating, String comment) {
        Review review = new Review();
        review.bookingId = bookingId;
        review.propertyId = propertyId;
        review.reviewerId = reviewerId;
        review.rating = rating;
        review.comment = comment;
        review.createdAt = Instant.now();
        review.updatedAt = review.createdAt;
        return review;
    }

    public void update(int rating, String comment) {
        this.rating = rating;
        this.comment = comment;
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public UUID getPropertyId() {
        return propertyId;
    }

    public UUID getReviewerId() {
        return reviewerId;
    }

    public int getRating() {
        return rating;
    }

    public String getComment() {
        return comment;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
