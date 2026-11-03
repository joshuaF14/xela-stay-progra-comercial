package com.xelastay.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.UUID;

import com.fasterxml.jackson.databind.SerializationFeature;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class OutboxServiceTest {

    @Mock
    private OutboxEventRepository repository;

    private OutboxService service;

    @BeforeEach
    void setUp() {
        service = new OutboxService(repository, Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build());
    }

    @Test
    void enqueueStoresPendingEventWithJsonPayload() {
        when(repository.save(any(OutboxEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        UUID bookingId = UUID.randomUUID();
        UUID guestId = UUID.randomUUID();
        BookingEventPayload payload = new BookingEventPayload(bookingId, UUID.randomUUID(), "Casa en Xela",
                guestId, UUID.randomUUID(), LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 6), "PENDING", null);
        String key = OutboxService.eventKey(EventTypes.BOOKING_AGGREGATE, bookingId, "CREATED");

        OutboxEvent event = service.enqueue(key, EventTypes.BOOKING_AGGREGATE, bookingId, EventTypes.BOOKING_CREATED,
                payload);

        assertThat(event.getEventKey()).isEqualTo("booking:" + bookingId + ":CREATED");
        assertThat(event.getEventType()).isEqualTo("booking.created");
        assertThat(event.getAggregateId()).isEqualTo(bookingId);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getAttempts()).isZero();
        assertThat(event.getCreatedAt()).isNotNull();
        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getPayload())
                .containsEntry("bookingId", bookingId.toString())
                .containsEntry("guestId", guestId.toString())
                .containsEntry("checkIn", "2026-11-04")
                .containsEntry("propertyTitle", "Casa en Xela");
    }

    @Test
    void enqueueOnlyRunsInsideAnExistingTransaction() throws NoSuchMethodException {
        Transactional transactional = OutboxService.class
                .getMethod("enqueue", String.class, String.class, UUID.class, String.class, Object.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.MANDATORY);
    }

    @Test
    void enqueueRejectsBlankEventKey() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> service.enqueue(" ", EventTypes.BOOKING_AGGREGATE, id, EventTypes.BOOKING_CREATED,
                new Object()))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }
}
