package com.saed.backend.dashboard.dto;

import java.math.BigDecimal;

public record MonthlyTrendDTO(
    String periodo,
    BigDecimal facturado,
    BigDecimal recaudado,
    BigDecimal carteraPeriodo
) {}
