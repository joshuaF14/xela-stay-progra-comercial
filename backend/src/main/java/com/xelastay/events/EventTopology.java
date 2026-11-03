package com.xelastay.events;

/**
 * Nombres de exchanges, colas y patrones de enrutamiento de RabbitMQ.
 * La clave de enrutamiento de cada mensaje es su tipo de evento (ver {@link EventTypes}).
 */
public final class EventTopology {

    /** Exchange principal (topic) donde se publican todos los eventos del outbox. */
    public static final String EXCHANGE = "xela.events";

    /** Exchange de mensajes que un consumidor no pudo procesar. */
    public static final String DEAD_LETTER_EXCHANGE = "xela.events.dlx";

    /** Cola que crea las notificaciones de reservas para el huésped y el anfitrión. */
    public static final String BOOKING_NOTIFICATIONS_QUEUE = "booking.notifications";

    /** Cola de mensajes fallidos de {@link #BOOKING_NOTIFICATIONS_QUEUE}. */
    public static final String BOOKING_NOTIFICATIONS_DLQ = "booking.notifications.dlq";

    /** Todos los eventos de reservas: booking.created, booking.confirmed, etc. */
    public static final String BOOKING_EVENTS_PATTERN = "booking.#";

    private EventTopology() {
    }
}
