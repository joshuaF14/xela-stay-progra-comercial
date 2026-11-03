package com.xelastay.profile;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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
import java.util.UUID;

import com.xelastay.profile.dto.RoleRequest;
import com.xelastay.profile.dto.RoleResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RoleService roleService;

    private final UUID roleId = UUID.randomUUID();

    @Test
    void nonAdminGets403() throws Exception {
        mockMvc.perform(get("/api/v1/roles").with(user("ROLE_GUEST")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(roleService);
    }

    @Test
    void adminCreatesRoleWithLocation() throws Exception {
        when(roleService.create(any(RoleRequest.class))).thenReturn(
                new RoleResponse(roleId, "SOPORTE", "Atención", false, OffsetDateTime.now(ZoneOffset.UTC)));

        mockMvc.perform(post("/api/v1/roles").with(user("ROLE_ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"SOPORTE\",\"description\":\"Atención\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/roles/" + roleId))
                .andExpect(jsonPath("$.system").value(false));
    }

    @Test
    void lowercaseNameIs400() throws Exception {
        mockMvc.perform(post("/api/v1/roles").with(user("ROLE_ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"soporte\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("name"));
        verifyNoInteractions(roleService);
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/v1/roles/{id}", roleId).with(user("ROLE_ADMIN")))
                .andExpect(status().isNoContent());
        verify(roleService).delete(roleId);
    }

    @Test
    void deletingAnAssignedRoleIs409() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "El rol está asignado"))
                .when(roleService).delete(roleId);

        mockMvc.perform(delete("/api/v1/roles/{id}", roleId).with(user("ROLE_ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("El rol está asignado"));
    }

    private RequestPostProcessor user(String role) {
        return jwt().jwt(token -> token.subject(UUID.randomUUID().toString()))
                .authorities(new SimpleGrantedAuthority(role));
    }
}
