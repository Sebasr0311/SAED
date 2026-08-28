package com.saed.backend.finanzas.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FlujoCajaMovimientoDTO(
    Long id,
    String tipo,
    String categoria,
    String descripcion,
    BigDecimal monto,
    LocalDate fecha,
    String estado,
    String unidad
) {}
