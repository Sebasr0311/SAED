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

    /** Used by the visits page cancel action; cancels the visit and revokes its QR codes (F07-01). */
    @DeleteMapping("/visitas/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_RESIDENTE', 'SCOPE_RESIDENTE_CONVIVENCIA')")
    public void cancelarVisita(@PathVariable Long id) {
        porteriaController.cancelarVisita(id);
    }

    @PostMapping("/visitas/rapida")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD') or hasAuthority('SCOPE_RESIDENTE') or hasAuthority('SCOPE_RESIDENTE_CONVIVENCIA') or hasAuthority('SCOPE_PORTERO')")
    @Auditable(action = "CREATE", resource = "VISITA_RAPIDA", category = AuditCategory.SECURITY, severity = AuditSeverity.INFO)
    public ResponseEntity<Map<String, Object>> visitaRapida(@RequestBody Map<String, Object> payload) {
        Map<String, Object> result = porteriaController.programarVisitaRapida(payload);
        return new ResponseEntity<>(result, HttpStatus.CREATED);
    }
}

