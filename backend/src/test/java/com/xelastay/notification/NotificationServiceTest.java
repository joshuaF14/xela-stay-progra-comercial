package com.xelastay.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.xelastay.events.BookingEventPayload;
import com.xelastay.events.EventEnvelope;
import com.xelastay.events.EventTypes;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
    private final UUID bookingId = UUID.randomUUID();
    private final UUID guestId = UUID.randomUUID();
    private final UUID hostId = UUID.randomUUID();

    @Mock
    private NotificationRepository repository;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(repository, objectMapper);
    }

    @Test
    void confirmedBookingNotifiesGuestAndHost() {
        when(repository.insertIfAbsent(any(), any(), any(), any(), any())).thenReturn(1);
        EventEnvelope event = event(EventTypes.BOOKING_CONFIRMED, "CONFIRMED", null);

        int created = service.notifyBookingEvent(event);

        assertThat(created).isEqualTo(2);
        verify(repository).insertIfAbsent(eq(NotificationService.notificationId(event.eventKey(), guestId)),
                eq(guestId), eq("BOOKING_CONFIRMED"), eq("Reserva confirmada"), contains("«Casa en Xela»"));
        verify(repository).insertIfAbsent(eq(NotificationService.notificationId(event.eventKey(), hostId)),
                eq(hostId), eq("BOOKING_CONFIRMED"), anyString(), contains("04/11/2026 al 06/11/2026"));
    }

    @Test
    void redeliveredEventDoesNotCreateDuplicates() {
        when(repository.insertIfAbsent(any(), any(), any(), any(), any())).thenReturn(1, 1, 0, 0);
        EventEnvelope event = event(EventTypes.BOOKING_CREATED, "CREATED", null);

        assertThat(service.notifyBookingEvent(event)).isEqualTo(2);
        assertThat(service.notifyBookingEvent(event)).isZero();
    }

    @Test
    void notificationIdDependsOnEventAndRecipient() {
        String key = "booking:" + bookingId + ":CONFIRMED";

        assertThat(NotificationService.notificationId(key, guestId))
                .isEqualTo(NotificationService.notificationId(key, guestId))
                .isNotEqualTo(NotificationService.notificationId(key, hostId))
                .isNotEqualTo(NotificationService.notificationId("booking:" + bookingId + ":CANCELLED", guestId));
    }

    @Test
    void cancellationIncludesTheReason() {
        when(repository.insertIfAbsent(any(), any(), any(), any(), any())).thenReturn(1);

        service.notifyBookingEvent(event(EventTypes.BOOKING_CANCELLED, "CANCELLED", "Cambio de planes"));

        verify(repository).insertIfAbsent(any(), eq(guestId), eq("BOOKING_CANCELLED"), eq("Reserva cancelada"),
                contains("Motivo: Cambio de planes"));
    }

    @Test
    void unknownEventTypeIsIgnored() {
        assertThat(service.notifyBookingEvent(event("booking.archived", "ARCHIVED", null))).isZero();
        verifyNoInteractions(repository);
    }

    @Test
    void eventWithoutGuestFailsSoItEndsInTheDlq() {
        EventEnvelope event = new EventEnvelope(UUID.randomUUID(), "booking:" + bookingId + ":CREATED",
                EventTypes.BOOKING_CREATED, EventTypes.BOOKING_AGGREGATE, bookingId, Instant.now(),
                Map.of("bookingId", bookingId.toString(), "hostId", hostId.toString()));

        assertThatThrownBy(() -> service.notifyBookingEvent(event)).isInstanceOf(NullPointerException.class);
        verifyNoInteractions(repository);
    }

    private EventEnvelope event(String eventType, String action, String reason) {
        BookingEventPayload payload = new BookingEventPayload(bookingId, UUID.randomUUID(), "Casa en Xela", guestId,
                hostId, LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 6), action, reason);
        Map<String, Object> body = objectMapper.convertValue(payload, new TypeReference<Map<String, Object>>() {
        });
        return new EventEnvelope(UUID.randomUUID(), "booking:" + bookingId + ":" + action, eventType,
                EventTypes.BOOKING_AGGREGATE, bookingId, Instant.now(), body);
    }
}
