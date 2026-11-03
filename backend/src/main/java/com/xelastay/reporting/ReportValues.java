package com.xelastay.reporting;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * Normaliza los valores que devuelve el SQL nativo (Timestamp, OffsetDateTime, java.sql.Date, Long, BigDecimal…)
 * a tipos estables, y les da formato para PDF y para el ancho de las columnas.
 */
public final class ReportValues {

    /** Formato numérico de Guatemala: coma para miles y punto decimal. */
    private static final Locale NUMBERS = Locale.forLanguageTag("es-GT");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private ReportValues() {
    }

    /** Fechas con hora → Instant; fechas → LocalDate; números → Long o BigDecimal; UUID → texto. */
    public static Object normalize(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof OffsetDateTime offset) {
            return offset.toInstant();
        }
        if (value instanceof ZonedDateTime zoned) {
            return zoned.toInstant();
        }
        if (value instanceof LocalDateTime local) {
            return local.atZone(ReportPeriod.ZONE).toInstant();
        }
        if (value instanceof Date date) {
            return date.toLocalDate();
        }
        if (value instanceof Integer || value instanceof Short || value instanceof Byte) {
            return ((Number) value).longValue();
        }
        if (value instanceof Double || value instanceof Float) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        }
        if (value instanceof UUID uuid) {
            return uuid.toString();
        }
        if (value instanceof Character character) {
            return character.toString();
        }
        return value;
    }

    public static Long toLong(Object value) {
        Object normalized = normalize(value);
        if (normalized == null) {
            return 0L;
        }
        return normalized instanceof Number number ? number.longValue() : Long.parseLong(normalized.toString());
    }

    public static BigDecimal toDecimal(Object value) {
        Object normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        if (normalized instanceof BigDecimal decimal) {
            return decimal;
        }
        if (normalized instanceof Number number) {
            return BigDecimal.valueOf(number.longValue());
        }
        return new BigDecimal(normalized.toString());
    }

    /** Texto para PDF y para estimar anchos de columna. */
    public static String format(Object value, ReportColumn.Type type) {
        Object normalized = normalize(value);
        if (normalized == null) {
            return "";
        }
        return switch (type) {
            case INTEGER -> normalized instanceof Number number ? String.format(NUMBERS, "%,d", number.longValue())
                    : normalized.toString();
            case DECIMAL -> String.format(NUMBERS, "%,.2f", toDecimal(normalized).setScale(2, RoundingMode.HALF_UP));
            case PERCENT -> String.format(NUMBERS, "%.1f %%", toDecimal(normalized));
            case DATE -> normalized instanceof LocalDate date ? DATE.format(date) : normalized.toString();
            case DATETIME -> normalized instanceof Instant instant
                    ? DATE_TIME.format(instant.atZone(ReportPeriod.ZONE))
                    : normalized.toString();
            case TEXT -> normalized.toString();
        };
    }
}
