package com.xelastay.reporting.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.xelastay.reporting.ReportColumn;
import com.xelastay.reporting.ReportSummary;

/** Reporte generado: metadatos, columnas, filas y resumen. Es lo que se exporta y lo que devuelve la vista previa. */
public record ReportResult(
        int number,
        String id,
        String title,
        String description,
        LocalDate start,
        LocalDate end,
        Instant generatedAt,
        List<ReportColumn> columns,
        List<List<Object>> rows,
        List<ReportSummary> summary) {
}
