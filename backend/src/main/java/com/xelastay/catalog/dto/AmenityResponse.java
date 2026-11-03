package com.xelastay.catalog.dto;

import java.util.UUID;

public record AmenityResponse(UUID id, String name, String description, boolean active) {
}
