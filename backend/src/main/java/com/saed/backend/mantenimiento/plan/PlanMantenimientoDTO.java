package com.saed.backend.mantenimiento.plan;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Recurring (preventive) maintenance plan of a property (F09-01). */
public record PlanMantenimientoDTO(
        Long idPlan,
        Long idPropiedad,
        Long idActivo,
        String titulo,
        String descripcionTrabajo,
        String tipoMantenimiento,
        String prioridad,
        String frecuencia,
        LocalDate fechaInicio,
        BigDecimal costoEstimado,
        Long idProveedorServicio,
        String activo
) {
}
