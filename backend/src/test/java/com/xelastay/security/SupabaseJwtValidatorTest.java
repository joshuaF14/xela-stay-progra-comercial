package com.xelastay.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

class SupabaseJwtValidatorTest {

    private static final String ISSUER = "https://proyecto.supabase.co/auth/v1";

    private final OAuth2TokenValidator<Jwt> validator = SupabaseJwtDecoderConfig.validator(
            new SupabaseJwtProperties("https://proyecto.supabase.co/auth/v1/.well-known/jwks.json", ISSUER, "authenticated"));

    @Test
    void acceptsValidSupabaseToken() {
        assertThat(validator.validate(jwt(ISSUER, "authenticated", UUID.randomUUID().toString())).hasErrors()).isFalse();
    }

    @Test
    void rejectsWrongIssuer() {
        assertThat(validator.validate(jwt("https://otro.supabase.co/auth/v1", "authenticated",
                UUID.randomUUID().toString())).hasErrors()).isTrue();
    }

    @Test
    void rejectsWrongAudience() {
        assertThat(validator.validate(jwt(ISSUER, "anon", UUID.randomUUID().toString())).hasErrors()).isTrue();
    }

    @Test
    void rejectsSubjectThatIsNotUuid() {
        assertThat(validator.validate(jwt(ISSUER, "authenticated", "no-uuid")).hasErrors()).isTrue();
    }

    private static Jwt jwt(String issuer, String audience, String subject) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("token")
                .header("alg", "ES256")
                .issuer(issuer)
                .audience(List.of(audience))
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();
    }
}
