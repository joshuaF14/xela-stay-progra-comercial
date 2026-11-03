package com.xelastay.ai.dto;

import java.util.List;
import java.util.UUID;

/**
 * Respuesta del agente con sus fuentes.
 *
 * @param interactionId fila de ai_interactions donde quedó registrada la conversación
 */
public record ChatResponse(UUID interactionId, String answer, List<SourceRef> sources) {
}
