package com.saed.backend.contratos.controller;

import com.saed.backend.audit.AuditCategory;
import com.saed.backend.audit.AuditSeverity;
import com.saed.backend.audit.Auditable;
import com.saed.backend.common.dto.ApiResponse;
import com.saed.backend.contratos.dto.PlantillaContratoDTO;
import com.saed.backend.contratos.dto.PlantillaContratoRequestDTO;
import com.saed.backend.contratos.service.PlantillaContratoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "OrgPlantillasContratos", description = "Gestion de plantillas de contratos por organizacion")
@RestController
@RequestMapping("/api/v1/org/contratos/plantillas")
@PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_ORGANIZACION', 'SCOPE_SUPERADMIN')")
public class OrgPlantillasContratosController {

    private final PlantillaContratoService plantillaService;

    public OrgPlantillasContratosController(PlantillaContratoService plantillaService) {
        this.plantillaService = plantillaService;
    }

    @Operation(summary = "Listar plantillas de contrato de la organizacion")
    @GetMapping
    public ResponseEntity<ApiResponse<List<PlantillaContratoDTO>>> listar(
            @RequestParam(required = false) String estado) {
        return ResponseEntity.ok(ApiResponse.success(plantillaService.listarPorOrganizacion(estado)));
    }

    @Operation(summary = "Consultar variables estandar soportadas")
    @GetMapping("/variables")
    public ResponseEntity<ApiResponse<List<String>>> getVariables() {
        return ResponseEntity.ok(ApiResponse.success(plantillaService.getVariablesSoportadas()));
    }

    @Operation(summary = "Obtener detalle de una plantilla")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PlantillaContratoDTO>> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(plantillaService.obtenerPorId(id)));
    }

    @Operation(summary = "Crear nueva plantilla de contrato")
    @PostMapping
    @Auditable(action = "CREATE", resource = "PLANTILLA_CONTRATO", category = AuditCategory.ADMINISTRATIVE, severity = AuditSeverity.HIGH)
    public ResponseEntity<ApiResponse<PlantillaContratoDTO>> crear(
            @Valid @RequestBody PlantillaContratoRequestDTO request) {
        PlantillaContratoDTO creada = plantillaService.crear(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(creada));
    }

    @Operation(summary = "Actualizar plantilla de contrato existente")
    @PutMapping("/{id}")
    @Auditable(action = "UPDATE", resource = "PLANTILLA_CONTRATO", category = AuditCategory.ADMINISTRATIVE, severity = AuditSeverity.HIGH)
    public ResponseEntity<ApiResponse<PlantillaContratoDTO>> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody PlantillaContratoRequestDTO request) {
        PlantillaContratoDTO actualizada = plantillaService.actualizar(id, request);
        return ResponseEntity.ok(ApiResponse.success(actualizada));
    }

    @Operation(summary = "Crear nueva version de una plantilla de contrato")
    @PostMapping("/{id}/version")
    @Auditable(action = "CREATE_VERSION", resource = "PLANTILLA_CONTRATO", category = AuditCategory.ADMINISTRATIVE, severity = AuditSeverity.HIGH)
    public ResponseEntity<ApiResponse<PlantillaContratoDTO>> crearNuevaVersion(
            @PathVariable Long id,
            @Valid @RequestBody PlantillaContratoRequestDTO request) {
        PlantillaContratoDTO nuevaVersion = plantillaService.crearNuevaVersion(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(nuevaVersion));
    }

    @Operation(summary = "Cambiar estado de una plantilla (ACTIVA, BORRADOR, HISTORICA)")
    @PatchMapping("/{id}/estado")
    @Auditable(action = "UPDATE_STATUS", resource = "PLANTILLA_CONTRATO", category = AuditCategory.ADMINISTRATIVE, severity = AuditSeverity.HIGH)
    public ResponseEntity<ApiResponse<Void>> cambiarEstado(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String estado = body.get("estado");
        if (estado == null || estado.isBlank()) {
            throw new IllegalArgumentException("El campo estado es requerido");
        }
        plantillaService.cambiarEstado(id, estado);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "Previsualizar renderizado de plantilla con variables")
    @PostMapping("/{id}/preview")
    public ResponseEntity<ApiResponse<String>> previsualizar(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> variables) {
        String html = plantillaService.renderizarPlantilla(id, variables != null ? variables : Map.of());
        return ResponseEntity.ok(ApiResponse.success(html));
    }
}
