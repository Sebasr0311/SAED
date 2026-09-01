package com.saed.backend.porteria.controller;

import com.saed.backend.audit.Auditable;
import com.saed.backend.audit.AuditCategory;
import com.saed.backend.audit.AuditSeverity;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import java.util.Map;

@Tag(name = "PorteriaExt", description = "API para la gestion de PorteriaExt")
@RestController
@RequestMapping("/api/v1")
public class PorteriaExtController {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    public PorteriaExtController(NamedParameterJdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    @PostMapping("/visitas/rapida")
    @PreAuthorize("hasAuthority('SCOPE_RESIDENTE') or hasAuthority('SCOPE_PORTERO')")
    @Auditable(action = "CREATE", resource = "VISITA_RAPIDA", category = AuditCategory.SECURITY, severity = AuditSeverity.INFO)
    public ResponseEntity<Void> visitaRapida(@RequestBody Map<String, Object> payload) {
        return ResponseEntity.ok().build();
    }
}

