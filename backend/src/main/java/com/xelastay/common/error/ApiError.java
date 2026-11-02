package com.xelastay.common.error;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * Formato único de error de la API: {timestamp, status, error, message, path, details}.
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<Detail> details) {

    /** Detalle de un error puntual, por ejemplo un campo inválido. */
    public record Detail(String field, String message) {
    }

    public static ApiError of(HttpStatusCode statusCode, String message, String path, List<Detail> details) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        String reason = status != null ? status.getReasonPhrase() : String.valueOf(statusCode.value());
        return new ApiError(Instant.now(), statusCode.value(), reason, message, path,
                details != null ? List.copyOf(details) : List.of());
    }
}
