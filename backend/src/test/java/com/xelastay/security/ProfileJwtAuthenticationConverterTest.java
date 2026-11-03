package com.xelastay.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.xelastay.profile.ProfileAccess;
import com.xelastay.profile.ProfileAccessService;
import com.xelastay.profile.ProfileStatus;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class ProfileJwtAuthenticationConverterTest {

    private final UUID userId = UUID.randomUUID();
    private final ProfileAccessService accessService = mock(ProfileAccessService.class);

    @SuppressWarnings("unchecked")
    private final ObjectProvider<ProfileAccessService> provider = mock(ObjectProvider.class);

    private final ProfileJwtAuthenticationConverter converter = new ProfileJwtAuthenticationConverter(provider);

    @Test
    void activeProfileGetsItsRolesAsAuthorities() {
        when(provider.getObject()).thenReturn(accessService);
        when(accessService.loadOrCreate(eq(userId), eq("ana@correo.com"), eq("Ana López")))
                .thenReturn(new ProfileAccess(true, ProfileStatus.ACTIVE, Set.of("GUEST", "HOST")));

        AbstractAuthenticationToken token = converter.convert(jwt(Map.of("full_name", "Ana López")));

        assertThat(token.getName()).isEqualTo(userId.toString());
        assertThat(token.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_GUEST", "ROLE_HOST");
    }

    @Test
    void suspendedProfileIsMarkedAsBlocked() {
        when(provider.getObject()).thenReturn(accessService);
        when(accessService.loadOrCreate(eq(userId), eq("ana@correo.com"), eq("ana")))
                .thenReturn(new ProfileAccess(true, ProfileStatus.SUSPENDED, Set.of("GUEST")));

        AbstractAuthenticationToken token = converter.convert(jwt(Map.of()));

        assertThat(token.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .contains(ProfileJwtAuthenticationConverter.PROFILE_BLOCKED);
    }

    @Test
    void missingProfileIsMarkedAsBlocked() {
        when(provider.getObject()).thenReturn(accessService);
        when(accessService.loadOrCreate(eq(userId), eq("ana@correo.com"), eq("ana")))
                .thenReturn(ProfileAccess.missing());

        AbstractAuthenticationToken token = converter.convert(jwt(Map.of()));

        assertThat(token.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly(ProfileJwtAuthenticationConverter.PROFILE_BLOCKED);
    }

    @Test
    void fullNameFallsBackToTheLocalPartOfTheEmail() {
        assertThat(ProfileJwtAuthenticationConverter.fullName(jwt(Map.of("full_name", "  ")), "ana@correo.com"))
                .isEqualTo("ana");
        assertThat(ProfileJwtAuthenticationConverter.fullName(jwt(Map.of("full_name", "Ana")), "ana@correo.com"))
                .isEqualTo("Ana");
    }

    private Jwt jwt(Map<String, Object> userMetadata) {
        return Jwt.withTokenValue("token")
                .header("alg", "ES256")
                .subject(userId.toString())
                .claim("email", "ana@correo.com")
                .claim("user_metadata", userMetadata)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
    }
}
