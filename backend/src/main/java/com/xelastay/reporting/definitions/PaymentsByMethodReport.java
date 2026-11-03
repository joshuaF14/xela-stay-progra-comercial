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

/** 8. Pagos por mes, método y estado (bi.pagos_por_metodo_estado). */
@Component
public class PaymentsByMethodReport implements ReportDefinition {

    private static final String SQL = """
            SELECT mes, metodo, estado, moneda, pagos, monto
            FROM bi.pagos_por_metodo_estado
            WHERE mes >= :fromMonth AND mes <= :end
            ORDER BY mes, metodo, estado
            """;

    private final NativeQueries queries;

    public PaymentsByMethodReport(NativeQueries queries) {
        this.queries = queries;
    }

    @Override
    public int number() {
        return 8;
    }

    @Override
    public String id() {
        return "pagos-por-metodo";
    }

    @Override
    public String title() {
        return "Pagos por método y estado";
    }

    @Override
    public String description() {
        return "Cantidad y monto de pagos por mes de creación, método (tarjeta, transferencia, efectivo) y estado, "
                + "para los meses que se cruzan con el periodo.";
    }

    @Override
    public ReportTable generate(ReportPeriod period) {
        List<List<Object>> rows = queries.rows(SQL, BiReportSupport.months(period)).stream()
                .map(BiReportSupport::withMonthLabel)
                .map(row -> BiReportSupport.translated(row, 1, BiReportSupport.PAYMENT_METHOD))
                .map(row -> BiReportSupport.translated(row, 2, BiReportSupport.PAYMENT_STATUS))
                .toList();
        long total = BiReportSupport.sumLong(rows, 4);
        long approved = rows.stream()
                .filter(row -> "Aprobado".equals(row.get(2)))
                .mapToLong(row -> ReportValues.toLong(row.get(4)))
                .sum();

        return new ReportTable(List.of(
                ReportColumn.text("Mes"),
                ReportColumn.text("Método"),
                ReportColumn.text("Estado"),
                ReportColumn.text("Moneda"),
                ReportColumn.integer("Pagos"),
                ReportColumn.decimal("Monto")),
                rows,
                List.of(
                        new ReportSummary("Pagos", total),
                        new ReportSummary("Aprobados", approved),
                        new ReportSummary("% de aprobación", BiReportSupport.percent(approved, total)),
                        new ReportSummary("Monto (GTQ)", BiReportSupport.sumDecimal(rows, 5))));
    }
}
