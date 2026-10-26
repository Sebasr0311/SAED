package com.saed.backend.dashboard.kpi;

import java.time.Instant;

/** One PQRS ticket used by the SLA KPIs. */
public record TicketRow(String prioridad, String estado, Instant fechaRadicacion) {
}
