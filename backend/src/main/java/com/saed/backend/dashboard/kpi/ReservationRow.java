package com.saed.backend.dashboard.kpi;

import java.time.LocalDate;

/** One common-area reservation used by the reservation KPIs. */
public record ReservationRow(String estado, LocalDate fechaReserva) {
}
