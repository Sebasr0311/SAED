package com.saed.backend.dashboard.controller;

import com.saed.backend.common.dto.ApiResponse;
import com.saed.backend.dashboard.dto.CarteraMorosaDTO;
import com.saed.backend.dashboard.dto.EjecucionCuotasDTO;
import com.saed.backend.dashboard.dto.EjecucionPresupuestalGlobalDTO;
import com.saed.backend.dashboard.dto.PagoRecienteDTO;
import com.saed.backend.dashboard.export.ExportFormat;
import com.saed.backend.dashboard.export.ExportResult;
import com.saed.backend.dashboard.export.ReportExportService;
import com.saed.backend.dashboard.service.ReportesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * ReportesController — Motor base de reportes operativos y financieros en SAED 2.0.
 *
 * Expone endpoints de solo lectura fuertemente tipados en JSON camelCase y soporta
 * exportación polimórfica en CSV (RFC 4180 BOM UTF-8) y PDF (OpenHTMLtoPDF).
 * Aplica aislamiento estricto por tenant, filtros temporales de negocio y control de volumen.
 *
 * Contratos:
 *   GET /api/v1/reportes/cartera-morosa          — unidades con saldo vencido y cálculo de días de mora
 *   GET /api/v1/reportes/ejecucion-cuotas        — facturación y recaudo operativo por periodo contable
 *   GET /api/v1/reportes/pagos-recientes         — historial cronológico de pagos registrados
 *   GET /api/v1/reportes/ejecucion-presupuestal  — consolidado de ejecución de ingresos y egresos
 */
@Tag(name = "Reportes", description = "Reportes operativos y financieros del sistema")
@RestController
@RequestMapping("/api/v1/reportes")
@PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD')")
public class ReportesController {

    private final ReportesService reportesService;
    private final ReportExportService reportExportService;

    public ReportesController(ReportesService reportesService, ReportExportService reportExportService) {
        this.reportesService = reportesService;
        this.reportExportService = reportExportService;
    }

