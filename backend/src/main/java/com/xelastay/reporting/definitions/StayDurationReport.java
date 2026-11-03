package com.xelastay.reporting.definitions;

import java.util.List;
import java.util.Map;

import com.xelastay.reporting.NativeQueries;
import com.xelastay.reporting.ReportColumn;
import com.xelastay.reporting.ReportDefinition;
import com.xelastay.reporting.ReportPeriod;
import com.xelastay.reporting.ReportSummary;
import com.xelastay.reporting.ReportTable;

import org.springframework.stereotype.Component;

/** 10. Noches por estancia y días de anticipación con que se reserva, por alojamiento. */
@Component
public class StayDurationReport implements ReportDefinition {

    private static final String FILTER = """
            WHERE b.status IN ('CONFIRMED', 'COMPLETED') AND b.created_at >= :from AND b.created_at < :to
            """;

    /** Días entre la fecha en que se hizo la reserva (hora de Guatemala) y la fecha de entrada. */
    private static final String LEAD_DAYS =
            "b.check_in - CAST(b.created_at AT TIME ZONE 'America/Guatemala' AS date)";

    private static final String SQL = """
            SELECT p.title, l.municipality, COUNT(*),
                   ROUND(AVG(b.check_out - b.check_in), 1),
                   MIN(b.check_out - b.check_in),
                   MAX(b.check_out - b.check_in),
                   ROUND(AVG(%s), 1)
            FROM bookings b
            JOIN properties p ON p.id = b.property_id
            JOIN locations l ON l.id = p.location_id
            %s
            GROUP BY p.id, p.title, l.municipality
            ORDER BY COUNT(*) DESC, p.title
            """.formatted(LEAD_DAYS, FILTER);

    private static final String TOTALS = """
            SELECT COUNT(*), ROUND(AVG(b.check_out - b.check_in), 1), ROUND(AVG(%s), 1)
            FROM bookings b
            %s
            """.formatted(LEAD_DAYS, FILTER);

    private final NativeQueries queries;

    public StayDurationReport(NativeQueries queries) {
        this.queries = queries;
    }

    @Override
    public int number() {
        return 10;
    }

    @Override
    public String id() {
        return "stay-duration";
    }

    @Override
    public String title() {
        return "Duración promedio de estancia y anticipación de reserva";
    }

    @Override
    public String description() {
        return "Reservas confirmadas o completadas creadas en el periodo: noches por estancia y días entre la "
                + "reserva y la fecha de entrada, por alojamiento.";
    }

    @Override
    public ReportTable generate(ReportPeriod period) {
        Map<String, Object> parameters = NativeQueries.period(period);
        List<List<Object>> rows = queries.rows(SQL, parameters);
        List<Object> totals = queries.single(TOTALS, parameters);

        return new ReportTable(List.of(
                ReportColumn.text("Alojamiento"),
                ReportColumn.text("Municipio"),
                ReportColumn.integer("Reservas"),
                ReportColumn.decimal("Noches promedio"),
                ReportColumn.integer("Mínimo de noches"),
                ReportColumn.integer("Máximo de noches"),
                ReportColumn.decimal("Anticipación promedio (días)")),
                rows,
                List.of(
                        new ReportSummary("Reservas analizadas", value(totals, 0)),
                        new ReportSummary("Noches promedio (todas)", value(totals, 1)),
                        new ReportSummary("Anticipación promedio en días (todas)", value(totals, 2))));
    }

    static Object value(List<Object> row, int index) {
        return row.size() > index ? row.get(index) : null;
    }
}
