package com.xelastay.profile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RoleRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,49}$",
                message = "El nombre va en mayúsculas, sin espacios (letras, números y _), de 2 a 50 caracteres")
        String name,
        @Size(max = 500, message = "La descripción admite máximo 500 caracteres") String description) {
}
