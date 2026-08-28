package com.saed.backend.finanzas.dto;

public record ContratoProveedorDTO(
    Long idContratoProveedor,
    Long idProveedor,
    Long idPropiedad,
    String numeroContrato,
    String objetoContrato,
    java.math.BigDecimal valorTotal,
    String periodicidadPago,
    java.time.LocalDate fechaInicio,
    java.time.LocalDate fechaFin,
    Integer diasAlertaVenc,
    String estado
) {}
