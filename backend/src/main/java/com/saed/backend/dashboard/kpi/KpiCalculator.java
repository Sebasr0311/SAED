package com.saed.backend.dashboard.kpi;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Pure KPI computations over source rows (F11-02). Every dashboard profile derives its figures from these
 * functions so that the numbers reconcile with the source tables, and golden-data tests prove them.
 */
public final class KpiCalculator {

    /** Fee states that still owe money (PAGADA_PARCIAL counts as outstanding). ANULADA is never billed. */
    static final Set<String> OUTSTANDING_FEE_STATES = Set.of("PENDIENTE", "PAGADA_PARCIAL", "VENCIDA");
    static final Set<String> OPEN_TICKET_STATES = Set.of("RADICADO", "ASIGNADO", "EN_GESTION", "ESCALADO");
    /** DEC-05 wall-clock SLA hours by priority; the schema's EMERGENCIA priority is the CRITICA level. */
    static final Map<String, Long> SLA_HOURS = Map.of("CRITICA", 1L, "EMERGENCIA", 1L, "ALTA", 4L, "MEDIA", 24L, "BAJA", 72L);
    static final Set<String> COVERING_MEMBERSHIP_STATES = Set.of("ACTIVA", "PRUEBA");

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private KpiCalculator() {
    }

    /** Percentage with one decimal, or null when the denominator is zero (no fabricated default). */
    static BigDecimal percent(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.signum() == 0) {
            return null;
        }
        return numerator.multiply(HUNDRED).divide(denominator, 1, RoundingMode.HALF_UP);
    }

    public static CollectionKpis collection(Collection<FeeRow> fees, long totalUnits, LocalDate today) {
        BigDecimal facturado = BigDecimal.ZERO;
        BigDecimal pendiente = BigDecimal.ZERO;
        BigDecimal morosa = BigDecimal.ZERO;
        long cuotasPendientes = 0;
        Set<Long> unitsInArrears = new HashSet<>();
        for (FeeRow fee : fees) {
            String estado = fee.estado() == null ? "" : fee.estado().toUpperCase(Locale.ROOT);
            if ("ANULADA".equals(estado)) {
                continue;
            }
            BigDecimal base = fee.valorBase() != null ? fee.valorBase() : BigDecimal.ZERO;
            BigDecimal saldo = fee.saldoPendiente() != null ? fee.saldoPendiente() : BigDecimal.ZERO;
            facturado = facturado.add(base);
            pendiente = pendiente.add(saldo);
            if (OUTSTANDING_FEE_STATES.contains(estado) && saldo.signum() > 0) {
                cuotasPendientes++;
                if (fee.fechaVencimiento() != null && fee.fechaVencimiento().isBefore(today)) {
                    morosa = morosa.add(saldo);
                    unitsInArrears.add(fee.unitId());
                }
            }
        }
        BigDecimal recaudado = facturado.subtract(pendiente);
        return new CollectionKpis(facturado, recaudado, percent(recaudado, facturado), pendiente, morosa,
                cuotasPendientes, unitsInArrears.size(),
                percent(BigDecimal.valueOf(unitsInArrears.size()), BigDecimal.valueOf(totalUnits)));
    }

    public static PqrsKpis pqrs(Collection<TicketRow> tickets, Instant now) {
        long open = 0;
        long atRisk = 0;
        long overdue = 0;
        for (TicketRow ticket : tickets) {
            String estado = ticket.estado() == null ? "" : ticket.estado().toUpperCase(Locale.ROOT);
            if (!OPEN_TICKET_STATES.contains(estado)) {
                continue;
            }
            open++;
            String priority = ticket.prioridad() == null ? "MEDIA" : ticket.prioridad().toUpperCase(Locale.ROOT);
            long slaMinutes = SLA_HOURS.getOrDefault(priority, SLA_HOURS.get("MEDIA")) * 60;
            long elapsedMinutes = Duration.between(ticket.fechaRadicacion(), now).toMinutes();
            if (elapsedMinutes * 100 >= slaMinutes * 100) {
                overdue++;
            } else if (elapsedMinutes * 100 >= slaMinutes * 75) {
                atRisk++;
            }
        }
        return new PqrsKpis(open, atRisk, overdue);
    }

    public static ReservationKpis reservations(Collection<ReservationRow> rows, LocalDate today) {
        long pending = 0;
        long upcoming = 0;
        long todayCount = 0;
        for (ReservationRow row : rows) {
            String estado = row.estado() == null ? "" : row.estado().toUpperCase(Locale.ROOT);
            if ("PENDIENTE".equals(estado)) {
                pending++;
            } else if ("APROBADA".equals(estado) && row.fechaReserva() != null && !row.fechaReserva().isBefore(today)) {
                upcoming++;
                if (row.fechaReserva().isEqual(today)) {
                    todayCount++;
                }
            }
        }
        return new ReservationKpis(pending, upcoming, todayCount);
    }

    /**
     * Churn over [from, to]: organizations with a membership covering {@code from} (in any state, since
     * historical rows keep their final state) that have no ACTIVA/PRUEBA membership covering {@code to}.
     */
    public static ChurnKpis churn(List<MembershipRow> rows, LocalDate from, LocalDate to) {
        Set<Long> base = new HashSet<>();
        Set<Long> activeAtEnd = new HashSet<>();
        for (MembershipRow row : rows) {
            if (covers(row, from)) {
                base.add(row.organizationId());
            }
            String estado = row.estado() == null ? "" : row.estado().toUpperCase(Locale.ROOT);
            if (COVERING_MEMBERSHIP_STATES.contains(estado) && covers(row, to)) {
                activeAtEnd.add(row.organizationId());
            }
        }
        long lost = base.stream().filter(org -> !activeAtEnd.contains(org)).count();
        return new ChurnKpis(base.size(), lost, percent(BigDecimal.valueOf(lost), BigDecimal.valueOf(base.size())),
                activeAtEnd.size());
    }

    private static boolean covers(MembershipRow row, LocalDate day) {
        return row.fechaInicio() != null && !row.fechaInicio().isAfter(day)
                && (row.fechaFin() == null || !row.fechaFin().isBefore(day));
    }
}
