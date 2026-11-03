package com.xelastay.reporting;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.HeaderFooter;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.xelastay.reporting.dto.ReportResult;

import org.springframework.stereotype.Component;

/**
 * Exporta un reporte a PDF con las fuentes estándar (Helvetica, admite tildes y ñ). Con más de 5 columnas la página
 * va en horizontal. El encabezado de la tabla se repite en cada página y el pie lleva el número de página.
 */
@Component
public class PdfExporter {

    private static final Color HEADER_BACKGROUND = new Color(64, 64, 64);
    private static final Color BORDER = new Color(200, 200, 200);

    public byte[] export(ReportResult report) {
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15);
        Font textFont = FontFactory.getFont(FontFactory.HELVETICA, 9);
        Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
        Font smallFont = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);

        List<ReportColumn> columns = report.columns();
        Rectangle page = columns.size() > 5 ? PageSize.A4.rotate() : PageSize.A4;

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(page, 36, 36, 36, 48);
            PdfWriter.getInstance(document, out);
            HeaderFooter footer = new HeaderFooter(new Phrase("Xela Stay · Página ", smallFont), true);
            footer.setAlignment(Element.ALIGN_CENTER);
            footer.setBorder(Rectangle.NO_BORDER);
            document.setFooter(footer);
            document.open();

            document.add(new Paragraph(report.number() + ". " + report.title(), titleFont));
            document.add(new Paragraph(report.description(), textFont));
            document.add(new Paragraph("Periodo: " + ReportValues.format(report.start(), ReportColumn.Type.DATE)
                    + " al " + ReportValues.format(report.end(), ReportColumn.Type.DATE) + "   ·   Generado: "
                    + ReportValues.format(report.generatedAt(), ReportColumn.Type.DATETIME), smallFont));

            if (!report.summary().isEmpty()) {
                PdfPTable summary = new PdfPTable(2);
                summary.setWidthPercentage(50);
                summary.setHorizontalAlignment(Element.ALIGN_LEFT);
                summary.setSpacingBefore(10);
                for (ReportSummary item : report.summary()) {
                    summary.addCell(cell(item.label(), boldFont, Element.ALIGN_LEFT));
                    summary.addCell(cell(summaryText(item.value()), textFont, Element.ALIGN_RIGHT));
                }
                document.add(summary);
            }

            if (report.rows().isEmpty()) {
                Paragraph empty = new Paragraph("No hay datos en el periodo seleccionado.", textFont);
                empty.setSpacingBefore(14);
                document.add(empty);
            } else {
                PdfPTable table = new PdfPTable(columns.size());
                table.setWidthPercentage(100);
                table.setSpacingBefore(14);
                table.setWidths(relativeWidths(report));
                table.setHeaderRows(1);
                for (ReportColumn column : columns) {
                    PdfPCell header = cell(column.header(), headerFont, Element.ALIGN_CENTER);
                    header.setBackgroundColor(HEADER_BACKGROUND);
                    table.addCell(header);
                }
                for (List<Object> row : report.rows()) {
                    for (int i = 0; i < columns.size(); i++) {
                        ReportColumn.Type type = columns.get(i).type();
                        table.addCell(cell(ReportValues.format(row.get(i), type), textFont, alignment(type)));
                    }
                }
                document.add(table);
            }

            document.close();
            return out.toByteArray();
        } catch (DocumentException | IOException ex) {
            throw new IllegalStateException("No se pudo generar el PDF", ex);
        }
    }

    private static PdfPCell cell(String text, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text == null ? "" : text, font));
        cell.setHorizontalAlignment(alignment);
        cell.setPadding(4);
        cell.setBorderColor(BORDER);
        return cell;
    }

    private static int alignment(ReportColumn.Type type) {
        return switch (type) {
            case INTEGER, DECIMAL, PERCENT -> Element.ALIGN_RIGHT;
            case DATE, DATETIME -> Element.ALIGN_CENTER;
            case TEXT -> Element.ALIGN_LEFT;
        };
    }

    private static String summaryText(Object raw) {
        Object value = ReportValues.normalize(raw);
        if (value instanceof Long) {
            return ReportValues.format(value, ReportColumn.Type.INTEGER);
        }
        if (value instanceof java.math.BigDecimal) {
            return ReportValues.format(value, ReportColumn.Type.DECIMAL);
        }
        if (value instanceof java.time.Instant) {
            return ReportValues.format(value, ReportColumn.Type.DATETIME);
        }
        return value == null ? "" : value.toString();
    }

    /** Ancho relativo de cada columna según su contenido más largo (entre 6 y 40 caracteres). */
    private static float[] relativeWidths(ReportResult report) {
        List<ReportColumn> columns = report.columns();
        float[] widths = new float[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            int chars = columns.get(i).header().length();
            for (List<Object> row : report.rows()) {
                chars = Math.max(chars, ReportValues.format(row.get(i), columns.get(i).type()).length());
            }
            widths[i] = Math.min(40, Math.max(6, chars));
        }
        return widths;
    }
}
