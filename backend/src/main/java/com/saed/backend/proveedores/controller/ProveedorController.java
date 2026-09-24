package com.saed.backend.proveedores.controller;

import com.saed.backend.audit.AuditCategory;
import com.saed.backend.audit.AuditSeverity;
import com.saed.backend.audit.Auditable;
import com.saed.backend.common.dto.ApiResponse;
import com.saed.backend.proveedores.dto.ProveedorCreateDTO;
import com.saed.backend.proveedores.dto.ProveedorDTO;
import com.saed.backend.proveedores.dto.ProveedorEstadoDTO;
import com.saed.backend.proveedores.dto.ProveedorUpdateDTO;
import com.saed.backend.proveedores.service.ProveedorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Proveedores", description = "Catálogo Maestro de Proveedores y Contratistas (GAP-F9-01)")
@RestController
@RequestMapping("/api/v1/proveedores")
@PreAuthorize("hasAuthority('SCOPE_SUPERADMIN') or hasAuthority('SCOPE_ADMIN_ORGANIZACION') or hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
public class ProveedorController {

    private final ProveedorService proveedorService;

    public ProveedorController(ProveedorService proveedorService) {
        this.proveedorService = proveedorService;
    }

    @Operation(summary = "Listar proveedores de la organización con filtros opcionales")
    @GetMapping
    public ResponseEntity<ApiResponse<List<ProveedorDTO>>> listar(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) String search) {
        List<ProveedorDTO> lista = proveedorService.listar(estado, categoria, search);
        return ResponseEntity.ok(ApiResponse.success(lista));
    }

    @Operation(summary = "Obtener detalle de un proveedor por ID (tenant-scoped)")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProveedorDTO>> obtenerPorId(@PathVariable Long id) {
        ProveedorDTO dto = proveedorService.obtenerPorId(id);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @Operation(summary = "Crear nuevo proveedor en el catálogo de la organización")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Auditable(action = "CREATE", resource = "PROVEEDOR", category = AuditCategory.ADMINISTRATIVE, severity = AuditSeverity.HIGH)
    public ResponseEntity<ApiResponse<ProveedorDTO>> crear(@Valid @RequestBody ProveedorCreateDTO request) {
        ProveedorDTO creado = proveedorService.crear(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(creado));
    }

    @Operation(summary = "Actualizar información de un proveedor existente")
    @PutMapping("/{id}")
    @Auditable(action = "UPDATE", resource = "PROVEEDOR", category = AuditCategory.ADMINISTRATIVE, severity = AuditSeverity.HIGH)
    public ResponseEntity<ApiResponse<ProveedorDTO>> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody ProveedorUpdateDTO request) {
        ProveedorDTO actualizado = proveedorService.actualizar(id, request);
        return ResponseEntity.ok(ApiResponse.success(actualizado));
    }

    @Operation(summary = "Cambiar estado de un proveedor (ACTIVO, INACTIVO, BLOQUEADO)")
    @PatchMapping("/{id}/estado")
    @Auditable(action = "UPDATE_STATUS", resource = "PROVEEDOR", category = AuditCategory.ADMINISTRATIVE, severity = AuditSeverity.HIGH)
    public ResponseEntity<ApiResponse<Void>> actualizarEstado(
            @PathVariable Long id,
            @Valid @RequestBody ProveedorEstadoDTO request) {
        proveedorService.actualizarEstado(id, request.estado());
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
