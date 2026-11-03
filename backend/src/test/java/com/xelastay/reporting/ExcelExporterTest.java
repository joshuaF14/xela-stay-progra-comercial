package com.xelastay.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ExcelExporterTest {

    private final ExcelExporter exporter = new ExcelExporter();

    @Test
    void writesTitleSummaryHeaderAndTypedCells() throws IOException {
        byte[] bytes = exporter.export(ReportFixtures.allTypes());

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getSheetName()).hasSizeLessThanOrEqualTo(31).startsWith("9 Cancelaciones");
            assertThat(text(sheet, 0, 0)).isEqualTo("9. Cancelaciones y rechazos, con motivo");
            assertThat(text(sheet, 1, 0)).isEqualTo("Periodo: 01/10/2026 al 31/10/2026");

            // Filas 4 a 6: resumen (la 3 queda en blanco).
            assertThat(text(sheet, 4, 0)).isEqualTo("Cancelaciones");
            assertThat(sheet.getRow(4).getCell(1).getNumericCellValue()).isEqualTo(2.0);
            assertThat(sheet.getRow(5).getCell(1).getNumericCellValue()).isEqualTo(1500.50);
            assertThat(sheet.getRow(6).getCell(1).getCellType()).isEqualTo(CellType.BLANK);

            // Fila 8: encabezado; filas 9 y 10: datos.
            Row header = sheet.getRow(8);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("Alojamiento");
            assertThat(header.getCell(5).getStringCellValue()).isEqualTo("Fecha");

            Row data = sheet.getRow(9);
            assertThat(data.getCell(0).getStringCellValue()).isEqualTo("Casa Xelajú");
            assertThat(data.getCell(1).getNumericCellValue()).isEqualTo(1234.0);
            assertThat(data.getCell(2).getNumericCellValue()).isEqualTo(1500.50);
            assertThat(data.getCell(3).getNumericCellValue()).isEqualTo(0.75);
            assertThat(data.getCell(3).getCellStyle().getDataFormatString()).isEqualTo("0.0%");
            assertThat(data.getCell(4).getLocalDateTimeCellValue().toLocalDate()).isEqualTo(LocalDate.of(2026, 12, 24));
            // 20:30 UTC son las 14:30 en Guatemala.
            assertThat(data.getCell(5).getLocalDateTimeCellValue()).isEqualTo(LocalDateTime.of(2026, 10, 15, 14, 30));

            Row withNulls = sheet.getRow(10);
            assertThat(withNulls.getCell(1).getCellType()).isEqualTo(CellType.BLANK);
            assertThat(withNulls.getCell(5).getCellType()).isEqualTo(CellType.BLANK);

            assertThat(sheet.getPaneInformation().isFreezePane()).isTrue();
        }
    }

    @Test
    void reportWithoutRowsStillHasTheHeader() throws IOException {
        byte[] bytes = exporter.export(ReportFixtures.empty());

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            // Sin resumen: el encabezado va justo después de la fila en blanco.
            assertThat(text(sheet, 4, 0)).isEqualTo("Tipo");
            assertThat(text(sheet, 4, 1)).isEqualTo("Enviadas");
            assertThat(sheet.getLastRowNum()).isEqualTo(4);
        }
    }

    private static String text(Sheet sheet, int row, int column) {
        Cell cell = sheet.getRow(row).getCell(column);
        return cell.getStringCellValue();
    }
}
