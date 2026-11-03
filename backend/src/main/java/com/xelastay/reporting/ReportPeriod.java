package com.xelastay.reporting;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Periodo de un reporte: del día {@code start} al día {@code end}, ambos incluidos, en hora de Guatemala.
 * Para las consultas se usa el intervalo [{@link #from()}, {@link #toExclusive()}).
 */
public record ReportPeriod(LocalDate start, LocalDate end) {

    public static final ZoneId ZONE = ZoneId.of("America/Guatemala");

    public ReportPeriod {
        if (start == null || end == null) {
            throw new IllegalArgumentException("El periodo necesita fecha inicial y final");
        }
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("La fecha final no puede ser anterior a la inicial");
        }
    }

    /** Primer instante del periodo (00:00 del día inicial). */
    public Instant from() {
        return start.atStartOfDay(ZONE).toInstant();
    }

    /** Primer instante después del periodo (00:00 del día siguiente al final). */
    public Instant toExclusive() {
        return end.plusDays(1).atStartOfDay(ZONE).toInstant();
    }
}
