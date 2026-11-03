package com.xelastay.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

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
        // Sin token no hay 401: la ruta es pública (el controlador de alojamientos llega en otro bloque).
        mockMvc.perform(get("/api/v1/properties"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
        mockMvc.perform(get("/api/v1/properties/{id}", UUID.randomUUID()))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
    }

    @Test
    void myPropertiesAndAvailabilityBlocksStayProtected() throws Exception {
        mockMvc.perform(get("/api/v1/me/properties"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/properties/{id}/availability-blocks", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
