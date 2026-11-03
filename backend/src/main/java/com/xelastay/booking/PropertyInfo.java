package com.xelastay.booking;

import java.math.BigDecimal;
import java.util.UUID;

/** Datos de properties que necesita el módulo de reservas (se leen con SQL nativo, sin la entidad Property). */
public record PropertyInfo(
        UUID id,
        UUID hostId,
        String title,
        BigDecimal pricePerNight,
        String currency,
        int capacity,
        String status) {
}
