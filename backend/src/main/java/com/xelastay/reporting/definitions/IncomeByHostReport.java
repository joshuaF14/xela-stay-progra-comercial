package com.xelastay.reporting.definitions;

import java.util.List;

import com.xelastay.reporting.NativeQueries;
import com.xelastay.reporting.ReportColumn;
import com.xelastay.reporting.ReportDefinition;
import com.xelastay.reporting.ReportPeriod;
import com.xelastay.reporting.ReportSummary;
import com.xelastay.reporting.ReportTable;

import org.springframework.stereotype.Component;

/** 6. Reservas y monto aprobado por anfitrión (bi.ingresos_por_anfitrion; solo el nombre, sin datos de contacto). */
@Component
public class IncomeByHostReport implements ReportDefinition {

    private static final String SQL = """
            SELECT anfitrion, MAX(alojamientos), SUM(reservas), SUM(monto_aprobado)
            FROM bi.ingresos_por_anfitrion
            WHERE mes >= :fromMonth AND mes <= :end
            GROUP BY anfitrion_id, anfitrion
            ORDER BY SUM(monto_aprobado) DESC, SUM(reservas) DESC, anfitrion
            """;

    private final NativeQueries queries;

    public IncomeByHostReport(NativeQueries queries) {
        this.queries = queries;
    }

    @Override
    public int number() {
        return 6;
    }

    @Override
    public String id() {
        return "ingresos-por-anfitrion";
    }

    @Override
    public String title() {
        return "Ingresos por anfitrión";
    }

    @Override
    public String description() {
        return "Por anfitrión: alojamientos vigentes, reservas confirmadas o completadas con entrada en los meses que "
                + "se cruzan con el periodo y el monto de sus pagos aprobados.";
    }

    @Override
    public ReportTable generate(ReportPeriod period) {
        List<List<Object>> rows = queries.rows(SQL, BiReportSupport.months(period));

        return new ReportTable(List.of(
                ReportColumn.text("Anfitrión"),
                ReportColumn.integer("Alojamientos"),
                ReportColumn.integer("Reservas"),
                ReportColumn.decimal("Monto aprobado (GTQ)")),
                rows,
                List.of(
                        new ReportSummary("Anfitriones con reservas", (long) rows.size()),
                        new ReportSummary("Reservas", BiReportSupport.sumLong(rows, 2)),
                        new ReportSummary("Monto aprobado (GTQ)", BiReportSupport.sumDecimal(rows, 3))));
    }
}
