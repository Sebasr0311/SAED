package com.saed.backend.porteria.controller;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class PorteriaExtController {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    public PorteriaExtController(NamedParameterJdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    @PostMapping("/visitas/rapida")
    @PreAuthorize("hasAuthority('SCOPE_RESIDENTE') or hasAuthority('SCOPE_PORTERO')")
    public ResponseEntity<Void> visitaRapida(@RequestBody Map<String, Object> payload) {
        return ResponseEntity.ok().build();
    }
}
