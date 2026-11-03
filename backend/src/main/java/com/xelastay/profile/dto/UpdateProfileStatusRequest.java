package com.xelastay.profile.dto;

import jakarta.validation.constraints.NotNull;

import com.xelastay.profile.ProfileStatus;

public record UpdateProfileStatusRequest(
        @NotNull(message = "El estado es obligatorio") ProfileStatus status) {
}
