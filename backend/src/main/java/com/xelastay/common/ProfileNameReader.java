package com.xelastay.common;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.stereotype.Repository;

/**
 * Nombres visibles de usuarios (profiles.full_name) leídos con SQL nativo, para mostrar autores de reseñas y
 * participantes del chat sin depender de la entidad Profile.
 */
@Repository
public class ProfileNameReader {

    @PersistenceContext
    private EntityManager entityManager;

    public Map<UUID, String> findNames(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<?> rows = entityManager.createNativeQuery("SELECT id, full_name FROM profiles WHERE id IN (:ids)")
                .setParameter("ids", ids)
                .getResultList();
        Map<UUID, String> names = new HashMap<>();
        for (Object row : rows) {
            Object[] columns = (Object[]) row;
            UUID id = columns[0] instanceof UUID uuid ? uuid : UUID.fromString(columns[0].toString());
            names.put(id, (String) columns[1]);
        }
        return names;
    }
}
