package com.xelastay.catalog;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.xelastay.catalog.dto.LocationRequest;
import com.xelastay.catalog.dto.LocationResponse;
import com.xelastay.common.PageResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LocationControllerTest {

    private static final String BODY = """
            {"department":"Quetzaltenango","municipality":"Quetzaltenango","address":"Zona 1",
             "latitude":%s,"longitude":%s}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LocationService locationService;

    private final UUID userId = UUID.randomUUID();
    private final UUID locationId = UUID.randomUUID();

    @Test
    void searchIsPublic() throws Exception {
        when(locationService.search("Quetzaltenango", null, 0, 20))
                .thenReturn(new PageResponse<>(List.of(response()), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/locations").param("department", "Quetzaltenango"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].latitude").value(14.8345679));
    }

    @Test
    void guestCannotCreateLocations() throws Exception {
        mockMvc.perform(post("/api/v1/locations").with(as("ROLE_GUEST")).contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.formatted("14.83", "-91.51")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(locationService);
    }

    @Test
    void hostCreatesLocation() throws Exception {
        when(locationService.create(any(LocationRequest.class))).thenReturn(response());

        mockMvc.perform(post("/api/v1/locations").with(as("ROLE_HOST")).contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.formatted("14.83", "-91.51")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(locationId.toString()));
    }

    @Test
    void latitudeOutOfRangeIs400() throws Exception {
        mockMvc.perform(post("/api/v1/locations").with(as("ROLE_HOST")).contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.formatted("91", "-91.51")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[*].field", hasItem("latitude")));
        verifyNoInteractions(locationService);
    }

    @Test
    void latitudeWithoutLongitudeIs400() throws Exception {
        mockMvc.perform(post("/api/v1/locations").with(as("ROLE_HOST")).contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.formatted("14.83", "null")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[*].field", hasItem("coordinatesPaired")));
    }

    @Test
    void updatePassesTheTokenUserAndAdminFlag() throws Exception {
        when(locationService.update(eq(userId), eq(false), eq(locationId), any(LocationRequest.class)))
                .thenReturn(response());

        mockMvc.perform(put("/api/v1/locations/{id}", locationId).with(as("ROLE_HOST"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY.formatted("14.83", "-91.51")))
                .andExpect(status().isOk());
    }

    private RequestPostProcessor as(String role) {
        return jwt().jwt(token -> token.subject(userId.toString())).authorities(new SimpleGrantedAuthority(role));
    }

    private LocationResponse response() {
        return new LocationResponse(locationId, "Quetzaltenango", "Quetzaltenango", "Zona 1",
                new BigDecimal("14.8345679"), new BigDecimal("-91.5180000"), OffsetDateTime.now(ZoneOffset.UTC));
    }
}
