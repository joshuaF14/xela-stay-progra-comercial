package com.xelastay.profile;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.xelastay.profile.dto.ProfileResponse;
import com.xelastay.profile.dto.UpdateMeRequest;
import com.xelastay.security.ProfileJwtAuthenticationConverter;

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
class MeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProfileService profileService;

    private final UUID userId = UUID.randomUUID();

    @Test
    void meRequiresAToken() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(profileService);
    }

    @Test
    void meReturnsTheProfileOfTheTokenUser() throws Exception {
        when(profileService.get(userId)).thenReturn(response(ProfileStatus.ACTIVE, List.of("GUEST")));

        mockMvc.perform(get("/api/v1/me").with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.roles[0]").value("GUEST"));
    }

    @Test
    void blockedProfileCanReadMeButNothingElse() throws Exception {
        when(profileService.get(userId)).thenReturn(response(ProfileStatus.SUSPENDED, List.of("GUEST")));

        mockMvc.perform(get("/api/v1/me").with(blocked()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
        mockMvc.perform(patch("/api/v1/me").with(blocked()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Ana\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        mockMvc.perform(get("/api/v1/me/bookings").with(blocked()))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateMeValidatesTheAvatarUrl() throws Exception {
        mockMvc.perform(patch("/api/v1/me").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"avatarUrl\":\"http://inseguro.com/a.png\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("avatarUrl"));
        verifyNoInteractions(profileService);
    }

    @Test
    void updateMeUsesTheTokenUser() throws Exception {
        when(profileService.updateMe(eq(userId), any(UpdateMeRequest.class)))
                .thenReturn(response(ProfileStatus.ACTIVE, List.of("GUEST")));

        mockMvc.perform(patch("/api/v1/me").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Ana\",\"phone\":\"+502 5555 5555\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Ana"));
    }

    @Test
    void becomeHostReturnsTheUpdatedRoles() throws Exception {
        when(profileService.becomeHost(userId)).thenReturn(response(ProfileStatus.ACTIVE, List.of("GUEST", "HOST")));

        mockMvc.perform(post("/api/v1/me/host").with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[1]").value("HOST"));
    }

    private RequestPostProcessor user() {
        return jwt().jwt(token -> token.subject(userId.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_GUEST"));
    }

    private RequestPostProcessor blocked() {
        return jwt().jwt(token -> token.subject(userId.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_GUEST"),
                        new SimpleGrantedAuthority(ProfileJwtAuthenticationConverter.PROFILE_BLOCKED));
    }

    private ProfileResponse response(ProfileStatus status, List<String> roles) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return new ProfileResponse(userId, "ana@correo.com", "Ana", null, null, status, roles, now, now);
    }
}
