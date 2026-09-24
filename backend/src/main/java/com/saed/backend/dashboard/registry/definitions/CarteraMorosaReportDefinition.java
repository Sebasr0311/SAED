package com.saed.backend.dashboard.registry.definitions;

import com.saed.backend.dashboard.dto.CarteraMorosaDTO;
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
public class CarteraMorosaReportDefinition implements ReportDefinition {

    @Override
    public ReportDefinitionKey getKey() {
        return ReportDefinitionKey.CARTERA_MOROSA;
    }

    @Override
    public ReportExecutionResult execute(
            ReportesService reportesService,
            ReportExportService reportExportService,
            ReportExecutionParams params,
            ExportFormat format
    ) {
        List<CarteraMorosaDTO> items = reportesService.getCarteraMorosa(
                params.propertyId(),
                params.fechaInicio(),
                params.fechaFin(),
                0,
                10001
        );
        ExportResult exportResult = reportExportService.exportCarteraMorosa(
                items,
                format,
                params.propertyId(),
                params.fechaInicio(),
                params.fechaFin()
        );
        return new ReportExecutionResult(items.size(), exportResult, items);
    }
}
