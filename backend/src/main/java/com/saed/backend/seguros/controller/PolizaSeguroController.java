package com.saed.backend.seguros.controller;

import com.saed.backend.audit.Auditable;
import com.saed.backend.audit.AuditCategory;
import com.saed.backend.audit.AuditSeverity;

import com.saed.backend.seguros.dto.PolizaSeguroDTO;
import com.saed.backend.seguros.service.PolizaSeguroService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@Tag(name = "Pólizas de Seguro", description = "Gestión de pólizas de seguro de copropiedad")
@RestController
@RequestMapping("/api/v1/seguros/polizas")
@PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
public class PolizaSeguroController {

    private final PolizaSeguroService service;

    public PolizaSeguroController(PolizaSeguroService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<PolizaSeguroDTO>> getAll() {
        return ResponseEntity.ok(service.getAllPolizas());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PolizaSeguroDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getPolizaById(id));
    }

    @PostMapping
    @Auditable(action = "CREATE", resource = "POLIZA_SEGURO", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.INFO)
    public ResponseEntity<Void> create(@RequestBody PolizaSeguroDTO dto) {
        service.createPoliza(dto);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}")
    @Auditable(action = "UPDATE", resource = "POLIZA_SEGURO", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.INFO)
    public ResponseEntity<Void> update(@PathVariable Long id, @RequestBody PolizaSeguroDTO dto) {
        service.updatePoliza(id, dto);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    @Auditable(action = "DELETE", resource = "POLIZA_SEGURO", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.WARN)
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deletePoliza(id);
        return ResponseEntity.ok().build();
    }
}
