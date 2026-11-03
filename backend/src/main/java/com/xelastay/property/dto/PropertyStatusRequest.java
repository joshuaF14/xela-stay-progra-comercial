package com.xelastay.property.dto;

import jakarta.validation.constraints.NotNull;

import com.xelastay.property.PropertyStatus;

public record PropertyStatusRequest(@NotNull(message = "El estado es obligatorio") PropertyStatus status) {
}
