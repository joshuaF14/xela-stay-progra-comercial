package com.xelastay.reporting.definitions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.xelastay.reporting.NativeQueries;
import com.xelastay.reporting.ReportColumn;
import com.xelastay.reporting.ReportDefinition;
import com.xelastay.reporting.ReportPeriod;
import com.xelastay.reporting.ReportSummary;
import com.xelastay.reporting.ReportTable;
import com.xelastay.reporting.ReportValues;

import org.springframework.stereotype.Component;

/** 15. Operaciones registradas en audit_logs, agrupadas por tipo, origen y usuario. */
@Component
public class AuditActivityReport implements ReportDefinition {

    static final Map<String, String> SOURCES = Map.of(
            "SYSTEM", "Sistema",
            "USER", "Usuario",
            "AI", "Agente de IA",
            "ADMIN", "Administrador");

    private static final String FILTER = "WHERE a.created_at >= :from AND a.created_at < :to";

    private static final String SQL = """
            SELECT a.action, a.entity_type, a.source, COALESCE(pr.full_name, 'Sistema'), COUNT(*), MAX(a.created_at)
            FROM audit_logs a
            LEFT JOIN profiles pr ON pr.id = a.actor_id
            %s
            GROUP BY a.action, a.entity_type, a.source, a.actor_id, pr.full_name
            ORDER BY COUNT(*) DESC, a.action
            """.formatted(FILTER);

    private static final String BY_SOURCE = """
            SELECT a.source, COUNT(*)
            FROM audit_logs a
            %s
            GROUP BY a.source
            ORDER BY a.source
            """.formatted(FILTER);

    private final NativeQueries queries;

    public AuditActivityReport(NativeQueries queries) {
        this.queries = queries;
    }

    @Override
    public int number() {
        return 15;
    }

    @Override
    public String id() {
        return "audit-activity";
    }

    @Override
    public String title() {
        return "Auditoría: operaciones por tipo, origen y usuario";
    }

    @Override
    public String description() {
        return "Registros de audit_logs del periodo agrupados por operación, entidad, origen y usuario.";
    }

    @Override
    public ReportTable generate(ReportPeriod period) {
        Map<String, Object> parameters = NativeQueries.period(period);
        List<List<Object>> rows = queries.rows(SQL, parameters).stream().map(AuditActivityReport::label).toList();

        List<ReportSummary> summary = new ArrayList<>();
        long total = 0;
        for (List<Object> row : queries.rows(BY_SOURCE, parameters)) {
            long count = ReportValues.toLong(row.get(1));
            total += count;
            summary.add(new ReportSummary("Origen: " + sourceLabel(row.get(0)), count));
        }
        summary.add(0, new ReportSummary("Total de operaciones", total));

        return new ReportTable(List.of(
                ReportColumn.text("Operación"),
                ReportColumn.text("Entidad"),
                ReportColumn.text("Origen"),
                ReportColumn.text("Usuario"),
                ReportColumn.integer("Cantidad"),
                ReportColumn.dateTime("Última vez")),
                rows,
                summary);
    }

    private static List<Object> label(List<Object> row) {
        List<Object> labeled = new ArrayList<>(row);
        labeled.set(2, sourceLabel(row.get(2)));
        return labeled;
    }

    private static String sourceLabel(Object source) {
        return source == null ? "" : SOURCES.getOrDefault(source.toString(), source.toString());
    }
}
