package com.saed.backend.dashboard.kpi;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * KPIs of the caller's profile. Sections that a profile must not see are absent (not zero): SUPERADMIN gets
 * only {@code plataforma}; a conviviente and the porter never get {@code finanzas}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DashboardKpisDTO(String perfil, CollectionKpis finanzas, PqrsKpis pqrs, ReservationKpis reservas,
                               PlatformKpis plataforma) {
}
