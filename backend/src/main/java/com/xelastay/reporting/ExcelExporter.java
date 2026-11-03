package com.xelastay.reporting;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.xelastay.reporting.dto.ReportResult;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/**
 * Exporta un reporte a .xlsx: título, periodo, resumen y la tabla con encabezado fijo y filtros. Los números y
 * las fechas se guardan como valores de Excel (no como texto) para poder ordenarlos y sumarlos.
 */
@Component
public class ExcelExporter {

    private static final int MIN_WIDTH = 10;
    private static final int MAX_WIDTH = 60;

    public byte[] export(ReportResult report) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles styles = new Styles(workbook);
            Sheet sheet = workbook.createSheet(sheetName(report));
            int rowIndex = 0;

            Row title = sheet.createRow(rowIndex++);
            cell(title, 0, report.number() + ". " + report.title(), styles.title);
            Row period = sheet.createRow(rowIndex++);
            cell(period, 0, "Periodo: " + ReportValues.format(report.start(), ReportColumn.Type.DATE) + " al "
                    + ReportValues.format(report.end(), ReportColumn.Type.DATE), styles.plain);
            Row generated = sheet.createRow(rowIndex++);
            cell(generated, 0, "Generado: " + ReportValues.format(report.generatedAt(), ReportColumn.Type.DATETIME)
                    + " · Xela Stay", styles.plain);
            rowIndex++;

            for (ReportSummary item : report.summary()) {
                Row row = sheet.createRow(rowIndex++);
                cell(row, 0, item.label(), styles.bold);
                writeSummaryValue(row.createCell(1), item.value(), styles);
            }
            if (!report.summary().isEmpty()) {
                rowIndex++;
            }

            List<ReportColumn> columns = report.columns();
            int headerIndex = rowIndex;
            Row header = sheet.createRow(rowIndex++);
            for (int i = 0; i < columns.size(); i++) {
                cell(header, i, columns.get(i).header(), styles.header);
            }
            for (List<Object> values : report.rows()) {
                Row row = sheet.createRow(rowIndex++);
                for (int i = 0; i < columns.size(); i++) {
                    write(row.createCell(i), values.get(i), columns.get(i).type(), styles);
                }
            }

            if (!columns.isEmpty()) {
                sheet.createFreezePane(0, headerIndex + 1);
                sheet.setAutoFilter(new CellRangeAddress(headerIndex, Math.max(headerIndex, rowIndex - 1), 0,
                        columns.size() - 1));
            }
            setWidths(sheet, report);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("No se pudo generar el Excel", ex);
        }
    }

    private static void write(Cell cell, Object raw, ReportColumn.Type type, Styles styles) {
        Object value = ReportValues.normalize(raw);
        if (value == null) {
            cell.setBlank();
            return;
        }
        switch (type) {
            case INTEGER -> {
                cell.setCellValue(ReportValues.toLong(value));
                cell.setCellStyle(styles.integer);
            }
            case DECIMAL -> {
                cell.setCellValue(ReportValues.toDecimal(value).doubleValue());
                cell.setCellStyle(styles.decimal);
            }
            case PERCENT -> {
                cell.setCellValue(ReportValues.toDecimal(value).divide(BigDecimal.valueOf(100)).doubleValue());
                cell.setCellStyle(styles.percent);
            }
            case DATE -> {
                if (value instanceof LocalDate date) {
                    cell.setCellValue(date);
                    cell.setCellStyle(styles.date);
                } else {
                    cell.setCellValue(value.toString());
                }
            }
            case DATETIME -> {
                if (value instanceof Instant instant) {
                    cell.setCellValue(instant.atZone(ReportPeriod.ZONE).toLocalDateTime());
                    cell.setCellStyle(styles.dateTime);
                } else {
                    cell.setCellValue(value.toString());
                }
            }
            case TEXT -> {
                cell.setCellValue(value.toString());
                cell.setCellStyle(styles.plain);
            }
        }
    }

    private static void writeSummaryValue(Cell cell, Object raw, Styles styles) {
        Object value = ReportValues.normalize(raw);
        if (value instanceof Long number) {
            write(cell, number, ReportColumn.Type.INTEGER, styles);
        } else if (value instanceof BigDecimal decimal) {
            write(cell, decimal, ReportColumn.Type.DECIMAL, styles);
        } else if (value instanceof Instant instant) {
            write(cell, instant, ReportColumn.Type.DATETIME, styles);
        } else {
            write(cell, value, ReportColumn.Type.TEXT, styles);
        }
    }

    private static void setWidths(Sheet sheet, ReportResult report) {
        List<ReportColumn> columns = report.columns();
        for (int i = 0; i < columns.size(); i++) {
            int chars = columns.get(i).header().length();
            for (List<Object> row : report.rows()) {
                chars = Math.max(chars, ReportValues.format(row.get(i), columns.get(i).type()).length());
            }
            int width = Math.min(MAX_WIDTH, Math.max(MIN_WIDTH, chars + 2));
            sheet.setColumnWidth(i, width * 256);
        }
        if (columns.isEmpty() || sheet.getColumnWidth(0) < 28 * 256) {
            sheet.setColumnWidth(0, 28 * 256);
        }
    }

    private static void cell(Row row, int column, String text, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(text);
        cell.setCellStyle(style);
    }

    /** Excel limita el nombre de la hoja a 31 caracteres sin algunos símbolos. */
    private static String sheetName(ReportResult report) {
        String name = (report.number() + " " + report.title()).replaceAll("[\\\\/?*\\[\\]:]", " ");
        return name.length() > 31 ? name.substring(0, 31) : name;
    }

    private static final class Styles {

        final CellStyle title;
        final CellStyle plain;
        final CellStyle bold;
        final CellStyle header;
        final CellStyle integer;
        final CellStyle decimal;
        final CellStyle percent;
        final CellStyle date;
        final CellStyle dateTime;

        Styles(XSSFWorkbook workbook) {
            DataFormat format = workbook.createDataFormat();

            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            title = workbook.createCellStyle();
            title.setFont(titleFont);

            plain = workbook.createCellStyle();

            Font boldFont = workbook.createFont();
            boldFont.setBold(true);
            bold = workbook.createCellStyle();
            bold.setFont(boldFont);

            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            header = workbook.createCellStyle();
            header.setFont(headerFont);
            header.setFillForegroundColor(IndexedColors.GREY_80_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setBorderBottom(BorderStyle.THIN);
            header.setWrapText(true);

            integer = numeric(workbook, format, "#,##0");
            decimal = numeric(workbook, format, "#,##0.00");
            percent = numeric(workbook, format, "0.0%");
            date = numeric(workbook, format, "dd/mm/yyyy");
            dateTime = numeric(workbook, format, "dd/mm/yyyy hh:mm");
        }

        private static CellStyle numeric(XSSFWorkbook workbook, DataFormat format, String pattern) {
            CellStyle style = workbook.createCellStyle();
            style.setDataFormat(format.getFormat(pattern));
            return style;
        }
    }
}
