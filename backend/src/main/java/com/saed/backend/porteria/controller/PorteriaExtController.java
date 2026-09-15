package com.saed.backend.porteria.controller;

import com.saed.backend.audit.Auditable;
import com.saed.backend.audit.AuditCategory;
import com.saed.backend.audit.AuditSeverity;
import org.springframework.http.HttpStatus;
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
    private final PorteriaController porteriaController;

    public PorteriaExtController(NamedParameterJdbcTemplate jdbcTemplate, PorteriaController porteriaController) {
        this.jdbcTemplate = jdbcTemplate;
        this.porteriaController = porteriaController;
    }

    @PostMapping("/visitas")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD') or hasAuthority('SCOPE_RESIDENTE') or hasAuthority('SCOPE_RESIDENTE_CONVIVENCIA') or hasAuthority('SCOPE_PORTERO')")
    public Map<String, Object> programarVisita(@RequestBody Map<String, Object> body) {
        return porteriaController.programarVisita(body);
    }

    @PostMapping("/visitas/rapida")
    @PreAuthorize("hasAuthority('SCOPE_RESIDENTE') or hasAuthority('SCOPE_PORTERO')")
    @Auditable(action = "CREATE", resource = "VISITA_RAPIDA", category = AuditCategory.SECURITY, severity = AuditSeverity.INFO)
    public ResponseEntity<Void> visitaRapida(@RequestBody Map<String, Object> payload) {
        return ResponseEntity.ok().build();
    }
}

