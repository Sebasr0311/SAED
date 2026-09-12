package com.saed.backend.automatizaciones.controller;

import com.saed.backend.automatizaciones.dto.AutomatizacionesSummaryDTO;
import com.saed.backend.automatizaciones.dto.EjecucionDTO;
import com.saed.backend.automatizaciones.dto.EventoDTO;
import com.saed.backend.automatizaciones.dto.ReglaDTO;
import com.saed.backend.automatizaciones.dto.ReglaRequestDTO;
import com.saed.backend.automatizaciones.dto.SimulacionReglaRequestDTO;
import com.saed.backend.automatizaciones.service.AutomatizacionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/automatizaciones")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD')")
public class AutomatizacionesController {

    private final AutomatizacionService service;

    @GetMapping("/resumen")
    public ResponseEntity<AutomatizacionesSummaryDTO> getSummary() {
        return ResponseEntity.ok(service.getSummary());
    }

    @GetMapping("/eventos")
    public ResponseEntity<List<EventoDTO>> getEventos() {
        return ResponseEntity.ok(service.getEventos());
    }

    @GetMapping("/reglas")
    public ResponseEntity<List<ReglaDTO>> getReglas(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) Long eventoId
    ) {
        return ResponseEntity.ok(service.getReglas(estado, eventoId));
    }

    @GetMapping("/reglas/{id}")
    public ResponseEntity<ReglaDTO> getReglaById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getReglaById(id));
    }

    @PostMapping("/reglas")
    public ResponseEntity<Map<String, Object>> createRegla(@Valid @RequestBody ReglaRequestDTO request) {
        ReglaDTO created = service.createRegla(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "Regla de automatización creada exitosamente",
                "idRegla", created.getIdRegla(),
                "regla", created
        ));
    }

    @PutMapping("/reglas/{id}")
    public ResponseEntity<ReglaDTO> updateRegla(
            @PathVariable Long id,
            @Valid @RequestBody ReglaRequestDTO request
    ) {
        return ResponseEntity.ok(service.updateRegla(id, request));
    }

    @PatchMapping("/reglas/{id}/toggle-estado")
    public ResponseEntity<Map<String, String>> toggleEstado(@PathVariable Long id) {
        service.toggleEstado(id);
        return ResponseEntity.ok(Map.of("message", "Estado de la regla alternado exitosamente"));
    }

    @DeleteMapping("/reglas/{id}")
    public ResponseEntity<Void> deleteRegla(@PathVariable Long id) {
        service.deleteRegla(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reglas/{id}/simular")
    public ResponseEntity<EjecucionDTO> simularRegla(
            @PathVariable Long id,
            @RequestBody(required = false) SimulacionReglaRequestDTO simulacion
    ) {
        return ResponseEntity.ok(service.simularRegla(id, simulacion));
    }

    @GetMapping("/ejecuciones")
    public ResponseEntity<List<EjecucionDTO>> getEjecuciones(
            @RequestParam(required = false) Long reglaId,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return ResponseEntity.ok(service.getHistorialEjecuciones(reglaId, limit));
    }
}
