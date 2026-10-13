package com.saed.backend.sanciones.service.impl;

import com.saed.backend.audit.AuditService;
import com.saed.backend.common.service.EmailService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.sanciones.dto.DescargoRequestDTO;
import com.saed.backend.sanciones.dto.ResolucionRequestDTO;
import com.saed.backend.sanciones.dto.SancionCreateRequestDTO;
import com.saed.backend.sanciones.dto.SancionDTO;
import com.saed.backend.sanciones.integration.FineReceivablesPort;
import com.saed.backend.sanciones.repository.SancionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sanction ownership, due-process terms and idempotent effects (audit F10-04, F10-06, F10-07, F10-09,
 * F10-15; plan F10-02). Pure Mockito, no database.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SancionLifecycleServiceTest {

    private static final Long SANCTION_ID = 100L;
    private static final Long PROPERTY_ID = 30L;
    private static final Long ORG_ID = 40L;
    private static final Long UNIT_ID = 50L;
    private static final Long USER_ID = 60L;
    private static final Long ACCUSED_PERSON = 70L;
    private static final Long OTHER_PERSON = 71L;
    private static final Long FINE_CONCEPT = 5L;
    private static final Long FINE_ID = 555L;

    @Mock
    private SancionRepository repository;
    @Mock
    private NamedParameterJdbcTemplate jdbc;
    @Mock
    private EmailService emailService;
    @Mock
    private AuditService auditService;
    @Mock
    private FineReceivablesPort receivables;

    private SancionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SancionServiceImpl(repository, jdbc, emailService, auditService, Optional.of(receivables));
        when(repository.findConceptoMulta(PROPERTY_ID)).thenReturn(Optional.of(FINE_CONCEPT));
        when(repository.findMultaPorSancion(SANCTION_ID)).thenReturn(Optional.empty());
        when(repository.crearMultaDesdeSancion(eq(SANCTION_ID), eq(UNIT_ID), eq(ACCUSED_PERSON), eq(FINE_CONCEPT),
                any(BigDecimal.class), anyString(), any())).thenReturn(FINE_ID);
        when(repository.emitirResolucionSiAbierta(eq(SANCTION_ID), anyString(), anyString(), any(), any())).thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        SaedContextHolder.clearContext();
    }

    // ---- F10-09: who may read a sanction file ----

    @Test
    void unrelatedResidentCannotReadSanctionFile() {
        loginAs("RESIDENTE", PROPERTY_ID);
        givenSanction("NOTIFICADA", "AMONESTACION_ESCRITA", LocalDate.now().plusDays(5));
        when(repository.findPersonaUsuario(USER_ID)).thenReturn(OTHER_PERSON);
        when(repository.personaVinculadaAUnidad(OTHER_PERSON, UNIT_ID)).thenReturn(false);

        assertThrows(NoSuchElementException.class, () -> service.getSancionById(SANCTION_ID));
    }

    @Test
    void accusedPersonCanReadOwnSanctionFile() {
        loginAs("RESIDENTE", PROPERTY_ID);
        givenSanction("NOTIFICADA", "AMONESTACION_ESCRITA", LocalDate.now().plusDays(5));
        when(repository.findPersonaUsuario(USER_ID)).thenReturn(ACCUSED_PERSON);

        assertEquals(SANCTION_ID, service.getSancionById(SANCTION_ID).getIdSancion());
    }

    @Test
    void memberOfTheSanctionedUnitCanReadTheFile() {
        loginAs("PROPIETARIO", PROPERTY_ID);
        givenSanction("NOTIFICADA", "AMONESTACION_ESCRITA", LocalDate.now().plusDays(5));
        when(repository.findPersonaUsuario(USER_ID)).thenReturn(OTHER_PERSON);
        when(repository.personaVinculadaAUnidad(OTHER_PERSON, UNIT_ID)).thenReturn(true);

        assertEquals(SANCTION_ID, service.getSancionById(SANCTION_ID).getIdSancion());
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUPERADMIN", "PORTERO"})
    void nonParticipantRolesCannotReadSanctionFile(String role) {
        loginAs(role, PROPERTY_ID);
        givenSanction("NOTIFICADA", "AMONESTACION_ESCRITA", LocalDate.now().plusDays(5));
        when(repository.findPersonaUsuario(USER_ID)).thenReturn(OTHER_PERSON);

        assertThrows(NoSuchElementException.class, () -> service.getSancionById(SANCTION_ID));
    }

    @Test
    void propertyAdministratorOfAnotherPropertyCannotReadSanctionFile() {
        loginAs("ADMIN_PROPIEDAD", PROPERTY_ID + 1);
        givenSanction("NOTIFICADA", "AMONESTACION_ESCRITA", LocalDate.now().plusDays(5));

        assertThrows(NoSuchElementException.class, () -> service.getSancionById(SANCTION_ID));
    }

    @Test
    void propertyAdministratorReadsSanctionFileOfOwnProperty() {
        loginAs("ADMIN_PROPIEDAD", PROPERTY_ID);
        givenSanction("NOTIFICADA", "AMONESTACION_ESCRITA", LocalDate.now().plusDays(5));

        assertEquals(SANCTION_ID, service.getSancionById(SANCTION_ID).getIdSancion());
    }

    @Test
    void missingSanctionIsNotFound() {
        loginAs("ADMIN_PROPIEDAD", PROPERTY_ID);
        when(repository.findById(SANCTION_ID)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.getSancionById(SANCTION_ID));
    }

    // ---- F10-04: state machine and discharge term ----

    @ParameterizedTest
    @ValueSource(strings = {"APLICADA", "ABSUELTA", "ANULADA"})
    void closedSanctionCannotBeResolvedAgain(String closedState) {
        loginAs("ADMIN_PROPIEDAD", PROPERTY_ID);
        givenSanction(closedState, "MULTA_ECONOMICA", LocalDate.now().minusDays(3));

        assertThrows(IllegalStateException.class, () -> service.emitirResolucion(SANCTION_ID, resolution("APLICADA")));

        verify(repository, never()).emitirResolucionSiAbierta(anyLong(), anyString(), anyString(), any(), any());
        verify(repository, never()).crearMultaDesdeSancion(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void resolutionBeforeDischargeTermWithoutDefenseIsRejected() {
        loginAs("ADMIN_PROPIEDAD", PROPERTY_ID);
        givenSanction("NOTIFICADA", "AMONESTACION_ESCRITA", LocalDate.now().plusDays(1));

        assertThrows(IllegalStateException.class, () -> service.emitirResolucion(SANCTION_ID, resolution("APLICADA")));

        verify(repository, never()).emitirResolucionSiAbierta(anyLong(), anyString(), anyString(), any(), any());
    }

    @Test
    void resolutionAfterDischargeTermExpiredIsAllowed() {
        loginAs("ADMIN_PROPIEDAD", PROPERTY_ID);
        givenSanction("NOTIFICADA", "AMONESTACION_ESCRITA", LocalDate.now().minusDays(1));

        service.emitirResolucion(SANCTION_ID, resolution("APLICADA"));

        verify(repository).emitirResolucionSiAbierta(eq(SANCTION_ID), eq("APLICADA"), anyString(), any(), any());
    }

    @Test
    void concurrentRepeatedResolutionCannotCreateASecondFine() {
        loginAs("ADMIN_PROPIEDAD", PROPERTY_ID);
        givenSanction("EN_DESCARGOS", "MULTA_ECONOMICA", LocalDate.now().plusDays(3));
        when(repository.emitirResolucionSiAbierta(eq(SANCTION_ID), anyString(), anyString(), any(), any())).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> service.emitirResolucion(SANCTION_ID, fine("120000")));

        verify(repository, never()).crearMultaDesdeSancion(any(), any(), any(), any(), any(), any(), any());
        verify(receivables, never()).postFineReceivable(any());
    }

    @Test
    void existingFineForTheSanctionIsNotDuplicated() {
        loginAs("ADMIN_PROPIEDAD", PROPERTY_ID);
        givenSanction("EN_DESCARGOS", "MULTA_ECONOMICA", LocalDate.now().plusDays(3));
        when(repository.findMultaPorSancion(SANCTION_ID)).thenReturn(Optional.of(FINE_ID));

        service.emitirResolucion(SANCTION_ID, fine("120000"));

        verify(repository, never()).crearMultaDesdeSancion(any(), any(), any(), any(), any(), any(), any());
    }

    // ---- F10-06: fine reaches receivables exactly once ----

    @Test
    void appliedFineIsPostedToReceivablesOnceAndLinked() {
        loginAs("ADMIN_PROPIEDAD", PROPERTY_ID);
        givenSanction("EN_DESCARGOS", "MULTA_ECONOMICA", LocalDate.now().plusDays(3));
        when(receivables.postFineReceivable(any())).thenReturn(Optional.of(900L));

        service.emitirResolucion(SANCTION_ID, fine("120000"));

        ArgumentCaptor<FineReceivablesPort.FineReceivable> posted =
                ArgumentCaptor.forClass(FineReceivablesPort.FineReceivable.class);
        verify(receivables, times(1)).postFineReceivable(posted.capture());
        assertEquals(FINE_ID, posted.getValue().idMulta());
        assertEquals(UNIT_ID, posted.getValue().idUnidad());
        assertEquals(0, new BigDecimal("120000").compareTo(posted.getValue().monto()));
        verify(repository).vincularCuotaMulta(FINE_ID, 900L);
    }

    @Test
    void finePostingIsSkippedWithoutFailingWhenNoReceivablesAdapterExists() {
        service = new SancionServiceImpl(repository, jdbc, emailService, auditService, Optional.empty());
        loginAs("ADMIN_PROPIEDAD", PROPERTY_ID);
        givenSanction("EN_DESCARGOS", "MULTA_ECONOMICA", LocalDate.now().plusDays(3));

        service.emitirResolucion(SANCTION_ID, fine("120000"));

        verify(repository).crearMultaDesdeSancion(eq(SANCTION_ID), eq(UNIT_ID), eq(ACCUSED_PERSON), eq(FINE_CONCEPT),
                any(BigDecimal.class), anyString(), any());
        verify(repository, never()).vincularCuotaMulta(any(), any());
    }

    // ---- F10-07: common-area restriction ----

    @Test
    void commonAreaSuspensionRequiresARestrictionEndDate() {
        loginAs("ADMIN_PROPIEDAD", PROPERTY_ID);
        givenSanction("EN_DESCARGOS", "SUSPENSION_ZONAS_COMUNES", LocalDate.now().plusDays(3));

        assertThrows(IllegalArgumentException.class, () -> service.emitirResolucion(SANCTION_ID, resolution("APLICADA")));

        verify(repository, never()).emitirResolucionSiAbierta(anyLong(), anyString(), anyString(), any(), any());
    }

    @Test
    void commonAreaSuspensionPersistsTheRestrictionPeriod() {
        loginAs("ADMIN_PROPIEDAD", PROPERTY_ID);
        givenSanction("EN_DESCARGOS", "SUSPENSION_ZONAS_COMUNES", LocalDate.now().plusDays(3));
        ResolucionRequestDTO request = resolution("APLICADA");
        LocalDate until = LocalDate.now().plusDays(30);
        request.setRestriccionHasta(until);

        service.emitirResolucion(SANCTION_ID, request);

        verify(repository).emitirResolucionSiAbierta(SANCTION_ID, "APLICADA", request.getResolucionFinal(),
                LocalDate.now(), until);
    }

    @Test
    void activeCommonAreaRestrictionIsQueryableByUnitAndDate() {
        LocalDate day = LocalDate.of(2026, 11, 3);
        when(repository.existeRestriccionZonasComunes(UNIT_ID, day)).thenReturn(true);

        assertTrue(service.hasActiveCommonAreaRestriction(UNIT_ID, day));
        assertFalse(service.hasActiveCommonAreaRestriction(UNIT_ID + 1, day));
        assertFalse(service.hasActiveCommonAreaRestriction(null, day));
    }

    // ---- descargos ownership and term ----

    @Test
    void defenseAfterDischargeTermIsRejected() {
        loginAs("RESIDENTE", PROPERTY_ID);
        givenSanction("NOTIFICADA", "AMONESTACION_ESCRITA", LocalDate.now().minusDays(1));
        when(repository.findPersonaUsuario(USER_ID)).thenReturn(ACCUSED_PERSON);

        assertThrows(IllegalStateException.class, () -> service.radicarDescargos(SANCTION_ID, defense()));

        verify(repository, never()).registrarDescargo(any(), any(), any(), any(), any());
    }

    @Test
    void unrelatedPersonCannotFileDefense() {
        loginAs("RESIDENTE", PROPERTY_ID);
        givenSanction("NOTIFICADA", "AMONESTACION_ESCRITA", LocalDate.now().plusDays(3));
        when(repository.findPersonaUsuario(USER_ID)).thenReturn(OTHER_PERSON);
        when(repository.personaVinculadaAUnidad(OTHER_PERSON, UNIT_ID)).thenReturn(false);

        assertThrows(NoSuchElementException.class, () -> service.radicarDescargos(SANCTION_ID, defense()));

        verify(repository, never()).registrarDescargo(any(), any(), any(), any(), any());
    }

    @Test
    void accusedFilesDefenseAsThemselves() {
        loginAs("RESIDENTE", PROPERTY_ID);
        givenSanction("NOTIFICADA", "AMONESTACION_ESCRITA", LocalDate.now().plusDays(3));
        when(repository.findPersonaUsuario(USER_ID)).thenReturn(ACCUSED_PERSON);

        service.radicarDescargos(SANCTION_ID, defense());

        verify(repository).registrarDescargo(eq(SANCTION_ID), eq(ACCUSED_PERSON), eq(USER_ID), anyString(), any());
    }

    // ---- F10-15: charge sheet scope ----

    @Test
    void chargeSheetWithoutActivePropertyIsRejected() {
        loginAs("ADMIN_ORGANIZACION", null);

        assertThrows(IllegalStateException.class, () -> service.crearPliego(chargeSheet()));

        verify(repository, never()).crearSancion(any(), any(), any(), any(), any());
    }

    @Test
    void chargeSheetForUnitOfAnotherPropertyIsRejected() {
        loginAs("ADMIN_PROPIEDAD", PROPERTY_ID);
        when(repository.findPropiedadUnidad(UNIT_ID)).thenReturn(PROPERTY_ID + 1);

        assertThrows(IllegalArgumentException.class, () -> service.crearPliego(chargeSheet()));

        verify(repository, never()).crearSancion(any(), any(), any(), any(), any());
    }

    @Test
    void chargeSheetAgainstPersonUnrelatedToTheUnitIsRejected() {
        loginAs("ADMIN_PROPIEDAD", PROPERTY_ID);
        when(repository.findPropiedadUnidad(UNIT_ID)).thenReturn(PROPERTY_ID);
        when(repository.personaVinculadaAUnidad(ACCUSED_PERSON, UNIT_ID)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> service.crearPliego(chargeSheet()));

        verify(repository, never()).crearSancion(any(), any(), any(), any(), any());
    }

    private void loginAs(String role, Long propertyId) {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(USER_ID)
                .organizationId(ORG_ID)
                .propertyId(propertyId)
                .roleCode(role)
                .build());
    }

    private void givenSanction(String state, String proposedType, LocalDate dischargeDeadline) {
        SancionDTO sanction = new SancionDTO();
        sanction.setIdSancion(SANCTION_ID);
        sanction.setIdPropiedad(PROPERTY_ID);
        sanction.setIdUnidad(UNIT_ID);
        sanction.setIdPersonaImputada(ACCUSED_PERSON);
        sanction.setNumeroExpediente("EXP-2026-P30-0001");
        sanction.setEstado(state);
        sanction.setTipoSancionPropuesta(proposedType);
        sanction.setFechaLimiteDescargos(dischargeDeadline);
        when(repository.findById(SANCTION_ID)).thenReturn(Optional.of(sanction));
    }

    private ResolucionRequestDTO resolution(String decision) {
        ResolucionRequestDTO request = new ResolucionRequestDTO();
        request.setDecision(decision);
        request.setResolucionFinal("Motivated resolution");
        return request;
    }

    private ResolucionRequestDTO fine(String amount) {
        ResolucionRequestDTO request = resolution("APLICADA");
        request.setMontoMulta(new BigDecimal(amount));
        return request;
    }

    private DescargoRequestDTO defense() {
        DescargoRequestDTO request = new DescargoRequestDTO();
        request.setDescargos("My defense");
        return request;
    }

    private SancionCreateRequestDTO chargeSheet() {
        SancionCreateRequestDTO request = new SancionCreateRequestDTO();
        request.setIdUnidad(UNIT_ID);
        request.setIdPersonaImputada(ACCUSED_PERSON);
        request.setTipoFalta("RUIDO");
        request.setDescripcionHechos("Facts");
        return request;
    }
}
