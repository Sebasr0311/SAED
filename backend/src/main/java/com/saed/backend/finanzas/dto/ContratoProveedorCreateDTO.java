package com.saed.backend.finanzas.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ContratoProveedorCreateDTO(
    Long idProveedor,
    String numeroContrato,
    String objetoContrato,
    BigDecimal valorTotal,
    String periodicidadPago,
    LocalDate fechaInicio,
    LocalDate fechaFin,
    Integer diasAlertaVenc
) {}
