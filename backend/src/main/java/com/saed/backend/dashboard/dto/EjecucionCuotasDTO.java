package com.saed.backend.dashboard.dto;

import java.math.BigDecimal;

/**
 * Contrato DTO para el reporte de facturación y ejecución de cuotas por periodo.
 * Representa facturación y recaudo operativo de cuotas de administración.
 *
 * @param periodo             Periodo contable (formato YYYY-MM).
 * @param totalCuotas         Número total de cuotas generadas en el periodo.
 * @param pagadas             Cantidad de cuotas totalmente pagadas.
 * @param pendientes          Cantidad de cuotas pendientes o vencidas.
 * @param totalFacturado      Monto total facturado en COP.
 * @param totalPendiente      Monto total pendiente por recaudar en COP.
 * @param totalRecaudado      Monto efectivamente recaudado en COP.
 * @param porcentajeRecaudado Porcentaje del recaudo efectivo sobre lo facturado (0.0 a 100.0).
 */
public record EjecucionCuotasDTO(
        String periodo,
        Long totalCuotas,
        Long pagadas,
        Long pendientes,
        BigDecimal totalFacturado,
        BigDecimal totalPendiente,
        BigDecimal totalRecaudado,
        Double porcentajeRecaudado
) {}
