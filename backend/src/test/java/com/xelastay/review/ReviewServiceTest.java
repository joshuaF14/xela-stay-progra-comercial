package com.xelastay.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.xelastay.booking.Booking;
import com.xelastay.booking.BookingRepository;
import com.xelastay.booking.PropertyReader;
import com.xelastay.common.ProfileNameReader;
import com.xelastay.review.dto.ReviewRequest;
import com.xelastay.review.dto.ReviewResponse;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviews;

    @Mock
    private BookingRepository bookings;

    @Mock
    private PropertyReader properties;

    @Mock
    private ProfileNameReader profileNames;

    private final UUID guestId = UUID.randomUUID();
    private final UUID propertyId = UUID.randomUUID();
    private final UUID bookingId = UUID.randomUUID();
    private ReviewService service;

    @BeforeEach
    void setUp() {
        service = new ReviewService(reviews, bookings, properties, profileNames);
    }

    @Test
    void guestReviewsCompletedStay() {
        when(bookings.findById(bookingId)).thenReturn(Optional.of(completedBooking()));
        when(reviews.saveAndFlush(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(profileNames.findNames(any())).thenReturn(Map.of(guestId, "Ana López"));

        ReviewResponse review = service.create(guestId, bookingId, new ReviewRequest(5, "  Excelente vista "));

        assertThat(review.rating()).isEqualTo(5);
        assertThat(review.comment()).isEqualTo("Excelente vista");
        assertThat(review.propertyId()).isEqualTo(propertyId);
        assertThat(review.bookingId()).isEqualTo(bookingId);
        assertThat(review.reviewerName()).isEqualTo("Ana López");
    }

    @Test
    void onlyTheGuestCanReview() {
        when(bookings.findById(bookingId)).thenReturn(Optional.of(completedBooking()));

        assertStatus(() -> service.create(UUID.randomUUID(), bookingId, new ReviewRequest(4, null)),
                HttpStatus.FORBIDDEN);
        verify(reviews, never()).saveAndFlush(any());
    }

    @Test
    void onlyCompletedStaysCanBeReviewed() {
        Booking pending = booking();
        when(bookings.findById(bookingId)).thenReturn(Optional.of(pending));

        assertStatus(() -> service.create(guestId, bookingId, new ReviewRequest(4, null)), HttpStatus.CONFLICT);
    }

    @Test
    void oneReviewPerBooking() {
        when(bookings.findById(bookingId)).thenReturn(Optional.of(completedBooking()));
        when(reviews.existsByBookingId(bookingId)).thenReturn(true);

        assertStatus(() -> service.create(guestId, bookingId, new ReviewRequest(4, null)), HttpStatus.CONFLICT);
        verify(reviews, never()).saveAndFlush(any());
    }

    @Test
    void authorUpdatesOwnReview() {
        Review review = review();
        when(reviews.findById(review.getId())).thenReturn(Optional.of(review));

        ReviewResponse updated = service.update(guestId, review.getId(), new ReviewRequest(3, "Ruido de noche"));

        assertThat(updated.rating()).isEqualTo(3);
        assertThat(updated.comment()).isEqualTo("Ruido de noche");
    }

    @Test
    void onlyTheAuthorCanEditOrDelete() {
        Review review = review();
        when(reviews.findById(review.getId())).thenReturn(Optional.of(review));
        UUID stranger = UUID.randomUUID();

        assertStatus(() -> service.update(stranger, review.getId(), new ReviewRequest(1, null)), HttpStatus.FORBIDDEN);
        assertStatus(() -> service.delete(stranger, review.getId()), HttpStatus.FORBIDDEN);
        verify(reviews, never()).delete(any());
    }

    @Test
    void authorDeletesOwnReview() {
        Review review = review();
        when(reviews.findById(review.getId())).thenReturn(Optional.of(review));

        service.delete(guestId, review.getId());

        verify(reviews).delete(review);
    }

    @Test
    void reviewsOfUnpublishedPropertyAreNotFound() {
        when(properties.findPublished(propertyId)).thenReturn(Optional.empty());

        assertStatus(() -> service.listForProperty(propertyId, 0, 20), HttpStatus.NOT_FOUND);
    }

    private Booking booking() {
        LocalDate checkIn = LocalDate.of(2030, 1, 10);
        Booking booking = Booking.pending(propertyId, guestId, checkIn, checkIn.plusDays(2), 2,
                new BigDecimal("350.00"), new BigDecimal("700.00"), new BigDecimal("0.00"), "GTQ", null);
        ReflectionTestUtils.setField(booking, "id", bookingId);
        return booking;
    }

    private Booking completedBooking() {
        Booking booking = booking();
        booking.confirm();
        booking.complete();
        return booking;
    }

    private Review review() {
        Review review = Review.create(bookingId, propertyId, guestId, 5, "Excelente");
        ReflectionTestUtils.setField(review, "id", UUID.randomUUID());
        return review;
    }

    private static void assertStatus(ThrowingCallable call, HttpStatus expected) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode()).isEqualTo(expected));
    }
}
