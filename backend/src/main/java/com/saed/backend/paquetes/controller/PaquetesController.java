package com.saed.backend.paquetes.controller;

import com.saed.backend.paquetes.dto.PaqueteDTO;
import com.saed.backend.paquetes.dto.PaqueteEntregaDTO;
import com.saed.backend.paquetes.dto.PaqueteRequestDTO;
import com.saed.backend.paquetes.service.PaquetesService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
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
    @PreAuthorize("hasAnyAuthority('SCOPE_PORTERO', 'SCOPE_ADMIN_PROPIEDAD', 'SCOPE_RESIDENTE', 'SCOPE_RESIDENTE_CONVIVENCIA')")
    public ResponseEntity<List<PaqueteDTO>> getPaquetes() {
        return ResponseEntity.ok(paquetesService.getPaquetes());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('SCOPE_PORTERO', 'SCOPE_ADMIN_PROPIEDAD', 'SCOPE_RESIDENTE', 'SCOPE_RESIDENTE_CONVIVENCIA')")
    public ResponseEntity<PaqueteDTO> getPaqueteById(@PathVariable Long id) {
        return ResponseEntity.ok(paquetesService.getPaqueteById(id));
    }

    @GetMapping("/{id}/imagen")
    @PreAuthorize("hasAnyAuthority('SCOPE_PORTERO', 'SCOPE_ADMIN_PROPIEDAD', 'SCOPE_RESIDENTE', 'SCOPE_RESIDENTE_CONVIVENCIA')")
    public ResponseEntity<Resource> getImagenPaquete(@PathVariable Long id) {
        Resource resource = paquetesService.getImagenPaquete(id);
        String mimeType = paquetesService.getImagenMimeType(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mimeType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"paquete_" + id + ".jpg\"")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .body(resource);
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

