package com.xelastay.reporting.definitions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.xelastay.reporting.NativeQueries;
import com.xelastay.reporting.ReportColumn;
import com.xelastay.reporting.ReportDefinition;
import com.xelastay.reporting.ReportPeriod;
import com.xelastay.reporting.ReportSummary;
import com.xelastay.reporting.ReportTable;
import com.xelastay.reporting.ReportValues;

import org.springframework.stereotype.Component;

/** 7. Usuarios registrados por mes y rol (bi.usuarios_por_mes_y_rol). */
@Component
public class UsersByMonthAndRoleReport implements ReportDefinition {

    private static final String SQL = """
            SELECT mes, rol, usuarios
            FROM bi.usuarios_por_mes_y_rol
            WHERE mes >= :fromMonth AND mes <= :end
            ORDER BY mes, rol
            """;

    private final NativeQueries queries;

    public UsersByMonthAndRoleReport(NativeQueries queries) {
        this.queries = queries;
    }

    @Override
    public int number() {
        return 7;
    }

    @Override
    public String id() {
        return "usuarios-por-mes-y-rol";
    }

    @Override
    public String title() {
        return "Usuarios registrados por mes y rol";
    }

    @Override
    public String description() {
        return "Usuarios por mes de registro y rol actual, para los meses que se cruzan con el periodo. Un usuario "
                + "con varios roles (por ejemplo GUEST y HOST) cuenta una vez en cada rol.";
    }

    @Override
    public ReportTable generate(ReportPeriod period) {
        List<List<Object>> rows = queries.rows(SQL, BiReportSupport.months(period)).stream()
                .map(BiReportSupport::withMonthLabel)
                .toList();
        Map<String, Long> byRole = new TreeMap<>();
        for (List<Object> row : rows) {
            byRole.merge(String.valueOf(row.get(1)), ReportValues.toLong(row.get(2)), Long::sum);
        }
        List<ReportSummary> summary = new ArrayList<>();
        byRole.forEach((role, users) -> summary.add(new ReportSummary("Con rol " + role, users)));

        return new ReportTable(List.of(
                ReportColumn.text("Mes"),
                ReportColumn.text("Rol"),
                ReportColumn.integer("Usuarios")),
                rows,
                summary);
    }
}
