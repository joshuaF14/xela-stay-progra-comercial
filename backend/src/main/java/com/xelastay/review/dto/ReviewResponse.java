package com.xelastay.review.dto;

import java.time.Instant;
import java.util.UUID;

import com.xelastay.review.Review;

public record ReviewResponse(
        UUID id,
        UUID bookingId,
        UUID propertyId,
        UUID reviewerId,
        String reviewerName,
        int rating,
        String comment,
        Instant createdAt,
        Instant updatedAt) {

    public static ReviewResponse from(Review review, String reviewerName) {
        return new ReviewResponse(review.getId(), review.getBookingId(), review.getPropertyId(),
                review.getReviewerId(), reviewerName, review.getRating(), review.getComment(),
                review.getCreatedAt(), review.getUpdatedAt());
    }
}
