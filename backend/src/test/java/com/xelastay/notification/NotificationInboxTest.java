package com.xelastay.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.xelastay.common.PageResponse;
import com.xelastay.notification.dto.NotificationResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.web.server.ResponseStatusException;

/** Bandeja de notificaciones del usuario (listado y marcar como leída). */
@ExtendWith(MockitoExtension.class)
class NotificationInboxTest {

    @Mock
    private NotificationRepository repository;

    private final UUID userId = UUID.randomUUID();
    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(repository, Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build());
    }

    @Test
    void unreadOnlyListsPendingNotifications() {
        Notification unread = notification();
        when(repository.findByProfileIdAndReadAtIsNull(eq(userId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(unread)));

        PageResponse<NotificationResponse> page = service.listMine(userId, true, 0, 20);

        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.content().get(0).title()).isEqualTo("Reserva confirmada");
        verify(repository, never()).findByProfileId(any(), any());
    }

    @Test
    void markReadSetsTheDateOnce() {
        Notification notification = notification();
        when(repository.findByIdAndProfileId(notification.getId(), userId)).thenReturn(Optional.of(notification));

        Instant firstRead = service.markRead(userId, notification.getId()).readAt();
        Instant secondRead = service.markRead(userId, notification.getId()).readAt();

        assertThat(firstRead).isNotNull();
        assertThat(secondRead).isEqualTo(firstRead);
    }

    @Test
    void otherUsersNotificationIsNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndProfileId(id, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead(userId, id)).isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    private Notification notification() {
        return Notification.create(UUID.randomUUID(), userId, "BOOKING_CONFIRMED", "Reserva confirmada",
                "El anfitrión confirmó tu reserva.");
    }
}
