package com.xelastay.messaging.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Conversación vista por el usuario actual.
 *
 * @param otherParticipantId   el anfitrión si el usuario es huésped, o el huésped si es anfitrión
 * @param lastMessage          último mensaje, o null si todavía no hay mensajes
 * @param unreadCount          mensajes que el otro participante envió y el usuario aún no leyó
 */
public record ConversationResponse(
        UUID id,
        UUID propertyId,
        String propertyTitle,
        UUID otherParticipantId,
        String otherParticipantName,
        MessageResponse lastMessage,
        long unreadCount,
        Instant createdAt,
        Instant updatedAt) {
}
