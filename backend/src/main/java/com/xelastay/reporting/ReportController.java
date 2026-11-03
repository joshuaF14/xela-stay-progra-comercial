package com.xelastay.reporting;

import java.time.LocalDate;
import java.util.List;

import com.xelastay.reporting.dto.ReportInfo;
import com.xelastay.reporting.dto.ReportResult;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reportes (solo ADMIN). Fechas en formato yyyy-MM-dd; sin fechas se usan los últimos 30 días.
 * La descarga requiere el token: desde el navegador hay que pedirla con fetch y el encabezado Authorization.
 */
@RestController
@RequestMapping("/api/v1/reports")
@PreAuthorize("hasRole('ADMIN')")
public class ReportController {

    private final ReportService reportService;
    private final ExcelExporter excelExporter;
    private final PdfExporter pdfExporter;

    public ReportController(ReportService reportService, ExcelExporter excelExporter, PdfExporter pdfExporter) {
        this.reportService = reportService;
        this.excelExporter = excelExporter;
        this.pdfExporter = pdfExporter;
    }

    @GetMapping
    public List<ReportInfo> list() {
        return reportService.list();
    }

    /** Vista previa en JSON. */
    @GetMapping("/{id}")
    public ReportResult preview(@PathVariable String id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return reportService.generate(id, start, end);
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> export(@PathVariable String id,
            @RequestParam(defaultValue = "excel") String format,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        ExportFormat exportFormat = ExportFormat.parse(format);
        ReportResult report = reportService.generate(id, start, end);
        byte[] content = exportFormat == ExportFormat.EXCEL ? excelExporter.export(report) : pdfExporter.export(report);
        String filename = "reporte-%02d-%s-%s_%s.%s".formatted(report.number(), report.id(), report.start(),
                report.end(), exportFormat.extension());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(exportFormat.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(content);
    }
}
