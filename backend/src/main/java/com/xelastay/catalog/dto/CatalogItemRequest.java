package com.xelastay.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Alta o edición de una categoría o amenidad. {@code active} es opcional: al crear vale true por defecto y al
 * editar, si no se envía, conserva el valor actual.
 */
public record CatalogItemRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre admite máximo 100 caracteres") String name,
        @Size(max = 500, message = "La descripción admite máximo 500 caracteres") String description,
        Boolean active) {
}
