/**
 * Módulo de reportes: base común para los 15 reportes y su exportación a Excel y PDF.
 * <p>
 * Responsables: Joshua (reportes 1 al 8) y Giovanni (base común y reportes 9 al 15).
 * <p>
 * Cada reporte es un {@code @Component} que implementa {@link com.xelastay.reporting.ReportDefinition}; el
 * {@link com.xelastay.reporting.ReportService} los descubre solos y {@link com.xelastay.reporting.ReportController}
 * los publica en /api/v1/reports (solo ADMIN). Las vistas reporting_bookings y reporting_property_ratings de V1
 * se pueden usar en las consultas. Contrato: docs/api/ENDPOINTS.md, sección Reportes.
 */
package com.xelastay.reporting;
