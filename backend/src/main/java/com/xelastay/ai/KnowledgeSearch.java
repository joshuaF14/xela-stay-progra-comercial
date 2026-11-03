package com.xelastay.ai;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.stereotype.Repository;

/**
 * Recuperación para el RAG sin pgvector: búsqueda de texto completo de PostgreSQL en español.
 * <p>
 * La pregunta se normaliza con plainto_tsquery('spanish', ...) (quita palabras vacías y reduce cada palabra a su
 * raíz) y sus términos se unen con OR, para que una pregunta natural encuentre fragmentos aunque no contengan
 * todas sus palabras; ts_rank ordena primero los que coinciden con más términos. El título del documento pesa
 * más que el contenido.
 */
@Repository
public class KnowledgeSearch {

    /** plainto_tsquery con los términos unidos por OR (|) en lugar de AND (&). */
    private static final String OR_QUERY = """
            (SELECT CAST(replace(CAST(plainto_tsquery('spanish', :question) AS text), '&', '|') AS tsquery) AS query) q
            """;

    @PersistenceContext
    private EntityManager entityManager;

    /** Fragmentos de documentos INDEXED más relevantes para la pregunta. */
    public List<ChunkHit> searchChunks(String question, int limit) {
        List<?> rows = entityManager.createNativeQuery("""
                SELECT c.id, c.document_id, d.title, d.document_type, c.chunk_index, c.content,
                       ts_rank(setweight(to_tsvector('spanish', d.title), 'A')
                               || to_tsvector('spanish', c.content), q.query) AS rank
                FROM knowledge_chunks c
                JOIN knowledge_documents d ON d.id = c.document_id
                CROSS JOIN %s
                WHERE d.status = 'INDEXED'
                  AND (setweight(to_tsvector('spanish', d.title), 'A') || to_tsvector('spanish', c.content)) @@ q.query
                ORDER BY rank DESC, c.chunk_index
                LIMIT :limit
                """.formatted(OR_QUERY))
                .setParameter("question", question)
                .setParameter("limit", limit)
                .getResultList();
        return rows.stream().map(row -> {
            Object[] r = (Object[]) row;
            return new ChunkHit(uuid(r[0]), uuid(r[1]), (String) r[2], (String) r[3], ((Number) r[4]).intValue(),
                    (String) r[5], ((Number) r[6]).doubleValue());
        }).toList();
    }

    /** Alojamientos publicados cuyo título, descripción o ubicación coinciden con la pregunta. */
    public List<PropertyHit> searchProperties(String question, int limit) {
        List<?> rows = entityManager.createNativeQuery("""
                SELECT p.id, p.title, p.price_per_night, CAST(p.currency AS varchar), p.capacity,
                       l.municipality, l.department, ts_rank(v.document, q.query) AS rank
                FROM properties p
                JOIN locations l ON l.id = p.location_id
                CROSS JOIN LATERAL (
                    SELECT setweight(to_tsvector('spanish', p.title), 'A')
                           || to_tsvector('spanish', coalesce(p.description, '') || ' ' || l.municipality || ' '
                                                     || l.department) AS document
                ) v
                CROSS JOIN %s
                WHERE p.status = 'PUBLISHED' AND p.deleted_at IS NULL AND v.document @@ q.query
                ORDER BY rank DESC
                LIMIT :limit
                """.formatted(OR_QUERY))
                .setParameter("question", question)
                .setParameter("limit", limit)
                .getResultList();
        return rows.stream().map(row -> {
            Object[] r = (Object[]) row;
            return new PropertyHit(uuid(r[0]), (String) r[1], (BigDecimal) r[2],
                    r[3] == null ? null : r[3].toString().trim(), ((Number) r[4]).intValue(), (String) r[5],
                    (String) r[6], ((Number) r[7]).doubleValue());
        }).toList();
    }

    private static UUID uuid(Object value) {
        return value instanceof UUID id ? id : UUID.fromString(value.toString());
    }

    public record ChunkHit(UUID chunkId, UUID documentId, String documentTitle, String documentType, int chunkIndex,
            String content, double rank) {
    }

    public record PropertyHit(UUID propertyId, String title, BigDecimal pricePerNight, String currency, int capacity,
            String municipality, String department, double rank) {
    }
}
