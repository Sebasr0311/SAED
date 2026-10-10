package com.saed.backend.activos.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** One entry of an asset's maintenance history (F09-01): scheduled order, execution, cost and report. */
public record ActivoHistorialDTO(
        Long idMantenimiento,
        String tipoMantenimiento,
        String titulo,
        String estado,
        LocalDate fechaProgramada,
        Instant fechaEjecucion,
        BigDecimal costoEstimado,
        BigDecimal costoReal,
        String proveedor,
        String tecnicoResponsable,
        String informeTecnicoUrl,
        String notasCierre
) {
}
