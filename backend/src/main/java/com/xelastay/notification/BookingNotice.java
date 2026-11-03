package com.xelastay.notification;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import com.xelastay.events.BookingEventPayload;
import com.xelastay.events.EventTypes;

/** Textos de la notificación que reciben el huésped y el anfitrión por cada evento de reserva. */
record BookingNotice(String type, String guestTitle, String guestMessage, String hostTitle, String hostMessage) {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    static Optional<BookingNotice> forEvent(String eventType, BookingEventPayload booking) {
        String stay = "«%s» (%s al %s)".formatted(
                booking.propertyTitle(), format(booking.checkIn()), format(booking.checkOut()));
        String reason = booking.reason() == null || booking.reason().isBlank()
                ? ""
                : " Motivo: " + booking.reason().trim();

        BookingNotice notice = switch (eventType) {
            case EventTypes.BOOKING_CREATED -> new BookingNotice("BOOKING_CREATED",
                    "Solicitud de reserva enviada",
                    "Tu solicitud para " + stay + " está pendiente de confirmación del anfitrión.",
                    "Nueva solicitud de reserva",
                    "Recibiste una solicitud de reserva para " + stay + ".");
            case EventTypes.BOOKING_CONFIRMED -> new BookingNotice("BOOKING_CONFIRMED",
                    "Reserva confirmada",
                    "El anfitrión confirmó tu reserva en " + stay + ".",
                    "Reserva confirmada",
                    "Confirmaste la reserva de " + stay + ".");
            case EventTypes.BOOKING_REJECTED -> new BookingNotice("BOOKING_REJECTED",
                    "Reserva rechazada",
                    "El anfitrión rechazó tu solicitud para " + stay + "." + reason,
                    "Reserva rechazada",
                    "Rechazaste la solicitud para " + stay + "." + reason);
            case EventTypes.BOOKING_CANCELLED -> new BookingNotice("BOOKING_CANCELLED",
                    "Reserva cancelada",
                    "La reserva de " + stay + " fue cancelada." + reason,
                    "Reserva cancelada",
                    "La reserva de " + stay + " fue cancelada." + reason);
            case EventTypes.BOOKING_COMPLETED -> new BookingNotice("BOOKING_COMPLETED",
                    "Estancia completada",
                    "Tu estancia en " + stay + " terminó. Ya puedes dejar tu reseña.",
                    "Reserva completada",
                    "La reserva de " + stay + " se marcó como completada.");
            default -> null;
        };
        return Optional.ofNullable(notice);
    }

    private static String format(LocalDate date) {
        return date == null ? "?" : DATE.format(date);
    }
}
