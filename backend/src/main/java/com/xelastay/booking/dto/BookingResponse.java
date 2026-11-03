package com.xelastay.booking.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.xelastay.booking.Booking;
import com.xelastay.booking.BookingStatus;
import com.xelastay.booking.PropertyInfo;

public record BookingResponse(
        UUID id,
        UUID propertyId,
        String propertyTitle,
        UUID hostId,
        UUID guestId,
        LocalDate checkIn,
        LocalDate checkOut,
        long nights,
        int guestsCount,
        BigDecimal nightlyPrice,
        BigDecimal subtotal,
        BigDecimal fees,
        BigDecimal total,
        String currency,
        BookingStatus status,
        String cancellationReason,
        Instant createdAt,
        Instant updatedAt) {

    /** @param property puede ser null si la propiedad ya no se puede leer */
    public static BookingResponse from(Booking booking, PropertyInfo property) {
        return new BookingResponse(
                booking.getId(),
                booking.getPropertyId(),
                property != null ? property.title() : null,
                property != null ? property.hostId() : null,
                booking.getGuestId(),
                booking.getCheckIn(),
                booking.getCheckOut(),
                booking.nights(),
                booking.getGuestsCount(),
                booking.getNightlyPrice(),
                booking.getSubtotal(),
                booking.getFees(),
                booking.getTotal(),
                booking.getCurrency(),
                booking.getStatus(),
                booking.getCancellationReason(),
                booking.getCreatedAt(),
                booking.getUpdatedAt());
    }
}
