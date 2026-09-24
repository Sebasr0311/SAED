package com.saed.backend.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Contrato DTO para el reporte de pagos recientes.
 *
 * @param idPago                Identificador único del pago en base de datos.
 * @param unidad                Identificador de la unidad residencial / comercial.
 * @param montoTotal            Monto total procesado en COP.
 * @param metodoPago            Medio de pago (EFECTIVO, TRANSFERENCIA, WOMPI, etc.).
 * @param estado                Estado del pago (APROBADO, PENDIENTE, RECHAZADO, etc.).
 * @param fechaPago             Fecha y hora en que se procesó el pago.
 * @param referenciaComprobante Referencia bancaria o código de comprobante.
 */
public record PagoRecienteDTO(
        Long idPago,
        String unidad,
        BigDecimal montoTotal,
        String metodoPago,
        String estado,
        LocalDateTime fechaPago,
        String referenciaComprobante
) {}
