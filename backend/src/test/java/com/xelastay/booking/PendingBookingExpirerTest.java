package com.xelastay.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PendingBookingExpirerTest {

    /** 3 de noviembre de 2026 a las 03:00 UTC: en Guatemala todavía es 2 de noviembre (21:00). */
    private static final Instant NOW = Instant.parse("2026-11-03T03:00:00Z");

    @Mock
    private BookingRepository bookings;

    @Mock
    private BookingService bookingService;

    private PendingBookingExpirer expirer;

    @BeforeEach
    void setUp() {
        expirer = new PendingBookingExpirer(bookings, bookingService, Duration.ofHours(24), 50,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void looksForPendingBookingsOlderThan24HoursOrWhoseCheckInArrived() {
        when(bookings.findIdsToExpire(any(), any(), any(), any())).thenReturn(List.of());

        assertThat(expirer.expireDue()).isZero();

        verify(bookings).findIdsToExpire(BookingStatus.PENDING, Instant.parse("2026-11-02T03:00:00Z"),
                LocalDate.of(2026, 11, 2), PageRequest.of(0, 50));
        verifyNoInteractions(bookingService);
    }

    @Test
    void expiresEachBookingAndCountsOnlyTheOnesThatChanged() {
        UUID first = UUID.randomUUID();
        UUID answered = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        when(bookings.findIdsToExpire(any(), any(), any(), any())).thenReturn(List.of(first, answered, third));
        when(bookingService.expire(first, PendingBookingExpirer.REASON)).thenReturn(true);
        when(bookingService.expire(answered, PendingBookingExpirer.REASON)).thenReturn(false);
        when(bookingService.expire(third, PendingBookingExpirer.REASON)).thenReturn(true);

        assertThat(expirer.expireDue()).isEqualTo(2);
    }

    @Test
    void oneFailureDoesNotStopTheRest() {
        UUID broken = UUID.randomUUID();
        UUID fine = UUID.randomUUID();
        when(bookings.findIdsToExpire(any(), any(), any(), any())).thenReturn(List.of(broken, fine));
        when(bookingService.expire(eq(broken), any()))
                .thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        when(bookingService.expire(eq(fine), any())).thenReturn(true);

        assertThat(expirer.expireDue()).isEqualTo(1);
        verify(bookingService).expire(fine, PendingBookingExpirer.REASON);
    }
}
