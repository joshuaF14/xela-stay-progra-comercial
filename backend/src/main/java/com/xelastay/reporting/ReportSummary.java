package com.xelastay.reporting;

/** Dato de resumen que se muestra arriba de la tabla, p. ej. "Total de cancelaciones: 12". */
public record ReportSummary(String label, Object value) {
}
