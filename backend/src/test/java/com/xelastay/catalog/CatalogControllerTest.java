package com.xelastay.catalog;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.xelastay.catalog.dto.AmenityResponse;
import com.xelastay.catalog.dto.CatalogItemRequest;
import com.xelastay.catalog.dto.CategoryResponse;

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

/** Categorías y amenidades: lectura pública y escritura solo ADMIN. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CatalogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoryService categoryService;

    @MockitoBean
    private AmenityService amenityService;

    private final UUID categoryId = UUID.randomUUID();

    @Test
    void categoriesArePublicAndAnonymousCannotSeeInactive() throws Exception {
        when(categoryService.list(false)).thenReturn(List.of(category()));

        mockMvc.perform(get("/api/v1/property-categories").param("includeInactive", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Casa"));
        verify(categoryService).list(false);
    }

    @Test
    void adminCanListInactiveCategories() throws Exception {
        when(categoryService.list(true)).thenReturn(List.of(category()));

        mockMvc.perform(get("/api/v1/property-categories").param("includeInactive", "true").with(as("ROLE_ADMIN")))
                .andExpect(status().isOk());
        verify(categoryService).list(true);
    }

    @Test
    void createCategoryRequiresAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/property-categories").with(as("ROLE_HOST"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Villa\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/property-categories")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Villa\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(categoryService);
    }

    @Test
    void adminCreatesCategoryWithLocation() throws Exception {
        when(categoryService.create(any(CatalogItemRequest.class))).thenReturn(category());

        mockMvc.perform(post("/api/v1/property-categories").with(as("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Casa\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/property-categories/" + categoryId));
    }

    @Test
    void blankNameIs400() throws Exception {
        mockMvc.perform(post("/api/v1/amenities").with(as("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("name"));
        verifyNoInteractions(amenityService);
    }

    @Test
    void amenitiesArePublic() throws Exception {
        when(amenityService.list(false)).thenReturn(List.of(new AmenityResponse(UUID.randomUUID(), "WiFi", null, true)));

        mockMvc.perform(get("/api/v1/amenities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("WiFi"));
    }

    @Test
    void adminDeactivatesAmenity() throws Exception {
        UUID amenityId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/amenities/{id}", amenityId).with(as("ROLE_ADMIN")))
                .andExpect(status().isNoContent());
        verify(amenityService).deactivate(amenityId);
    }

    private RequestPostProcessor as(String role) {
        return jwt().jwt(token -> token.subject(UUID.randomUUID().toString()))
                .authorities(new SimpleGrantedAuthority(role));
    }

    private CategoryResponse category() {
        return new CategoryResponse(categoryId, "Casa", null, true, OffsetDateTime.now(ZoneOffset.UTC));
    }
}
