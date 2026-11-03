package com.xelastay.notification;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xelastay.common.PageResponse;
import com.xelastay.events.BookingEventPayload;
import com.xelastay.events.EventEnvelope;
import com.xelastay.notification.dto.NotificationResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository repository;
    private final ObjectMapper objectMapper;

    public NotificationService(NotificationRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * Crea la notificación del huésped y la del anfitrión para un evento de reserva. Es idempotente: si el
     * mismo evento llega dos veces, la segunda no inserta nada.
     *
     * @return cuántas notificaciones nuevas se crearon
     */
    @Transactional
    public int notifyBookingEvent(EventEnvelope event) {
        BookingEventPayload booking = objectMapper.convertValue(event.payload(), BookingEventPayload.class);
        Optional<BookingNotice> notice = BookingNotice.forEvent(event.eventType(), booking);
        if (notice.isEmpty()) {
            log.debug("Evento {} sin notificación asociada", event.eventType());
            return 0;
        }
        Objects.requireNonNull(booking.guestId(), "El evento " + event.eventKey() + " no trae guestId");
        Objects.requireNonNull(booking.hostId(), "El evento " + event.eventKey() + " no trae hostId");

        BookingNotice text = notice.get();
        int created = insert(event.eventKey(), booking.guestId(), text.type(), text.guestTitle(), text.guestMessage());
        if (!booking.hostId().equals(booking.guestId())) {
            created += insert(event.eventKey(), booking.hostId(), text.type(), text.hostTitle(), text.hostMessage());
        }
        return created;
    }

    /** Notificaciones del usuario, de la más reciente a la más antigua; con unreadOnly, solo las no leídas. */
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> listMine(UUID profileId, boolean unreadOnly, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Notification> result = unreadOnly
                ? repository.findByProfileIdAndReadAtIsNull(profileId, pageable)
                : repository.findByProfileId(profileId, pageable);
        return PageResponse.of(result, result.getContent().stream().map(NotificationResponse::from).toList());
    }

    /** Solo el destinatario puede marcarla; para cualquier otro usuario la notificación "no existe" (404). */
    @Transactional
    public NotificationResponse markRead(UUID profileId, UUID notificationId) {
        Notification notification = repository.findByIdAndProfileId(notificationId, profileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notificación no encontrada"));
        notification.markRead(Instant.now());
        return NotificationResponse.from(notification);
    }

    private int insert(String eventKey, UUID profileId, String type, String title, String message) {
        return repository.insertIfAbsent(notificationId(eventKey, profileId), profileId, type, title, message);
    }

    /** ID determinista (UUID v3) a partir del evento y el destinatario. */
    static UUID notificationId(String eventKey, UUID profileId) {
        return UUID.nameUUIDFromBytes((eventKey + "|" + profileId).getBytes(StandardCharsets.UTF_8));
    }
}
