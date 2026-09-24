package com.saed.backend.trabajadores.controller;

import com.saed.backend.platform.annotation.RequireModule;
import com.saed.backend.trabajadores.dto.TrabajadorCreateDTO;
import com.saed.backend.trabajadores.dto.TrabajadorDTO;
import com.saed.backend.trabajadores.dto.TrabajadorEstadoDTO;
import com.saed.backend.trabajadores.dto.TrabajadorUpdateDTO;
import com.saed.backend.trabajadores.service.TrabajadorService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/trabajadores")
@RequireModule("OBRAS")
public class TrabajadorController {

    private final TrabajadorService trabajadorService;

    public TrabajadorController(TrabajadorService trabajadorService) {
        this.trabajadorService = trabajadorService;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<TrabajadorDTO> crear(@Valid @RequestBody TrabajadorCreateDTO dto) {
        TrabajadorDTO creado = trabajadorService.crearTrabajador(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD', 'SCOPE_RESIDENTE', 'SCOPE_PROPIETARIO')")
    public ResponseEntity<List<TrabajadorDTO>> listar(
            @RequestParam(required = false) Long idProveedor,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String search
    ) {
        return ResponseEntity.ok(trabajadorService.listarTrabajadores(idProveedor, estado, search));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD', 'SCOPE_RESIDENTE', 'SCOPE_PROPIETARIO')")
    public ResponseEntity<TrabajadorDTO> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(trabajadorService.obtenerTrabajador(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<TrabajadorDTO> actualizar(
            @PathVariable Long id,
            @RequestBody TrabajadorUpdateDTO dto
    ) {
        return ResponseEntity.ok(trabajadorService.actualizarTrabajador(id, dto));
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<Void> cambiarEstado(
            @PathVariable Long id,
            @RequestBody TrabajadorEstadoDTO dto
    ) {
        trabajadorService.cambiarEstado(id, dto != null ? dto.estado() : null);
        return ResponseEntity.ok().build();
    }
}
