package com.saed.backend.dashboard.kpi;

import java.time.LocalDate;

/** One organization membership used by the platform churn KPI. */
public record MembershipRow(long organizationId, String estado, LocalDate fechaInicio, LocalDate fechaFin) {
}
