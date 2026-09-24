package com.saed.backend.dashboard.dto;

import java.math.BigDecimal;

/**
 * EjecucionPresupuestalGlobalDTO
 *
 * Contrato tipado para la ejecución presupuestal global (ingresos y egresos consolidados).
 * Desacoplado de columnas Oracle, en estricto camelCase.
 *
 * NOTA ARQUITECTÓNICA (F11-04 Bloque B):
 * Este DTO refleja la ejecución global de ingresos a nivel de copropiedad/organización,
 * respaldada por pagos efectivamente recaudados y aprobados (PAGOS.ESTADO = 'APROBADO').
 * La ejecución por rubro de ingreso individual requiere la extensión del modelo de dominio
 * mediante una tabla puente (PRESUPUESTO_CONCEPTOS) que vincule rubros con conceptos de cobro.
 */
public record EjecucionPresupuestalGlobalDTO(
        Integer vigenciaAnio,
        BigDecimal ingresosPresupuestados,
        BigDecimal ingresosEjecutados,
        Double porcentajeEjecucionIngresos,
        BigDecimal egresosPresupuestados,
        BigDecimal egresosEjecutados,
        Double porcentajeEjecucionEgresos,
        BigDecimal superavitPresupuestado,
        BigDecimal superavitEjecutado,
        String estadoFinanciero,
        Long totalPagosAprobados,
        Long totalGastosPagados
) {}
