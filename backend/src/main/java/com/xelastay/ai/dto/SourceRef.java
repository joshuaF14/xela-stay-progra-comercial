package com.xelastay.ai.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Fuente citada en una respuesta. {@code ref} es el número que el modelo usa entre corchetes, p. ej. [1].
 *
 * @param type DOCUMENT (fragmento de la base de conocimiento) o PROPERTY (alojamiento publicado)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SourceRef(
        int ref,
        String type,
        UUID documentId,
        String documentTitle,
        UUID chunkId,
        Integer chunkIndex,
        UUID propertyId,
        String propertyTitle,
        String excerpt) {

    public static final String DOCUMENT = "DOCUMENT";
    public static final String PROPERTY = "PROPERTY";
}
