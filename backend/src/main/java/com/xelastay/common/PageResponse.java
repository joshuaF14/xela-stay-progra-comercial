package com.xelastay.common;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * Respuesta paginada estable para la API (evita serializar PageImpl de Spring Data).
 *
 * @param page número de página, empezando en 0
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> of(Page<?> page, List<T> content) {
        return new PageResponse<>(content, page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages());
    }
}
