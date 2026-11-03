package com.xelastay.reporting;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.xelastay.reporting.dto.ReportResult;

/** Reportes de ejemplo para las pruebas de exportación y del controlador. */
final class ReportFixtures {

    static final Instant CREATED = Instant.parse("2026-10-15T20:30:00Z");

    private ReportFixtures() {
    }

    /** Un reporte con una columna de cada tipo (6 columnas: el PDF sale horizontal). */
    static ReportResult allTypes() {
        List<ReportColumn> columns = List.of(
                ReportColumn.text("Alojamiento"),
                ReportColumn.integer("Reservas"),
                ReportColumn.decimal("Total"),
                ReportColumn.percent("% leídas"),
                ReportColumn.date("Entrada"),
                ReportColumn.dateTime("Fecha"));
        List<List<Object>> rows = new ArrayList<>();
        rows.add(Arrays.asList("Casa Xelajú", 1234L, new BigDecimal("1500.50"), new BigDecimal("75.0"),
                LocalDate.of(2026, 12, 24), CREATED));
        rows.add(Arrays.asList("Cabaña sin datos", null, null, null, null, null));
        return new ReportResult(9, "cancellations", "Cancelaciones y rechazos, con motivo",
                "Reservas canceladas o rechazadas.", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31),
                CREATED, columns, rows,
                List.of(new ReportSummary("Cancelaciones", 2L), new ReportSummary("Monto", new BigDecimal("1500.50")),
                        new ReportSummary("Sin dato", null)));
    }

    /** Un reporte de 2 columnas sin filas. */
    static ReportResult empty() {
        return new ReportResult(13, "notifications-by-type", "Notificaciones enviadas por tipo", "Sin filas.",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), CREATED,
                List.of(ReportColumn.text("Tipo"), ReportColumn.integer("Enviadas")), List.of(), List.of());
    }
}
