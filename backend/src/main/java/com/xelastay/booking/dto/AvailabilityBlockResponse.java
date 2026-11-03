package com.xelastay.booking.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.xelastay.booking.AvailabilityBlock;

public record AvailabilityBlockResponse(
        UUID id,
        UUID propertyId,
        LocalDate startDate,
        LocalDate endDate,
        String reason,
        UUID createdBy,
        Instant createdAt) {

    public static AvailabilityBlockResponse from(AvailabilityBlock block) {
        return new AvailabilityBlockResponse(block.getId(), block.getPropertyId(), block.getStartDate(),
                block.getEndDate(), block.getReason(), block.getCreatedBy(), block.getCreatedAt());
    }
}
