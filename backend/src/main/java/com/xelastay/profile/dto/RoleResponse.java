package com.xelastay.profile.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/** @param system true para GUEST, HOST y ADMIN, que no se pueden renombrar ni borrar */
public record RoleResponse(UUID id, String name, String description, boolean system, OffsetDateTime createdAt) {
}
