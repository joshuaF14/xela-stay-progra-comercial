package com.xelastay.security;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Identidad del usuario autenticado, tomada del claim sub del JWT de Supabase (= auth.users.id = profiles.id).
 * Es la única fuente válida del ID del usuario: nunca se debe confiar en un ID enviado por el cliente.
 */
@Component
public class CurrentUser {

    /** UUID del usuario autenticado, si la petición trae un JWT válido. */
    public Optional<UUID> id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return Optional.of(UUID.fromString(jwt.getSubject()));
        }
        return Optional.empty();
    }

    /** UUID del usuario autenticado; falla con 401 si no hay JWT. */
    public UUID requireId() {
        return id().orElseThrow(() -> new AuthenticationCredentialsNotFoundException("No hay un usuario autenticado"));
    }
}
