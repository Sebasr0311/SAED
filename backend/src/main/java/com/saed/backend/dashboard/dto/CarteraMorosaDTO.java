package com.saed.backend.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Contrato DTO para el reporte de Cartera Morosa por unidad.
 * Desacoplado del esquema de base de datos Oracle, en estricto formato JSON camelCase.
 *
 * @param unidad            Identificador de la unidad residencial / comercial.
 * @param propiedad         Nombre de la copropiedad.
 * @param cuotasPendientes  Cantidad de cuotas vencidas o pendientes de pago.
 * @param deudaTotal        Monto total acumulado adeudado en COP.
 * @param primerVencimiento Fecha de vencimiento de la cuota impaga más antigua.
 * @param ultimoVencimiento Fecha de vencimiento de la cuota impaga más reciente.
 * @param diasMora          Días transcurridos desde el primer vencimiento (0 si no está vencida).
 */
public record CarteraMorosaDTO(
        String unidad,
        String propiedad,
        Long cuotasPendientes,
        BigDecimal deudaTotal,
        LocalDate primerVencimiento,
        LocalDate ultimoVencimiento,
        Long diasMora
) {}
