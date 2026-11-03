package com.xelastay.reporting;

import java.util.List;

/**
 * Resultado de un reporte: columnas, filas y un resumen opcional (totales, promedios) que se muestra arriba de la
 * tabla. Cada fila tiene un valor por columna, del tipo que indica {@link ReportColumn.Type}.
 */
public record ReportTable(List<ReportColumn> columns, List<List<Object>> rows, List<ReportSummary> summary) {

    public ReportTable {
        columns = List.copyOf(columns);
        rows = rows.stream().map(ReportTable::copyRow).toList();
        summary = summary == null ? List.of() : List.copyOf(summary);
        for (List<Object> row : rows) {
            if (row.size() != columns.size()) {
                throw new IllegalArgumentException(
                        "Cada fila debe tener " + columns.size() + " valores y una tiene " + row.size());
            }
        }
    }

    private static List<Object> copyRow(List<Object> row) {
        // List.copyOf no admite null y las celdas vacías son válidas.
        return java.util.Collections.unmodifiableList(new java.util.ArrayList<>(row));
    }
}
