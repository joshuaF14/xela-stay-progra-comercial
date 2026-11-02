package com.xelastay.security;

import java.util.Collection;
import java.util.UUID;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Decodificador de JWT de Supabase mediante llaves asimétricas publicadas en JWKS.
 * Supabase firma con ES256 (ECC P-256) o RS256 según la llave activa del proyecto; el secreto
 * heredado HS256 no se publica en el JWKS y no está soportado aquí (ver docs/architecture/AUTENTICACION.md).
 */
@Configuration
@EnableConfigurationProperties(SupabaseJwtProperties.class)
public class SupabaseJwtDecoderConfig {

    @Bean
    public JwtDecoder jwtDecoder(SupabaseJwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwksUri())
                .jwsAlgorithms(algorithms -> {
                    algorithms.add(SignatureAlgorithm.ES256);
                    algorithms.add(SignatureAlgorithm.RS256);
                })
                .build();
        decoder.setJwtValidator(validator(properties));
        return decoder;
    }

    /** Vigencia (exp/nbf), issuer, audience y sub con formato UUID. */
    static OAuth2TokenValidator<Jwt> validator(SupabaseJwtProperties properties) {
        OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<Collection<String>>(
                JwtClaimNames.AUD, aud -> aud != null && aud.contains(properties.audience()));
        OAuth2TokenValidator<Jwt> subjectIsUuid = new JwtClaimValidator<String>(
                JwtClaimNames.SUB, SupabaseJwtDecoderConfig::isUuid);
        return new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuer()), audience, subjectIsUuid);
    }

    private static boolean isUuid(String value) {
        if (value == null) {
            return false;
        }
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
