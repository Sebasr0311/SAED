package com.saed.backend.finanzas.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record PazYSalvoDetalleDTO(
    Long idPazSalvo,
    Long idUnidad,
    String numeroUnidad,
    Long idPropiedad,
    String nombrePropiedad,
    String nitPropiedad,
    String direccionPropiedad,
    String ciudadPropiedad,
    Long idOrganizacion,
    Long idPersonaSolicitante,
    String nombreSolicitante,
    String documentoSolicitante,
    String codigoVerificacion,
    OffsetDateTime fechaEmision,
    LocalDate fechaVencimiento,
    BigDecimal saldoALaFecha,
    String motivo,
    String documentoPdfUrl,
    String documentoHash,
    Long documentoTamanoBytes,
    OffsetDateTime documentoFechaGeneracion,
    Long emitidoPor,
    String estado
) {}
