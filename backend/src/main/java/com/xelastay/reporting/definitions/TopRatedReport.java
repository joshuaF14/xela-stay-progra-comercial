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

/** 5. Alojamientos mejor calificados por las reseñas del periodo (bi.alojamientos_mejor_calificados). */
@Component
public class TopRatedReport implements ReportDefinition {

    private static final String FILTER = "WHERE fecha_resena >= :start AND fecha_resena <= :end";

    private static final String SQL = """
            SELECT alojamiento, municipio, COUNT(*), ROUND(AVG(calificacion), 2), MAX(fecha_resena)
            FROM bi.alojamientos_mejor_calificados
            %s
            GROUP BY alojamiento_id, alojamiento, municipio
            ORDER BY AVG(calificacion) DESC, COUNT(*) DESC, alojamiento
            """.formatted(FILTER);

    private static final String TOTALS = """
            SELECT COUNT(*), ROUND(AVG(calificacion), 2), COUNT(DISTINCT alojamiento_id)
            FROM bi.alojamientos_mejor_calificados
            %s
            """.formatted(FILTER);

    private final NativeQueries queries;

    public TopRatedReport(NativeQueries queries) {
        this.queries = queries;
    }

    @Override
    public int number() {
        return 5;
    }

    @Override
    public String id() {
        return "mejor-calificados";
    }

    @Override
    public String title() {
        return "Alojamientos mejor calificados";
    }

    @Override
    public String description() {
        return "Calificación promedio (1 a 5) y cantidad de reseñas por alojamiento, con las reseñas escritas en el "
                + "periodo; de mayor a menor calificación.";
    }

    @Override
    public ReportTable generate(ReportPeriod period) {
        Map<String, Object> parameters = BiReportSupport.days(period);
        List<List<Object>> rows = queries.rows(SQL, parameters);
        List<Object> totals = queries.single(TOTALS, parameters);

        return new ReportTable(List.of(
                ReportColumn.text("Alojamiento"),
                ReportColumn.text("Municipio"),
                ReportColumn.integer("Reseñas"),
                ReportColumn.decimal("Calificación promedio"),
                ReportColumn.date("Última reseña")),
                rows,
                List.of(
                        new ReportSummary("Reseñas", BiReportSupport.value(totals, 0)),
                        new ReportSummary("Calificación promedio general", BiReportSupport.value(totals, 1)),
                        new ReportSummary("Alojamientos reseñados", BiReportSupport.value(totals, 2))));
    }
}
