package com.saed.backend.dashboard.registry.definitions;

import com.saed.backend.dashboard.dto.EjecucionCuotasDTO;
import com.saed.backend.dashboard.export.ExportFormat;
import com.saed.backend.dashboard.export.ExportResult;
import com.saed.backend.dashboard.export.ReportExportService;
import com.saed.backend.dashboard.registry.ReportDefinition;
import com.saed.backend.dashboard.registry.ReportDefinitionKey;
import com.saed.backend.dashboard.registry.ReportExecutionParams;
import com.saed.backend.dashboard.registry.ReportExecutionResult;
import com.saed.backend.dashboard.service.ReportesService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EjecucionCuotasReportDefinition implements ReportDefinition {

    @Override
    public ReportDefinitionKey getKey() {
        return ReportDefinitionKey.EJECUCION_CUOTAS;
    }

    @Override
    public ReportExecutionResult execute(
            ReportesService reportesService,
            ReportExportService reportExportService,
            ReportExecutionParams params,
            ExportFormat format
    ) {
        List<EjecucionCuotasDTO> items = reportesService.getEjecucionCuotas(
                params.propertyId(),
                params.fechaInicio(),
                params.fechaFin(),
                params.periodoInicio(),
                params.periodoFin()
        );
        ExportResult exportResult = reportExportService.exportEjecucionCuotas(
                items,
                format,
                params.propertyId(),
                params.periodoInicio(),
                params.periodoFin()
        );
        return new ReportExecutionResult(items.size(), exportResult, items);
    }
}
