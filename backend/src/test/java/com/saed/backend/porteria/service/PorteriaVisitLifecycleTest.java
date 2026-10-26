package com.saed.backend.porteria.service;

import com.saed.backend.authorization.service.PropertyConfigService;
import com.saed.backend.common.service.EmailService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.porteria.dto.ParkingSpotClaim;
import com.saed.backend.porteria.dto.QrAccesoDTO;
import com.saed.backend.porteria.dto.QrAccesoRequestDTO;
import com.saed.backend.porteria.dto.QrConsumoResultadoDTO;
import com.saed.backend.porteria.dto.RegistroAccesoDTO;
import com.saed.backend.porteria.dto.RegistroAccesoRequestDTO;
import com.saed.backend.porteria.dto.VehiculoVisitaDTO;
import com.saed.backend.porteria.dto.VehiculoVisitaRequestDTO;
import com.saed.backend.porteria.dto.VisitaDTO;
import com.saed.backend.porteria.exception.QrAccessException;
import com.saed.backend.porteria.repository.PorteriaRepository;
import com.saed.backend.porteria.service.impl.PorteriaServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * F07-01 (QR ownership/lifecycle) and F07-03 (exit releases parking, server-side fee) in
 * {@link PorteriaServiceImpl}. Persistence is mocked; atomic QR consumption itself stays in
 * SP_VALIDAR_CONSUMIR_QR and is covered on Oracle by QrAtomicConsumptionIntegrationTest.
 */
class PorteriaVisitLifecycleTest {

    private static final long PROP = 70L;
    private static final long OTHER_PROP = 80L;
    private static final long OWN_UNIT = 701L;
    private static final long FOREIGN_UNIT = 801L;
    private static final long VISIT = 5000L;
    private static final String TOKEN = "0f8b9c2e-0000-4000-8000-000000000001";

    private PorteriaRepository repo;
    private NamedParameterJdbcTemplate jdbc;
    private PropertyConfigService config;
    private PorteriaServiceImpl service;

    @BeforeEach
    void setUp() {
        repo = mock(PorteriaRepository.class);
        jdbc = mock(NamedParameterJdbcTemplate.class);
        config = mock(PropertyConfigService.class);
        when(config.getIntValue(anyLong(), anyString(), anyInt())).thenAnswer(inv -> inv.getArgument(2));
        service = new PorteriaServiceImpl(repo, mock(EmailService.class), jdbc, config);
        when(repo.findFirstPorteriaId(PROP)).thenReturn(Optional.of(9L));
    }

    @AfterEach
    void clear() {
        SaedContextHolder.clearContext();
    }

    private static void portero() {
        SaedContextHolder.setContext(SaedContext.builder().userId(30L).organizationId(1L).propertyId(PROP)
            .roleCode("PORTERO").roleScope("PROPIEDAD").build());
    }

    private static void admin(long prop) {
        SaedContextHolder.setContext(SaedContext.builder().userId(20L).organizationId(1L).propertyId(prop)
            .roleCode("ADMIN_PROPIEDAD").roleScope("PROPIEDAD").build());
    }

    private static void resident(long unit) {
        SaedContextHolder.setContext(SaedContext.builder().userId(40L).organizationId(1L).propertyId(PROP)
            .unitId(unit).roleCode("RESIDENTE").roleScope("UNIDAD").build());
    }

    private static void superadmin() {
        SaedContextHolder.setContext(SaedContext.builder().userId(1L).organizationId(1L).propertyId(PROP)
            .roleCode("SUPERADMIN").roleScope("GLOBAL").build());
    }

    private void visit(long unit, long prop, String estado) {
        when(repo.getVisitaById(VISIT)).thenReturn(Optional.of(new VisitaDTO(VISIT, unit, 900L, "CODIGO_QR", "m",
            40L, ZonedDateTime.now(), estado, ZonedDateTime.now())));
        when(repo.findVisitaPropertyId(VISIT)).thenReturn(Optional.of(prop));
    }

    private void qr(String estado) {
        QrAccesoDTO q = new QrAccesoDTO(77L, VISIT, TOKEN, ZonedDateTime.now(), ZonedDateTime.now().plusHours(2),
            1, 0, null, null, null, estado, 40L);
        when(repo.getQrAccesoByToken(TOKEN)).thenReturn(Optional.of(q));
        when(repo.getQrAccesoById(77L)).thenReturn(Optional.of(q));
    }

    // ---------- F07-01 QR entry ----------

