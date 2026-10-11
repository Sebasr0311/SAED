package com.saed.backend.dashboard.kpi;

import java.math.BigDecimal;

/** SUPERADMIN platform KPIs: tenants, memberships, plans and churn only (DEC-02: no operational data). */
public record PlatformKpis(long organizacionesTotal, long organizacionesActivas, long propiedadesTotal,
                           long membresiasActivas, long membresiasPorVencer, long planesActivos,
                           BigDecimal ingresoMensualRecurrente, ChurnKpis churn30Dias) {
}
