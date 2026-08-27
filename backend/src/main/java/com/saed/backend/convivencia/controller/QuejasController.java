package com.saed.backend.convivencia.controller;

import com.saed.backend.convivencia.dto.QuejaDTO;
import com.saed.backend.convivencia.dto.QuejaRequestDTO;
import com.saed.backend.convivencia.service.QuejaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

@Tag(name = "Quejas", description = "API para la gestion de Quejas")
@RestController
public class QuejasController {
    private final QuejaService service;

    public QuejasController(QuejaService service) {
        this.service = service;
    }


    @GetMapping("/api/v1/quejas")
    @PreAuthorize("hasAuthority('SCOPE_RESIDENTE')")
    public ResponseEntity<List<QuejaDTO>> getMyQuejas() {
        return ResponseEntity.ok(service.findMyQuejas());
    }

    @PostMapping("/api/v1/quejas")
    @PreAuthorize("hasAuthority('SCOPE_RESIDENTE')")
    public ResponseEntity<Void> createQueja(@Valid @RequestBody QuejaRequestDTO request) {
        service.createQueja(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/api/v1/quejas/todas")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD') or hasAuthority('SCOPE_SUPERADMIN')")
    public ResponseEntity<List<QuejaDTO>> getAllQuejas() {
        return ResponseEntity.ok(service.findAll());
    }

    @PutMapping("/api/v1/quejas/{id}/responder")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<Void> responderQueja(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        service.responder(id, payload.get("respuesta"));
        return ResponseEntity.ok().build();
    }

    @PutMapping("/api/v1/quejas/{id}/estado")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<Void> actualizarEstadoQueja(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        service.actualizarEstado(id, payload.get("estado"));
        return ResponseEntity.ok().build();
    }

    @PutMapping("/api/v1/quejas/{id}/prioridad")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<Void> actualizarPrioridadQueja(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        service.actualizarPrioridad(id, payload.get("prioridad"));
        return ResponseEntity.ok().build();
    }
}

