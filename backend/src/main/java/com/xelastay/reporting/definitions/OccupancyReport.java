package com.xelastay.reporting.definitions;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.xelastay.reporting.NativeQueries;
import com.xelastay.reporting.ReportColumn;
import com.xelastay.reporting.ReportDefinition;
import com.xelastay.reporting.ReportPeriod;
import com.xelastay.reporting.ReportSummary;
import com.xelastay.reporting.ReportTable;
import com.xelastay.reporting.ReportValues;

import org.springframework.stereotype.Component;

/** 3. Ocupación por alojamiento: noches reservadas sobre los días del periodo (bi.ocupacion_por_alojamiento). */
@Component
public class OccupancyReport implements ReportDefinition {

    /** Todos los alojamientos vigentes, aunque no tengan noches reservadas (ocupación 0 %). */
    private static final String SQL = """
            SELECT d.titulo, d.municipio, COALESCE(o.noches, 0)
            FROM bi.dim_alojamientos d
            LEFT JOIN (
                SELECT alojamiento_id, COUNT(*) AS noches
                FROM bi.ocupacion_por_alojamiento
                WHERE fecha >= :start AND fecha <= :end
                GROUP BY alojamiento_id
            ) o ON o.alojamiento_id = d.alojamiento_id
            WHERE NOT d.eliminado
            ORDER BY COALESCE(o.noches, 0) DESC, d.titulo
            """;

    private final NativeQueries queries;

    public OccupancyReport(NativeQueries queries) {
        this.queries = queries;
    }

    @Override
    public int number() {
        return 3;
    }

    @Override
    public String id() {
        return "ocupacion-por-alojamiento";
    }

    @Override
    public String title() {
        return "Ocupación por alojamiento (%)";
    }

    @Override
    public String description() {
        return "Noches reservadas (reservas confirmadas o completadas) dentro del periodo sobre los días del periodo, "
                + "por alojamiento vigente.";
    }

    @Override
    public ReportTable generate(ReportPeriod period) {
        long days = BiReportSupport.dayCount(period);
        List<List<Object>> rows = new ArrayList<>();
        for (List<Object> row : queries.rows(SQL, BiReportSupport.days(period))) {
            long nights = ReportValues.toLong(row.get(2));
            List<Object> out = new ArrayList<>(row);
            out.add(days);
            out.add(BiReportSupport.percent(nights, days));
            rows.add(out);
        }
        long totalNights = BiReportSupport.sumLong(rows, 2);
        BigDecimal average = rows.isEmpty() ? null : rows.stream()
                .map(row -> (BigDecimal) row.get(4))
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(rows.size()), 1, RoundingMode.HALF_UP);

        return new ReportTable(List.of(
                ReportColumn.text("Alojamiento"),
                ReportColumn.text("Municipio"),
                ReportColumn.integer("Noches reservadas"),
                ReportColumn.integer("Días del periodo"),
                ReportColumn.percent("Ocupación")),
                rows,
                List.of(
                        new ReportSummary("Alojamientos", (long) rows.size()),
                        new ReportSummary("Noches reservadas", totalNights),
                        new ReportSummary("Ocupación promedio (%)", average)));
    }
}
