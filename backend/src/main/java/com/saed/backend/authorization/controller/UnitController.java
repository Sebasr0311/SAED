package com.saed.backend.authorization.controller;

import com.saed.backend.audit.Auditable;
import com.saed.backend.audit.AuditCategory;
import com.saed.backend.audit.AuditSeverity;

import com.saed.backend.authorization.dto.UnitDTO;
import com.saed.backend.authorization.dto.UnitRequestDTO;
import com.saed.backend.authorization.service.UnitService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.Map;

@Tag(name = "Unit", description = "API para la gestion de Unit")
@RestController
@RequestMapping("/api/v1/units")
@PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_PROPIEDAD')")
public class UnitController {

    private final UnitService unitService;

    public UnitController(UnitService unitService) {
        this.unitService = unitService;
    }

    @GetMapping
    public ResponseEntity<List<UnitDTO>> findAll() {
        return ResponseEntity.ok(unitService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<UnitDTO> findById(@PathVariable Long id) {
        return ResponseEntity.ok(unitService.findById(id));
    }

    @PostMapping
    @Auditable(action = "CREATE", resource = "UNIDAD", category = AuditCategory.ADMINISTRATIVE, severity = AuditSeverity.INFO)
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody UnitRequestDTO request) {
        Long id = unitService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("success", true, "id", id));
    }

    @PutMapping("/{id}")
    @Auditable(action = "UPDATE", resource = "UNIDAD", category = AuditCategory.ADMINISTRATIVE, severity = AuditSeverity.INFO)
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id, @Valid @RequestBody UnitRequestDTO request) {
        unitService.update(id, request);
        return ResponseEntity.ok(Map.of("success", true));
    }
}

