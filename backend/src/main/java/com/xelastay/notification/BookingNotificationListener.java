package com.xelastay.notification;

import com.xelastay.events.EventEnvelope;
import com.xelastay.events.EventTopology;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Consumidor de booking.notifications. Si falla 3 veces, el mensaje pasa a booking.notifications.dlq.
 */
@Component
@ConditionalOnProperty(prefix = "xelastay.events", name = "enabled", havingValue = "true", matchIfMissing = true)
public class BookingNotificationListener {

    private final NotificationService notificationService;

    public BookingNotificationListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = EventTopology.BOOKING_NOTIFICATIONS_QUEUE)
    public void onBookingEvent(EventEnvelope event) {
        notificationService.notifyBookingEvent(event);
    }
}