    @Test
    void qrEntryWithoutContextIsDeniedWithoutDefaultingToPropertyOne() {
        assertThrows(AccessDeniedException.class, () -> service.registrarEntradaQr(TOKEN, "A_PIE", null, null));
        verify(repo, never()).validarYConsumirQrSp(anyString(), any(), any());
    }

    @Test
    void qrOfCancelledVisitDoesNotAdmitEntry() {
        portero();
        qr("ACTIVO");
        visit(OWN_UNIT, PROP, "CANCELADA");

        QrAccessException ex = assertThrows(QrAccessException.class,
            () -> service.registrarEntradaQr(TOKEN, "A_PIE", null, null));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(repo, never()).validarYConsumirQrSp(anyString(), any(), any());
    }

    @Test
    void spInternalErrorIsNotLeakedToTheClient() {
        portero();
        qr("ACTIVO");
        visit(OWN_UNIT, PROP, "PROGRAMADA");
        when(repo.validarYConsumirQrSp(TOKEN, 9L, 30L))
            .thenReturn(new QrConsumoResultadoDTO(false, "Error interno al validar QR: ORA-00001: unique constraint", null));

        QrAccessException ex = assertThrows(QrAccessException.class,
            () -> service.registrarEntradaQr(TOKEN, "A_PIE", null, null));
        assertFalse(ex.getMessage().contains("ORA-"), ex.getMessage());
    }

    @Test
    void qrEntryUsesTheAtomicProcedureWithTheCallersPorteria() {
        portero();
        qr("ACTIVO");
        visit(OWN_UNIT, PROP, "PROGRAMADA");
        when(repo.validarYConsumirQrSp(TOKEN, 9L, 30L)).thenReturn(new QrConsumoResultadoDTO(true, "ok", VISIT));

        service.registrarEntradaQr(TOKEN, "A_PIE", null, null);

        verify(repo).validarYConsumirQrSp(TOKEN, 9L, 30L);
        verify(repo, never()).consumeQrUso(anyLong());
    }

    @Test
    void carEntryClaimsOnlyAVisitorSpotThroughTheConditionalClaim() {
        portero();
        qr("ACTIVO");
        visit(OWN_UNIT, PROP, "PROGRAMADA");
        when(repo.validarYConsumirQrSp(TOKEN, 9L, 30L)).thenReturn(new QrConsumoResultadoDTO(true, "ok", VISIT));
        when(repo.claimAvailableSpot(PROP, "VISITANTES")).thenReturn(Optional.of(new ParkingSpotClaim(15L, "V-15")));

        Map<String, Object> resp = service.registrarEntradaQr(TOKEN, "CARRO", "abc123", null);

        assertEquals("V-15", resp.get("parqueadero"));
        ArgumentCaptor<VehiculoVisitaRequestDTO> veh = ArgumentCaptor.forClass(VehiculoVisitaRequestDTO.class);
        verify(repo).createVehiculoVisita(veh.capture());
        assertEquals(15L, veh.getValue().parqueaderoId());
        verifyNoInteractions(jdbc);
    }

    @Test
    void motorbikeEntryClaimsAMotorbikeSpot() {
        portero();
        qr("ACTIVO");
        visit(OWN_UNIT, PROP, "PROGRAMADA");
        when(repo.validarYConsumirQrSp(TOKEN, 9L, 30L)).thenReturn(new QrConsumoResultadoDTO(true, "ok", VISIT));
        when(repo.claimAvailableSpot(PROP, "MOTOS")).thenReturn(Optional.empty());

        Map<String, Object> resp = service.registrarEntradaQr(TOKEN, "MOTO", "abc12d", null);

        verify(repo).claimAvailableSpot(PROP, "MOTOS");
        assertEquals(null, resp.get("parqueadero"));
    }

    @Test
    void qrValidationReportsCancelledVisitAsInvalid() {
        portero();
        qr("ACTIVO");
        visit(OWN_UNIT, PROP, "CANCELADA");

        Map<String, Object> res = service.validarQrDetalle(TOKEN);
        assertEquals(false, res.get("valido"));
    }

    @Test
    void qrValidationOfAnotherPropertyLooksLikeUnknownQr() {
        portero();
        qr("ACTIVO");
        visit(FOREIGN_UNIT, OTHER_PROP, "PROGRAMADA");

        Map<String, Object> res = service.validarQrDetalle(TOKEN);
        assertEquals(false, res.get("valido"));
        assertEquals("Código QR no encontrado", res.get("mensaje"));
    }

    // ---------- F07-01 cancel / revoke / generate ----------

