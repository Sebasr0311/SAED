package com.saed.backend.incidentes.controller;

import com.saed.backend.incidentes.dto.*;
import com.saed.backend.incidentes.service.IncidenteService;
import com.saed.backend.platform.annotation.RequireModule;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/incidentes")
@RequireModule("INCIDENTES")
public class IncidenteController {

    private final IncidenteService incidenteService;

    public IncidenteController(IncidenteService incidenteService) {
        this.incidenteService = incidenteService;
    }

    @GetMapping("/admin")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_PORTERO')")
    public ResponseEntity<List<IncidenteDTO>> getAllIncidentes() {
        return ResponseEntity.ok(incidenteService.getAllIncidentes());
    }

    @GetMapping("/mis-incidentes")
    @PreAuthorize("hasAnyAuthority('SCOPE_RESIDENTE', 'SCOPE_RESIDENTE_CONVIVENCIA')")
    public ResponseEntity<List<IncidenteDTO>> getMisIncidentes() {
        return ResponseEntity.ok(incidenteService.getMisIncidentes());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_PORTERO', 'SCOPE_RESIDENTE', 'SCOPE_RESIDENTE_CONVIVENCIA')")
    public ResponseEntity<IncidenteDTO> getIncidenteById(@PathVariable Long id) {
        return ResponseEntity.ok(incidenteService.getIncidenteById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_PORTERO', 'SCOPE_RESIDENTE', 'SCOPE_RESIDENTE_CONVIVENCIA')")
    public ResponseEntity<Map<String, Long>> reportarIncidente(@Valid @RequestBody IncidenteDTO request) {
        Long id = incidenteService.reportarIncidente(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("idIncidente", id));
    }

    @PutMapping("/{id}/estado")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<Void> cambiarEstado(@PathVariable Long id, @Valid @RequestBody IncidenteEstadoRequestDTO dto) {
        incidenteService.cambiarEstado(id, dto);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/investigacion/iniciar")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<Void> iniciarInvestigacion(@PathVariable Long id) {
        incidenteService.iniciarInvestigacion(id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/investigacion")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<Void> actualizarInvestigacion(@PathVariable Long id, @Valid @RequestBody IncidenteInvestigacionRequestDTO dto) {
        incidenteService.actualizarInvestigacion(id, dto);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/investigacion/concluir")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<Void> concluirInvestigacion(@PathVariable Long id, @Valid @RequestBody IncidenteInvestigacionRequestDTO dto) {
        incidenteService.concluirInvestigacion(id, dto);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/escalar")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<Void> escalarIncidente(@PathVariable Long id, @Valid @RequestBody IncidenteEscalamientoRequestDTO dto) {
        incidenteService.escalarIncidente(id, dto);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/cerrar")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<Void> cerrarIncidente(@PathVariable Long id, @RequestBody(required = false) Map<String, String> payload) {
        String conclusiones = (payload != null) ? payload.get("conclusiones") : null;
        incidenteService.cerrarIncidente(id, conclusiones);
        return ResponseEntity.ok().build();
    }

    // Involucrados
    @GetMapping("/{id}/involucrados")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_PORTERO', 'SCOPE_RESIDENTE', 'SCOPE_RESIDENTE_CONVIVENCIA')")
    public ResponseEntity<List<IncidenteInvolucradoDTO>> getInvolucrados(@PathVariable Long id) {
        return ResponseEntity.ok(incidenteService.getInvolucrados(id));
    }

    @PostMapping("/{id}/involucrados")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_PORTERO')")
    public ResponseEntity<Map<String, Long>> addInvolucrado(@PathVariable Long id, @Valid @RequestBody IncidenteInvolucradoDTO dto) {
        Long idInvolucrado = incidenteService.addInvolucrado(id, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("idIncidenteInvolucrado", idInvolucrado));
    }

    @DeleteMapping("/{id}/involucrados/{idInvolucrado}")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<Void> removeInvolucrado(@PathVariable Long id, @PathVariable Long idInvolucrado) {
        incidenteService.removeInvolucrado(id, idInvolucrado);
        return ResponseEntity.noContent().build();
    }
}
