package com.saed.backend.activos.controller;

import com.saed.backend.activos.dto.ActivoCreateDTO;
import com.saed.backend.activos.dto.ActivoDTO;
import com.saed.backend.activos.dto.ActivoEstadoDTO;
import com.saed.backend.activos.dto.ActivoUpdateDTO;
import com.saed.backend.activos.service.ActivoService;
import com.saed.backend.audit.AuditCategory;
import com.saed.backend.audit.AuditSeverity;
import com.saed.backend.audit.Auditable;
import com.saed.backend.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Activos", description = "Módulo Completo de Activos e Inventario Físico de la Copropiedad (GAP-F9-02)")
@RestController
@RequestMapping("/api/v1/activos")
@PreAuthorize("hasAuthority('SCOPE_SUPERADMIN') or hasAuthority('SCOPE_ADMIN_ORGANIZACION') or hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
public class ActivoController {

    private final ActivoService activoService;

    public ActivoController(ActivoService activoService) {
        this.activoService = activoService;
    }

    @Operation(summary = "Listar activos de la copropiedad con filtros opcionales")
    @GetMapping
    public ResponseEntity<ApiResponse<List<ActivoDTO>>> listar(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) String search) {
        List<ActivoDTO> lista = activoService.listar(estado, categoria, search);
        return ResponseEntity.ok(ApiResponse.success(lista));
    }

    @Operation(summary = "Obtener detalle de un activo por ID (property-scoped)")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ActivoDTO>> obtenerPorId(@PathVariable Long id) {
        ActivoDTO dto = activoService.obtenerPorId(id);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @Operation(summary = "Registrar un nuevo activo en la copropiedad")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Auditable(action = "CREATE", resource = "ACTIVO", category = AuditCategory.ADMINISTRATIVE, severity = AuditSeverity.HIGH)
    public ResponseEntity<ApiResponse<ActivoDTO>> crear(@Valid @RequestBody ActivoCreateDTO request) {
        ActivoDTO creado = activoService.crear(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(creado));
    }

    @Operation(summary = "Actualizar información y metadata de un activo existente")
    @PutMapping("/{id}")
    @Auditable(action = "UPDATE", resource = "ACTIVO", category = AuditCategory.ADMINISTRATIVE, severity = AuditSeverity.HIGH)
    public ResponseEntity<ApiResponse<ActivoDTO>> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody ActivoUpdateDTO request) {
        ActivoDTO actualizado = activoService.actualizar(id, request);
        return ResponseEntity.ok(ApiResponse.success(actualizado));
    }

    @Operation(summary = "Transición de estado del activo (OPERATIVO <-> MANTENIMIENTO, -> DADO_DE_BAJA)")
    @PatchMapping("/{id}/estado")
    @Auditable(action = "UPDATE_STATUS", resource = "ACTIVO", category = AuditCategory.ADMINISTRATIVE, severity = AuditSeverity.HIGH)
    public ResponseEntity<ApiResponse<Void>> actualizarEstado(
            @PathVariable Long id,
            @Valid @RequestBody ActivoEstadoDTO request) {
        activoService.actualizarEstado(id, request.estado());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "Dar de baja un activo de forma lógica (DADO_DE_BAJA)")
    @DeleteMapping("/{id}")
    @Auditable(action = "RETIRE", resource = "ACTIVO", category = AuditCategory.ADMINISTRATIVE, severity = AuditSeverity.HIGH)
    public ResponseEntity<ApiResponse<Void>> darDeBaja(@PathVariable Long id) {
        activoService.darDeBaja(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
