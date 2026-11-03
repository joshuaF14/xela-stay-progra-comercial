package com.xelastay.reporting.definitions;

import java.util.List;

import com.xelastay.reporting.NativeQueries;
import com.xelastay.reporting.ReportColumn;
import com.xelastay.reporting.ReportDefinition;
import com.xelastay.reporting.ReportPeriod;
import com.xelastay.reporting.ReportSummary;
import com.xelastay.reporting.ReportTable;

import org.springframework.stereotype.Component;

/** 1. Ingresos por mes (vista bi.ingresos_por_mes). */
@Component
public class IncomeByMonthReport implements ReportDefinition {

    private static final String SQL = """
            SELECT mes, moneda, pagos_aprobados, monto_aprobado, monto_reembolsado, monto_neto
            FROM bi.ingresos_por_mes
            WHERE mes >= :fromMonth AND mes <= :end
            ORDER BY mes, moneda
            """;

    private final NativeQueries queries;

    public IncomeByMonthReport(NativeQueries queries) {
        this.queries = queries;
    }

    @Override
    public int number() {
        return 1;
    }

    @Override
    public String id() {
        return "ingresos-por-mes";
    }

    @Override
    public String title() {
        return "Ingresos por mes";
    }

    @Override
    public String description() {
        return "Pagos aprobados por mes de aprobación, con monto aprobado, reembolsado y neto, para los meses que se "
                + "cruzan con el periodo. Un pago reembolsado cuenta en el mes en que se aprobó.";
    }

    @Override
    public ReportTable generate(ReportPeriod period) {
        List<List<Object>> rows = queries.rows(SQL, BiReportSupport.months(period)).stream()
                .map(BiReportSupport::withMonthLabel)
                .toList();

        return new ReportTable(List.of(
                ReportColumn.text("Mes"),
                ReportColumn.text("Moneda"),
                ReportColumn.integer("Pagos aprobados"),
                ReportColumn.decimal("Monto aprobado"),
                ReportColumn.decimal("Monto reembolsado"),
                ReportColumn.decimal("Monto neto")),
                rows,
                List.of(
                        new ReportSummary("Pagos aprobados", BiReportSupport.sumLong(rows, 2)),
                        new ReportSummary("Monto aprobado (GTQ)", BiReportSupport.sumDecimal(rows, 3)),
                        new ReportSummary("Monto reembolsado (GTQ)", BiReportSupport.sumDecimal(rows, 4)),
                        new ReportSummary("Ingreso neto (GTQ)", BiReportSupport.sumDecimal(rows, 5))));
    }
}
