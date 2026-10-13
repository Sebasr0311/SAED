package com.saed.backend.dashboard.kpi;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Six-profile dashboard KPIs (F11-02). The profile and its scope come from the server context only:
 * SUPERADMIN (platform data, DEC-02), organization admin, property admin, porter (tactical, no finances),
 * titular resident (own unit) and conviviente (own unit, no financial details). A missing context denies.
 */
@Service
@Transactional(readOnly = true)
public class DashboardKpiService {

    private static final int CHURN_WINDOW_DAYS = 30;

    private final DashboardKpiRepository repository;
    private final Clock clock;

    @Autowired
    public DashboardKpiService(DashboardKpiRepository repository) {
        this(repository, Clock.system(ZoneId.of("America/Bogota")));
    }

    DashboardKpiService(DashboardKpiRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public DashboardKpisDTO forCurrentUser() {
        SaedContext ctx = SaedContextHolder.getContext();
        String role = ctx != null && ctx.getRoleCode() != null ? ctx.getRoleCode() : "";
        LocalDate today = LocalDate.now(clock);
        return switch (role) {
            case "SUPERADMIN" -> platform(today);
            case "ADMIN_ORGANIZACION" -> {
                KpiScope scope = new KpiScope(require(ctx.getOrganizationId()), null, null);
                yield new DashboardKpisDTO("ORGANIZACION", finances(scope, today), pqrs(scope), reservations(scope, today), null);
            }
            case "ADMIN_PROPIEDAD" -> {
                KpiScope scope = new KpiScope(require(ctx.getOrganizationId()), require(ctx.getPropertyId()), null);
                yield new DashboardKpisDTO("PROPIEDAD", finances(scope, today), pqrs(scope), reservations(scope, today), null);
            }
            case "PORTERO" -> {
                KpiScope scope = new KpiScope(require(ctx.getOrganizationId()), require(ctx.getPropertyId()), null);
                yield new DashboardKpisDTO("PORTERIA", null, null, reservations(scope, today), null);
            }
            case "RESIDENTE", "PROPIETARIO" -> {
                KpiScope scope = unitScope(ctx);
                yield new DashboardKpisDTO("RESIDENTE_TITULAR", finances(scope, today), pqrs(scope),
                        reservations(scope, today), null);
            }
            case "RESIDENTE_CONVIVENCIA" -> {
                KpiScope scope = unitScope(ctx);
                yield new DashboardKpisDTO("RESIDENTE_CONVIVIENTE", null, pqrs(scope), reservations(scope, today), null);
            }
            default -> throw new AccessDeniedException("Perfil sin tablero disponible.");
        };
    }

    private static Long require(Long id) {
        if (id == null) {
            throw new AccessDeniedException("No hay un contexto activo para el tablero.");
        }
        return id;
    }

    private static KpiScope unitScope(SaedContext ctx) {
        return new KpiScope(require(ctx.getOrganizationId()), require(ctx.getPropertyId()), require(ctx.getUnitId()));
    }

    private CollectionKpis finances(KpiScope scope, LocalDate today) {
        return KpiCalculator.collection(repository.fees(scope), repository.countUnits(scope), today);
    }

    private PqrsKpis pqrs(KpiScope scope) {
        return KpiCalculator.pqrs(repository.openTickets(scope), clock.instant());
    }

    private ReservationKpis reservations(KpiScope scope, LocalDate today) {
        return KpiCalculator.reservations(repository.reservations(scope, today), today);
    }

    private DashboardKpisDTO platform(LocalDate today) {
        PlatformKpis counts = repository.platformCounts();
        ChurnKpis churn = KpiCalculator.churn(repository.memberships(), today.minusDays(CHURN_WINDOW_DAYS), today);
        PlatformKpis kpis = new PlatformKpis(counts.organizacionesTotal(), counts.organizacionesActivas(),
                counts.propiedadesTotal(), counts.membresiasActivas(), counts.membresiasPorVencer(),
                counts.planesActivos(), counts.ingresoMensualRecurrente(), churn);
        return new DashboardKpisDTO("PLATAFORMA", null, null, null, kpis);
    }
}
