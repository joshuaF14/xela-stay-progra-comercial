package com.xelastay.events;

/**
 * Tipos de evento (columna outbox_events.event_type). Cada tipo es también la clave de enrutamiento
 * con la que se publica en {@link EventTopology#EXCHANGE}.
 */
public final class EventTypes {

    /** Valor de outbox_events.aggregate_type para eventos de reservas. */
    public static final String BOOKING_AGGREGATE = "booking";

    public static final String BOOKING_CREATED = "booking.created";
    public static final String BOOKING_CONFIRMED = "booking.confirmed";
    public static final String BOOKING_REJECTED = "booking.rejected";
    public static final String BOOKING_CANCELLED = "booking.cancelled";
    public static final String BOOKING_COMPLETED = "booking.completed";

    private EventTypes() {
    }
}
