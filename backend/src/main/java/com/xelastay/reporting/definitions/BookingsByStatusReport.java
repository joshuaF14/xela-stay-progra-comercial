package com.xelastay.reporting.definitions;

import java.util.List;

import com.xelastay.reporting.NativeQueries;
import com.xelastay.reporting.ReportColumn;
import com.xelastay.reporting.ReportDefinition;
import com.xelastay.reporting.ReportPeriod;
import com.xelastay.reporting.ReportSummary;
import com.xelastay.reporting.ReportTable;
import com.xelastay.reporting.ReportValues;

import org.springframework.stereotype.Component;

/** 2. Reservas por mes de entrada y estado (vista bi.reservas_por_estado). */
@Component
public class BookingsByStatusReport implements ReportDefinition {

    static final String CONFIRMED = "Confirmada";
    static final String COMPLETED = "Completada";

    private static final String SQL = """
            SELECT mes, estado, moneda, reservas, noches, monto_total
            FROM bi.reservas_por_estado
            WHERE mes >= :fromMonth AND mes <= :end
            ORDER BY mes, estado
            """;

    private final NativeQueries queries;

    public BookingsByStatusReport(NativeQueries queries) {
        this.queries = queries;
    }

    @Override
    public int number() {
        return 2;
    }

    @Override
    public String id() {
        return "reservas-por-estado";
    }

    @Override
    public String title() {
        return "Reservas por estado";
    }

    @Override
    public String description() {
        return "Reservas por mes de entrada (check-in) y estado, con noches y monto total, para los meses que se "
                + "cruzan con el periodo.";
    }

    @Override
    public ReportTable generate(ReportPeriod period) {
        List<List<Object>> rows = queries.rows(SQL, BiReportSupport.months(period)).stream()
                .map(BiReportSupport::withMonthLabel)
                .toList();
        long total = BiReportSupport.sumLong(rows, 3);
        long effective = rows.stream()
                .filter(row -> CONFIRMED.equals(row.get(1)) || COMPLETED.equals(row.get(1)))
                .mapToLong(row -> ReportValues.toLong(row.get(3)))
                .sum();

        return new ReportTable(List.of(
                ReportColumn.text("Mes"),
                ReportColumn.text("Estado"),
                ReportColumn.text("Moneda"),
                ReportColumn.integer("Reservas"),
                ReportColumn.integer("Noches"),
                ReportColumn.decimal("Monto total")),
                rows,
                List.of(
                        new ReportSummary("Reservas", total),
                        new ReportSummary("Confirmadas o completadas", effective),
                        new ReportSummary("% confirmadas o completadas", BiReportSupport.percent(effective, total)),
                        new ReportSummary("Noches", BiReportSupport.sumLong(rows, 4)),
                        new ReportSummary("Monto total (GTQ)", BiReportSupport.sumDecimal(rows, 5))));
    }
}
