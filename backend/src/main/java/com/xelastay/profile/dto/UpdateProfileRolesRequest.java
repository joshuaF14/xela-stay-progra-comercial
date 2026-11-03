package com.xelastay.profile.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

/** Lista completa de roles del perfil (reemplaza la actual), por nombre: ["GUEST", "HOST"]. */
public record UpdateProfileRolesRequest(
        @NotEmpty(message = "Debe indicar al menos un rol")
        List<@NotBlank(message = "El nombre del rol no puede estar vacío") String> roles) {
}
