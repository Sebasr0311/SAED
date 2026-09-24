package com.saed.backend.dashboard.registry;

import com.saed.backend.dashboard.export.ExportFormat;
import com.saed.backend.dashboard.export.ReportExportService;
import com.saed.backend.dashboard.service.ReportesService;

/**
 * ReportDefinition — Contrato para una definición de reporte Java tipada y segura.
 */
public interface ReportDefinition {

    ReportDefinitionKey getKey();

    default String getModulo() {
        return getKey().getModulo();
    }

    default String getNombreDefecto() {
        return getKey().getDefaultName();
    }

    ReportExecutionResult execute(
            ReportesService reportesService,
            ReportExportService reportExportService,
            ReportExecutionParams params,
            ExportFormat format
    );
}
