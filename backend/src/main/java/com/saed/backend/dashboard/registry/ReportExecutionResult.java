package com.saed.backend.dashboard.registry;

import com.saed.backend.dashboard.export.ExportResult;

/**
 * ReportExecutionResult — Resultado desacoplado y tipado de la ejecución de una definición de reporte.
 */
public record ReportExecutionResult(
        int totalRows,
        ExportResult exportResult,
        Object rawData
) {}
