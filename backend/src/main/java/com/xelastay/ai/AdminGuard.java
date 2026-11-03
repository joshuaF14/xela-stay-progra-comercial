package com.xelastay.ai;

import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Verifica el rol ADMIN en profile_roles/roles con SQL nativo. Es provisional: cuando Joshua publique los roles
 * con {@code @PreAuthorize}, se reemplaza por {@code @PreAuthorize("hasRole('ADMIN')")} en el controlador.
 */
@Component
public class AdminGuard {

    @PersistenceContext
    private EntityManager entityManager;

    public void requireAdmin(UUID profileId) {
        Number admins = (Number) entityManager.createNativeQuery("""
                SELECT COUNT(*) FROM profile_roles pr
                JOIN roles r ON r.id = pr.role_id
                WHERE pr.profile_id = :profileId AND upper(r.name) = 'ADMIN'
                """)
                .setParameter("profileId", profileId)
                .getSingleResult();
        if (admins.longValue() == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Solo un administrador puede cargar documentos al agente");
        }
    }
}
