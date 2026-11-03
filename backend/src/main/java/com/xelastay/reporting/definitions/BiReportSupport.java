package com.xelastay.reporting.definitions;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.xelastay.reporting.ReportPeriod;
import com.xelastay.reporting.ReportValues;

/**
 * Apoyo de los reportes 1 a 8, que leen las vistas del esquema bi (V3). Las vistas mensuales guardan en "mes" el
 * primer día del mes; las diarias, la fecha del día.
 */
final class BiReportSupport {

    private BiReportSupport() {
    }

    /** :fromMonth (primer día del mes inicial) y :end: meses que se cruzan con el periodo. */
    static Map<String, Object> months(ReportPeriod period) {
        return Map.of("fromMonth", period.start().withDayOfMonth(1), "end", period.end());
    }

    /** :start y :end: días del periodo, ambos incluidos. */
    static Map<String, Object> days(ReportPeriod period) {
        return Map.of("start", period.start(), "end", period.end());
    }

    static long dayCount(ReportPeriod period) {
        return ChronoUnit.DAYS.between(period.start(), period.end()) + 1;
    }

    /** "2026-11" a partir del primer día del mes. */
    static String monthLabel(Object value) {
        Object normalized = ReportValues.normalize(value);
        return normalized instanceof LocalDate date ? YearMonth.from(date).toString() : Objects.toString(normalized, "");
    }

    /** Copia de la fila con la primera columna (el mes) como "AAAA-MM". */
    static List<Object> withMonthLabel(List<Object> row) {
        List<Object> copy = new ArrayList<>(row);
        copy.set(0, monthLabel(row.get(0)));
        return copy;
    }

    /** Copia de la fila con la columna {@code index} traducida. */
    static List<Object> translated(List<Object> row, int index, Map<String, String> labels) {
        List<Object> copy = new ArrayList<>(row);
        Object value = row.get(index);
        copy.set(index, value == null ? null : labels.getOrDefault(value.toString(), value.toString()));
        return copy;
    }

    /** Valor de la fila de totales; null si la consulta no devolvió fila. */
    static Object value(List<Object> row, int index) {
        return row.size() > index ? row.get(index) : null;
    }

    static long sumLong(List<List<Object>> rows, int index) {
        return rows.stream().mapToLong(row -> ReportValues.toLong(row.get(index))).sum();
    }

    static BigDecimal sumDecimal(List<List<Object>> rows, int index) {
        return rows.stream()
                .map(row -> ReportValues.toDecimal(row.get(index)))
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** part / total * 100 con 1 decimal; null si total es 0. */
    static BigDecimal percent(long part, long total) {
        if (total == 0) {
            return null;
        }
        return BigDecimal.valueOf(part * 100L).divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
    }

    static final Map<String, String> PROPERTY_STATUS = Map.of(
            "DRAFT", "Borrador",
            "PUBLISHED", "Publicado",
            "PAUSED", "Pausado",
            "REJECTED", "Rechazado");

    static final Map<String, String> PAYMENT_STATUS = Map.of(
            "PENDING", "Pendiente",
            "APPROVED", "Aprobado",
            "REJECTED", "Rechazado",
            "REFUNDED", "Reembolsado");

    static final Map<String, String> PAYMENT_METHOD = Map.of(
            "CARD", "Tarjeta",
            "TRANSFER", "Transferencia",
            "CASH", "Efectivo");
}
