package com.saed.backend.dashboard.dto;

import java.math.BigDecimal;

public record OrgAnalyticsKpisDTO(
    long totalPropiedades,
    long totalUnidades,
    BigDecimal totalFacturadoPeriodo,
    BigDecimal totalRecaudadoPeriodo,
    BigDecimal totalCarteraPeriodo,
    BigDecimal totalCarteraVivaActual,
    Double efectividadRecaudoGlobalPct,
    Double indiceMorosidadGlobalPct,
    Double ocupacionPromedioPct
) {}
