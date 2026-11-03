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

/** 11. Cuántas reseñas de 1 a 5 estrellas recibió cada alojamiento y su promedio. */
@Component
public class RatingsDistributionReport implements ReportDefinition {

    private static final String FILTER = "WHERE r.created_at >= :from AND r.created_at < :to";

    private static final String SQL = """
            SELECT p.title, COUNT(*), ROUND(AVG(r.rating), 2),
                   COUNT(*) FILTER (WHERE r.rating = 5),
                   COUNT(*) FILTER (WHERE r.rating = 4),
                   COUNT(*) FILTER (WHERE r.rating = 3),
                   COUNT(*) FILTER (WHERE r.rating = 2),
                   COUNT(*) FILTER (WHERE r.rating = 1)
            FROM reviews r
            JOIN properties p ON p.id = r.property_id
            %s
            GROUP BY p.id, p.title
            ORDER BY AVG(r.rating) DESC, COUNT(*) DESC, p.title
            """.formatted(FILTER);

    private static final String TOTALS = """
            SELECT COUNT(*), ROUND(AVG(r.rating), 2), COUNT(DISTINCT r.property_id)
            FROM reviews r
            %s
            """.formatted(FILTER);

    private final NativeQueries queries;

    public RatingsDistributionReport(NativeQueries queries) {
        this.queries = queries;
    }

    @Override
    public int number() {
        return 11;
    }

    @Override
    public String id() {
        return "ratings-distribution";
    }

    @Override
    public String title() {
        return "Distribución de calificaciones por alojamiento";
    }

    @Override
    public String description() {
        return "Reseñas publicadas en el periodo: cantidad por calificación (1 a 5) y promedio de cada alojamiento.";
    }

    @Override
    public ReportTable generate(ReportPeriod period) {
        Map<String, Object> parameters = NativeQueries.period(period);
        List<List<Object>> rows = queries.rows(SQL, parameters);
        List<Object> totals = queries.single(TOTALS, parameters);

        return new ReportTable(List.of(
                ReportColumn.text("Alojamiento"),
                ReportColumn.integer("Reseñas"),
                ReportColumn.decimal("Promedio"),
                ReportColumn.integer("5 estrellas"),
                ReportColumn.integer("4 estrellas"),
                ReportColumn.integer("3 estrellas"),
                ReportColumn.integer("2 estrellas"),
                ReportColumn.integer("1 estrella")),
                rows,
                List.of(
                        new ReportSummary("Reseñas en el periodo", StayDurationReport.value(totals, 0)),
                        new ReportSummary("Calificación promedio general", StayDurationReport.value(totals, 1)),
                        new ReportSummary("Alojamientos reseñados", StayDurationReport.value(totals, 2))));
    }
}
