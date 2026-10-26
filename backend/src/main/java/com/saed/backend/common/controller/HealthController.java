package com.saed.backend.common.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * HealthController — public endpoints for monitoring and warm-up. {@code /health} reflects database reachability
 * (503 when it is down, without any detail); {@code /ping} is a cheap liveness answer for keep-alive callers.
 */
@Tag(name = "Health", description = "Monitorización y Warm-up de SAED")
@RestController
@RequestMapping("/api/v1")
public class HealthController {

    private static final String SERVICE = "saed-backend";

    private final DatabaseHealthProbe databaseProbe;

    public HealthController(DatabaseHealthProbe databaseProbe) {
        this.databaseProbe = databaseProbe;
    }

    @Operation(summary = "Estado del servicio y de su base de datos")
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> checkHealth() {
        boolean up = databaseProbe.isUp();
        String state = up ? "UP" : "DOWN";
        return ResponseEntity.status(up ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "status", state,
                "database", state,
                "service", SERVICE,
                "timestamp", Instant.now().toString()));
    }

    @Operation(summary = "Liveness barato para mantener despierto el contenedor")
    @GetMapping("/ping")
    public ResponseEntity<Map<String, Object>> ping() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", SERVICE,
                "timestamp", Instant.now().toString()));
    }
}
