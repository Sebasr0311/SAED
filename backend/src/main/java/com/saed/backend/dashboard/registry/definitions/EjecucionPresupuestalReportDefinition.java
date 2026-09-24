package com.saed.backend.dashboard.registry.definitions;

import com.saed.backend.dashboard.dto.EjecucionPresupuestalGlobalDTO;
import com.saed.backend.dashboard.export.ExportFormat;
import com.saed.backend.dashboard.export.ExportResult;
import com.saed.backend.dashboard.export.ReportExportService;
import com.saed.backend.dashboard.registry.ReportDefinition;
import com.saed.backend.dashboard.registry.ReportDefinitionKey;
import com.saed.backend.dashboard.registry.ReportExecutionParams;
import com.saed.backend.dashboard.registry.ReportExecutionResult;
import com.saed.backend.dashboard.service.ReportesService;
import org.springframework.stereotype.Component;

@Component
public class EjecucionPresupuestalReportDefinition implements ReportDefinition {

    @Override
    public ReportDefinitionKey getKey() {
        return ReportDefinitionKey.EJECUCION_PRESUPUESTAL;
    }

    @Override
    public ReportExecutionResult execute(
            ReportesService reportesService,
            ReportExportService reportExportService,
            ReportExecutionParams params,
            ExportFormat format
    ) {
        EjecucionPresupuestalGlobalDTO dto = reportesService.getEjecucionPresupuestal(
                params.propertyId(),
                params.vigenciaAnio()
        );
        ExportResult exportResult = reportExportService.exportEjecucionPresupuestal(
                dto,
                format,
                params.propertyId(),
                params.vigenciaAnio()
        );
        int rows = 1;
        return new ReportExecutionResult(rows, exportResult, dto);
    }
}
