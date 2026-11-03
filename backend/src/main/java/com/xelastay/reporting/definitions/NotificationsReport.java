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

import org.springframework.stereotype.Component;

/** 13. Notificaciones enviadas por tipo y cuántas se leyeron. */
@Component
public class NotificationsReport implements ReportDefinition {

    static final Map<String, String> LABELS = Map.of(
            "BOOKING_CREATED", "Solicitud de reserva",
            "BOOKING_CONFIRMED", "Reserva confirmada",
            "BOOKING_REJECTED", "Reserva rechazada",
            "BOOKING_CANCELLED", "Reserva cancelada",
            "BOOKING_COMPLETED", "Estancia completada");

    private static final String FILTER = "WHERE n.created_at >= :from AND n.created_at < :to";

    private static final String SQL = """
            SELECT n.type, COUNT(*), COUNT(n.read_at), COUNT(*) - COUNT(n.read_at),
                   ROUND(100.0 * COUNT(n.read_at) / COUNT(*), 1), MIN(n.created_at), MAX(n.created_at)
            FROM notifications n
            %s
            GROUP BY n.type
            ORDER BY COUNT(*) DESC, n.type
            """.formatted(FILTER);

    private static final String TOTALS = """
            SELECT COUNT(*), COUNT(n.read_at), COUNT(DISTINCT n.profile_id)
            FROM notifications n
            %s
            """.formatted(FILTER);

    private final NativeQueries queries;

    public NotificationsReport(NativeQueries queries) {
        this.queries = queries;
    }

    @Override
    public int number() {
        return 13;
    }

    @Override
    public String id() {
        return "notifications-by-type";
    }

    @Override
    public String title() {
        return "Notificaciones enviadas por tipo";
    }

    @Override
    public String description() {
        return "Notificaciones creadas en el periodo, por tipo: enviadas, leídas, sin leer y porcentaje de lectura.";
    }

    @Override
    public ReportTable generate(ReportPeriod period) {
        Map<String, Object> parameters = NativeQueries.period(period);
        List<List<Object>> rows = queries.rows(SQL, parameters).stream().map(NotificationsReport::label).toList();
        List<Object> totals = queries.single(TOTALS, parameters);

        return new ReportTable(List.of(
                ReportColumn.text("Tipo"),
                ReportColumn.integer("Enviadas"),
                ReportColumn.integer("Leídas"),
                ReportColumn.integer("Sin leer"),
                ReportColumn.percent("% leídas"),
                ReportColumn.dateTime("Primera"),
                ReportColumn.dateTime("Última")),
                rows,
                List.of(
                        new ReportSummary("Notificaciones enviadas", StayDurationReport.value(totals, 0)),
                        new ReportSummary("Leídas", StayDurationReport.value(totals, 1)),
                        new ReportSummary("Usuarios notificados", StayDurationReport.value(totals, 2))));
    }

    private static List<Object> label(List<Object> row) {
        List<Object> labeled = new ArrayList<>(row);
        Object type = row.get(0);
        labeled.set(0, type == null ? "" : LABELS.getOrDefault(type.toString(), type.toString()));
        return labeled;
    }
}
