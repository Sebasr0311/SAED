package com.saed.backend.reservas;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.finanzas.dto.PazYSalvoEstadoFinancieroDTO;
import com.saed.backend.finanzas.service.PazYSalvoService;
import com.saed.backend.reservas.dto.ReservaDTO;
import com.saed.backend.reservas.dto.ZonaComunDTO;
import com.saed.backend.reservas.exception.ReservaSolapadaException;
import com.saed.backend.reservas.exception.ReservaTransicionInvalidaException;
import com.saed.backend.reservas.exception.UnidadEnMoraException;
import com.saed.backend.reservas.repository.ReservasRepository;
import com.saed.backend.reservas.service.CommonAreaRestrictionChecker;
import com.saed.backend.reservas.service.impl.ReservasServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** F08-01 server-side reservation policy (time window, advance, overlap, parallel booking, deposit, cancel, sanctions, debt privacy). */
class ReservationPolicyTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final ZonedDateTime NOW = ZonedDateTime.of(2026, 10, 9, 10, 0, 0, 0, BOGOTA);
    private static final LocalDate TODAY = NOW.toLocalDate();
    private static final long PROP = 70L;
    private static final long UNIT = 701L;
    private static final long ZONA = 5L;

    private ReservasRepository repo;
    private PazYSalvoService pazYSalvo;
    private CommonAreaRestrictionChecker restrictions;
    private ReservasServiceImpl service;

    @BeforeEach
    void setUp() {
        repo = mock(ReservasRepository.class);
        pazYSalvo = mock(PazYSalvoService.class);
        restrictions = mock(CommonAreaRestrictionChecker.class);
        service = new ReservasServiceImpl(repo, pazYSalvo, restrictions, Clock.fixed(NOW.toInstant(), BOGOTA));
        when(repo.existsUnidadInPropiedad(UNIT, PROP)).thenReturn(true);
        when(repo.findZonaByIdAndPropiedad(ZONA, PROP)).thenReturn(Optional.of(zona(BigDecimal.ZERO)));
        when(pazYSalvo.verificarEstadoFinanciero(UNIT)).thenReturn(alDia());
        when(repo.createReserva(any(), eq(PROP))).thenReturn(900L);
    }

    @AfterEach
    void clear() {
        SaedContextHolder.clearContext();
    }

    private static ZonaComunDTO zona(BigDecimal deposito) {
        ZonaComunDTO z = new ZonaComunDTO();
        z.setIdZona(ZONA);
        z.setNombre("BBQ");
        z.setEstado("ACTIVA");
        z.setAforoMaximo(20);
        z.setCostoReserva(BigDecimal.ZERO);
        z.setHoraApertura("08:00");
        z.setHoraCierre("22:00");
        z.setAnticipacionMinHoras(2);
        z.setAnticipacionMaxDias(60);
        z.setMaxReservasActivasUnidad(2);
        z.setValorDeposito(deposito);
        z.setHorasLimiteCancelacion(24);
        return z;
    }

    private static PazYSalvoEstadoFinancieroDTO alDia() {
        return new PazYSalvoEstadoFinancieroDTO(UNIT, "101", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, true, List.of());
    }

    private static PazYSalvoEstadoFinancieroDTO enMora() {
        return new PazYSalvoEstadoFinancieroDTO(UNIT, "101", new BigDecimal("350000"), new BigDecimal("50000"),
            new BigDecimal("400000"), false, List.of("Cartera vencida por $350.000"));
    }

    private static void resident(String role) {
        SaedContextHolder.setContext(SaedContext.builder().userId(40L).organizationId(1L).propertyId(PROP)
            .unitId(UNIT).roleCode(role).roleScope("UNIDAD").build());
    }

    private static void admin() {
        SaedContextHolder.setContext(SaedContext.builder().userId(20L).organizationId(1L).propertyId(PROP)
            .roleCode("ADMIN_PROPIEDAD").roleScope("PROPIEDAD").build());
    }

    private static ReservaDTO booking(LocalDate fecha, String ini, String fin) {
        ReservaDTO r = new ReservaDTO();
        r.setIdZona(ZONA);
        r.setFechaReserva(fecha);
        r.setHoraInicio(ini);
        r.setHoraFin(fin);
        r.setCantidadAsistentes(5);
        return r;
    }

    // --- time window and advance ---

    @Test
    void validBookingIsCreatedUnderUnitAndZoneLocks() {
        resident("RESIDENTE");
        assertEquals(900L, service.createReserva(booking(TODAY.plusDays(3), "10:00", "12:00")));
        var order = inOrder(repo);
        order.verify(repo).lockUnidadForUpdate(UNIT);
        order.verify(repo).lockZonaForUpdate(ZONA, PROP);
        order.verify(repo).createReserva(any(), eq(PROP));
    }

    @Test
    void pastDateIsRejected() {
        resident("RESIDENTE");
        assertThrows(IllegalArgumentException.class, () -> service.createReserva(booking(TODAY.minusDays(1), "10:00", "12:00")));
        verify(repo, never()).createReserva(any(), anyLong());
    }

    @Test
    void bookingInsideTheMinimumAdvanceIsRejected() {
        resident("RESIDENTE");
        assertThrows(IllegalArgumentException.class, () -> service.createReserva(booking(TODAY, "11:00", "12:00")));
    }

    @Test
    void bookingBeyondTheMaximumAdvanceIsRejected() {
        resident("RESIDENTE");
        assertThrows(IllegalArgumentException.class, () -> service.createReserva(booking(TODAY.plusDays(61), "10:00", "12:00")));
    }

    @Test
    void bookingOutsideOperatingHoursIsRejected() {
        resident("RESIDENTE");
        assertThrows(IllegalArgumentException.class, () -> service.createReserva(booking(TODAY.plusDays(3), "07:00", "09:00")));
        assertThrows(IllegalArgumentException.class, () -> service.createReserva(booking(TODAY.plusDays(3), "21:00", "23:00")));
    }

    @Test
    void malformedHoursAreRejected() {
        resident("RESIDENTE");
        assertThrows(IllegalArgumentException.class, () -> service.createReserva(booking(TODAY.plusDays(3), "9:5", "12:00")));
        assertThrows(IllegalArgumentException.class, () -> service.createReserva(booking(TODAY.plusDays(3), "10:00", "25:00")));
    }

    // --- overlap and parallel booking ---

    @Test
    void parallelBookingOfTheSameUnitInAnotherZoneIsRejected() {
        resident("RESIDENTE");
        when(repo.hasOverlappingReservaUnidad(UNIT, TODAY.plusDays(3), "10:00", "12:00")).thenReturn(true);
        assertThrows(ReservaSolapadaException.class, () -> service.createReserva(booking(TODAY.plusDays(3), "10:00", "12:00")));
        verify(repo, never()).createReserva(any(), anyLong());
    }

    @Test
    void unitCannotExceedItsActiveReservationQuota() {
        resident("RESIDENTE");
        when(repo.countActiveReservasUnidad(UNIT, TODAY)).thenReturn(2);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.createReserva(booking(TODAY.plusDays(3), "10:00", "12:00")));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    // --- sanctions and debt ---

    @Test
    void activeCommonAreaRestrictionBlocksBooking() {
        resident("RESIDENTE");
        when(restrictions.hasActiveCommonAreaRestriction(UNIT, TODAY.plusDays(3))).thenReturn(true);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.createReserva(booking(TODAY.plusDays(3), "10:00", "12:00")));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(repo, never()).createReserva(any(), anyLong());
    }

    @Test
    void debtRejectionNeverCarriesAmounts() {
        resident("RESIDENTE_CONVIVENCIA");
        when(pazYSalvo.verificarEstadoFinanciero(UNIT)).thenReturn(enMora());
        UnidadEnMoraException ex = assertThrows(UnidadEnMoraException.class,
            () -> service.createReserva(booking(TODAY.plusDays(3), "10:00", "12:00")));
        assertFalse(ex.getReason().matches(".*\\d.*"), ex.getReason());
    }

    @Test
    void convivienteSeesOnlyEligibilityNotDebtDetails() {
        resident("RESIDENTE_CONVIVENCIA");
        when(pazYSalvo.verificarEstadoFinanciero(UNIT)).thenReturn(enMora());
        PazYSalvoEstadoFinancieroDTO view = service.getMiEstadoMora();
        assertFalse(view.pazYSalvo());
        assertNull(view.saldoCartera());
        assertNull(view.saldoMultasNoFacturadas());
        assertNull(view.saldoTotalExigible());
        assertTrue(view.motivosBloqueo().isEmpty());
    }

    @Test
    void titularStillSeesDebtDetails() {
        resident("RESIDENTE");
        when(pazYSalvo.verificarEstadoFinanciero(UNIT)).thenReturn(enMora());
        assertEquals(new BigDecimal("400000"), service.getMiEstadoMora().saldoTotalExigible());
    }

    // --- deposit and cancellation ---

    private ReservaDTO stored(String estado, LocalDate fecha, String ini, BigDecimal pagado) {
        ReservaDTO r = booking(fecha, ini, "12:00");
        r.setIdReserva(900L);
        r.setIdUnidad(UNIT);
        r.setEstado(estado);
        r.setDepositoPagado(pagado);
        return r;
    }

    @Test
    void approvalRequiresTheZoneDepositToBePaid() {
        admin();
        when(repo.findZonaByIdAndPropiedad(ZONA, PROP)).thenReturn(Optional.of(zona(new BigDecimal("100000"))));
        when(repo.existsZonaInPropiedad(ZONA, PROP)).thenReturn(true);
        when(repo.findReservaById(900L)).thenReturn(Optional.of(stored("PENDIENTE", TODAY.plusDays(3), "10:00", BigDecimal.ZERO)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.updateReservaStatus(900L, "APROBADA"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(repo, never()).updateEstadoReserva(eq(900L), eq("APROBADA"), any());
    }

    @Test
    void approvalProceedsOnceTheDepositIsPaid() {
        admin();
        when(repo.findZonaByIdAndPropiedad(ZONA, PROP)).thenReturn(Optional.of(zona(new BigDecimal("100000"))));
        when(repo.existsZonaInPropiedad(ZONA, PROP)).thenReturn(true);
        when(repo.findReservaById(900L)).thenReturn(Optional.of(
            stored("PENDIENTE", TODAY.plusDays(3), "10:00", new BigDecimal("100000"))));

        service.updateReservaStatus(900L, "APROBADA");

        verify(repo).updateEstadoReserva(900L, "APROBADA", 20L);
    }

    @Test
    void adminRegistersTheDepositOfAReservationOfTheProperty() {
        admin();
        when(repo.existsZonaInPropiedad(ZONA, PROP)).thenReturn(true);
        when(repo.findReservaById(900L)).thenReturn(Optional.of(stored("PENDIENTE", TODAY.plusDays(3), "10:00", BigDecimal.ZERO)));
        service.registrarDeposito(900L, new BigDecimal("100000"));
        verify(repo).registrarDeposito(900L, new BigDecimal("100000"));
        assertThrows(IllegalArgumentException.class, () -> service.registrarDeposito(900L, new BigDecimal("-1")));
    }

    @Test
    void residentCannotCancelInsideTheCancellationWindow() {
        resident("RESIDENTE");
        when(repo.existsZonaInPropiedad(ZONA, PROP)).thenReturn(true);
        when(repo.findReservaById(900L)).thenReturn(Optional.of(stored("APROBADA", TODAY, "20:00", BigDecimal.ZERO)));
        assertThrows(ReservaTransicionInvalidaException.class, () -> service.cancelReserva(900L));
        verify(repo, never()).updateEstadoReserva(anyLong(), any(), any());
    }

    @Test
    void residentCancelsBeforeTheWindow() {
        resident("RESIDENTE");
        when(repo.existsZonaInPropiedad(ZONA, PROP)).thenReturn(true);
        when(repo.findReservaById(900L)).thenReturn(Optional.of(stored("APROBADA", TODAY.plusDays(3), "10:00", BigDecimal.ZERO)));
        service.cancelReserva(900L);
        verify(repo).updateEstadoReserva(900L, "CANCELADA", null);
    }

    // --- DEC-02 ---

    @Test
    void superadminAndMissingPropertyDoNotListReservations() {
        SaedContextHolder.setContext(SaedContext.builder().userId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());
        assertThrows(AccessDeniedException.class, () -> service.getAllReservas());
        assertThrows(AccessDeniedException.class, () -> service.getAllZonasComunes());
        SaedContextHolder.setContext(SaedContext.builder().userId(20L).roleCode("ADMIN_PROPIEDAD").roleScope("PROPIEDAD").build());
        assertThrows(AccessDeniedException.class, () -> service.getAllReservas());
        verify(repo, never()).findAllReservas();
        verify(repo, never()).findAllZonas();
    }
}
