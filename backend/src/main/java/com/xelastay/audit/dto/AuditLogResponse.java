package com.xelastay.audit.dto;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import com.xelastay.audit.AuditSource;

/** @param actorName nombre visible del actor; null si la acción fue del sistema o el perfil ya no existe */
public record AuditLogResponse(
        UUID id,
        UUID actorId,
        String actorName,
        String action,
        String entityType,
        UUID entityId,
        Map<String, Object> oldValues,
        Map<String, Object> newValues,
        String ipAddress,
        String userAgent,
        AuditSource source,
        OffsetDateTime createdAt) {
}
