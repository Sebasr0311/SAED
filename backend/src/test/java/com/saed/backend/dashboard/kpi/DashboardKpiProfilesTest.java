package com.saed.backend.dashboard.kpi;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** F11-02: six dashboard profiles, each scoped by server context and limited to the data it may see. */
class DashboardKpiProfilesTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-10T12:00:00Z"), ZoneId.of("America/Bogota"));
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 10);

    private DashboardKpiRepository repo;
    private DashboardKpiService service;

    @BeforeEach
    void setUp() {
        repo = mock(DashboardKpiRepository.class);
        service = new DashboardKpiService(repo, CLOCK);
        when(repo.fees(any())).thenReturn(List.of(
            new FeeRow(1, "VENCIDA", new BigDecimal("100"), new BigDecimal("100"), TODAY.minusDays(1)),
            new FeeRow(2, "PAGADA", new BigDecimal("100"), BigDecimal.ZERO, TODAY.minusDays(1))));
        when(repo.countUnits(any())).thenReturn(4L);
        when(repo.openTickets(any())).thenReturn(List.of(
            new TicketRow("ALTA", "RADICADO", Instant.parse("2026-10-10T06:00:00Z"))));
        when(repo.reservations(any(), any())).thenReturn(List.of(new ReservationRow("PENDIENTE", TODAY)));
        when(repo.platformCounts()).thenReturn(new PlatformKpis(3, 2, 7, 2, 1, 4, new BigDecimal("300000"), null));
        when(repo.memberships()).thenReturn(List.of(
            new MembershipRow(1, "ACTIVA", LocalDate.of(2026, 1, 1), null)));
    }

    @AfterEach
    void clear() {
        SaedContextHolder.clearContext();
    }

    private static void as(String role, Long org, Long prop, Long unit) {
        SaedContextHolder.setContext(SaedContext.builder().userId(9L).organizationId(org).propertyId(prop)
            .unitId(unit).roleCode(role).roleScope(role).build());
    }

    @Test
    void superadminSeesOnlyPlatformDataIncludingChurn() {
        as("SUPERADMIN", 1L, 1L, null);
        DashboardKpisDTO dto = service.forCurrentUser();
        assertEquals("PLATAFORMA", dto.perfil());
        assertNotNull(dto.plataforma().churn30Dias());
        assertEquals(0, dto.plataforma().churn30Dias().organizacionesPerdidas());
        assertNull(dto.finanzas());
        assertNull(dto.pqrs());
        verify(repo, never()).fees(any());
        verify(repo, never()).openTickets(any());
        verify(repo, never()).reservations(any(), any());
    }

    @Test
    void organizationAdminIsScopedToTheContextOrganization() {
        as("ADMIN_ORGANIZACION", 5L, null, null);
        DashboardKpisDTO dto = service.forCurrentUser();
        assertEquals("ORGANIZACION", dto.perfil());
        verify(repo).fees(new KpiScope(5L, null, null));
        assertEquals(new BigDecimal("50.0"), dto.finanzas().tasaRecaudo());
        assertEquals(new BigDecimal("25.0"), dto.finanzas().porcentajeMora());
        assertEquals(1, dto.pqrs().vencidas());
        assertNull(dto.plataforma());
    }

    @Test
    void propertyAdminIsScopedToTheContextProperty() {
        as("ADMIN_PROPIEDAD", 5L, 50L, null);
        DashboardKpisDTO dto = service.forCurrentUser();
        assertEquals("PROPIEDAD", dto.perfil());
        verify(repo).fees(new KpiScope(5L, 50L, null));
        verify(repo).reservations(new KpiScope(5L, 50L, null), TODAY);
        assertEquals(1, dto.reservas().pendientes());
    }

    @Test
    void porterGetsTacticalDataWithoutFinances() {
        as("PORTERO", 5L, 50L, null);
        DashboardKpisDTO dto = service.forCurrentUser();
        assertEquals("PORTERIA", dto.perfil());
        assertNull(dto.finanzas());
        assertNotNull(dto.reservas());
        verify(repo, never()).fees(any());
    }

    @Test
    void titularResidentSeesOwnUnitFinances() {
        as("RESIDENTE", 5L, 50L, 501L);
        DashboardKpisDTO dto = service.forCurrentUser();
        assertEquals("RESIDENTE_TITULAR", dto.perfil());
        verify(repo).fees(new KpiScope(5L, 50L, 501L));
        assertEquals(new BigDecimal("100"), dto.finanzas().carteraMorosa());
    }

    @Test
    void convivienteNeverReceivesFinancialDetails() {
        as("RESIDENTE_CONVIVENCIA", 5L, 50L, 501L);
        DashboardKpisDTO dto = service.forCurrentUser();
        assertEquals("RESIDENTE_CONVIVIENTE", dto.perfil());
        assertNull(dto.finanzas());
        verify(repo, never()).fees(any());
        verify(repo).openTickets(new KpiScope(5L, 50L, 501L));
    }

    @Test
    void absentContextNeverBroadensScope() {
        as("ADMIN_PROPIEDAD", 5L, null, null);
        assertThrows(AccessDeniedException.class, () -> service.forCurrentUser());
        as("ADMIN_ORGANIZACION", null, null, null);
        assertThrows(AccessDeniedException.class, () -> service.forCurrentUser());
        as("RESIDENTE", 5L, 50L, null);
        assertThrows(AccessDeniedException.class, () -> service.forCurrentUser());
        as("DESCONOCIDO", 5L, 50L, 1L);
        assertThrows(AccessDeniedException.class, () -> service.forCurrentUser());
        verify(repo, never()).fees(any());
    }
}
