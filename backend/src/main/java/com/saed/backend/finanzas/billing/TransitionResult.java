package com.saed.backend.finanzas.billing;

/** Outcome of one overdue / discount / interest pass. */
public record TransitionResult(int vencidas, int descuentosVencidos, int cuotasConInteres) {
}
