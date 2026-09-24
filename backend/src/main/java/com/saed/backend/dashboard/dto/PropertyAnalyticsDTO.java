package com.saed.backend.dashboard.dto;

import java.util.List;

public record PropertyAnalyticsDTO(
    Long idPropiedad,
    String horizonteTemporal,
    Double ocupacionActualPct,
    List<PropertyFinancialTrendDTO> tendenciaFinanciera,
    List<PropertyOperationalTrendDTO> tendenciaOperativa
) {}
