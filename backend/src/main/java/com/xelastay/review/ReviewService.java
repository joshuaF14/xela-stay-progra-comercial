package com.xelastay.review;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.xelastay.booking.Booking;
import com.xelastay.booking.BookingRepository;
import com.xelastay.booking.BookingStatus;
import com.xelastay.booking.PropertyReader;
import com.xelastay.common.PageResponse;
import com.xelastay.common.ProfileNameReader;
import com.xelastay.review.dto.ReviewRequest;
import com.xelastay.review.dto.ReviewResponse;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReviewService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ReviewRepository reviews;
    private final BookingRepository bookings;
    private final PropertyReader properties;
    private final ProfileNameReader profileNames;

    public ReviewService(ReviewRepository reviews, BookingRepository bookings, PropertyReader properties,
            ProfileNameReader profileNames) {
        this.reviews = reviews;
        this.bookings = bookings;
        this.properties = properties;
        this.profileNames = profileNames;
    }

    /** Solo el huésped de una reserva COMPLETED puede reseñarla, una sola vez. */
    @Transactional
    public ReviewResponse create(UUID userId, UUID bookingId, ReviewRequest request) {
        Booking booking = bookings.findById(bookingId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Reserva no encontrada"));
        if (!booking.getGuestId().equals(userId)) {
            throw error(HttpStatus.FORBIDDEN, "Solo el huésped de la reserva puede reseñarla");
        }
        if (booking.getStatus() != BookingStatus.COMPLETED) {
            throw error(HttpStatus.CONFLICT, "Solo se pueden reseñar estancias completadas");
        }
        if (reviews.existsByBookingId(bookingId)) {
            throw error(HttpStatus.CONFLICT, "Ya reseñaste esta reserva");
        }
        Review review = Review.create(bookingId, booking.getPropertyId(), userId, request.rating(),
                blankToNull(request.comment()));
        try {
            reviews.saveAndFlush(review);
        } catch (DataIntegrityViolationException ex) {
            throw error(HttpStatus.CONFLICT, "Ya reseñaste esta reserva");
        }
        return withName(review);
    }

    /** Público: reseñas de un alojamiento publicado, de la más reciente a la más antigua. */
    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> listForProperty(UUID propertyId, int page, int size) {
        properties.findPublished(propertyId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Alojamiento no encontrado"));
        Page<Review> result = reviews.findByPropertyId(propertyId, PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE), Sort.by(Sort.Direction.DESC, "createdAt")));
        Map<UUID, String> names = profileNames.findNames(
                result.getContent().stream().map(Review::getReviewerId).collect(Collectors.toSet()));
        List<ReviewResponse> content = result.getContent().stream()
                .map(review -> ReviewResponse.from(review, names.get(review.getReviewerId())))
                .toList();
        return PageResponse.of(result, content);
    }

    @Transactional
    public ReviewResponse update(UUID userId, UUID reviewId, ReviewRequest request) {
        Review review = own(userId, reviewId);
        review.update(request.rating(), blankToNull(request.comment()));
        return withName(review);
    }

    @Transactional
    public void delete(UUID userId, UUID reviewId) {
        reviews.delete(own(userId, reviewId));
    }

    private Review own(UUID userId, UUID reviewId) {
        Review review = reviews.findById(reviewId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Reseña no encontrada"));
        if (!review.getReviewerId().equals(userId)) {
            throw error(HttpStatus.FORBIDDEN, "Solo el autor puede modificar o borrar su reseña");
        }
        return review;
    }

    private ReviewResponse withName(Review review) {
        return ReviewResponse.from(review, profileNames.findNames(Set.of(review.getReviewerId()))
                .get(review.getReviewerId()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
}
