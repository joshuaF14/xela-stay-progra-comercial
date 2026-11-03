package com.xelastay.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.xelastay.reporting.dto.ReportInfo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportControllerTest {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportService reportService;

    private final UUID userId = UUID.randomUUID();

    @Test
    void reportsRequireAToken() throws Exception {
        mockMvc.perform(get("/api/v1/reports")).andExpect(status().isUnauthorized());
        verifyNoInteractions(reportService);
    }

    @Test
    void reportsAreOnlyForAdmins() throws Exception {
        mockMvc.perform(get("/api/v1/reports/cancellations/export").with(user()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        verifyNoInteractions(reportService);
    }

    @Test
    void adminListsTheReports() throws Exception {
        when(reportService.list()).thenReturn(List.of(
                new ReportInfo(9, "cancellations", "Cancelaciones y rechazos, con motivo", "Descripción")));

        mockMvc.perform(get("/api/v1/reports").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].number").value(9))
                .andExpect(jsonPath("$[0].id").value("cancellations"));
    }

    @Test
    void previewReturnsColumnsRowsAndSummary() throws Exception {
        when(reportService.generate("cancellations", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)))
                .thenReturn(ReportFixtures.allTypes());

        mockMvc.perform(get("/api/v1/reports/cancellations").with(admin())
                        .param("start", "2026-10-01").param("end", "2026-10-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.start").value("2026-10-01"))
                .andExpect(jsonPath("$.columns[1].type").value("INTEGER"))
                .andExpect(jsonPath("$.rows[0][0]").value("Casa Xelajú"))
                .andExpect(jsonPath("$.rows[0][4]").value("2026-12-24"))
                .andExpect(jsonPath("$.summary[0].label").value("Cancelaciones"));
    }

    @Test
    void exportsExcelByDefault() throws Exception {
        when(reportService.generate(eq("cancellations"), isNull(), isNull())).thenReturn(ReportFixtures.allTypes());

        MvcResult result = mockMvc.perform(get("/api/v1/reports/cancellations/export").with(admin()))
                .andExpect(status().isOk())
                // La app agrega charset=UTF-8 a todas las respuestas (server.servlet.encoding.force=true).
                .andExpect(content().contentTypeCompatibleWith(XLSX))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"reporte-09-cancellations-2026-10-01_2026-10-31.xlsx\""))
                .andReturn();

        // Un .xlsx es un ZIP: empieza con "PK".
        assertThat(result.getResponse().getContentAsByteArray()).startsWith((byte) 'P', (byte) 'K');
    }

    @Test
    void exportsPdf() throws Exception {
        when(reportService.generate(eq("cancellations"), any(), any())).thenReturn(ReportFixtures.allTypes());

        MvcResult result = mockMvc.perform(get("/api/v1/reports/cancellations/export").with(admin())
                        .param("format", "pdf").param("start", "2026-10-01"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/pdf"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"reporte-09-cancellations-2026-10-01_2026-10-31.pdf\""))
                .andReturn();

        assertThat(result.getResponse().getContentAsByteArray()).startsWith((byte) '%', (byte) 'P', (byte) 'D');
        verify(reportService).generate("cancellations", LocalDate.of(2026, 10, 1), null);
    }

    @Test
    void unknownFormatIs400() throws Exception {
        mockMvc.perform(get("/api/v1/reports/cancellations/export").with(admin()).param("format", "csv"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(reportService);
    }

    @Test
    void unknownReportIs404() throws Exception {
        when(reportService.generate(eq("nope"), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Reporte no encontrado: nope"));

        mockMvc.perform(get("/api/v1/reports/nope").with(admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void invalidDateIs400() throws Exception {
        mockMvc.perform(get("/api/v1/reports/cancellations").with(admin()).param("start", "01/10/2026"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(reportService);
    }

    private RequestPostProcessor user() {
        return jwt().jwt(token -> token.subject(userId.toString()));
    }

    private RequestPostProcessor admin() {
        return jwt().jwt(token -> token.subject(userId.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
