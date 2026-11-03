package com.xelastay.reporting.definitions;

import java.math.BigDecimal;
import java.util.List;

import com.xelastay.reporting.NativeQueries;
import com.xelastay.reporting.ReportColumn;
import com.xelastay.reporting.ReportDefinition;
import com.xelastay.reporting.ReportPeriod;
import com.xelastay.reporting.ReportSummary;
import com.xelastay.reporting.ReportTable;
import com.xelastay.reporting.ReportValues;

import org.springframework.stereotype.Component;

/** 9. Reservas canceladas o rechazadas en el periodo, con quién lo hizo y el motivo. */
@Component
public class CancellationsReport implements ReportDefinition {

    static final String CANCELLED = "Cancelada";
    static final String REJECTED = "Rechazada";
    static final String NO_REASON = "Sin motivo";

    private static final String SQL = """
            SELECT b.updated_at, p.title, g.full_name, b.check_in, b.check_out,
                   CASE b.status WHEN 'CANCELLED' THEN 'Cancelada' ELSE 'Rechazada' END,
                   CASE WHEN a.actor_id IS NULL THEN 'Sin registro'
                        WHEN a.actor_id = b.guest_id THEN 'Huésped'
                        WHEN a.actor_id = p.host_id THEN 'Anfitrión'
                        ELSE 'Administrador' END,
                   COALESCE(NULLIF(trim(b.cancellation_reason), ''), 'Sin motivo'),
                   b.total
            FROM bookings b
            JOIN properties p ON p.id = b.property_id
            LEFT JOIN profiles g ON g.id = b.guest_id
            LEFT JOIN LATERAL (
                SELECT al.actor_id FROM audit_logs al
                WHERE al.entity_type = 'booking' AND al.entity_id = b.id
                  AND al.action IN ('BOOKING_CANCELLED', 'BOOKING_REJECTED')
                ORDER BY al.created_at DESC
                LIMIT 1
            ) a ON TRUE
            WHERE b.status IN ('CANCELLED', 'REJECTED') AND b.updated_at >= :from AND b.updated_at < :to
            ORDER BY b.updated_at DESC
            """;

    private final NativeQueries queries;

    public CancellationsReport(NativeQueries queries) {
        this.queries = queries;
    }

    @Override
    public int number() {
        return 9;
    }

    @Override
    public String id() {
        return "cancellations";
    }

    @Override
    public String title() {
        return "Cancelaciones y rechazos, con motivo";
    }

    @Override
    public String description() {
        return "Reservas que pasaron a canceladas o rechazadas en el periodo (según su última actualización), "
                + "con quién lo hizo y el motivo registrado.";
    }

    @Override
    public ReportTable generate(ReportPeriod period) {
        List<List<Object>> rows = queries.rows(SQL, NativeQueries.period(period));
        long cancelled = rows.stream().filter(row -> CANCELLED.equals(row.get(5))).count();
        long rejected = rows.stream().filter(row -> REJECTED.equals(row.get(5))).count();
        long withoutReason = rows.stream().filter(row -> NO_REASON.equals(row.get(7))).count();
        BigDecimal amount = rows.stream()
                .map(row -> ReportValues.toDecimal(row.get(8)))
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new ReportTable(List.of(
                ReportColumn.dateTime("Fecha"),
                ReportColumn.text("Alojamiento"),
                ReportColumn.text("Huésped"),
                ReportColumn.date("Entrada"),
                ReportColumn.date("Salida"),
                ReportColumn.text("Estado"),
                ReportColumn.text("Quién"),
                ReportColumn.text("Motivo"),
                ReportColumn.decimal("Total")),
                rows,
                List.of(
                        new ReportSummary("Cancelaciones", cancelled),
                        new ReportSummary("Rechazos", rejected),
                        new ReportSummary("Sin motivo registrado", withoutReason),
                        new ReportSummary("Monto de las reservas afectadas", amount)));
    }
}
