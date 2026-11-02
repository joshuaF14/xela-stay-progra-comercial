package com.xelastay.common.error;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Traduce todas las excepciones de la capa web al formato {@link ApiError}.
 * Las excepciones estándar de Spring MVC (404, 405, 415, JSON inválido, etc.) pasan por
 * {@link #handleExceptionInternal}; los errores no controlados devuelven 500 sin exponer detalles internos.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ApiError.Detail> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.Detail(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return build(status, "La solicitud contiene datos inválidos", path(request), details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        List<ApiError.Detail> details = ex.getConstraintViolations().stream()
                .map(v -> new ApiError.Detail(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "La solicitud contiene datos inválidos", request.getRequestURI(), details);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Error no controlado en {}", request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor", request.getRequestURI(), List.of());
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        String message = ex instanceof ErrorResponse errorResponse && errorResponse.getBody().getDetail() != null
                ? errorResponse.getBody().getDetail()
                : ex.getMessage();
        return ResponseEntity.status(statusCode).headers(headers)
                .body(ApiError.of(statusCode, message, path(request), List.of()));
    }

    private static ResponseEntity<Object> build(
            HttpStatusCode status, String message, String path, List<ApiError.Detail> details) {
        return ResponseEntity.status(status).body(ApiError.of(status, message, path, details));
    }

    private static String path(WebRequest request) {
        return request instanceof ServletWebRequest servletRequest
                ? servletRequest.getRequest().getRequestURI()
                : null;
    }
}
