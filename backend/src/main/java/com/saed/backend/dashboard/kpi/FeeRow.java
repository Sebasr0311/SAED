package com.saed.backend.dashboard.kpi;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One fee (CUOTAS row) used by the collection and arrears KPIs. */
public record FeeRow(long unitId, String estado, BigDecimal valorBase, BigDecimal saldoPendiente, LocalDate fechaVencimiento) {
}
