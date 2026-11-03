package com.xelastay.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import com.xelastay.common.PageResponse;
import com.xelastay.property.PropertyService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PropertyService propertyService;

    @Test
    void protectedRouteWithoutTokenReturns401WithApiError() throws Exception {
        mockMvc.perform(get("/api/v1/ruta-protegida"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(HttpHeaders.WWW_AUTHENTICATE))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/api/v1/ruta-protegida"));
    }

    @Test
    void protectedRouteWithMalformedTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/ruta-protegida").header(HttpHeaders.AUTHORIZATION, "Bearer no-es-un-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());
    }

    @Test
    void propertySearchAndDetailArePublic() throws Exception {
        UUID propertyId = UUID.randomUUID();
        when(propertyService.search(any(), anyInt(), anyInt(), any()))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/v1/properties"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/properties/{id}", propertyId))
                .andExpect(status().isOk());
        verify(propertyService).get(propertyId, null, false);
    }

    @Test
    void myPropertiesAndAvailabilityBlocksStayProtected() throws Exception {
        mockMvc.perform(get("/api/v1/me/properties"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/properties/{id}/availability-blocks", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
