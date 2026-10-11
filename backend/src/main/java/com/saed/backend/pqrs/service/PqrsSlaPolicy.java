package com.saed.backend.pqrs.service;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Set;

/**
 * PQRS service-level policy (DEC-05): default hours per priority and the server-side SLA clock.
 *
 * <p>Wall-clock hours: CRITICA 1 h, ALTA 4 h, MEDIA 24 h, BAJA 72 h. CRITICA is stored as the
 * schema's {@code EMERGENCIA} priority. An open ticket is "at risk" from 75 % of its SLA and
 * "overdue" once more than 100 % has elapsed. Closed tickets are measured at their closing time.
 */
public final class PqrsSlaPolicy {

    public static final String ON_TIME = "EN_TIEMPO";
    public static final String AT_RISK = "EN_RIESGO";
    public static final String OVERDUE = "VENCIDO";
    public static final String MET = "CUMPLIDO";
    public static final String BREACHED = "INCUMPLIDO";
    public static final String FINISHED = "FINALIZADO";

    static final int AT_RISK_PERCENT = 75;

    private static final Set<String> TERMINAL_STATES = Set.of("RESUELTO", "CERRADO", "RECHAZADO");

    /** Result of evaluating a ticket against its SLA. */
    public record SlaStatus(String estado, int porcentajeConsumido, boolean vencido) { }

    private PqrsSlaPolicy() { }

    /** Maps the user-facing priority name to the stored one ({@code CRITICA} -> {@code EMERGENCIA}). */
    public static String normalizePriority(String prioridad) {
        if (prioridad == null) {
            return null;
        }
        String p = prioridad.trim().toUpperCase(Locale.ROOT);
        return "CRITICA".equals(p) ? "EMERGENCIA" : p;
    }

    /** Default SLA hours when the property has no configuration (DEC-05). */
    public static int defaultHours(String prioridad) {
        String p = normalizePriority(prioridad);
        if (p == null) {
            return 24;
        }
        return switch (p) {
            case "EMERGENCIA" -> 1;
            case "ALTA" -> 4;
            case "BAJA" -> 72;
            default -> 24;
        };
    }

    /**
     * Evaluates the SLA. {@code radicacion} and {@code limite} define the window; open tickets are
     * measured at {@code now}, terminal tickets at {@code cierre} (when known).
     */
    public static SlaStatus evaluate(ZonedDateTime radicacion, ZonedDateTime limite, String estado,
                                     ZonedDateTime cierre, ZonedDateTime now) {
        if (limite == null) {
            return new SlaStatus(null, 0, false);
        }
        boolean terminal = estado != null && TERMINAL_STATES.contains(estado.toUpperCase(Locale.ROOT));
        if (terminal) {
            if (cierre == null) {
                return new SlaStatus(FINISHED, percent(radicacion, limite, limite), false);
            }
            String result = cierre.isAfter(limite) ? BREACHED : MET;
            return new SlaStatus(result, percent(radicacion, limite, cierre), false);
        }
        if (now.isAfter(limite)) {
            return new SlaStatus(OVERDUE, percent(radicacion, limite, now), true);
        }
        int pct = percent(radicacion, limite, now);
        return new SlaStatus(pct >= AT_RISK_PERCENT ? AT_RISK : ON_TIME, pct, false);
    }

    private static int percent(ZonedDateTime start, ZonedDateTime due, ZonedDateTime at) {
        if (start == null || !due.isAfter(start)) {
            return at.isAfter(due) ? 101 : 0;
        }
        long total = Duration.between(start, due).toSeconds();
        long elapsed = Math.max(0, Duration.between(start, at).toSeconds());
        return (int) Math.floor(elapsed * 100.0 / total);
    }
}