    @Test
    void residentCancelsOwnVisitAndItsQrsAreRevoked() {
        resident(OWN_UNIT);
        visit(OWN_UNIT, PROP, "PROGRAMADA");
        when(repo.transitionVisitaEstado(eq(VISIT), eq("CANCELADA"), any())).thenReturn(1);

        service.cancelarVisita(VISIT);

        verify(repo).transitionVisitaEstado(eq(VISIT), eq("CANCELADA"), any());
        verify(repo).revokeActiveQrs(eq(VISIT), eq(40L), anyString());
    }

    @Test
    void residentCannotCancelAVisitOfAnotherUnit() {
        resident(OWN_UNIT);
        visit(FOREIGN_UNIT, PROP, "PROGRAMADA");

        assertThrows(NoSuchElementException.class, () -> service.cancelarVisita(VISIT));
        verify(repo, never()).revokeActiveQrs(anyLong(), any(), anyString());
    }

    @Test
    void adminCannotRevokeQrOfAnotherProperty() {
        admin(PROP);
        qr("ACTIVO");
        visit(FOREIGN_UNIT, OTHER_PROP, "PROGRAMADA");

        assertThrows(NoSuchElementException.class, () -> service.revocarQr(77L, "x"));
        verify(repo, never()).revokeQr(anyLong(), any(), anyString());
    }

    @Test
    void residentRevokesOwnQr() {
        resident(OWN_UNIT);
        qr("ACTIVO");
        visit(OWN_UNIT, PROP, "PROGRAMADA");
        when(repo.revokeQr(eq(77L), eq(40L), anyString())).thenReturn(1);

        service.revocarQr(77L, "ya no viene");

        verify(repo).revokeQr(eq(77L), eq(40L), anyString());
    }

    @Test
    void updatingAVisitToCancelledRevokesItsQrsAndKeepsUnitAndVisitor() {
        resident(OWN_UNIT);
        visit(OWN_UNIT, PROP, "PROGRAMADA");
        when(repo.updateVisita(eq(VISIT), any())).thenAnswer(inv -> repo.getVisitaById(VISIT).orElseThrow());

        service.actualizarVisita(VISIT, new com.saed.backend.porteria.dto.VisitaRequestDTO(
            FOREIGN_UNIT, 12345L, "CODIGO_QR", "m", 999L, ZonedDateTime.now(), "CANCELADA"));

        ArgumentCaptor<com.saed.backend.porteria.dto.VisitaRequestDTO> req =
            ArgumentCaptor.forClass(com.saed.backend.porteria.dto.VisitaRequestDTO.class);
        verify(repo).updateVisita(eq(VISIT), req.capture());
        assertEquals(OWN_UNIT, req.getValue().unidadId());
        assertEquals(900L, req.getValue().visitanteId());
        verify(repo).revokeActiveQrs(eq(VISIT), eq(40L), anyString());
    }

    @Test
    void generatedQrIgnoresClientTokenUsesAndUnboundedExpiry() {
        resident(OWN_UNIT);
        visit(OWN_UNIT, PROP, "PROGRAMADA");
        when(repo.createQrAcceso(any())).thenAnswer(inv -> {
            QrAccesoRequestDTO r = inv.getArgument(0);
            return new QrAccesoDTO(1L, r.visitaId(), r.tokenQr(), ZonedDateTime.now(), r.fechaExpiracion(),
                r.usosPermitidos(), 0, null, null, null, r.estado(), r.generadoPor());
        });

        service.generarQrAcceso(new QrAccesoRequestDTO(VISIT, "predictable", ZonedDateTime.now().plusYears(5),
            1000, "USADO", 999L));

        ArgumentCaptor<QrAccesoRequestDTO> cap = ArgumentCaptor.forClass(QrAccesoRequestDTO.class);
        verify(repo).createQrAcceso(cap.capture());
        QrAccesoRequestDTO saved = cap.getValue();
        assertNotEquals("predictable", saved.tokenQr());
        assertEquals("ACTIVO", saved.estado());
        assertEquals(40L, saved.generadoPor());
        assertTrue(saved.usosPermitidos() <= PorteriaServiceImpl.MAX_QR_USES);
        assertTrue(saved.fechaExpiracion().isBefore(ZonedDateTime.now().plusDays(8)));
    }

