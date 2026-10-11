package com.saed.backend.dashboard.kpi;

import java.time.LocalDate;
import java.util.List;

/** Source rows for the KPI calculator, always filtered by a server-derived {@link KpiScope}. */
public interface DashboardKpiRepository {

    /** Non-voided fees of the last twelve periods plus any fee that still has a balance. */
    List<FeeRow> fees(KpiScope scope);

    long countUnits(KpiScope scope);

    List<TicketRow> openTickets(KpiScope scope);

    /** Pending reservations and reservations dated on or after {@code from}. */
    List<ReservationRow> reservations(KpiScope scope, LocalDate from);

    List<MembershipRow> memberships();

    PlatformKpis platformCounts();
}
