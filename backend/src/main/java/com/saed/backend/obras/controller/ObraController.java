package com.saed.backend.obras.controller;

import com.saed.backend.obras.dto.ObraDTO;
import com.saed.backend.obras.service.ObraService;
import com.saed.backend.trabajadores.dto.ObraTrabajadorDTO;
import com.saed.backend.trabajadores.service.TrabajadorService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import com.saed.backend.platform.annotation.RequireModule;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/obras")
@RequireModule("OBRAS")
public class ObraController {

    private final ObraService obraService;
    private final TrabajadorService trabajadorService;

    public ObraController(ObraService obraService, TrabajadorService trabajadorService) {
        this.obraService = obraService;
        this.trabajadorService = trabajadorService;
    }

    @GetMapping("/admin")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<List<ObraDTO>> getObrasAdmin() {
        return ResponseEntity.ok(obraService.getObrasAdmin());
    }

    @GetMapping("/mis-obras")
    @PreAuthorize("hasAnyAuthority('SCOPE_RESIDENTE', 'SCOPE_PROPIETARIO')")
    public ResponseEntity<List<ObraDTO>> getMisObras() {
        return ResponseEntity.ok(obraService.getMisObras());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD', 'SCOPE_RESIDENTE', 'SCOPE_PROPIETARIO')")
    public ResponseEntity<ObraDTO> getObraById(@PathVariable Long id) {
        return ResponseEntity.ok(obraService.getObraById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD', 'SCOPE_RESIDENTE', 'SCOPE_PROPIETARIO')")
    public ResponseEntity<Map<String, Long>> solicitarObra(@RequestBody ObraDTO request) {
        Long id = obraService.solicitarObra(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("idObra", id));
    }

    @PostMapping("/{id}/aprobar")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<Void> aprobarObra(@PathVariable Long id) {
        obraService.aprobarObra(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/iniciar")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<Void> iniciarObra(@PathVariable Long id) {
        obraService.iniciarObra(id);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<Void> cambiarEstado(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String nuevoEstado = (body != null) ? body.get("estado") : null;
        obraService.cambiarEstado(id, nuevoEstado);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/rechazar")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<Void> rechazarObra(@PathVariable Long id) {
        obraService.rechazarObra(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/finalizar")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<Void> finalizarObra(@PathVariable Long id) {
        obraService.finalizarObra(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/trabajadores")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD', 'SCOPE_RESIDENTE', 'SCOPE_PROPIETARIO')")
    public ResponseEntity<List<ObraTrabajadorDTO>> listarTrabajadoresObra(@PathVariable Long id) {
        return ResponseEntity.ok(trabajadorService.listarTrabajadoresObra(id));
    }

    @PostMapping("/{id}/trabajadores/{idTrabajador}")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD', 'SCOPE_RESIDENTE', 'SCOPE_PROPIETARIO')")
    public ResponseEntity<Void> asignarTrabajadorObra(
            @PathVariable Long id,
            @PathVariable Long idTrabajador,
            @RequestParam(required = false, defaultValue = "S") String autorizado
    ) {
        trabajadorService.asignarTrabajadorObra(id, idTrabajador, autorizado);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/{id}/trabajadores")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD', 'SCOPE_RESIDENTE', 'SCOPE_PROPIETARIO')")
    public ResponseEntity<Void> asignarTrabajadorObraBody(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body
    ) {
        Long idTrabajador = body.get("idTrabajador") != null ? Long.valueOf(body.get("idTrabajador").toString()) : null;
        if (idTrabajador == null) {
            throw new IllegalArgumentException("El campo idTrabajador es obligatorio");
        }
        String autorizado = body.get("autorizado") != null ? body.get("autorizado").toString() : "S";
        trabajadorService.asignarTrabajadorObra(id, idTrabajador, autorizado);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/{id}/trabajadores/{idTrabajador}/autorizar")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<Void> autorizarTrabajadorObra(
            @PathVariable Long id,
            @PathVariable Long idTrabajador
    ) {
        trabajadorService.autorizarTrabajadorObra(id, idTrabajador);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/trabajadores/{idTrabajador}/revocar")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<Void> revocarTrabajadorObra(
            @PathVariable Long id,
            @PathVariable Long idTrabajador
    ) {
        trabajadorService.revocarTrabajadorObra(id, idTrabajador);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/trabajadores/{idTrabajador}")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD', 'SCOPE_RESIDENTE', 'SCOPE_PROPIETARIO')")
    public ResponseEntity<Void> desasignarTrabajadorObra(
            @PathVariable Long id,
            @PathVariable Long idTrabajador
    ) {
        trabajadorService.desasignarTrabajadorObra(id, idTrabajador);
        return ResponseEntity.noContent().build();
    }
}
