package com.saed.backend.reservas.controller;

import com.saed.backend.reservas.dto.ReservaDTO;
import com.saed.backend.reservas.dto.ZonaComunDTO;
import com.saed.backend.reservas.service.ReservasService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.saed.backend.platform.annotation.RequireModule;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequireModule("RESERVAS")
public class ReservasController {

    private final ReservasService reservasService;

    public ReservasController(ReservasService reservasService) {
        this.reservasService = reservasService;
    }

    // --- Zonas Comunes (GAP-F8-05) ---
    @GetMapping("/zonas-comunes")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_RESIDENTE', 'SCOPE_RESIDENTE_CONVIVENCIA')")
    public ResponseEntity<List<ZonaComunDTO>> getZonasComunes() {
        return ResponseEntity.ok(reservasService.getAllZonasComunes());
    }

    @GetMapping("/zonas-comunes/{id}")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_RESIDENTE', 'SCOPE_RESIDENTE_CONVIVENCIA')")
    public ResponseEntity<ZonaComunDTO> getZonaComunById(@PathVariable Long id) {
        return ResponseEntity.ok(reservasService.getZonaComunById(id));
    }

    @PostMapping("/zonas-comunes")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<ZonaComunDTO> createZonaComun(@jakarta.validation.Valid @RequestBody com.saed.backend.reservas.dto.CreateZonaComunDTO request) {
        ZonaComunDTO created = reservasService.createZonaComun(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/zonas-comunes/{id}")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<ZonaComunDTO> updateZonaComun(@PathVariable Long id, @jakarta.validation.Valid @RequestBody com.saed.backend.reservas.dto.UpdateZonaComunDTO request) {
        return ResponseEntity.ok(reservasService.updateZonaComun(id, request));
    }

    @DeleteMapping("/zonas-comunes/{id}")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<Void> deleteZonaComun(@PathVariable Long id) {
        reservasService.deleteZonaComun(id);
        return ResponseEntity.noContent().build();
    }

    // --- Reservas ---
    @GetMapping("/reservas/todas")
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<List<ReservaDTO>> getAllReservas() {
        return ResponseEntity.ok(reservasService.getAllReservas());
    }

    @GetMapping("/reservas/mis-reservas")
    @PreAuthorize("hasAnyAuthority('SCOPE_RESIDENTE', 'SCOPE_RESIDENTE_CONVIVENCIA')")
    public ResponseEntity<List<ReservaDTO>> getMyReservas() {
        return ResponseEntity.ok(reservasService.getMyReservas());
    }

    @GetMapping("/reservas/{id}")
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_PROPIEDAD', 'SCOPE_RESIDENTE', 'SCOPE_RESIDENTE_CONVIVENCIA')")
    public ResponseEntity<ReservaDTO> getReservaById(@PathVariable Long id) {
        return ResponseEntity.ok(reservasService.getReservaById(id));
    }

    @PostMapping("/reservas")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_RESIDENTE', 'SCOPE_RESIDENTE_CONVIVENCIA')")
    public ResponseEntity<Long> createReserva(@RequestBody ReservaDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reservasService.createReserva(request));
    }

    @PutMapping("/reservas/{id}/estado")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        reservasService.updateReservaStatus(id, payload.get("estado"));
        return ResponseEntity.ok().build();
    }

    @PutMapping("/reservas/{id}/cancelar")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_RESIDENTE', 'SCOPE_RESIDENTE_CONVIVENCIA')")
    public ResponseEntity<Void> cancelReserva(@PathVariable Long id) {
        reservasService.cancelReserva(id);
        return ResponseEntity.ok().build();
    }

    // --- GAP-F8-08: Consulta segura de estado de mora preventiva ---
    @GetMapping("/reservas/mi-estado-mora")
    @PreAuthorize("hasAnyAuthority('SCOPE_RESIDENTE', 'SCOPE_RESIDENTE_CONVIVENCIA', 'SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<com.saed.backend.common.dto.ApiResponse<com.saed.backend.finanzas.dto.PazYSalvoEstadoFinancieroDTO>> getMiEstadoMora() {
        return ResponseEntity.ok(com.saed.backend.common.dto.ApiResponse.success(reservasService.getMiEstadoMora()));
    }
}
