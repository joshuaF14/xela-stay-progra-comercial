package com.xelastay.booking;

import java.util.Set;

/** Estados de bookings.status (CHECK de V1). */
public enum BookingStatus {
    PENDING,
    CONFIRMED,
    CANCELLED,
    COMPLETED,
    REJECTED;

    /** Estados que ocupan fechas en el calendario del alojamiento. */
    public static final Set<BookingStatus> ACTIVE = Set.of(PENDING, CONFIRMED);
}