    @Test
    void qrCannotBeGeneratedForAVisitOfAnotherUnit() {
        resident(OWN_UNIT);
        visit(FOREIGN_UNIT, PROP, "PROGRAMADA");
        assertThrows(NoSuchElementException.class, () -> service.generarQrAcceso(
            new QrAccesoRequestDTO(VISIT, "t", ZonedDateTime.now().plusHours(1), 1, "ACTIVO", null)));
        verify(repo, never()).createQrAcceso(any());
    }

    @Test
    void qrCannotBeGeneratedForACancelledVisit() {
        resident(OWN_UNIT);
        visit(OWN_UNIT, PROP, "CANCELADA");
        assertThrows(IllegalStateException.class, () -> service.generarQrAcceso(
            new QrAccesoRequestDTO(VISIT, "t", ZonedDateTime.now().plusHours(1), 1, "ACTIVO", null)));
        verify(repo, never()).createQrAcceso(any());
    }

    @Test
    void residentCannotReadVisitDetailOfAnotherUnit() {
        resident(OWN_UNIT);
        visit(FOREIGN_UNIT, PROP, "PROGRAMADA");
        assertThrows(NoSuchElementException.class, () -> service.getVisitaDetalle(VISIT));
        assertThrows(NoSuchElementException.class, () -> service.getVisitaById(VISIT));
    }

    // ---------- Manual check-in / check-out must never leave the visit state silently unchanged ----------

    private static RegistroAccesoRequestDTO movement(String type) {
        return new RegistroAccesoRequestDTO(PROP, 9L, null, VISIT, 900L, OWN_UNIT, null, type, "MANUAL_PORTERO", 30L, null, "manual");
    }

    @Test
    void manualEntryMovesTheVisitToEnCursoAndRecordsTheAccess() {
        portero();
        when(repo.transitionVisitaEstado(eq(VISIT), eq("EN_CURSO"), any())).thenReturn(1);
        RegistroAccesoDTO record = mock(RegistroAccesoDTO.class);
        when(repo.createRegistroAcceso(any())).thenReturn(record);

        assertEquals(record, service.registrarEntrada(movement("ENTRADA")));

        verify(repo).transitionVisitaEstado(eq(VISIT), eq("EN_CURSO"), any());
    }

    @Test
    void manualEntryFailsLoudlyWhenNoVisitRowWasUpdated() {
        portero();
        // 0 rows: the visit does not exist, is hidden by RLS (another property/unit) or is already closed.
        when(repo.transitionVisitaEstado(eq(VISIT), eq("EN_CURSO"), any())).thenReturn(0);

        assertThrows(NoSuchElementException.class, () -> service.registrarEntrada(movement("ENTRADA")));

        verify(repo, never()).createRegistroAcceso(any());
    }

    @Test
    void manualExitFailsLoudlyWhenNoVisitRowWasUpdated() {
        portero();
        when(repo.transitionVisitaEstado(eq(VISIT), eq("FINALIZADA"), any())).thenReturn(0);

        assertThrows(NoSuchElementException.class, () -> service.registrarSalida(movement("SALIDA")));

        verify(repo, never()).createRegistroAcceso(any());
    }

    @Test
    void manualEntryWithoutAVisitStillRecordsTheAccess() {
        portero();
        RegistroAccesoDTO record = mock(RegistroAccesoDTO.class);
        when(repo.createRegistroAcceso(any())).thenReturn(record);

        RegistroAccesoRequestDTO walkIn = new RegistroAccesoRequestDTO(PROP, 9L, null, null, 900L, OWN_UNIT, null,
            "ENTRADA", "MANUAL_PORTERO", 30L, null, "sin visita");

        assertEquals(record, service.registrarEntrada(walkIn));
        verify(repo, never()).transitionVisitaEstado(anyLong(), anyString(), any());
    }

    // ---------- F07-03 exit ----------

    @Test
    void exitReleasesParkingRecordsSalidaRevokesQrAndChargesServerSideFee() {
        portero();
        visit(OWN_UNIT, PROP, "EN_CURSO");
        when(repo.transitionVisitaEstado(eq(VISIT), eq("FINALIZADA"), any())).thenReturn(1);
        when(config.getIntValue(PROP, PorteriaServiceImpl.KEY_TARIFA_PARQUEADERO_HORA, 0)).thenReturn(2000);
        when(config.getIntValue(PROP, PorteriaServiceImpl.KEY_MINUTOS_GRATIS_PARQUEADERO, 240)).thenReturn(60);
        VehiculoVisitaDTO inside = new VehiculoVisitaDTO(3L, VISIT, 15L, "ABC123", "CARRO",
            ZonedDateTime.now().minusMinutes(150), null, null, "DENTRO");
        when(repo.findVehiculosDentro(VISIT)).thenReturn(List.of(inside));
        when(repo.closeVehiculoVisita(eq(3L), any())).thenReturn(true);

        service.registrarSalidaVisita(VISIT);

        verify(repo).closeVehiculoVisita(3L, new BigDecimal("4000"));
        verify(repo).releaseParqueadero(15L);
        verify(repo).revokeActiveQrs(eq(VISIT), eq(30L), anyString());
        verify(repo).insertSalidaVisita(VISIT, 9L, 30L);
    }

