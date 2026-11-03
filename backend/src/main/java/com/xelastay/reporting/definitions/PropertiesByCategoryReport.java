package com.xelastay.reporting.definitions;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

/** 4. Alojamientos vigentes por categoría, municipio y estado (bi.alojamientos_por_categoria_municipio). */
@Component
public class PropertiesByCategoryReport implements ReportDefinition {

    /** Foto actual del catálogo: no se filtra por periodo. */
    private static final String SQL = """
            SELECT categoria, departamento, municipio, estado, moneda, alojamientos, precio_promedio_noche
            FROM bi.alojamientos_por_categoria_municipio
            ORDER BY categoria, departamento, municipio, estado
            """;

    private final NativeQueries queries;

    public PropertiesByCategoryReport(NativeQueries queries) {
        this.queries = queries;
    }

    @Override
    public int number() {
        return 4;
    }

    @Override
    public String id() {
        return "alojamientos-por-categoria";
    }

    @Override
    public String title() {
        return "Alojamientos por categoría y municipio";
    }

    @Override
    public String description() {
        return "Cantidad de alojamientos vigentes (sin los borrados) y precio promedio por noche, por categoría, "
                + "departamento, municipio y estado. Muestra el estado actual: no depende del periodo.";
    }

    @Override
    public ReportTable generate(ReportPeriod period) {
        List<List<Object>> rows = queries.rows(SQL, Map.of()).stream()
                .map(row -> BiReportSupport.translated(row, 3, BiReportSupport.PROPERTY_STATUS))
                .toList();
        long total = BiReportSupport.sumLong(rows, 5);
        long published = rows.stream()
                .filter(row -> "Publicado".equals(row.get(3)))
                .mapToLong(row -> ReportValues.toLong(row.get(5)))
                .sum();
        // Promedio general ponderado por la cantidad de alojamientos de cada grupo.
        BigDecimal weighted = rows.stream()
                .filter(row -> row.get(6) != null)
                .map(row -> ReportValues.toDecimal(row.get(6)).multiply(BigDecimal.valueOf(ReportValues.toLong(row.get(5)))))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal averagePrice = total == 0 ? null : weighted.divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);

        return new ReportTable(List.of(
                ReportColumn.text("Categoría"),
                ReportColumn.text("Departamento"),
                ReportColumn.text("Municipio"),
                ReportColumn.text("Estado"),
                ReportColumn.text("Moneda"),
                ReportColumn.integer("Alojamientos"),
                ReportColumn.decimal("Precio promedio por noche")),
                rows,
                List.of(
                        new ReportSummary("Alojamientos vigentes", total),
                        new ReportSummary("Publicados", published),
                        new ReportSummary("Precio promedio por noche (GTQ)", averagePrice)));
    }
}
