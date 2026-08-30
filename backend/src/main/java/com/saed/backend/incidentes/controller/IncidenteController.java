package com.saed.backend.incidentes.controller;

import com.saed.backend.incidentes.dto.IncidenteDTO;
import com.saed.backend.incidentes.service.IncidenteService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/incidentes")
public class IncidenteController {

    private final IncidenteService incidenteService;

    public IncidenteController(IncidenteService incidenteService) {
        this.incidenteService = incidenteService;
    }

    @GetMapping("/admin")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'ADMIN_PROPIEDAD', 'PORTERO')")
    public ResponseEntity<List<IncidenteDTO>> getAllIncidentes() {
        return ResponseEntity.ok(incidenteService.getAllIncidentes());
    }

    @GetMapping("/mis-incidentes")
    @PreAuthorize("hasRole('RESIDENTE')")
    public ResponseEntity<List<IncidenteDTO>> getMisIncidentes() {
        return ResponseEntity.ok(incidenteService.getMisIncidentes());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'ADMIN_PROPIEDAD', 'PORTERO', 'RESIDENTE')")
    public ResponseEntity<IncidenteDTO> getIncidenteById(@PathVariable Long id) {
        return ResponseEntity.ok(incidenteService.getIncidenteById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'ADMIN_PROPIEDAD', 'PORTERO', 'RESIDENTE')")
    public ResponseEntity<Map<String, Long>> reportarIncidente(@Valid @RequestBody IncidenteDTO request) {
        Long id = incidenteService.reportarIncidente(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("idIncidente", id));
    }

    @PostMapping("/{id}/cerrar")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'ADMIN_PROPIEDAD')")
    public ResponseEntity<Void> cerrarIncidente(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        incidenteService.cerrarIncidente(id, payload.get("conclusiones"));
        return ResponseEntity.ok().build();
    }
}
