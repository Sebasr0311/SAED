package com.saed.backend.dashboard.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.common.dto.ApiResponse;
import com.saed.backend.dashboard.dto.GenerarReporteResponseDTO;
import com.saed.backend.dashboard.dto.ReporteConfiguradoCreateRequest;
import com.saed.backend.dashboard.dto.ReporteConfiguradoDTO;
import com.saed.backend.dashboard.dto.ReporteConfiguradoUpdateRequest;
import com.saed.backend.dashboard.export.ExportFormat;
import com.saed.backend.dashboard.export.ExportResult;
import com.saed.backend.dashboard.service.ReportesConfiguradosService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * ReportesConfiguradosController — API REST para la gestión y generación de reportes configurables.
 *
 * Aplica aislamiento multi-tenant por organización y propiedad, catálogo allowlistado de definiciones
 * y persistencia auditable en HISTORIAL_REPORTES.
 */
@Tag(name = "Reportes Configurables", description = "Gestión de plantillas y configuraciones de reportes con filtros y formatos")
@RestController
@RequestMapping("/api/v1/reportes/configurados")
@PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD')")
public class ReportesConfiguradosController {

    private final ReportesConfiguradosService reportesConfiguradosService;
    private final ObjectMapper objectMapper;

    public ReportesConfiguradosController(ReportesConfiguradosService reportesConfiguradosService, ObjectMapper objectMapper) {
        this.reportesConfiguradosService = reportesConfiguradosService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Listar reportes configurados", description = "Retorna las plantillas y configuraciones visibles para el tenant en sesión.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<ReporteConfiguradoDTO>>> listar(
            @Parameter(description = "ID de propiedad (opcional para ADMIN_ORGANIZACION)")
            @RequestParam(required = false) Long propertyId
    ) {
        List<ReporteConfiguradoDTO> list = reportesConfiguradosService.listarConfiguraciones(propertyId);
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @Operation(summary = "Obtener reporte configurado", description = "Retorna el detalle de una configuración por ID verificando propiedad de tenant.")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ReporteConfiguradoDTO>> obtenerPorId(@PathVariable Long id) {
        ReporteConfiguradoDTO dto = reportesConfiguradosService.obtenerPorId(id);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @Operation(summary = "Crear reporte configurado", description = "Crea una nueva configuración validando que la clave pertenezca al catálogo allowlistado.")
    @PostMapping
    public ResponseEntity<ApiResponse<ReporteConfiguradoDTO>> crear(
            @Valid @RequestBody ReporteConfiguradoCreateRequest request
    ) {
        ReporteConfiguradoDTO created = reportesConfiguradosService.crearConfiguracion(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    @Operation(summary = "Actualizar reporte configurado", description = "Actualiza filtros, formato o nombre de una configuración existente.")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ReporteConfiguradoDTO>> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody ReporteConfiguradoUpdateRequest request
    ) {
        ReporteConfiguradoDTO updated = reportesConfiguradosService.actualizarConfiguracion(id, request);
        return ResponseEntity.ok(ApiResponse.success(updated));
    }

    @Operation(summary = "Desactivar reporte configurado", description = "Desactivación lógica de una configuración sin destruir historial previo.")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> desactivar(@PathVariable Long id) {
        reportesConfiguradosService.desactivarConfiguracion(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "Generar reporte configurado", description = "Ejecuta de forma segura la consulta asociada a la configuración, exporta al formato solicitado y registra evidencia inmutable en HISTORIAL_REPORTES.")
    @PostMapping("/{id}/generar")
    public ResponseEntity<?> generar(
            @PathVariable Long id,
            @Parameter(description = "Formato de exportación (JSON, PDF, CSV)")
            @RequestParam(required = false) String formato,
            @Parameter(description = "Formato alternativo")
            @RequestParam(required = false) String format,
            @Parameter(description = "Si es true, retorna los metadatos JSON del historial en lugar del archivo descargable")
            @RequestParam(required = false, defaultValue = "false") boolean metadata,
            @RequestBody(required = false) Map<String, Object> bodyPayload
    ) {
        String effFormat = (formato != null && !formato.isBlank()) ? formato : format;

        String overrideFiltrosJson = null;
        if (bodyPayload != null && !bodyPayload.isEmpty()) {
            if (bodyPayload.containsKey("formato") && effFormat == null) {
                effFormat = String.valueOf(bodyPayload.get("formato"));
            }
            if (bodyPayload.containsKey("filtros")) {
                try {
                    overrideFiltrosJson = objectMapper.writeValueAsString(bodyPayload.get("filtros"));
                } catch (Exception ignored) {}
            } else {
                try {
                    overrideFiltrosJson = objectMapper.writeValueAsString(bodyPayload);
                } catch (Exception ignored) {}
            }
        }

        if (metadata) {
            GenerarReporteResponseDTO meta = reportesConfiguradosService.generarReporteMetadata(id, effFormat, overrideFiltrosJson);
            return ResponseEntity.ok(ApiResponse.success(meta));
        }

        ExportResult result = reportesConfiguradosService.generarReporte(id, effFormat, overrideFiltrosJson);
        ExportFormat expFormat = ExportFormat.fromString(effFormat);

        if (expFormat == ExportFormat.JSON) {
            GenerarReporteResponseDTO meta = reportesConfiguradosService.generarReporteMetadata(id, effFormat, overrideFiltrosJson);
            return ResponseEntity.ok(ApiResponse.success(meta));
        }

        return result.toResponseEntity();
    }
}
