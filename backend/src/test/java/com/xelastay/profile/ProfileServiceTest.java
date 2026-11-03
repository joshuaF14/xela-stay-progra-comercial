package com.xelastay.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.xelastay.profile.dto.ProfileResponse;
import com.xelastay.profile.dto.UpdateMeRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock
    private ProfileRepository profiles;

    @Mock
    private RoleRepository roles;

    @Mock
    private ProfileRoleRepository profileRoles;

    @Captor
    private ArgumentCaptor<Iterable<ProfileRole>> assignments;

    private final UUID profileId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();
    private final Role guest = role("GUEST");
    private final Role host = role("HOST");
    private final Role admin = role("ADMIN");
    private ProfileService service;
    private Profile profile;

    @BeforeEach
    void setUp() {
        service = new ProfileService(profiles, roles, profileRoles);
        profile = Profile.of(profileId, "Ana", "ana@correo.com", ProfileStatus.ACTIVE);
    }

    @Test
    void getReturnsProfileWithSortedRoles() {
        when(profiles.findById(profileId)).thenReturn(Optional.of(profile));
        when(profileRoles.findWithRoleByProfileIds(List.of(profileId)))
                .thenReturn(List.of(ProfileRole.of(profileId, host), ProfileRole.of(profileId, guest)));

        ProfileResponse response = service.get(profileId);

        assertThat(response.email()).isEqualTo("ana@correo.com");
        assertThat(response.roles()).containsExactly("GUEST", "HOST");
    }

    @Test
    void unknownProfileIs404() {
        when(profiles.findById(profileId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(profileId)).isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void updateMeChangesOnlyTheFieldsSentAndBlankClears() {
        profile.changePhone("+502 5555 5555");
        when(profiles.findById(profileId)).thenReturn(Optional.of(profile));

        ProfileResponse response = service.updateMe(profileId, new UpdateMeRequest(" Ana María ", "", null));

        assertThat(response.fullName()).isEqualTo("Ana María");
        assertThat(response.phone()).isNull();
        verify(profiles).save(profile);
    }

    @Test
    void becomeHostAddsTheRoleOnlyOnce() {
        when(profiles.findById(profileId)).thenReturn(Optional.of(profile));
        when(roles.findByName("HOST")).thenReturn(Optional.of(host));
        when(profileRoles.existsById(new ProfileRoleId(profileId, host.getId()))).thenReturn(false, true);

        service.becomeHost(profileId);
        service.becomeHost(profileId);

        verify(profileRoles).save(any(ProfileRole.class));
    }

    @Test
    void adminCannotChangeOwnStatus() {
        assertThatThrownBy(() -> service.updateStatus(adminId, adminId, ProfileStatus.SUSPENDED))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(profiles, never()).save(any());
    }

    @Test
    void updateStatusSuspendsAnotherProfile() {
        when(profiles.findById(profileId)).thenReturn(Optional.of(profile));

        ProfileResponse response = service.updateStatus(adminId, profileId, ProfileStatus.SUSPENDED);

        assertThat(response.status()).isEqualTo(ProfileStatus.SUSPENDED);
    }

    @Test
    void replaceRolesRejectsUnknownNames() {
        when(profiles.findById(profileId)).thenReturn(Optional.of(profile));
        when(roles.findByNameIn(any())).thenReturn(List.of(guest));

        assertThatThrownBy(() -> service.replaceRoles(adminId, profileId, List.of("guest", "VIP")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getReason()).contains("VIP");
                });
    }

    @Test
    void adminCannotRemoveOwnAdminRole() {
        Profile adminProfile = Profile.of(adminId, "Admin", "admin@correo.com", ProfileStatus.ACTIVE);
        when(profiles.findById(adminId)).thenReturn(Optional.of(adminProfile));
        when(roles.findByNameIn(any())).thenReturn(List.of(guest));

        assertThatThrownBy(() -> service.replaceRoles(adminId, adminId, List.of("GUEST")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void replaceRolesRemovesAndAddsOnlyTheDifference() {
        ProfileRole currentGuest = ProfileRole.of(profileId, guest);
        ProfileRole currentAdmin = ProfileRole.of(profileId, admin);
        when(profiles.findById(profileId)).thenReturn(Optional.of(profile));
        when(roles.findByNameIn(any())).thenReturn(List.of(guest, host));
        when(profileRoles.findWithRoleByProfileIds(List.of(profileId)))
                .thenReturn(List.of(currentGuest, currentAdmin),
                        List.of(currentGuest, ProfileRole.of(profileId, host)));

        ProfileResponse response = service.replaceRoles(adminId, profileId, List.of("GUEST", "HOST"));

        verify(profileRoles).deleteAll(assignments.capture());
        assertThat(assignments.getValue()).containsExactly(currentAdmin);
        verify(profileRoles).saveAll(assignments.capture());
        assertThat(assignments.getValue()).extracting(pr -> pr.getRole().getName()).containsExactly("HOST");
        assertThat(response.roles()).containsExactly("GUEST", "HOST");
    }

    private static Role role(String name) {
        Role role = Role.create(name, null);
        ReflectionTestUtils.setField(role, "id", UUID.randomUUID());
        return role;
    }
}
