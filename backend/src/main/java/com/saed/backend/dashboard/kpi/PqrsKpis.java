package com.saed.backend.dashboard.kpi;

/** Open PQRS tickets and their SLA state (DEC-05). */
public record PqrsKpis(long abiertas, long enRiesgo, long vencidas) {
}
