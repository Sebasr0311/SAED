package com.saed.backend.sanciones.controller;

import com.saed.backend.sanciones.dto.SancionDTO;
import com.saed.backend.sanciones.service.SancionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/sanciones")
public class SancionController {

    private final SancionService sancionService;

    public SancionController(SancionService sancionService) {
        this.sancionService = sancionService;
    }

    @GetMapping("/todas")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'ADMIN_PROPIEDAD')")
    public ResponseEntity<List<SancionDTO>> getAllSanciones() {
        return ResponseEntity.ok(sancionService.getAllSanciones());
    }

    @GetMapping("/mis-sanciones")
    @PreAuthorize("hasRole('RESIDENTE')")
    public ResponseEntity<List<SancionDTO>> getMisSanciones() {
        return ResponseEntity.ok(sancionService.getMisSanciones());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'ADMIN_PROPIEDAD', 'RESIDENTE')")
    public ResponseEntity<SancionDTO> getSancionById(@PathVariable Long id) {
        return ResponseEntity.ok(sancionService.getSancionById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'ADMIN_PROPIEDAD')")
    public ResponseEntity<Long> createSancion(@RequestBody SancionDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sancionService.createSancion(request));
    }

    @PostMapping("/{id}/descargos")
    @PreAuthorize("hasRole('RESIDENTE')")
    public ResponseEntity<Void> submitDescargos(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        sancionService.submitDescargos(id, payload);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/resolucion")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'ADMIN_PROPIEDAD')")
    public ResponseEntity<Void> emitirResolucion(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        sancionService.emitirResolucion(id, payload);
        return ResponseEntity.ok().build();
    }
}
