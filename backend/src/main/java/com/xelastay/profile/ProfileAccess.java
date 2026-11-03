package com.xelastay.profile;

import java.util.Set;

/**
 * Estado y roles del perfil del usuario autenticado, resueltos al validar su JWT.
 *
 * @param exists false si no se pudo crear el perfil (por ejemplo, token sin email o email ya usado por otro perfil)
 */
public record ProfileAccess(boolean exists, ProfileStatus status, Set<String> roles) {

    public static ProfileAccess missing() {
        return new ProfileAccess(false, null, Set.of());
    }

    /** Solo un perfil ACTIVE puede operar en la API (salvo GET /api/v1/me). */
    public boolean active() {
        return exists && status == ProfileStatus.ACTIVE;
    }
}
