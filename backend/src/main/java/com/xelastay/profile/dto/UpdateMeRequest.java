package com.xelastay.profile.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cambios parciales del perfil propio. Un campo ausente (null) no se modifica; en phone y avatarUrl,
 * una cadena vacía borra el valor.
 */
public record UpdateMeRequest(
        @Size(min = 1, max = 150, message = "El nombre debe tener entre 1 y 150 caracteres")
        @Pattern(regexp = ".*\\S.*", message = "El nombre no puede estar en blanco") String fullName,
        @Size(max = 30, message = "El teléfono admite máximo 30 caracteres")
        @Pattern(regexp = "^$|^\\+?[0-9 ()-]{7,30}$", message = "Teléfono inválido") String phone,
        @Size(max = 2048, message = "La URL del avatar admite máximo 2048 caracteres")
        @Pattern(regexp = "^$|^https://\\S+$", message = "La URL del avatar debe empezar con https://") String avatarUrl) {
}
