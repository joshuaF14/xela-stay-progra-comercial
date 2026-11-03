package com.xelastay.audit;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.xelastay.audit.dto.AuditLogResponse;
import com.xelastay.common.PageResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuditService auditService;

    @Test
    void onlyAdminsReadTheAuditLog() throws Exception {
        mockMvc.perform(get("/api/v1/audit-logs"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/audit-logs").with(as("ROLE_HOST")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(auditService);
    }

    @Test
    void adminSearchesWithAllFilters() throws Exception {
        UUID entityId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        AuditLogResponse row = new AuditLogResponse(UUID.randomUUID(), actorId, "Ana", "PROPERTY_STATUS_CHANGED",
                "property", entityId, Map.of("status", "DRAFT"), Map.of("status", "PUBLISHED"), "190.1.2.3",
                "Mozilla", AuditSource.USER, OffsetDateTime.now(ZoneOffset.UTC));
        when(auditService.search("property", entityId, actorId, AuditSource.USER, LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 30), 0, 20)).thenReturn(new PageResponse<>(List.of(row), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/audit-logs").with(as("ROLE_ADMIN"))
                        .param("entityType", "property").param("entityId", entityId.toString())
                        .param("actorId", actorId.toString()).param("source", "USER")
                        .param("from", "2026-11-01").param("to", "2026-11-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].actorName").value("Ana"))
                .andExpect(jsonPath("$.content[0].newValues.status").value("PUBLISHED"));
    }

    private RequestPostProcessor as(String role) {
        return jwt().jwt(token -> token.subject(UUID.randomUUID().toString()))
                .authorities(new SimpleGrantedAuthority(role));
    }
}
