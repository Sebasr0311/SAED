package com.saed.backend.authorization.controller;

import com.saed.backend.audit.Auditable;
import com.saed.backend.audit.AuditCategory;
import com.saed.backend.audit.AuditSeverity;
import com.saed.backend.authorization.dto.BlockDTO;
import com.saed.backend.authorization.dto.BlockRequestDTO;
import com.saed.backend.authorization.dto.BlockTreeDTO;
import com.saed.backend.authorization.service.BlockService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Blocks", description = "API para la gestión de bloques y estructura de propiedades")
@RestController
@RequestMapping("/api/v1/properties/{propertyId}/blocks")
public class BlockController {

    private final BlockService blockService;

    public BlockController(BlockService blockService) {
        this.blockService = blockService;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD', 'SCOPE_PORTERO', 'SCOPE_RESIDENTE', 'SCOPE_RESIDENTE_CONVIVENCIA')")
    public ResponseEntity<List<BlockDTO>> findAll(@PathVariable Long propertyId) {
        return ResponseEntity.ok(blockService.findByPropertyId(propertyId));
    }

    @GetMapping("/tree")
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD', 'SCOPE_PORTERO', 'SCOPE_RESIDENTE', 'SCOPE_RESIDENTE_CONVIVENCIA')")
    public ResponseEntity<List<BlockTreeDTO>> findTree(@PathVariable Long propertyId) {
        return ResponseEntity.ok(blockService.findTreeByPropertyId(propertyId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD', 'SCOPE_PORTERO', 'SCOPE_RESIDENTE', 'SCOPE_RESIDENTE_CONVIVENCIA')")
    public ResponseEntity<BlockDTO> findById(@PathVariable Long propertyId, @PathVariable Long id) {
        return ResponseEntity.ok(blockService.findById(propertyId, id));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD')")
    @Auditable(action = "CREATE", resource = "BLOQUE", category = AuditCategory.ADMINISTRATIVE, severity = AuditSeverity.INFO)
    public ResponseEntity<Map<String, Object>> create(
            @PathVariable Long propertyId,
            @Valid @RequestBody BlockRequestDTO request) {
        Long id = blockService.create(propertyId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("success", true, "id", id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD')")
    @Auditable(action = "UPDATE", resource = "BLOQUE", category = AuditCategory.ADMINISTRATIVE, severity = AuditSeverity.INFO)
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable Long propertyId,
            @PathVariable Long id,
            @Valid @RequestBody BlockRequestDTO request) {
        blockService.update(propertyId, id, request);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD')")
    @Auditable(action = "UPDATE_STATUS", resource = "BLOQUE", category = AuditCategory.ADMINISTRATIVE, severity = AuditSeverity.INFO)
    public ResponseEntity<Map<String, Object>> updateStatus(
            @PathVariable Long propertyId,
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String estado = body.get("estado");
        blockService.updateStatus(propertyId, id, estado);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD')")
    @Auditable(action = "DELETE", resource = "BLOQUE", category = AuditCategory.ADMINISTRATIVE, severity = AuditSeverity.WARN)
    public ResponseEntity<Map<String, Object>> delete(
            @PathVariable Long propertyId,
            @PathVariable Long id) {
        blockService.delete(propertyId, id);
        return ResponseEntity.ok(Map.of("success", true, "message", "Bloque eliminado exitosamente"));
    }
}
