package com.saed.backend.dashboard.dto;

import java.math.BigDecimal;

public record DashboardPropiedadDTO(
    Long idPropiedad,
    long totalUnidades,
    long totalPersonas,
    BigDecimal carteraTotal,
    BigDecimal moraTotal,
    long cuotasPendientesCount,
    long paquetesPendientes,
    long visitasActivas,
    long multasPendientes
) {}
