package com.saed.backend.dashboard.kpi;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** F11-02: golden source data proves the KPI computations (no fabricated defaults). */
class KpiCalculatorGoldenTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 10);
    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");

    private static FeeRow fee(long unit, String estado, String base, String saldo, LocalDate due) {
        return new FeeRow(unit, estado, new BigDecimal(base), new BigDecimal(saldo), due);
    }

    @Test
    void collectionAndArrearsReconcileWithSourceFees() {
        List<FeeRow> fees = List.of(
            fee(1, "PAGADA", "100000", "0", TODAY.minusDays(40)),
            fee(1, "PAGADA_PARCIAL", "100000", "40000", TODAY.minusDays(10)),  // overdue partial -> arrears
            fee(2, "VENCIDA", "100000", "100000", TODAY.minusDays(5)),         // overdue
            fee(3, "PENDIENTE", "100000", "100000", TODAY.plusDays(5)),        // outstanding, not overdue
            fee(4, "ANULADA", "100000", "100000", TODAY.minusDays(50)),        // voided: ignored
            fee(4, "PAGADA_PARCIAL", "50000", "0", TODAY.minusDays(3)));       // zero balance: not arrears

        CollectionKpis k = KpiCalculator.collection(fees, 5, TODAY);

        assertEquals(new BigDecimal("450000"), k.facturado());
        assertEquals(new BigDecimal("210000"), k.recaudado());
        assertEquals(new BigDecimal("46.7"), k.tasaRecaudo());
        assertEquals(new BigDecimal("240000"), k.carteraPendiente());
        assertEquals(new BigDecimal("140000"), k.carteraMorosa());
        assertEquals(3, k.cuotasPendientes());
        assertEquals(2, k.unidadesEnMora());
        assertEquals(new BigDecimal("40.0"), k.porcentajeMora());
    }

    @Test
    void noBillingMeansNoRateInsteadOfAFabricatedValue() {
        CollectionKpis k = KpiCalculator.collection(List.of(), 0, TODAY);
        assertEquals(BigDecimal.ZERO, k.facturado());
        assertNull(k.tasaRecaudo());
        assertNull(k.porcentajeMora());
    }

    @Test
    void pqrsOpenAtRiskAndOverdueFollowDec05() {
        List<TicketRow> tickets = List.of(
            new TicketRow("EMERGENCIA", "RADICADO", NOW.minus(30, ChronoUnit.MINUTES)),   // 50% of 1h: on time
            new TicketRow("EMERGENCIA", "ASIGNADO", NOW.minus(50, ChronoUnit.MINUTES)),   // 83%: at risk
            new TicketRow("ALTA", "EN_GESTION", NOW.minus(5, ChronoUnit.HOURS)),         // 125% of 4h: overdue
            new TicketRow("MEDIA", "ESCALADO", NOW.minus(18, ChronoUnit.HOURS)),         // 75% of 24h: at risk
            new TicketRow("BAJA", "RADICADO", NOW.minus(72, ChronoUnit.HOURS)),          // exactly 100% of 72h: at risk (DEC-05: overdue only PAST 100%, as PqrsSlaPolicy)
            new TicketRow("BAJA", "RADICADO", NOW.minus(73, ChronoUnit.HOURS)),          // 101% of 72h: overdue
            new TicketRow("BAJA", "CERRADO", NOW.minus(500, ChronoUnit.HOURS)),          // closed: ignored
            new TicketRow("ALTA", "RESUELTO", NOW.minus(500, ChronoUnit.HOURS)));        // resolved: ignored

        PqrsKpis k = KpiCalculator.pqrs(tickets, NOW);

        assertEquals(6, k.abiertas());
        assertEquals(3, k.enRiesgo());
        assertEquals(2, k.vencidas());
    }

    @Test
    void reservationsCountPendingAndUpcomingApproved() {
        List<ReservationRow> rows = List.of(
            new ReservationRow("PENDIENTE", TODAY.plusDays(2)),
            new ReservationRow("APROBADA", TODAY),
            new ReservationRow("APROBADA", TODAY.plusDays(9)),
            new ReservationRow("APROBADA", TODAY.minusDays(1)),
            new ReservationRow("CANCELADA", TODAY.plusDays(1)));

        ReservationKpis k = KpiCalculator.reservations(rows, TODAY);

        assertEquals(1, k.pendientes());
        assertEquals(2, k.proximasAprobadas());
        assertEquals(1, k.hoy());
    }

    @Test
    void churnIsTheShareOfOrganizationsActiveAtWindowStartWithoutCoverageAtWindowEnd() {
        LocalDate from = LocalDate.of(2026, 9, 10);
        List<MembershipRow> rows = List.of(
            new MembershipRow(1, "ACTIVA", LocalDate.of(2026, 1, 1), null),                       // retained
            new MembershipRow(2, "EXPIRADA", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 30)), // churned
            new MembershipRow(3, "CANCELADA", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 20)),
            new MembershipRow(3, "ACTIVA", LocalDate.of(2026, 9, 21), LocalDate.of(2027, 9, 20)), // renewed: retained
            new MembershipRow(4, "ACTIVA", LocalDate.of(2026, 10, 1), null),                       // new: not in base
            new MembershipRow(5, "SUSPENDIDA", LocalDate.of(2026, 1, 1), null),                    // churned
            new MembershipRow(6, "EXPIRADA", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31))); // gone before

        ChurnKpis k = KpiCalculator.churn(rows, from, TODAY);

        assertEquals(4, k.organizacionesInicio());
        assertEquals(2, k.organizacionesPerdidas());
        assertEquals(new BigDecimal("50.0"), k.tasaChurn());
        assertEquals(3, k.organizacionesActivas());
    }

    @Test
    void churnWithoutBaseIsNotFabricated() {
        assertNull(KpiCalculator.churn(List.of(), TODAY.minusDays(30), TODAY).tasaChurn());
    }
}
