package com.saed.backend.parqueaderos.controller;

import com.saed.backend.parqueaderos.dto.AsignacionParqueaderoDTO;
import com.saed.backend.parqueaderos.dto.AsignacionParqueaderoRequestDTO;
import com.saed.backend.parqueaderos.dto.ParqueaderoDTO;
import com.saed.backend.parqueaderos.dto.ParqueaderoRequestDTO;
import com.saed.backend.parqueaderos.service.ParqueaderosService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@Tag(name = "Parqueaderos", description = "API para la gestion de Parqueaderos")
@RestController
@RequestMapping("/api/v1/parqueaderos")
public class ParqueaderosController {

    private final ParqueaderosService parqueaderosService;

    public ParqueaderosController(ParqueaderosService parqueaderosService) {
        this.parqueaderosService = parqueaderosService;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_PORTERO', 'SCOPE_RESIDENTE')")
    public ResponseEntity<List<ParqueaderoDTO>> getParqueaderos() {
        return ResponseEntity.ok(parqueaderosService.getParqueaderos());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_PORTERO', 'SCOPE_RESIDENTE')")
    public ResponseEntity<ParqueaderoDTO> getParqueaderoById(@PathVariable Long id) {
        return ResponseEntity.ok(parqueaderosService.getParqueaderoById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<ParqueaderoDTO> registrarParqueadero(@Valid @RequestBody ParqueaderoRequestDTO request) {
        return new ResponseEntity<>(parqueaderosService.registrarParqueadero(request), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<ParqueaderoDTO> actualizarParqueadero(@PathVariable Long id, @Valid @RequestBody ParqueaderoRequestDTO request) {
        return ResponseEntity.ok(parqueaderosService.actualizarParqueadero(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<Void> eliminarParqueadero(@PathVariable Long id) {
        parqueaderosService.eliminarParqueadero(id);
        return ResponseEntity.noContent().build();
    }

    // Asignaciones

    @GetMapping("/asignaciones")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_PORTERO', 'SCOPE_RESIDENTE')")
    public ResponseEntity<List<AsignacionParqueaderoDTO>> getAsignaciones() {
        return ResponseEntity.ok(parqueaderosService.getAsignaciones());
    }

    @PostMapping("/asignaciones")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<AsignacionParqueaderoDTO> crearAsignacion(@Valid @RequestBody AsignacionParqueaderoRequestDTO request) {
        return new ResponseEntity<>(parqueaderosService.crearAsignacion(request), HttpStatus.CREATED);
    }

    @PutMapping("/asignaciones/{id}/finalizar")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<Void> finalizarAsignacion(@PathVariable Long id) {
        parqueaderosService.finalizarAsignacion(id);
        return ResponseEntity.noContent().build();
    }
}

