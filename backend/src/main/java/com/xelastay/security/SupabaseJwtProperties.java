package com.xelastay.security;

import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Parámetros de validación del JWT emitido por Supabase Auth.
 * Si falta alguno, la aplicación no arranca, para no aceptar tokens sin validar.
 *
 * @param jwksUri  URL del JWKS (SUPABASE_JWKS_URI)
 * @param issuer   emisor esperado en el claim iss (SUPABASE_JWT_ISSUER)
 * @param audience audiencia esperada en el claim aud
 */
@Validated
@ConfigurationProperties("xelastay.security.jwt")
public record SupabaseJwtProperties(
        @NotBlank String jwksUri,
        @NotBlank String issuer,
        @NotBlank String audience) {
}
