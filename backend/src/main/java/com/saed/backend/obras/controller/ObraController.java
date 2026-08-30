package com.saed.backend.obras.controller;

import com.saed.backend.obras.dto.ObraDTO;
import com.saed.backend.obras.service.ObraService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/obras")
public class ObraController {

    private final ObraService obraService;

    public ObraController(ObraService obraService) {
        this.obraService = obraService;
    }

    @GetMapping("/admin")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'ADMIN_PROPIEDAD')")
    public ResponseEntity<List<ObraDTO>> getObrasAdmin() {
        return ResponseEntity.ok(obraService.getObrasAdmin());
    }

    @GetMapping("/mis-obras")
    @PreAuthorize("hasRole('RESIDENTE')")
    public ResponseEntity<List<ObraDTO>> getMisObras() {
        return ResponseEntity.ok(obraService.getMisObras());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'ADMIN_PROPIEDAD', 'RESIDENTE')")
    public ResponseEntity<ObraDTO> getObraById(@PathVariable Long id) {
        return ResponseEntity.ok(obraService.getObraById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'ADMIN_PROPIEDAD', 'RESIDENTE')")
    public ResponseEntity<Map<String, Long>> solicitarObra(@RequestBody ObraDTO request) {
        Long id = obraService.solicitarObra(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("idObra", id));
    }

    @PostMapping("/{id}/aprobar")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'ADMIN_PROPIEDAD')")
    public ResponseEntity<Void> aprobarObra(@PathVariable Long id) {
        obraService.aprobarObra(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/rechazar")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'ADMIN_PROPIEDAD')")
    public ResponseEntity<Void> rechazarObra(@PathVariable Long id) {
        obraService.rechazarObra(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/finalizar")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'ADMIN_PROPIEDAD')")
    public ResponseEntity<Void> finalizarObra(@PathVariable Long id) {
        obraService.finalizarObra(id);
        return ResponseEntity.ok().build();
    }
}
