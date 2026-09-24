package com.saed.backend.dashboard.dto;

import java.math.BigDecimal;

public record PropertyBenchmarkDTO(
    Long idPropiedad,
    String nombre,
    String ciudad,
    long totalUnidades,
    long unidadesOcupadas,
    Double ocupacionActualPct,
    BigDecimal facturadoPeriodo,
    BigDecimal recaudadoPeriodo,
    BigDecimal carteraPeriodo,
    BigDecimal carteraTotalActual,
    Double efectividadRecaudoPct,
    Double indiceMorosidadPct,
    Integer rankingEfectividad,
    Integer rankingMorosidad,
    Integer rankingOcupacion
) {}
