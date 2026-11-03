package com.xelastay.security;

import jakarta.servlet.DispatcherType;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.expression.WebExpressionAuthorizationManager;

/**
 * Seguridad como resource server de Supabase Auth: API stateless, health público y
 * el resto de /api/** con JWT válido. Cualquier otra ruta se rechaza.
 * Los roles (ROLE_GUEST, ROLE_HOST, ROLE_ADMIN) se exigen con {@code @PreAuthorize} en los controladores.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /** Autenticado y con perfil ACTIVE: un perfil SUSPENDED o INACTIVE recibe 403. */
    private static final WebExpressionAuthorizationManager ACTIVE_PROFILE = new WebExpressionAuthorizationManager(
            "isAuthenticated() and !hasAuthority('" + ProfileJwtAuthenticationConverter.PROFILE_BLOCKED + "')");

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ApiErrorSecurityHandlers errorHandlers,
            ProfileJwtAuthenticationConverter jwtConverter) throws Exception {
        http
                .cors(Customizer.withDefaults())
                // La API usa Bearer token sin cookies de sesión: CSRF no aplica a /api/**.
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/health", "/actuator/health", "/actuator/health/**")
                        .permitAll()
                        // Calendario y reseñas públicas de un alojamiento (módulos de reservas y reseñas).
                        .requestMatchers(HttpMethod.GET, "/api/v1/properties/*/availability",
                                "/api/v1/properties/*/reviews").permitAll()
                        // Catálogo y búsqueda de alojamientos (el servicio decide qué es visible).
                        // "/api/v1/properties/*" solo cubre /properties/{id}; /me/properties sigue protegida.
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/property-categories", "/api/v1/property-categories/**",
                                "/api/v1/amenities", "/api/v1/amenities/**",
                                "/api/v1/locations", "/api/v1/locations/**",
                                "/api/v1/properties", "/api/v1/properties/*").permitAll()
                        // Un perfil bloqueado todavía puede consultar su propio estado.
                        .requestMatchers(HttpMethod.GET, "/api/v1/me").authenticated()
                        .requestMatchers("/api/**").access(ACTIVE_PROFILE)
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter))
                        .authenticationEntryPoint(errorHandlers)
                        .accessDeniedHandler(errorHandlers))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(errorHandlers)
                        .accessDeniedHandler(errorHandlers));
        return http.build();
    }
}
