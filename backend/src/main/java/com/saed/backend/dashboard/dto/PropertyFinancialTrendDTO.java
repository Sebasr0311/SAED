package com.saed.backend.dashboard.dto;

import java.math.BigDecimal;

public record PropertyFinancialTrendDTO(
    String periodo,
    BigDecimal facturado,
    BigDecimal recaudado,
    BigDecimal gastos,
    BigDecimal balanceNeto
) {}
