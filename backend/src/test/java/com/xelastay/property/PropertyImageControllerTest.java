package com.xelastay.property;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import com.xelastay.property.dto.PropertyImageRequest;
import com.xelastay.property.dto.PropertyImageResponse;

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
class PropertyImageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PropertyImageService imageService;

    private final UUID userId = UUID.randomUUID();
    private final UUID propertyId = UUID.randomUUID();
    private final UUID imageId = UUID.randomUUID();

    @Test
    void guestCannotUploadImages() throws Exception {
        mockMvc.perform(post("/api/v1/properties/{id}/images", propertyId).with(as("ROLE_GUEST"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"imageUrl\":\"https://img.example.com/a.jpg\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(imageService);
    }

    @Test
    void imageUrlMustBeHttps() throws Exception {
        mockMvc.perform(post("/api/v1/properties/{id}/images", propertyId).with(as("ROLE_HOST"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"imageUrl\":\"http://img.example.com/a.jpg\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("imageUrl"));
        verifyNoInteractions(imageService);
    }

    @Test
    void hostAddsImage() throws Exception {
        when(imageService.add(eq(userId), eq(false), eq(propertyId), any(PropertyImageRequest.class)))
                .thenReturn(new PropertyImageResponse(imageId, "https://img.example.com/a.jpg", null, 0, true,
                        OffsetDateTime.now(ZoneOffset.UTC)));

        mockMvc.perform(post("/api/v1/properties/{id}/images", propertyId).with(as("ROLE_HOST"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"imageUrl\":\"https://img.example.com/a.jpg\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/properties/" + propertyId + "/images/" + imageId))
                .andExpect(jsonPath("$.cover").value(true));
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/v1/properties/{id}/images/{imageId}", propertyId, imageId).with(as("ROLE_HOST")))
                .andExpect(status().isNoContent());
        verify(imageService).delete(userId, false, propertyId, imageId);
    }

    private RequestPostProcessor as(String role) {
        return jwt().jwt(token -> token.subject(userId.toString())).authorities(new SimpleGrantedAuthority(role));
    }
}