    @Test
    void repeatedExitIsANoOp() {
        portero();
        visit(OWN_UNIT, PROP, "FINALIZADA");

        service.registrarSalidaVisita(VISIT);

        verify(repo, never()).insertSalidaVisita(anyLong(), anyLong(), anyLong());
        verify(repo, never()).releaseParqueadero(anyLong());
    }

    @Test
    void exitOfAVisitOfAnotherPropertyLooksLikeNotFound() {
        portero();
        visit(FOREIGN_UNIT, OTHER_PROP, "EN_CURSO");
        assertThrows(NoSuchElementException.class, () -> service.registrarSalidaVisita(VISIT));
        verify(repo, never()).transitionVisitaEstado(anyLong(), anyString(), any());
    }

    @Test
    void vehicleExitIgnoresClientCostAndReleasesTheSpot() {
        portero();
        visit(OWN_UNIT, PROP, "EN_CURSO");
        when(repo.getVehiculoVisitaById(3L)).thenReturn(Optional.of(new VehiculoVisitaDTO(3L, VISIT, 15L, "ABC123",
            "CARRO", ZonedDateTime.now().minusMinutes(10), null, null, "DENTRO")));
        when(repo.closeVehiculoVisita(eq(3L), any())).thenReturn(true);

        service.registrarSalidaVehiculo(3L);

        verify(repo).closeVehiculoVisita(3L, BigDecimal.ZERO);
        verify(repo).releaseParqueadero(15L);
    }

    @Test
    void secondVehicleExitDoesNotReleaseTheSpotAgain() {
        portero();
        visit(OWN_UNIT, PROP, "EN_CURSO");
        when(repo.getVehiculoVisitaById(3L)).thenReturn(Optional.of(new VehiculoVisitaDTO(3L, VISIT, 15L, "ABC123",
            "CARRO", ZonedDateTime.now().minusMinutes(10), null, null, "DENTRO")));
        when(repo.closeVehiculoVisita(eq(3L), any())).thenReturn(false); // already closed concurrently

        service.registrarSalidaVehiculo(3L);

        verify(repo, never()).releaseParqueadero(anyLong());
    }

    @Test
    void accessRecordsOfAnotherPropertyAreDenied() {
        admin(PROP);
        assertThrows(AccessDeniedException.class, () -> service.getRegistrosByPropiedad(OTHER_PROP));
        verify(repo, never()).getRegistrosByPropiedad(anyLong());
    }

    @Test
    void visitSummaryAndHistoryAreScopedToTheCallersProperty() {
        portero();
        when(repo.getVisitasResumen(PROP)).thenReturn(List.of());
        service.getVisitasResumen();
        service.getVisitasHistorial("2026-01-01", "2026-01-31");
        verify(repo).getVisitasResumen(PROP);
        verify(repo).getVisitasHistorial(PROP, "2026-01-01", "2026-01-31");
    }

    @Test
    @SuppressWarnings("unchecked")
    void porteriaUpdateAndDeleteAreScopedToTheCallersProperty() {
        admin(PROP);
        when(jdbc.update(anyString(), any(org.springframework.jdbc.core.namedparam.SqlParameterSource.class))).thenReturn(0);
        when(jdbc.update(anyString(), any(Map.class))).thenReturn(0);
        assertThrows(NoSuchElementException.class, () -> service.actualizarPorteria(9L,
            new com.saed.backend.porteria.dto.PorteriaCreateDTO("n", "u", "t")));
        assertThrows(NoSuchElementException.class, () -> service.eliminarPorteria(9L));
    }

    @Test
    void superadminCannotOperateVisits() {
        superadmin();
        visit(OWN_UNIT, PROP, "EN_CURSO");
        assertThrows(AccessDeniedException.class, () -> service.registrarSalidaVisita(VISIT));
        assertThrows(AccessDeniedException.class, () -> service.cancelarVisita(VISIT));
        assertThrows(AccessDeniedException.class, () -> service.getVisitaDetalle(VISIT));
    }
}
