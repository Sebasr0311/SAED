package com.saed.backend.sanciones.controller;

import com.saed.backend.sanciones.dto.*;
import com.saed.backend.sanciones.service.SancionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Sanciones y Debido Proceso", description = "Gestión integral de pliegos de cargos, descargos y resoluciones sancionatorias según Ley 675")
@RestController
@RequestMapping("/api/v1/sanciones")
public class SancionesController {

    private final SancionService sancionService;

    public SancionesController(SancionService sancionService) {
        this.sancionService = sancionService;
    }

    @Operation(summary = "Listar todas las sanciones de la propiedad para administradores")
    @GetMapping("/todas")
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<List<SancionDTO>> getAllSanciones() {
        return ResponseEntity.ok(sancionService.getAllSanciones());
    }

    @Operation(summary = "Obtener el detalle de un expediente disciplinario con sus descargos")
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SancionDTO> getSancionById(@PathVariable Long id) {
        return ResponseEntity.ok(sancionService.getSancionById(id));
    }

    @Operation(summary = "Abrir un pliego de cargos formal con debido proceso")
    @PostMapping
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<SancionDTO> crearPliego(@Valid @RequestBody SancionCreateRequestDTO request) {
        SancionDTO creada = sancionService.crearPliego(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    @Operation(summary = "Radicar descargos y pruebas de defensa por parte del residente o propietario")
    @PostMapping("/{id}/descargos")
    @PreAuthorize("hasAnyAuthority('SCOPE_RESIDENTE', 'SCOPE_PROPIETARIO')")
    public ResponseEntity<Map<String, String>> radicarDescargos(@PathVariable Long id,
                                                                 @Valid @RequestBody DescargoRequestDTO request) {
        sancionService.radicarDescargos(id, request);
        return ResponseEntity.ok(Map.of("message", "Descargos radicados correctamente"));
    }

    @Operation(summary = "Emitir resolución de fondo (Aplicada, Absuelta, Anulada) por el Consejo/Administración")
    @PostMapping("/{id}/resolucion")
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<Map<String, String>> emitirResolucion(@PathVariable Long id,
                                                                 @Valid @RequestBody ResolucionRequestDTO request) {
        sancionService.emitirResolucion(id, request);
        return ResponseEntity.ok(Map.of("message", "Resolución emitida exitosamente"));
    }

    @Operation(summary = "Listar los expedientes disciplinarios que atañen al residente autenticado")
    @GetMapping("/mis-sanciones")
    @PreAuthorize("hasAnyAuthority('SCOPE_RESIDENTE', 'SCOPE_PROPIETARIO')")
    public ResponseEntity<List<SancionDTO>> getMisSanciones() {
        return ResponseEntity.ok(sancionService.getMisSanciones());
    }
}
