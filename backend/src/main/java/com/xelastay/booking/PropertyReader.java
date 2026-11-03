package com.xelastay.booking;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.stereotype.Repository;

/**
 * Lectura de la tabla properties con SQL nativo. La entidad Property es de Joshua; aquí solo se leen
 * precio, capacidad, dueño, título y estado. Los métodos lock* deben llamarse dentro de una transacción.
 */
@Repository
public class PropertyReader {

    private static final String COLUMNS = """
            SELECT id, host_id, title, price_per_night, CAST(currency AS varchar), capacity, status
            FROM properties
            """;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Bloquea la propiedad publicada (FOR UPDATE) para crear una reserva. Dos reservas simultáneas de la misma
     * propiedad se atienden una después de la otra, así la segunda ve a la primera al revisar el solape.
     */
    public Optional<PropertyInfo> lockPublished(UUID id) {
        return single(COLUMNS + "WHERE id = :id AND status = 'PUBLISHED' AND deleted_at IS NULL FOR UPDATE", id);
    }

    /** Bloquea la propiedad (no borrada, en cualquier estado) para cambiar sus bloqueos de disponibilidad. */
    public Optional<PropertyInfo> lockActive(UUID id) {
        return single(COLUMNS + "WHERE id = :id AND deleted_at IS NULL FOR UPDATE", id);
    }

    /** Propiedad visible al público: publicada y no borrada. */
    public Optional<PropertyInfo> findPublished(UUID id) {
        return single(COLUMNS + "WHERE id = :id AND status = 'PUBLISHED' AND deleted_at IS NULL", id);
    }

    /** Propiedad no borrada, en cualquier estado (para el panel del anfitrión). */
    public Optional<PropertyInfo> findActive(UUID id) {
        return single(COLUMNS + "WHERE id = :id AND deleted_at IS NULL", id);
    }

    /** Propiedad de una reserva existente, aunque después se haya pausado o borrado. */
    public Optional<PropertyInfo> findById(UUID id) {
        return single(COLUMNS + "WHERE id = :id", id);
    }

    public Map<UUID, PropertyInfo> findByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<?> rows = entityManager.createNativeQuery(COLUMNS + "WHERE id IN (:ids)")
                .setParameter("ids", ids)
                .getResultList();
        return rows.stream()
                .map(row -> map((Object[]) row))
                .collect(Collectors.toMap(PropertyInfo::id, Function.identity()));
    }

    /** IDs de todas las propiedades del anfitrión, incluidas las borradas (sus reservas siguen existiendo). */
    public List<UUID> findIdsByHost(UUID hostId) {
        List<?> rows = entityManager.createNativeQuery("SELECT id FROM properties WHERE host_id = :hostId")
                .setParameter("hostId", hostId)
                .getResultList();
        return rows.stream().map(PropertyReader::uuid).toList();
    }

    private Optional<PropertyInfo> single(String sql, UUID id) {
        List<?> rows = entityManager.createNativeQuery(sql).setParameter("id", id).getResultList();
        return rows.stream().findFirst().map(row -> map((Object[]) row));
    }

    static PropertyInfo map(Object[] row) {
        return new PropertyInfo(
                uuid(row[0]),
                uuid(row[1]),
                (String) row[2],
                (BigDecimal) row[3],
                row[4] == null ? null : row[4].toString().trim(),
                ((Number) row[5]).intValue(),
                (String) row[6]);
    }

    private static UUID uuid(Object value) {
        return value instanceof UUID id ? id : UUID.fromString(value.toString());
    }
}
