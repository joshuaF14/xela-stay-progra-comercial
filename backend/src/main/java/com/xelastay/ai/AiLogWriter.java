package com.xelastay.ai;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xelastay.ai.dto.SourceRef;
import com.xelastay.audit.AuditService;
import com.xelastay.audit.AuditSource;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registro del agente: ai_interactions (pregunta, respuesta y fuentes) con SQL nativo, y audit_logs a través de
 * {@link AuditService} del módulo de auditoría.
 */
@Component
public class AiLogWriter {

    private static final int MAX_USER_AGENT = 500;

    @PersistenceContext
    private EntityManager entityManager;

    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    public AiLogWriter(ObjectMapper objectMapper, AuditService auditService) {
        this.objectMapper = objectMapper;
        this.auditService = auditService;
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
        auditService.record(actorId, action, entityType, entityId, null, newValues, AuditSource.valueOf(source),
                ipAddress == null || ipAddress.isBlank() ? "0.0.0.0" : ipAddress, truncate(userAgent));
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
