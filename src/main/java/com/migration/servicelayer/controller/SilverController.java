package com.migration.servicelayer.controller;

import com.migration.servicelayer.service.SilverTransformationScheduler;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Management endpoints for the Silver Layer.
 *
 * <p>Normal operation is fully automatic: the scheduler runs at midnight and transforms
 * all bronze records that have a registered schema. This endpoint allows triggering the
 * pipeline immediately without waiting for midnight (useful for testing or catching up
 * after downtime).
 *
 * <p>To register or update schemas, use {@code POST /api/schema/{eventType}}.
 * <p>Requires a valid JWT.
 */
@RestController
@RequestMapping("/api/silver")
public class SilverController {

    private final SilverTransformationScheduler silverTransformationScheduler;

    public SilverController(SilverTransformationScheduler silverTransformationScheduler) {
        this.silverTransformationScheduler = silverTransformationScheduler;
    }

    /**
     * Triggers the full transformation pipeline immediately for all tenants.
     *
     * <p>Example: {@code POST /api/silver/run}
     */
    @PostMapping("/run")
    public ResponseEntity<Map<String, String>> forceRun(JwtAuthenticationToken token) {
        silverTransformationScheduler.processAllTenants();
        return ResponseEntity.accepted().body(Map.of(
                "message", "Execução da camada silver iniciada. Verifique os logs para acompanhar o progresso."
        ));
    }
}
