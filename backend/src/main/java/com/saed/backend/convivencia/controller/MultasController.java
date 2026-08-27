package com.saed.backend.convivencia.controller;

import com.saed.backend.convivencia.dto.MultaDTO;
import com.saed.backend.convivencia.service.MultaService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/multas")
public class MultasController {
    private final MultaService service;

    public MultasController(MultaService service) {
        this.service = service;
    }


    @GetMapping("/todas")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD') or hasAuthority('SCOPE_SUPERADMIN')")
    public ResponseEntity<List<MultaDTO>> getAllMultas() {
        return ResponseEntity.ok(service.findAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD') or hasAuthority('SCOPE_SUPERADMIN')")
    public ResponseEntity<MultaDTO> getMultaById(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PutMapping("/{id}/pagar")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD') or hasAuthority('SCOPE_SUPERADMIN')")
    public ResponseEntity<Void> pagarMulta(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        service.pagar(id, payload.getOrDefault("metodoPago", "EFECTIVO"));
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/anular")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD') or hasAuthority('SCOPE_SUPERADMIN')")
    public ResponseEntity<Void> anularMulta(@PathVariable Long id) {
        service.anular(id);
        return ResponseEntity.ok().build();
    }
}
