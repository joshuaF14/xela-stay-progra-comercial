package com.xelastay.common.health;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Verificación pública de que la API está en línea. El estado de la base de datos
 * se consulta en /actuator/health.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final String version;

    public HealthController(@Value("${xelastay.version}") String version) {
        this.version = version;
    }

    @GetMapping
    public HealthResponse health() {
        return new HealthResponse("UP", version, Instant.now());
    }

    public record HealthResponse(String status, String version, Instant timestamp) {
    }
}
