package com.xelastay.property.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PropertyImageResponse(
        UUID id,
        String imageUrl,
        String caption,
        int displayOrder,
        boolean cover,
        OffsetDateTime createdAt) {
}
