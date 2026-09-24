package com.saed.backend.dashboard.controller;

import com.saed.backend.common.dto.ApiResponse;
import com.saed.backend.dashboard.dto.HistorialReporteDTO;
import com.saed.backend.dashboard.service.ReportesConfiguradosService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * HistorialReportesController — Consulta auditable e inmutable del historial de reportes generados.
 */
@Tag(name = "Historial de Reportes", description = "Auditoría de ejecuciones, hashes de integridad y registros procesados")
@RestController
@RequestMapping("/api/v1/reportes/historial")
@PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD')")
public class HistorialReportesController {

    private final ReportesConfiguradosService reportesConfiguradosService;

    public HistorialReportesController(ReportesConfiguradosService reportesConfiguradosService) {
        this.reportesConfiguradosService = reportesConfiguradosService;
    }

    @Operation(summary = "Listar historial de reportes", description = "Retorna el historial paginado de reportes generados según el ámbito de tenant.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<HistorialReporteDTO>>> listar(
            @Parameter(description = "ID de propiedad (opcional para ADMIN_ORGANIZACION)")
            @RequestParam(required = false) Long propertyId,
            @Parameter(description = "Número de página (0-indexed)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamaño de página (máximo 200)")
            @RequestParam(defaultValue = "50") int size
    ) {
        List<HistorialReporteDTO> list = reportesConfiguradosService.listarHistorial(propertyId, page, size);
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @Operation(summary = "Obtener detalle de reporte generado", description = "Retorna los metadatos y hash de integridad de un reporte generado específico.")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<HistorialReporteDTO>> obtenerPorId(@PathVariable Long id) {
        HistorialReporteDTO dto = reportesConfiguradosService.obtenerHistorialPorId(id);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }
}
