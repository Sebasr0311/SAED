package com.saed.backend.paquetes.controller;

import com.saed.backend.paquetes.dto.PaqueteDTO;
import com.saed.backend.paquetes.dto.PaqueteEntregaDTO;
import com.saed.backend.paquetes.dto.PaqueteRequestDTO;
import com.saed.backend.paquetes.service.PaquetesService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@Tag(name = "Paquetes", description = "API para la gestion de Paquetes")
@RestController
@RequestMapping("/api/v1/paquetes")
public class PaquetesController {

    private final PaquetesService paquetesService;

    public PaquetesController(PaquetesService paquetesService) {
        this.paquetesService = paquetesService;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('SCOPE_PORTERO', 'SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<PaqueteDTO> registrarPaquete(@Valid @RequestBody PaqueteRequestDTO request) {
        return new ResponseEntity<>(paquetesService.registrarPaquete(request), HttpStatus.CREATED);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('SCOPE_PORTERO', 'SCOPE_ADMIN_PROPIEDAD', 'SCOPE_RESIDENTE')")
    public ResponseEntity<List<PaqueteDTO>> getPaquetes() {
        return ResponseEntity.ok(paquetesService.getPaquetes());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('SCOPE_PORTERO', 'SCOPE_ADMIN_PROPIEDAD', 'SCOPE_RESIDENTE')")
    public ResponseEntity<PaqueteDTO> getPaqueteById(@PathVariable Long id) {
        return ResponseEntity.ok(paquetesService.getPaqueteById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('SCOPE_PORTERO', 'SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<PaqueteDTO> actualizarPaquete(@PathVariable Long id, @Valid @RequestBody PaqueteRequestDTO request) {
        return ResponseEntity.ok(paquetesService.actualizarPaquete(id, request));
    }

    @PostMapping("/{id}/entrega")
    @PreAuthorize("hasAnyAuthority('SCOPE_PORTERO', 'SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<PaqueteDTO> registrarEntrega(@PathVariable Long id, @Valid @RequestBody PaqueteEntregaDTO request) {
        return ResponseEntity.ok(paquetesService.registrarEntrega(id, request));
    }
}

