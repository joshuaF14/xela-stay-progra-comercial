package com.xelastay.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.xelastay.audit.AuditService;
import com.xelastay.audit.AuditSource;
import com.xelastay.booking.dto.BookingResponse;
import com.xelastay.booking.dto.BookingResult;
import com.xelastay.booking.dto.CreateBookingRequest;
import com.xelastay.common.PageResponse;
import com.xelastay.events.BookingEventPayload;
import com.xelastay.events.OutboxService;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    private static final LocalDate CHECK_IN = LocalDate.now(BookingService.ZONE).plusDays(30);
    private static final LocalDate CHECK_OUT = CHECK_IN.plusDays(2);

    @Mock
    private BookingRepository bookings;

    @Mock
    private AvailabilityBlockRepository blocks;

    @Mock
    private PropertyReader properties;

    @Mock
    private OutboxService outbox;

    @Mock
    private AuditService audit;

    private final UUID guestId = UUID.randomUUID();
    private final UUID hostId = UUID.randomUUID();
    private final UUID propertyId = UUID.randomUUID();
    private PropertyInfo property;
    private BookingService service;

    @BeforeEach
    void setUp() {
        property = new PropertyInfo(propertyId, hostId, "Casa en Xela", new BigDecimal("350"), "GTQ", 4, "PUBLISHED");
        service = new BookingService(bookings, blocks, properties, outbox, audit);
    }

    @Test
    void createsPendingBookingWithPricesCalculatedOnTheServer() {
        UUID bookingId = UUID.randomUUID();
        when(properties.lockPublished(propertyId)).thenReturn(Optional.of(property));
        when(bookings.saveAndFlush(any(Booking.class))).thenAnswer(invocation -> {
            Booking saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", bookingId);
            return saved;
        });

        BookingResult result = service.create(guestId, request(2, "clave-1"));

        assertThat(result.created()).isTrue();
        BookingResponse booking = result.booking();
        assertThat(booking.id()).isEqualTo(bookingId);
        assertThat(booking.status()).isEqualTo(BookingStatus.PENDING);
        assertThat(booking.nights()).isEqualTo(2);
        assertThat(booking.nightlyPrice()).isEqualByComparingTo("350.00");
        assertThat(booking.subtotal()).isEqualByComparingTo("700.00");
        assertThat(booking.fees()).isEqualByComparingTo("0.00");
        assertThat(booking.total()).isEqualByComparingTo("700.00");
        assertThat(booking.currency()).isEqualTo("GTQ");
        assertThat(booking.hostId()).isEqualTo(hostId);
        assertThat(booking.propertyTitle()).isEqualTo("Casa en Xela");

        ArgumentCaptor<BookingEventPayload> payload = ArgumentCaptor.forClass(BookingEventPayload.class);
        verify(outbox).enqueue(eq("booking:" + bookingId + ":CREATED"), eq("booking"), eq(bookingId),
                eq("booking.created"), payload.capture());
        assertThat(payload.getValue().guestId()).isEqualTo(guestId);
        assertThat(payload.getValue().hostId()).isEqualTo(hostId);
        assertThat(payload.getValue().status()).isEqualTo("PENDING");
    }

    @Test
    void rejectsDatesThatOverlapAnActiveBooking() {
        when(properties.lockPublished(propertyId)).thenReturn(Optional.of(property));
        when(bookings.countOverlapping(propertyId, BookingStatus.ACTIVE, CHECK_IN, CHECK_OUT)).thenReturn(1L);

        assertStatus(() -> service.create(guestId, request(2, null)), HttpStatus.CONFLICT);
        verify(bookings, never()).saveAndFlush(any());
        verifyNoInteractions(outbox);
    }

    @Test
    void rejectsDatesBlockedByTheHost() {
        when(properties.lockPublished(propertyId)).thenReturn(Optional.of(property));
        when(blocks.findOverlapping(propertyId, CHECK_IN, CHECK_OUT))
                .thenReturn(List.of(AvailabilityBlock.create(propertyId, CHECK_IN, CHECK_OUT, "Mantenimiento", hostId)));

        assertStatus(() -> service.create(guestId, request(2, null)), HttpStatus.CONFLICT);
        verify(bookings, never()).saveAndFlush(any());
    }

    @Test
    void hostCannotBookOwnProperty() {
        when(properties.lockPublished(propertyId)).thenReturn(Optional.of(property));

        assertStatus(() -> service.create(hostId, request(2, null)), HttpStatus.FORBIDDEN);
    }

    @Test
    void rejectsMoreGuestsThanCapacity() {
        when(properties.lockPublished(propertyId)).thenReturn(Optional.of(property));

        assertStatus(() -> service.create(guestId, request(5, null)), HttpStatus.BAD_REQUEST);
    }

    @Test
    void checkOutMustBeAfterCheckIn() {
        CreateBookingRequest sameDay = new CreateBookingRequest(propertyId, CHECK_IN, CHECK_IN, 2, null);

        assertStatus(() -> service.create(guestId, sameDay), HttpStatus.BAD_REQUEST);
        verifyNoInteractions(properties);
    }

    @Test
    void checkInCannotBeInThePast() {
        LocalDate today = LocalDate.now(BookingService.ZONE);
        when(properties.lockPublished(propertyId)).thenReturn(Optional.of(property));

        assertStatus(() -> service.create(guestId,
                new CreateBookingRequest(propertyId, today.minusDays(1), today.plusDays(1), 2, null)),
                HttpStatus.BAD_REQUEST);
    }

    @Test
    void unpublishedPropertyIsNotFound() {
        when(properties.lockPublished(propertyId)).thenReturn(Optional.empty());

        assertStatus(() -> service.create(guestId, request(2, null)), HttpStatus.NOT_FOUND);
    }

    @Test
    void sameIdempotencyKeyReturnsTheExistingBooking() {
        when(properties.lockPublished(propertyId)).thenReturn(Optional.of(property));
        when(bookings.findByGuestIdAndIdempotencyKey(guestId, "clave-1")).thenReturn(Optional.of(booking()));

        BookingResult result = service.create(guestId, request(2, "clave-1"));

        assertThat(result.created()).isFalse();
        assertThat(result.booking().status()).isEqualTo(BookingStatus.PENDING);
        verify(bookings, never()).saveAndFlush(any());
        verifyNoInteractions(outbox);
    }

    @Test
    void idempotencyKeyReusedForDifferentDataIsAConflict() {
        when(properties.lockPublished(propertyId)).thenReturn(Optional.of(property));
        when(bookings.findByGuestIdAndIdempotencyKey(guestId, "clave-1")).thenReturn(Optional.of(booking()));

        assertStatus(() -> service.create(guestId, request(3, "clave-1")), HttpStatus.CONFLICT);
    }

    @Test
    void hostConfirmsPendingBooking() {
        Booking booking = booking();
        givenLocked(booking);

        BookingResponse response = service.confirm(hostId, booking.getId());

        assertThat(response.status()).isEqualTo(BookingStatus.CONFIRMED);
        verify(outbox).enqueue(eq("booking:" + booking.getId() + ":CONFIRMED"), eq("booking"), eq(booking.getId()),
                eq("booking.confirmed"), any(BookingEventPayload.class));
    }

    @Test
    void guestCannotConfirm() {
        Booking booking = booking();
        givenLocked(booking);

        assertStatus(() -> service.confirm(guestId, booking.getId()), HttpStatus.FORBIDDEN);
        verifyNoInteractions(outbox);
    }

    @Test
    void cancelledBookingCannotBeConfirmed() {
        Booking booking = booking();
        booking.cancel("Cambio de planes");
        givenLocked(booking);

        assertStatus(() -> service.confirm(hostId, booking.getId()), HttpStatus.CONFLICT);
        verifyNoInteractions(outbox);
    }

    @Test
    void hostRejectsPendingBookingWithReason() {
        Booking booking = booking();
        givenLocked(booking);

        BookingResponse response = service.reject(hostId, booking.getId(), "Fechas no disponibles");

        assertThat(response.status()).isEqualTo(BookingStatus.REJECTED);
        assertThat(response.cancellationReason()).isEqualTo("Fechas no disponibles");
        verify(outbox).enqueue(eq("booking:" + booking.getId() + ":REJECTED"), eq("booking"), eq(booking.getId()),
                eq("booking.rejected"), any(BookingEventPayload.class));
    }

    @Test
    void guestCancelsWithReason() {
        Booking booking = booking();
        givenLocked(booking);

        BookingResponse response = service.cancel(guestId, booking.getId(), "  Cambio de planes ");

        assertThat(response.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(response.cancellationReason()).isEqualTo("Cambio de planes");
        ArgumentCaptor<BookingEventPayload> payload = ArgumentCaptor.forClass(BookingEventPayload.class);
        verify(outbox).enqueue(eq("booking:" + booking.getId() + ":CANCELLED"), eq("booking"), eq(booking.getId()),
                eq("booking.cancelled"), payload.capture());
        assertThat(payload.getValue().reason()).isEqualTo("Cambio de planes");
    }

    @Test
    void strangerCannotCancel() {
        Booking booking = booking();
        givenLocked(booking);

        assertStatus(() -> service.cancel(UUID.randomUUID(), booking.getId(), "Motivo"), HttpStatus.FORBIDDEN);
    }

    @Test
    void onlyConfirmedBookingsCanBeCompleted() {
        Booking booking = booking();
        givenLocked(booking);

        assertStatus(() -> service.complete(hostId, booking.getId()), HttpStatus.CONFLICT);

        booking.confirm();
        assertThat(service.complete(hostId, booking.getId()).status()).isEqualTo(BookingStatus.COMPLETED);
    }

    @Test
    void expiringAPendingBookingCancelsItAsTheSystem() {
        Booking booking = booking();
        givenLocked(booking);

        boolean expired = service.expire(booking.getId(), "Venció: el anfitrión no respondió a tiempo");

        assertThat(expired).isTrue();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(booking.getCancellationReason()).isEqualTo("Venció: el anfitrión no respondió a tiempo");
        // Sale como cancelación (mismas notificaciones), con su propia clave de evento.
        ArgumentCaptor<BookingEventPayload> payload = ArgumentCaptor.forClass(BookingEventPayload.class);
        verify(outbox).enqueue(eq("booking:" + booking.getId() + ":EXPIRED"), eq("booking"), eq(booking.getId()),
                eq("booking.cancelled"), payload.capture());
        assertThat(payload.getValue().status()).isEqualTo("CANCELLED");
        assertThat(payload.getValue().reason()).isEqualTo("Venció: el anfitrión no respondió a tiempo");
        verify(audit).record(eq(null), eq("BOOKING_EXPIRED"), eq("booking"), eq(booking.getId()),
                eq(Map.of("status", "PENDING")), any(), eq(AuditSource.SYSTEM));
        verify(audit, never()).recordUserAction(any(), any(), any(), any(), any());
    }

    @Test
    void bookingAnsweredMeanwhileIsNotExpired() {
        Booking booking = booking();
        booking.confirm();
        when(bookings.findByIdForUpdate(booking.getId())).thenReturn(Optional.of(booking));

        assertThat(service.expire(booking.getId(), "Venció")).isFalse();

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verifyNoInteractions(outbox, audit);
    }

    @Test
    void expiringAMissingBookingIs404() {
        UUID bookingId = UUID.randomUUID();
        when(bookings.findByIdForUpdate(bookingId)).thenReturn(Optional.empty());

        assertStatus(() -> service.expire(bookingId, "Venció"), HttpStatus.NOT_FOUND);
    }

    @Test
    void onlyGuestAndHostCanSeeTheBooking() {
        Booking booking = booking();
        when(bookings.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(properties.findById(propertyId)).thenReturn(Optional.of(property));

        assertThat(service.get(guestId, booking.getId()).id()).isEqualTo(booking.getId());
        assertThat(service.get(hostId, booking.getId()).id()).isEqualTo(booking.getId());
        assertStatus(() -> service.get(UUID.randomUUID(), booking.getId()), HttpStatus.FORBIDDEN);
    }

    @Test
    void hostWithoutPropertiesHasNoBookings() {
        when(properties.findIdsByHost(hostId)).thenReturn(List.of());

        PageResponse<BookingResponse> page = service.listAsHost(hostId, null, 0, 20);

        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isZero();
        verifyNoInteractions(bookings);
    }

    private void givenLocked(Booking booking) {
        when(bookings.findByIdForUpdate(booking.getId())).thenReturn(Optional.of(booking));
        when(properties.findById(propertyId)).thenReturn(Optional.of(property));
    }

    private Booking booking() {
        Booking booking = Booking.pending(propertyId, guestId, CHECK_IN, CHECK_OUT, 2, new BigDecimal("350.00"),
                new BigDecimal("700.00"), new BigDecimal("0.00"), "GTQ", "clave-1");
        ReflectionTestUtils.setField(booking, "id", UUID.randomUUID());
        return booking;
    }

    private CreateBookingRequest request(int guests, String idempotencyKey) {
        return new CreateBookingRequest(propertyId, CHECK_IN, CHECK_OUT, guests, idempotencyKey);
    }

    private static void assertStatus(ThrowingCallable call, HttpStatus expected) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode()).isEqualTo(expected));
    }
}
