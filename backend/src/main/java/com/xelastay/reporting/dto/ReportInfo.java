package com.xelastay.reporting.dto;

import com.xelastay.reporting.ReportDefinition;

/** Entrada del listado de reportes (para llenar el selector del frontend). */
public record ReportInfo(int number, String id, String title, String description) {

    public static ReportInfo from(ReportDefinition definition) {
        return new ReportInfo(definition.number(), definition.id(), definition.title(), definition.description());
    }
}
