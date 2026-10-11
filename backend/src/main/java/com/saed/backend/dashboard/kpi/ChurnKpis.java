package com.saed.backend.dashboard.kpi;

import java.math.BigDecimal;

/** Platform churn over a window; the rate is null when no organization was active at the window start. */
public record ChurnKpis(long organizacionesInicio, long organizacionesPerdidas, BigDecimal tasaChurn,
                        long organizacionesActivas) {
}
