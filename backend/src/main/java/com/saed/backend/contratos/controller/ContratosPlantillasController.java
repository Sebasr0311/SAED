package com.saed.backend.contratos.controller;

import com.saed.backend.common.dto.ApiResponse;
import com.saed.backend.contratos.dto.PlantillaContratoDTO;
import com.saed.backend.contratos.service.PlantillaContratoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "ContratosPlantillas", description = "Catalogo y renderizado de plantillas para administradores de propiedad")
@RestController
@RequestMapping("/api/v1/contratos/plantillas")
@PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_ADMIN_ORGANIZACION')")
public class ContratosPlantillasController {

    private final PlantillaContratoService plantillaService;

    public ContratosPlantillasController(PlantillaContratoService plantillaService) {
        this.plantillaService = plantillaService;
    }

    @Operation(summary = "Listar plantillas de contrato activas de la organizacion")
    @GetMapping("/activas")
    public ResponseEntity<ApiResponse<List<PlantillaContratoDTO>>> listarActivas() {
        return ResponseEntity.ok(ApiResponse.success(plantillaService.listarActivasParaPropiedad()));
    }

    @Operation(summary = "Renderizar vista previa de contrato con plantilla y variables")
    @PostMapping("/{id}/preview")
    public ResponseEntity<ApiResponse<String>> renderizarPreview(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> variables) {
        String html = plantillaService.renderizarPlantilla(id, variables != null ? variables : Map.of());
        return ResponseEntity.ok(ApiResponse.success(html));
    }
}
