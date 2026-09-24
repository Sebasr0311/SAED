package com.saed.backend.finanzas.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record PagoResponseDTO(
    Long idPago,
    Long idUnidad,
    String numeroApartamento,
    String nombreResidente,
    Long idCuota,
    String conceptoCuota,
    String periodoCuota,
    BigDecimal montoTotal,
    String metodoPago,
    String referenciaComprobante,
    String comprobanteUrl,
    String comprobanteHash,
    Long comprobanteTamanoBytes,
    LocalDate fechaPago,
    String estado,
    Long aprobadoPor,
    String nombreAprobador,
    OffsetDateTime fechaAprobacion,
    Long rechazadoPor,
    String nombreRechazador,
    OffsetDateTime fechaRechazo,
    String observaciones
) {}
