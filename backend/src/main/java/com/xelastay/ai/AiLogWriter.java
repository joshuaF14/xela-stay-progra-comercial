package com.xelastay.ai;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xelastay.ai.dto.SourceRef;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registro del agente: ai_interactions (pregunta, respuesta y fuentes) y audit_logs. Usa SQL nativo para no
 * crear una entidad AuditLog que choque con la que defina el módulo de auditoría.
 */
@Component
public class AiLogWriter {

    private static final int MAX_USER_AGENT = 500;

    @PersistenceContext
    private EntityManager entityManager;

    private final ObjectMapper objectMapper;

    public AiLogWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Guarda la interacción y su registro de auditoría (source = 'AI') en una sola transacción. */
    @Transactional
    public UUID recordChat(UUID profileId, String question, String answer, List<SourceRef> sources,
            String ipAddress, String userAgent) {
        UUID interactionId = UUID.randomUUID();
        entityManager.createNativeQuery("""
                INSERT INTO ai_interactions (id, profile_id, question, answer, sources, created_at)
                VALUES (:id, :profileId, :question, :answer, CAST(:sources AS jsonb), now())
                """)
                .setParameter("id", interactionId)
                .setParameter("profileId", profileId)
                .setParameter("question", question)
                .setParameter("answer", answer)
                .setParameter("sources", json(sources))
                .executeUpdate();
        audit(profileId, "AI_CHAT", "ai_interaction", interactionId,
                Map.of("question", question, "sourceCount", sources.size()), "AI", ipAddress, userAgent);
        return interactionId;
    }

    /**
     * Fila en audit_logs. Se une a la transacción en curso si existe.
     *
     * @param source SYSTEM, USER, AI o ADMIN (CHECK de V1)
     */
    @Transactional
    public void audit(UUID actorId, String action, String entityType, UUID entityId, Map<String, Object> newValues,
            String source, String ipAddress, String userAgent) {
        entityManager.createNativeQuery("""
                INSERT INTO audit_logs (id, actor_id, action, entity_type, entity_id, new_values, ip_address,
                                        user_agent, source, created_at)
                VALUES (:id, :actorId, :action, :entityType, :entityId, CAST(:newValues AS jsonb),
                        CAST(:ipAddress AS inet), :userAgent, :source, now())
                """)
                .setParameter("id", UUID.randomUUID())
                .setParameter("actorId", actorId)
                .setParameter("action", action)
                .setParameter("entityType", entityType)
                .setParameter("entityId", entityId)
                .setParameter("newValues", json(newValues))
                .setParameter("ipAddress", ipAddress == null || ipAddress.isBlank() ? "0.0.0.0" : ipAddress)
                .setParameter("userAgent", truncate(userAgent))
                .setParameter("source", source)
                .executeUpdate();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudo serializar a JSON", ex);
        }
    }

    private static String truncate(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "desconocido";
        }
        return userAgent.length() > MAX_USER_AGENT ? userAgent.substring(0, MAX_USER_AGENT) : userAgent;
    }
}
