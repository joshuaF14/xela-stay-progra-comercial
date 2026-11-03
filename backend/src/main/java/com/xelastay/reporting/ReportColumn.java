package com.xelastay.reporting;

/**
 * Columna de un reporte. El tipo decide el formato en Excel y PDF.
 */
public record ReportColumn(String header, Type type) {

    public enum Type {
        /** Texto libre. */
        TEXT,
        /** Número entero (conteos). */
        INTEGER,
        /** Número con 2 decimales (montos, promedios). */
        DECIMAL,
        /** Porcentaje de 0 a 100. */
        PERCENT,
        /** Fecha sin hora ({@code LocalDate}). */
        DATE,
        /** Fecha y hora; se muestra en hora de Guatemala. */
        DATETIME
    }

    public static ReportColumn text(String header) {
        return new ReportColumn(header, Type.TEXT);
    }

    public static ReportColumn integer(String header) {
        return new ReportColumn(header, Type.INTEGER);
    }

    public static ReportColumn decimal(String header) {
        return new ReportColumn(header, Type.DECIMAL);
    }

    public static ReportColumn percent(String header) {
        return new ReportColumn(header, Type.PERCENT);
    }

    public static ReportColumn date(String header) {
        return new ReportColumn(header, Type.DATE);
    }

    public static ReportColumn dateTime(String header) {
        return new ReportColumn(header, Type.DATETIME);
    }
}
