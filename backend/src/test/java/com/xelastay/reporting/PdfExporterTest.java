package com.xelastay.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfReader;

import org.junit.jupiter.api.Test;

class PdfExporterTest {

    private final PdfExporter exporter = new PdfExporter();

    // OpenPDF guarda la hoja horizontal como A4 con /Rotate 90: hay que medirla con la rotación aplicada.
    @Test
    void wideReportsAreLandscape() throws IOException {
        byte[] bytes = exporter.export(ReportFixtures.allTypes());

        assertThat(new String(bytes, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        PdfReader reader = new PdfReader(bytes);
        try {
            assertThat(reader.getNumberOfPages()).isGreaterThanOrEqualTo(1);
            Rectangle page = reader.getPageSizeWithRotation(1);
            assertThat(page.getWidth()).isGreaterThan(page.getHeight());
        } finally {
            reader.close();
        }
    }

    @Test
    void narrowAndEmptyReportsArePortrait() throws IOException {
        byte[] bytes = exporter.export(ReportFixtures.empty());

        PdfReader reader = new PdfReader(bytes);
        try {
            assertThat(reader.getNumberOfPages()).isEqualTo(1);
            Rectangle page = reader.getPageSizeWithRotation(1);
            assertThat(page.getHeight()).isGreaterThan(page.getWidth());
        } finally {
            reader.close();
        }
    }
}
