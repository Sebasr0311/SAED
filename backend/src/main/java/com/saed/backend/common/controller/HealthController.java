package com.saed.backend.common.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * HealthController — Endpoint ligero y público para warm-up y monitorización
 * (p. ej. UptimeRobot, cron-jobs de keep-alive y pre-calentamiento frontend).
 */
@Tag(name = "Health", description = "Monitorización y Warm-up de SAED")
@RestController
@RequestMapping("/api/v1")
public class HealthController {

    @Operation(summary = "Verificar estado y mantener despierto el contenedor")
    @GetMapping({"/health", "/ping"})
    public ResponseEntity<Map<String, Object>> checkHealth() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "saed-backend",
            "timestamp", Instant.now().toString()
        ));
    }
}
