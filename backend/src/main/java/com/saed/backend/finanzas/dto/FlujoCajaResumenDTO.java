package com.saed.backend.finanzas.dto;

import java.math.BigDecimal;

public record FlujoCajaResumenDTO(
    BigDecimal saldoActual,
    BigDecimal totalIngresos,
    BigDecimal totalEgresos,
    BigDecimal ingresosEsperados,
    BigDecimal gastosProgramados,
    BigDecimal proyeccionSaldo
) {}
