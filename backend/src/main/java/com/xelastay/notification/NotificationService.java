package com.xelastay.notification;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xelastay.events.BookingEventPayload;
import com.xelastay.events.EventEnvelope;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private int insert(String eventKey, UUID profileId, String type, String title, String message) {
        return repository.insertIfAbsent(notificationId(eventKey, profileId), profileId, type, title, message);
    }

    /** ID determinista (UUID v3) a partir del evento y el destinatario. */
    static UUID notificationId(String eventKey, UUID profileId) {
        return UUID.nameUUIDFromBytes((eventKey + "|" + profileId).getBytes(StandardCharsets.UTF_8));
    }
}
