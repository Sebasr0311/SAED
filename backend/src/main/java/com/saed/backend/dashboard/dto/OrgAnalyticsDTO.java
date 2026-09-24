package com.saed.backend.dashboard.dto;

import java.util.List;

public record OrgAnalyticsDTO(
    String horizonteTemporal,
    int mesesEvaluados,
    OrgAnalyticsKpisDTO kpisGlobales,
    List<PropertyBenchmarkDTO> benchmarkPropiedades,
    List<MonthlyTrendDTO> tendenciaMensual
) {}
