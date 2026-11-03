package com.xelastay.profile;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resuelve el perfil de cada petición autenticada y lo crea la primera vez (estado ACTIVE y rol GUEST).
 * Usa SQL nativo con {@code INSERT … ON CONFLICT DO NOTHING}: si dos peticiones simultáneas del mismo usuario
 * intentan crearlo, una inserta y la otra espera y lo encuentra, sin error.
 */
@Service
public class ProfileAccessService {

    private static final Logger log = LoggerFactory.getLogger(ProfileAccessService.class);

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public ProfileAccess loadOrCreate(UUID profileId, String email, String fullName) {
        ProfileAccess access = load(profileId);
        if (access != null) {
            return access;
        }
        if (email == null || email.isBlank()) {
            log.warn("El token del usuario {} no trae email; no se puede crear su perfil", profileId);
            return ProfileAccess.missing();
        }
        int inserted = entityManager.createNativeQuery("""
                INSERT INTO profiles (id, full_name, email, status)
                VALUES (:id, :fullName, :email, 'ACTIVE')
                ON CONFLICT DO NOTHING
                """)
                .setParameter("id", profileId)
                .setParameter("fullName", fullName)
                .setParameter("email", email)
                .executeUpdate();
        if (inserted == 1) {
            entityManager.createNativeQuery("""
                    INSERT INTO profile_roles (profile_id, role_id)
                    SELECT :id, r.id FROM roles r WHERE r.name = :guest
                    ON CONFLICT DO NOTHING
                    """)
                    .setParameter("id", profileId)
                    .setParameter("guest", SystemRoles.GUEST)
                    .executeUpdate();
        }
        access = load(profileId);
        if (access == null) {
            // El email ya pertenece a otro perfil (UNIQUE): no se crea un segundo perfil con el mismo correo.
            log.warn("No se pudo crear el perfil del usuario {}: el email ya está registrado", profileId);
            return ProfileAccess.missing();
        }
        return access;
    }

    private ProfileAccess load(UUID profileId) {
        List<?> rows = entityManager.createNativeQuery("""
                SELECT p.status, r.name
                FROM profiles p
                LEFT JOIN profile_roles pr ON pr.profile_id = p.id
                LEFT JOIN roles r ON r.id = pr.role_id
                WHERE p.id = :id
                """)
                .setParameter("id", profileId)
                .getResultList();
        if (rows.isEmpty()) {
            return null;
        }
        ProfileStatus status = null;
        Set<String> roles = new HashSet<>();
        for (Object row : rows) {
            Object[] columns = (Object[]) row;
            status = ProfileStatus.valueOf((String) columns[0]);
            if (columns[1] != null) {
                roles.add((String) columns[1]);
            }
        }
        return new ProfileAccess(true, status, Set.copyOf(roles));
    }
}
