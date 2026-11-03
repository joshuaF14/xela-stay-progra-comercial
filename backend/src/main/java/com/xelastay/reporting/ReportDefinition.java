package com.xelastay.reporting;

/**
 * Un reporte. Para agregar uno basta con un {@code @Component} que implemente esta interfaz: aparece en
 * GET /api/v1/reports y se exporta a Excel y PDF sin tocar nada más.
 */
public interface ReportDefinition {

    /** Número del reporte en la lista del proyecto (1 a 15); ordena el listado. */
    int number();

    /** Identificador para la URL, en minúsculas y con guiones (p. ej. {@code cancellations}). */
    String id();

    String title();

    /** Qué muestra el reporte y con qué criterio filtra el periodo. */
    String description();

    ReportTable generate(ReportPeriod period);
}
