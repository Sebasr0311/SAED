package com.saed.backend.reservas.controller;

import com.saed.backend.reservas.dto.ReservaDTO;
import com.saed.backend.reservas.dto.ZonaComunDTO;
import com.saed.backend.reservas.service.ReservasService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class ReservasController {

    private final ReservasService reservasService;

    public ReservasController(ReservasService reservasService) {
        this.reservasService = reservasService;
    }

    // --- Zonas Comunes ---
    @GetMapping("/zonas-comunes")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'ADMIN_PROPIEDAD', 'RESIDENTE')")
    public ResponseEntity<List<ZonaComunDTO>> getZonasComunes() {
        return ResponseEntity.ok(reservasService.getAllZonasComunes());
    }

    // --- Reservas ---
    @GetMapping("/reservas/todas")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'ADMIN_PROPIEDAD')")
    public ResponseEntity<List<ReservaDTO>> getAllReservas() {
        return ResponseEntity.ok(reservasService.getAllReservas());
    }

    @GetMapping("/reservas/mis-reservas")
    @PreAuthorize("hasRole('RESIDENTE')")
    public ResponseEntity<List<ReservaDTO>> getMyReservas() {
        return ResponseEntity.ok(reservasService.getMyReservas());
    }

    @PostMapping("/reservas")
    @PreAuthorize("hasAnyRole('RESIDENTE', 'ADMIN_PROPIEDAD')")
    public ResponseEntity<Long> createReserva(@RequestBody ReservaDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reservasService.createReserva(request));
    }

    @PutMapping("/reservas/{id}/estado")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'ADMIN_PROPIEDAD')")
    public ResponseEntity<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        reservasService.updateReservaStatus(id, payload.get("estado"));
        return ResponseEntity.ok().build();
    }
}
