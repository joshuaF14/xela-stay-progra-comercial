package com.xelastay.profile;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.xelastay.common.PageResponse;
import com.xelastay.profile.dto.ProfileResponse;

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
class ProfileAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProfileService profileService;

    private final UUID adminId = UUID.randomUUID();
    private final UUID profileId = UUID.randomUUID();

    @Test
    void nonAdminGets403() throws Exception {
        mockMvc.perform(get("/api/v1/profiles").with(withRoles("ROLE_GUEST", "ROLE_HOST")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        verifyNoInteractions(profileService);
    }

    @Test
    void adminSearchesWithFilters() throws Exception {
        when(profileService.search(ProfileStatus.ACTIVE, "HOST", 0, 20))
                .thenReturn(new PageResponse<>(List.of(response()), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/profiles").param("status", "ACTIVE").param("role", "HOST")
                        .with(withRoles("ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(profileId.toString()));
    }

    @Test
    void adminChangesStatusAsTheTokenUser() throws Exception {
        when(profileService.updateStatus(eq(adminId), eq(profileId), eq(ProfileStatus.SUSPENDED)))
                .thenReturn(response());

        mockMvc.perform(patch("/api/v1/profiles/{id}/status", profileId).with(withRoles("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void invalidStatusIs400() throws Exception {
        mockMvc.perform(patch("/api/v1/profiles/{id}/status", profileId).with(withRoles("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"BORRADO\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(profileService);
    }

    @Test
    void emptyRoleListIs400() throws Exception {
        mockMvc.perform(put("/api/v1/profiles/{id}/roles", profileId).with(withRoles("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("roles"));
        verifyNoInteractions(profileService);
    }

    @Test
    void adminReplacesRoles() throws Exception {
        when(profileService.replaceRoles(adminId, profileId, List.of("GUEST", "HOST"))).thenReturn(response());

        mockMvc.perform(put("/api/v1/profiles/{id}/roles", profileId).with(withRoles("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"GUEST\",\"HOST\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[1]").value("HOST"));
    }

    private RequestPostProcessor withRoles(String... roles) {
        SimpleGrantedAuthority[] authorities = new SimpleGrantedAuthority[roles.length];
        for (int i = 0; i < roles.length; i++) {
            authorities[i] = new SimpleGrantedAuthority(roles[i]);
        }
        return jwt().jwt(token -> token.subject(adminId.toString())).authorities(authorities);
    }

    private ProfileResponse response() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return new ProfileResponse(profileId, "ana@correo.com", "Ana", null, null, ProfileStatus.ACTIVE,
                List.of("GUEST", "HOST"), now, now);
    }
}
