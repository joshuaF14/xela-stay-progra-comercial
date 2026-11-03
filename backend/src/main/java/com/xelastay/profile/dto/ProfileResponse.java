package com.xelastay.profile.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.xelastay.profile.ProfileStatus;

public record ProfileResponse(
        UUID id,
        String email,
        String fullName,
        String phone,
        String avatarUrl,
        ProfileStatus status,
        List<String> roles,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
