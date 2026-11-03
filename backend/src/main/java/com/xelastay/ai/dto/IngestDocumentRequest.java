package com.xelastay.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Documento en texto para la base de conocimiento.
 *
 * @param documentType categoría libre, p. ej. POLITICA, REGLAS o FAQ
 * @param source       de dónde viene el documento (opcional); por defecto "api"
 */
public record IngestDocumentRequest(
        @NotBlank(message = "El título es obligatorio")
        @Size(max = 200, message = "El título admite máximo 200 caracteres") String title,
        @NotBlank(message = "El tipo de documento es obligatorio")
        @Size(max = 50, message = "El tipo admite máximo 50 caracteres") String documentType,
        @NotBlank(message = "El contenido es obligatorio")
        @Size(max = 200_000, message = "El contenido admite máximo 200000 caracteres") String content,
        @Size(max = 500, message = "La fuente admite máximo 500 caracteres") String source) {
}
