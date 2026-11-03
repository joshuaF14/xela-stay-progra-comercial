package com.xelastay.catalog.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CategoryResponse(UUID id, String name, String description, boolean active, OffsetDateTime createdAt) {
}
