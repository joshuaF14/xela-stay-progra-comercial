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
 * 12. Mensajes del chat por alojamiento y tiempo de respuesta del anfitrión. Una respuesta es un mensaje del
 * anfitrión cuyo mensaje anterior en la conversación fue del huésped; el tiempo es la diferencia entre ambos.
 */
@Component
public class ChatResponseReport implements ReportDefinition {

    /**
     * Cada mensaje con el remitente y la hora del mensaje anterior de su conversación. Solo se corta al final del
     * periodo: el mensaje anterior puede ser de antes del inicio.
     */
    private static final String ORDERED = """
            WITH ordered AS (
                SELECT m.conversation_id, m.sender_id, m.created_at, c.property_id, p.host_id,
                       LAG(m.sender_id) OVER w AS previous_sender,
                       LAG(m.created_at) OVER w AS previous_at
                FROM messages m
                JOIN conversations c ON c.id = m.conversation_id
                JOIN properties p ON p.id = c.property_id
                WHERE m.created_at < :to
                WINDOW w AS (PARTITION BY m.conversation_id ORDER BY m.created_at)
            )
            """;

    private static final String RESPONSE_MINUTES = """
            ROUND(CAST(AVG(EXTRACT(EPOCH FROM (o.created_at - o.previous_at)) / 60)
                  FILTER (WHERE o.sender_id = o.host_id AND o.previous_sender <> o.host_id) AS numeric), 1)
            """;

    private static final String SQL = ORDERED + """
            SELECT p.title, h.full_name, COUNT(DISTINCT o.conversation_id), COUNT(*),
                   COUNT(*) FILTER (WHERE o.sender_id <> o.host_id),
                   COUNT(*) FILTER (WHERE o.sender_id = o.host_id),
                   %s
            FROM ordered o
            JOIN properties p ON p.id = o.property_id
            LEFT JOIN profiles h ON h.id = o.host_id
            WHERE o.created_at >= :from AND o.created_at < :to
            GROUP BY o.property_id, p.title, h.full_name
            ORDER BY COUNT(*) DESC, p.title
            """.formatted(RESPONSE_MINUTES);

    private static final String TOTALS = ORDERED + """
            SELECT COUNT(DISTINCT o.conversation_id), COUNT(*), %s
            FROM ordered o
            WHERE o.created_at >= :from AND o.created_at < :to
            """.formatted(RESPONSE_MINUTES);

    private final NativeQueries queries;

    public ChatResponseReport(NativeQueries queries) {
        this.queries = queries;
    }

    @Override
    public int number() {
        return 12;
    }

    @Override
    public String id() {
        return "chat-response";
    }

    @Override
    public String title() {
        return "Mensajes del chat y tiempo de respuesta del anfitrión";
    }

    @Override
    public String description() {
        return "Mensajes enviados en el periodo por alojamiento: conversaciones activas, mensajes de huéspedes y del "
                + "anfitrión, y minutos promedio que tarda el anfitrión en responder.";
    }

    @Override
    public ReportTable generate(ReportPeriod period) {
        Map<String, Object> parameters = NativeQueries.period(period);
        List<List<Object>> rows = queries.rows(SQL, parameters);
        List<Object> totals = queries.single(TOTALS, parameters);

        return new ReportTable(List.of(
                ReportColumn.text("Alojamiento"),
                ReportColumn.text("Anfitrión"),
                ReportColumn.integer("Conversaciones"),
                ReportColumn.integer("Mensajes"),
                ReportColumn.integer("De huéspedes"),
                ReportColumn.integer("Del anfitrión"),
                ReportColumn.decimal("Respuesta promedio (min)")),
                rows,
                List.of(
                        new ReportSummary("Conversaciones con actividad", StayDurationReport.value(totals, 0)),
                        new ReportSummary("Mensajes enviados", StayDurationReport.value(totals, 1)),
                        new ReportSummary("Respuesta promedio de anfitriones (min)",
                                StayDurationReport.value(totals, 2))));
    }
}
