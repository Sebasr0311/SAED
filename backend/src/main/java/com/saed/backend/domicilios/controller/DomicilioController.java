package com.saed.backend.domicilios.controller;

import com.saed.backend.audit.AuditCategory;
import com.saed.backend.audit.AuditSeverity;
import com.saed.backend.audit.Auditable;
import com.saed.backend.domicilios.dto.DomicilioCreateDTO;
import com.saed.backend.domicilios.dto.DomicilioDTO;
import com.saed.backend.domicilios.service.DomicilioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Domicilios", description = "API canónica para la gestión y trazabilidad operativa de domicilios en portería")
@RestController
@RequestMapping("/api/v1/domicilios")
public class DomicilioController {

    private final DomicilioService domicilioService;

    public DomicilioController(DomicilioService domicilioService) {
        this.domicilioService = domicilioService;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('SCOPE_PORTERO', 'SCOPE_ADMIN_PROPIEDAD')")
    @Auditable(action = "CREATE", resource = "DOMICILIOS", category = AuditCategory.SECURITY, severity = AuditSeverity.INFO)
    @Operation(summary = "Registrar ingreso rápido de domiciliario en portería")
    public ResponseEntity<DomicilioDTO> registrarDomicilio(@Valid @RequestBody DomicilioCreateDTO request) {
        DomicilioDTO creado = domicilioService.registrarDomicilio(request);
        return new ResponseEntity<>(creado, HttpStatus.CREATED);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('SCOPE_PORTERO', 'SCOPE_ADMIN_PROPIEDAD', 'SCOPE_RESIDENTE', 'SCOPE_RESIDENTE_CONVIVENCIA')")
    @Operation(summary = "Listar domicilios registrados (filtrado por estado opcional)")
    public ResponseEntity<List<DomicilioDTO>> listarDomicilios(@RequestParam(required = false) String estado) {
        return ResponseEntity.ok(domicilioService.listarDomicilios(estado));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('SCOPE_PORTERO', 'SCOPE_ADMIN_PROPIEDAD', 'SCOPE_RESIDENTE', 'SCOPE_RESIDENTE_CONVIVENCIA')")
    @Operation(summary = "Consultar detalle de un domicilio por ID")
    public ResponseEntity<DomicilioDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(domicilioService.buscarPorId(id));
    }

    @PatchMapping("/{id}/finalizar")
    @PreAuthorize("hasAnyAuthority('SCOPE_PORTERO', 'SCOPE_ADMIN_PROPIEDAD')")
    @Auditable(action = "UPDATE", resource = "DOMICILIOS", category = AuditCategory.SECURITY, severity = AuditSeverity.INFO)
    @Operation(summary = "Registrar salida y finalización del domicilio")
    public ResponseEntity<DomicilioDTO> finalizarDomicilio(@PathVariable Long id) {
        return ResponseEntity.ok(domicilioService.finalizarDomicilio(id));
    }

    @PatchMapping("/{id}/cancelar")
    @PreAuthorize("hasAnyAuthority('SCOPE_PORTERO', 'SCOPE_ADMIN_PROPIEDAD')")
    @Auditable(action = "UPDATE", resource = "DOMICILIOS", category = AuditCategory.SECURITY, severity = AuditSeverity.INFO)
    @Operation(summary = "Cancelar registro de domicilio")
    public ResponseEntity<DomicilioDTO> cancelarDomicilio(@PathVariable Long id) {
        return ResponseEntity.ok(domicilioService.cancelarDomicilio(id));
    }
}
