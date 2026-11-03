package com.xelastay.catalog.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record LocationResponse(
        UUID id,
        String department,
        String municipality,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        OffsetDateTime createdAt) {
}
