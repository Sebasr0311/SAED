package com.saed.backend.dashboard.registry;

import java.time.LocalDate;

/**
 * ReportExecutionParams — Parámetros tipados de entrada para la ejecución segura de un reporte.
 * Protege contra inyecciones SQL y parámetros no autorizados.
 */
public record ReportExecutionParams(
        Long propertyId,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        String periodoInicio,
        String periodoFin,
        Integer vigenciaAnio,
        int page,
        int size
) {
    public static ReportExecutionParams defaultParams(Long propertyId) {
        return new ReportExecutionParams(propertyId, null, null, null, null, null, 0, 10001);
    }
}
