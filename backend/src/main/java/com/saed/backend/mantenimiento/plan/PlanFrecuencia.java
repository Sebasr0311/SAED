package com.saed.backend.mantenimiento.plan;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;

/** Supported recurrence of a maintenance plan, expressed in months. */
public enum PlanFrecuencia {
    MENSUAL(1),
    BIMESTRAL(2),
    TRIMESTRAL(3),
    SEMESTRAL(6),
    ANUAL(12);

    private final int meses;

    PlanFrecuencia(int meses) {
        this.meses = meses;
    }

    public static PlanFrecuencia parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("La frecuencia del plan es obligatoria.");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Frecuencia no válida: " + value
                    + ". Permitidas: MENSUAL, BIMESTRAL, TRIMESTRAL, SEMESTRAL, ANUAL.", e);
        }
    }

    /**
     * The n-th occurrence is {@code inicio + n * meses} months (day clamped to the month length, always
     * computed from {@code inicio} so a 31st start returns to the 31st). Returns the latest occurrence on
     * or before {@code corte}, or empty when the plan has not started yet.
     */
    public Optional<LocalDate> ultimaOcurrencia(LocalDate inicio, LocalDate corte) {
        if (inicio == null || corte == null || corte.isBefore(inicio)) {
            return Optional.empty();
        }
        long n = ChronoUnit.MONTHS.between(inicio.withDayOfMonth(1), corte.withDayOfMonth(1)) / meses;
        LocalDate occurrence = inicio.plusMonths(n * meses);
        while (occurrence.isAfter(corte)) {
            n--;
            occurrence = inicio.plusMonths(n * meses);
        }
        return Optional.of(occurrence);
    }
}
