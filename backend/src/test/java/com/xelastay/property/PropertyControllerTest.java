package com.xelastay.property;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.xelastay.common.PageResponse;
import com.xelastay.property.dto.PropertyRequest;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
class PropertyControllerTest {

    private static final String BODY = """
            {"categoryId":"%s","locationId":"%s","title":"Casa en Xela","pricePerNight":%s,
             "capacity":4,"bedrooms":2,"bathrooms":1.5}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PropertyService propertyService;

    private final UUID userId = UUID.randomUUID();
    private final UUID propertyId = UUID.randomUUID();

    @Test
    void searchIsPublicAndPassesAllFilters() throws Exception {
        UUID wifi = UUID.randomUUID();
        UUID parking = UUID.randomUUID();
        when(propertyService.search(any(PropertySearch.class), eq(1), eq(10), eq("pricePerNight,asc")))
                .thenReturn(new PageResponse<>(List.of(PropertyFixtures.summary(propertyId)), 1, 10, 11, 2));

        mockMvc.perform(get("/api/v1/properties")
                        .param("department", "Quetzaltenango").param("municipality", "Almolonga")
                        .param("minPrice", "100").param("maxPrice", "500").param("guests", "3")
                        .param("amenityIds", wifi.toString(), parking.toString()).param("q", "volcán")
                        .param("page", "1").param("size", "10").param("sort", "pricePerNight,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(11))
                .andExpect(jsonPath("$.content[0].pricePerNight").value(350.0))
                .andExpect(jsonPath("$.content[0].location.latitude").value(14.8347))
                .andExpect(jsonPath("$.content[0].coverImageUrl").value("https://img.example.com/1.jpg"))
                .andExpect(jsonPath("$.content[0].averageRating").value(4.5));

        ArgumentCaptor<PropertySearch> filter = ArgumentCaptor.forClass(PropertySearch.class);
        verify(propertyService).search(filter.capture(), eq(1), eq(10), eq("pricePerNight,asc"));
        assertThat(filter.getValue().amenityIds()).containsExactly(wifi, parking);
        assertThat(filter.getValue().guests()).isEqualTo(3);
        assertThat(filter.getValue().minPrice()).isEqualByComparingTo("100");
    }

    @Test
    void detailIsPublicForAnonymousUsers() throws Exception {
        when(propertyService.get(eq(propertyId), isNull(), eq(false)))
                .thenReturn(PropertyFixtures.detail(propertyId, PropertyStatus.PUBLISHED));

        mockMvc.perform(get("/api/v1/properties/{id}", propertyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.host.fullName").value("Ana"))
                .andExpect(jsonPath("$.category.name").value("Casa"));
    }

    @Test
    void ownerSeesTheirDraftDetail() throws Exception {
        when(propertyService.get(propertyId, userId, false))
                .thenReturn(PropertyFixtures.detail(propertyId, PropertyStatus.DRAFT));

        mockMvc.perform(get("/api/v1/properties/{id}", propertyId).with(as("ROLE_HOST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void onlyHostsCreate() throws Exception {
        mockMvc.perform(post("/api/v1/properties").with(as("ROLE_GUEST")).contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.formatted(UUID.randomUUID(), UUID.randomUUID(), "350")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(propertyService);
    }

    @Test
    void hostCreatesDraftWithLocation() throws Exception {
        when(propertyService.create(eq(userId), any(PropertyRequest.class)))
                .thenReturn(PropertyFixtures.detail(propertyId, PropertyStatus.DRAFT));

        mockMvc.perform(post("/api/v1/properties").with(as("ROLE_HOST")).contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.formatted(UUID.randomUUID(), UUID.randomUUID(), "350")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/properties/" + propertyId))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void invalidBodyIs400WithDetails() throws Exception {
        String bothLocations = """
                {"categoryId":"%s","locationId":"%s","location":{"department":"Q","municipality":"Q","address":"Z"},
                 "title":"Casa","pricePerNight":0,"capacity":4,"bedrooms":2,"bathrooms":1}
                """.formatted(UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post("/api/v1/properties").with(as("ROLE_HOST")).contentType(MediaType.APPLICATION_JSON)
                        .content(bothLocations))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[*].field", hasItem("locationProvidedOnce")))
                .andExpect(jsonPath("$.details[*].field", hasItem("pricePerNight")));
        verifyNoInteractions(propertyService);
    }

    @Test
    void statusChangePassesTheTokenUser() throws Exception {
        when(propertyService.changeStatus(userId, false, propertyId, PropertyStatus.PUBLISHED))
                .thenReturn(PropertyFixtures.detail(propertyId, PropertyStatus.PUBLISHED));

        mockMvc.perform(patch("/api/v1/properties/{id}/status", propertyId).with(as("ROLE_HOST"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/v1/properties/{id}", propertyId).with(as("ROLE_ADMIN")))
                .andExpect(status().isNoContent());
        verify(propertyService).delete(userId, true, propertyId);
    }

    @Test
    void myPropertiesRequireAToken() throws Exception {
        mockMvc.perform(get("/api/v1/me/properties"))
                .andExpect(status().isUnauthorized());
        when(propertyService.mine(userId, 0, 20))
                .thenReturn(new PageResponse<>(List.of(PropertyFixtures.summary(propertyId)), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/me/properties").with(as("ROLE_HOST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(propertyId.toString()));
    }

    @Test
    void replaceAmenitiesRequiresTheList() throws Exception {
        mockMvc.perform(put("/api/v1/properties/{id}/amenities", propertyId).with(as("ROLE_HOST"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("amenityIds"));
        verifyNoInteractions(propertyService);
    }

    @Test
    void nonNumericPriceFilterIs400() throws Exception {
        when(propertyService.search(any(PropertySearch.class), eq(0), eq(20), isNull()))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/v1/properties").param("maxPrice", "abc"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/properties").param("maxPrice", new BigDecimal("99.5").toPlainString()))
                .andExpect(status().isOk());
    }

    private RequestPostProcessor as(String role) {
        return jwt().jwt(token -> token.subject(userId.toString())).authorities(new SimpleGrantedAuthority(role));
    }
}
