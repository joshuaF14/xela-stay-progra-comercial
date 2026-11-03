package com.xelastay.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.xelastay.profile.ProfileAccess;
import com.xelastay.profile.ProfileAccessService;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Convierte un JWT válido de Supabase en la autenticación de la petición: asegura que exista el perfil
 * (creándolo con rol GUEST la primera vez) y expone sus roles como ROLE_GUEST, ROLE_HOST y ROLE_ADMIN.
 * Un perfil que no está ACTIVE (o que no se pudo crear) recibe además {@link #PROFILE_BLOCKED}.
 */
@Component
public class ProfileJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    public static final String PROFILE_BLOCKED = "PROFILE_BLOCKED";

    private static final int MAX_NAME_LENGTH = 150;

    // Se resuelve en cada conversión: en el perfil test no hay JPA y este servicio nunca se crea.
    private final ObjectProvider<ProfileAccessService> profileAccess;

    public ProfileJwtAuthenticationConverter(ObjectProvider<ProfileAccessService> profileAccess) {
        this.profileAccess = profileAccess;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        ProfileAccess access = profileAccess.getObject()
                .loadOrCreate(UUID.fromString(jwt.getSubject()), email, fullName(jwt, email));

        List<GrantedAuthority> authorities = new ArrayList<>();
        access.roles().forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
        if (!access.active()) {
            authorities.add(new SimpleGrantedAuthority(PROFILE_BLOCKED));
        }
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }

    /** user_metadata.full_name de Supabase o, si no viene, la parte local del email. */
    static String fullName(Jwt jwt, String email) {
        String name = null;
        Map<String, Object> metadata = jwt.getClaimAsMap("user_metadata");
        if (metadata != null && metadata.get("full_name") instanceof String fullName && !fullName.isBlank()) {
            name = fullName.trim();
        } else if (email != null && email.contains("@")) {
            name = email.substring(0, email.indexOf('@'));
        }
        if (name == null || name.isBlank()) {
            name = "Usuario";
        }
        return name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name;
    }
}