    @Operation(summary = "Reporte de Cartera Morosa", description = "Retorna unidades morosas con saldo y cálculo de días de mora. Soporta exportación en JSON, PDF y CSV.")
    @GetMapping("/cartera-morosa")
    public ResponseEntity<?> carteraMorosa(
            @Parameter(description = "ID de propiedad (solo para ADMIN_ORGANIZACION perteneciente a su org)")
            @RequestParam(required = false) Long propertyId,
            @Parameter(description = "Fecha de vencimiento mínima (YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @Parameter(description = "Fecha de vencimiento máxima (YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin,
            @Parameter(description = "Número de página (0-indexed)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamaño de página (máximo 200, por defecto 50)")
            @RequestParam(defaultValue = "50") int size,
            @Parameter(description = "Formato de exportación (JSON, PDF, CSV)")
            @RequestParam(required = false) String formato,
            @Parameter(description = "Formato de exportación alternativo (JSON, PDF, CSV)")
            @RequestParam(required = false) String format
    ) {
        String effFormat = (formato != null && !formato.isBlank()) ? formato : format;
        ExportFormat exportFormat = ExportFormat.fromString(effFormat);

        if (exportFormat == ExportFormat.JSON) {
            List<CarteraMorosaDTO> items = reportesService.getCarteraMorosa(propertyId, fechaInicio, fechaFin, page, size);
            return ResponseEntity.ok(ApiResponse.success(items));
        }

        List<CarteraMorosaDTO> items = reportesService.getCarteraMorosa(propertyId, fechaInicio, fechaFin, 0, 10001);
        ExportResult result = reportExportService.exportCarteraMorosa(items, exportFormat, propertyId, fechaInicio, fechaFin);
        return result.toResponseEntity();
    }

    @Operation(summary = "Reporte de Ejecución de Cuotas", description = "Facturación, saldos pendientes y recaudo agrupado por periodo contable. Soporta exportación en JSON, PDF y CSV.")
    @GetMapping("/ejecucion-cuotas")
    public ResponseEntity<?> ejecucionCuotas(
            @Parameter(description = "ID de propiedad (solo para ADMIN_ORGANIZACION perteneciente a su org)")
            @RequestParam(required = false) Long propertyId,
            @Parameter(description = "Fecha inicio para derivar periodo (YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @Parameter(description = "Fecha fin para derivar periodo (YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin,
            @Parameter(description = "Periodo inicial de consulta (YYYY-MM)")
            @RequestParam(required = false) String periodoInicio,
            @Parameter(description = "Periodo final de consulta (YYYY-MM)")
            @RequestParam(required = false) String periodoFin,
            @Parameter(description = "Formato de exportación (JSON, PDF, CSV)")
            @RequestParam(required = false) String formato,
            @Parameter(description = "Formato de exportación alternativo (JSON, PDF, CSV)")
            @RequestParam(required = false) String format
    ) {
        String effFormat = (formato != null && !formato.isBlank()) ? formato : format;
        ExportFormat exportFormat = ExportFormat.fromString(effFormat);

        List<EjecucionCuotasDTO> items = reportesService.getEjecucionCuotas(propertyId, fechaInicio, fechaFin, periodoInicio, periodoFin);

        if (exportFormat == ExportFormat.JSON) {
            return ResponseEntity.ok(ApiResponse.success(items));
        }

        ExportResult result = reportExportService.exportEjecucionCuotas(items, exportFormat, propertyId, periodoInicio, periodoFin);
        return result.toResponseEntity();
    }

    @Operation(summary = "Reporte de Pagos Recientes", description = "Listado ordenado de pagos registrados con filtro por fecha de pago. Soporta exportación en JSON, PDF y CSV.")
    @GetMapping("/pagos-recientes")
    public ResponseEntity<?> pagosRecientes(
            @Parameter(description = "ID de propiedad (solo para ADMIN_ORGANIZACION perteneciente a su org)")
            @RequestParam(required = false) Long propertyId,
            @Parameter(description = "Fecha de pago mínima (YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @Parameter(description = "Fecha de pago máxima (YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin,
            @Parameter(description = "Número de página (0-indexed)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamaño de página (máximo 200, por defecto 50)")
            @RequestParam(defaultValue = "50") int size,
            @Parameter(description = "Formato de exportación (JSON, PDF, CSV)")
            @RequestParam(required = false) String formato,
            @Parameter(description = "Formato de exportación alternativo (JSON, PDF, CSV)")
            @RequestParam(required = false) String format
    ) {
        String effFormat = (formato != null && !formato.isBlank()) ? formato : format;
        ExportFormat exportFormat = ExportFormat.fromString(effFormat);

        if (exportFormat == ExportFormat.JSON) {
            List<PagoRecienteDTO> items = reportesService.getPagosRecientes(propertyId, fechaInicio, fechaFin, page, size);
            return ResponseEntity.ok(ApiResponse.success(items));
        }

        List<PagoRecienteDTO> items = reportesService.getPagosRecientes(propertyId, fechaInicio, fechaFin, 0, 10001);
        ExportResult result = reportExportService.exportPagosRecientes(items, exportFormat, propertyId, fechaInicio, fechaFin);
        return result.toResponseEntity();
    }

    @Operation(summary = "Reporte de Ejecución Presupuestal Global", description = "Resumen consolidado de ingresos y egresos presupuestados vs ejecutados. Soporta exportación en JSON, PDF y CSV.")
    @GetMapping("/ejecucion-presupuestal")
    public ResponseEntity<?> ejecucionPresupuestal(
            @Parameter(description = "ID de propiedad (solo para ADMIN_ORGANIZACION perteneciente a su org)")
            @RequestParam(required = false) Long propertyId,
            @Parameter(description = "Año fiscal / vigencia a evaluar (YYYY, por defecto año actual)")
            @RequestParam(required = false) Integer vigencia,
            @Parameter(description = "Alias para vigencia")
            @RequestParam(required = false) Integer vigenciaAnio,
            @Parameter(description = "Formato de exportación (JSON, PDF, CSV)")
            @RequestParam(required = false) String formato,
            @Parameter(description = "Formato de exportación alternativo (JSON, PDF, CSV)")
            @RequestParam(required = false) String format
    ) {
        String effFormat = (formato != null && !formato.isBlank()) ? formato : format;
        ExportFormat exportFormat = ExportFormat.fromString(effFormat);

        Integer effVigencia = vigencia != null ? vigencia : vigenciaAnio;
        EjecucionPresupuestalGlobalDTO resumen = reportesService.getEjecucionPresupuestal(propertyId, effVigencia);

        if (exportFormat == ExportFormat.JSON) {
            return ResponseEntity.ok(ApiResponse.success(resumen));
        }

        ExportResult result = reportExportService.exportEjecucionPresupuestal(resumen, exportFormat, propertyId, effVigencia);
        return result.toResponseEntity();
    }
}
