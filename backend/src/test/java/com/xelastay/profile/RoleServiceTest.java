package com.xelastay.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import com.xelastay.profile.dto.RoleRequest;
import com.xelastay.profile.dto.RoleResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock
    private RoleRepository roles;

    @Mock
    private ProfileRoleRepository profileRoles;

    private RoleService service;

    @BeforeEach
    void setUp() {
        service = new RoleService(roles, profileRoles);
    }

    @Test
    void createRejectsDuplicateName() {
        when(roles.existsByName("SOPORTE")).thenReturn(true);

        assertConflict(() -> service.create(new RoleRequest("SOPORTE", null)));
        verify(roles, never()).save(any());
    }

    @Test
    void createSavesNewRole() {
        when(roles.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RoleResponse response = service.create(new RoleRequest("SOPORTE", "  Atención al cliente "));

        assertThat(response.name()).isEqualTo("SOPORTE");
        assertThat(response.description()).isEqualTo("Atención al cliente");
        assertThat(response.system()).isFalse();
    }

    @Test
    void systemRolesCannotBeRenamed() {
        Role guest = role("GUEST");
        when(roles.findById(guest.getId())).thenReturn(Optional.of(guest));

        assertConflict(() -> service.update(guest.getId(), new RoleRequest("VISITANTE", null)));
    }

    @Test
    void systemRoleDescriptionCanChange() {
        Role guest = role("GUEST");
        when(roles.findById(guest.getId())).thenReturn(Optional.of(guest));
        when(roles.save(guest)).thenReturn(guest);

        RoleResponse response = service.update(guest.getId(), new RoleRequest("GUEST", "Huésped"));

        assertThat(response.description()).isEqualTo("Huésped");
        assertThat(response.system()).isTrue();
    }

    @Test
    void systemRolesCannotBeDeleted() {
        Role admin = role("ADMIN");
        when(roles.findById(admin.getId())).thenReturn(Optional.of(admin));

        assertConflict(() -> service.delete(admin.getId()));
        verify(roles, never()).delete(any());
    }

    @Test
    void assignedRoleCannotBeDeleted() {
        Role support = role("SOPORTE");
        when(roles.findById(support.getId())).thenReturn(Optional.of(support));
        when(profileRoles.existsByIdRoleId(support.getId())).thenReturn(true);

        assertConflict(() -> service.delete(support.getId()));
        verify(roles, never()).delete(any());
    }

    @Test
    void unassignedCustomRoleIsDeleted() {
        Role support = role("SOPORTE");
        when(roles.findById(support.getId())).thenReturn(Optional.of(support));
        when(profileRoles.existsByIdRoleId(support.getId())).thenReturn(false);

        service.delete(support.getId());

        verify(roles).delete(support);
    }

    private static void assertConflict(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    private static Role role(String name) {
        Role role = Role.create(name, null);
        ReflectionTestUtils.setField(role, "id", UUID.randomUUID());
        return role;
    }
}
