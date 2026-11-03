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

/**
 * 14. Uso del agente de IA (resumen) y documentos más citados (tabla). Las citas salen del JSON de fuentes que
 * guarda cada fila de ai_interactions.
 */
@Component
public class AiUsageReport implements ReportDefinition {

    private static final String SQL = """
            SELECT COALESCE(d.title, s ->> 'documentTitle'), COALESCE(d.document_type, '-'),
                   COUNT(*), COUNT(DISTINCT i.id), MAX(i.created_at)
            FROM ai_interactions i
            CROSS JOIN LATERAL jsonb_array_elements(i.sources) AS s
            LEFT JOIN knowledge_documents d ON CAST(d.id AS text) = s ->> 'documentId'
            WHERE i.created_at >= :from AND i.created_at < :to AND s ->> 'type' = 'DOCUMENT'
            GROUP BY s ->> 'documentId', COALESCE(d.title, s ->> 'documentTitle'), COALESCE(d.document_type, '-')
            ORDER BY COUNT(*) DESC
            """;

    private static final String TOTALS = """
            SELECT COUNT(*), COUNT(DISTINCT i.profile_id),
                   COUNT(*) FILTER (WHERE jsonb_array_length(i.sources) = 0),
                   ROUND(AVG(jsonb_array_length(i.sources)), 1)
            FROM ai_interactions i
            WHERE i.created_at >= :from AND i.created_at < :to
            """;

    private final NativeQueries queries;

    public AiUsageReport(NativeQueries queries) {
        this.queries = queries;
    }

    @Override
    public int number() {
        return 14;
    }

    @Override
    public String id() {
        return "ai-usage";
    }

    @Override
    public String title() {
        return "Uso del agente de IA y documentos más citados";
    }

    @Override
    public String description() {
        return "Preguntas hechas al agente en el periodo y documentos de la base de conocimiento que más usó para "
                + "responder.";
    }

    @Override
    public ReportTable generate(ReportPeriod period) {
        Map<String, Object> parameters = NativeQueries.period(period);
        List<List<Object>> rows = queries.rows(SQL, parameters);
        List<Object> totals = queries.single(TOTALS, parameters);

        return new ReportTable(List.of(
                ReportColumn.text("Documento"),
                ReportColumn.text("Tipo"),
                ReportColumn.integer("Fragmentos citados"),
                ReportColumn.integer("Respuestas que lo citan"),
                ReportColumn.dateTime("Última cita")),
                rows,
                List.of(
                        new ReportSummary("Preguntas al agente", StayDurationReport.value(totals, 0)),
                        new ReportSummary("Usuarios que preguntaron", StayDurationReport.value(totals, 1)),
                        new ReportSummary("Preguntas sin fuentes", StayDurationReport.value(totals, 2)),
                        new ReportSummary("Fuentes promedio por respuesta", StayDurationReport.value(totals, 3)),
                        new ReportSummary("Documentos citados", (long) rows.size())));
    }
}
