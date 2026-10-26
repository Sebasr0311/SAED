package com.saed.backend.asambleas.service.impl;

import com.saed.backend.asambleas.dto.AsambleaDTO;
import com.saed.backend.asambleas.dto.AsistenciaRequestDTO;
import com.saed.backend.asambleas.dto.VotacionDTO;
import com.saed.backend.asambleas.dto.VotoRequestDTO;
import com.saed.backend.asambleas.repository.AsambleaRepository;
import com.saed.backend.audit.AuditService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DEC-03 voting slice 2 (audit F10-02, F10-10, F10-11): only the legal owner or an approved proxy
 * of a unit with registered attendance may vote while the assembly is EN_CURSO; the weight is the
 * registered unit coefficient and a missing coefficient rejects the vote. Pure Mockito, no database.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VotingEligibilityServiceTest {

    private static final Long VOTING_ID = 10L;
    private static final Long ASSEMBLY_ID = 20L;
    private static final Long PROPERTY_ID = 30L;
    private static final Long ORG_ID = 40L;
    private static final Long UNIT_ID = 50L;
    private static final Long USER_ID = 60L;
    private static final Long PERSON_ID = 70L;
    private static final Long PROXY_ID = 80L;
    private static final BigDecimal UNIT_COEFFICIENT = new BigDecimal("0.052500");

    @Mock
    private AsambleaRepository repository;

    @Mock
    private AuditService auditService;

    private AsambleaServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AsambleaServiceImpl(repository, auditService);
        givenAssemblyInState("EN_CURSO");
        givenVotingInState("ABIERTA");
        when(repository.findPropiedadUnidad(UNIT_ID)).thenReturn(PROPERTY_ID);
        when(repository.findPersonaUsuario(USER_ID)).thenReturn(PERSON_ID);
        when(repository.findCoeficienteUnidad(UNIT_ID)).thenReturn(UNIT_COEFFICIENT);
        when(repository.tieneAsistenciaVigente(ASSEMBLY_ID, UNIT_ID)).thenReturn(true);
        when(repository.registrarVoto(eq(VOTING_ID), any(VotoRequestDTO.class), any(BigDecimal.class))).thenReturn(99L);
    }

    @AfterEach
    void tearDown() {
        SaedContextHolder.clearContext();
    }

    @Test
    void voteIsRejectedWhileAssemblyIsOnlyConvened() {
        loginAs("RESIDENTE");
        givenCallerIsLegalOwner();
        givenAssemblyInState("CONVOCADA");

        assertThrows(IllegalStateException.class, () -> service.emitirVoto(VOTING_ID, vote(null)));

        verify(repository, never()).registrarVoto(any(), any(), any());
    }

    @Test
    void residentWhoIsNotTheLegalOwnerCannotVote() {
        loginAs("RESIDENTE");
        when(repository.esPropietarioLegalActivo(UNIT_ID, PERSON_ID)).thenReturn(false);
        when(repository.esApoderadoAprobado(ASSEMBLY_ID, UNIT_ID, PERSON_ID)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.emitirVoto(VOTING_ID, vote(null)));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(repository, never()).registrarVoto(any(), any(), any());
    }

    @Test
    void approvedProxyVotesForTheRepresentedUnitAsThemselves() {
        loginAs("RESIDENTE");
        when(repository.findPersonaUsuario(USER_ID)).thenReturn(PROXY_ID);
        when(repository.esPropietarioLegalActivo(UNIT_ID, PROXY_ID)).thenReturn(false);
        when(repository.esApoderadoAprobado(ASSEMBLY_ID, UNIT_ID, PROXY_ID)).thenReturn(true);

        service.emitirVoto(VOTING_ID, vote(null));

        assertEquals(PROXY_ID, capturedVote().getIdPersonaVotante());
    }

    @Test
    void unitWithoutActiveAttendanceCannotVote() {
        loginAs("RESIDENTE");
        givenCallerIsLegalOwner();
        when(repository.tieneAsistenciaVigente(ASSEMBLY_ID, UNIT_ID)).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> service.emitirVoto(VOTING_ID, vote(null)));

        verify(repository, never()).registrarVoto(any(), any(), any());
    }

    @Test
    void missingUnitCoefficientRejectsTheVote() {
        loginAs("RESIDENTE");
        givenCallerIsLegalOwner();
        when(repository.findCoeficienteUnidad(UNIT_ID)).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> service.emitirVoto(VOTING_ID, vote(null)));

        verify(repository, never()).registrarVoto(any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN_PROPIEDAD", "ADMIN_ORGANIZACION", "SUPERADMIN"})
    void administratorCannotVoteOnBehalfOfAnArbitraryUnit(String role) {
        loginAs(role);
        when(repository.esPropietarioLegalActivo(UNIT_ID, PERSON_ID)).thenReturn(false);
        when(repository.esApoderadoAprobado(ASSEMBLY_ID, UNIT_ID, PERSON_ID)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.emitirVoto(VOTING_ID, vote(999L)));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(repository, never()).registrarVoto(any(), any(), any());
    }

    @Test
    void administratorWhoOwnsTheUnitCannotChooseTheVoterPerson() {
        loginAs("ADMIN_PROPIEDAD");
        givenCallerIsLegalOwner();

        service.emitirVoto(VOTING_ID, vote(999L));

        assertEquals(PERSON_ID, capturedVote().getIdPersonaVotante());
    }

    @Test
    void callerWithoutPersonCannotVote() {
        loginAs("ADMIN_PROPIEDAD");
        when(repository.findPersonaUsuario(USER_ID)).thenReturn(null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.emitirVoto(VOTING_ID, vote(null)));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(repository, never()).registrarVoto(any(), any(), any());
    }

    @Test
    void attendanceWeightIsTheRegisteredUnitCoefficientNotTheClientValue() {
        loginAs("ADMIN_PROPIEDAD");
        when(repository.esPropietarioLegalActivo(UNIT_ID, PERSON_ID)).thenReturn(true);
        when(repository.registrarAsistencia(eq(ASSEMBLY_ID), any(AsistenciaRequestDTO.class), any(BigDecimal.class)))
                .thenReturn(5L);

        service.registrarAsistencia(ASSEMBLY_ID, ownerAttendance(new BigDecimal("0.9")));

        ArgumentCaptor<BigDecimal> weight = ArgumentCaptor.forClass(BigDecimal.class);
        verify(repository).registrarAsistencia(eq(ASSEMBLY_ID), any(AsistenciaRequestDTO.class), weight.capture());
        assertEquals(0, UNIT_COEFFICIENT.compareTo(weight.getValue()));
    }

    @Test
    void attendanceWithoutUnitCoefficientIsRejected() {
        loginAs("ADMIN_PROPIEDAD");
        when(repository.esPropietarioLegalActivo(UNIT_ID, PERSON_ID)).thenReturn(true);
        when(repository.findCoeficienteUnidad(UNIT_ID)).thenReturn(null);

        assertThrows(IllegalStateException.class,
                () -> service.registrarAsistencia(ASSEMBLY_ID, ownerAttendance(new BigDecimal("0.9"))));

        verify(repository, never()).registrarAsistencia(any(), any(), any());
    }

    @Test
    void attendanceAsDirectOwnerRequiresLegalOwnership() {
        loginAs("ADMIN_PROPIEDAD");
        when(repository.esPropietarioLegalActivo(UNIT_ID, PERSON_ID)).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> service.registrarAsistencia(ASSEMBLY_ID, ownerAttendance(null)));

        verify(repository, never()).registrarAsistencia(any(), any(), any());
    }

    @Test
    void attendanceAsProxyRequiresAnApprovedPowerForThatUnitAndPerson() {
        loginAs("ADMIN_PROPIEDAD");
        when(repository.esPoderAprobadoDe(77L, ASSEMBLY_ID, UNIT_ID, PROXY_ID)).thenReturn(false);
        AsistenciaRequestDTO request = proxyAttendance(77L);

        assertThrows(IllegalArgumentException.class, () -> service.registrarAsistencia(ASSEMBLY_ID, request));

        verify(repository, never()).registrarAsistencia(any(), any(), any());
    }

    @Test
    void withdrawalRecomputesAndPersistsQuorum() {
        loginAs("ADMIN_PROPIEDAD");
        when(repository.retirarAsistencia(ASSEMBLY_ID, UNIT_ID)).thenReturn(1);
        when(repository.calcularQuorumAlcanzadoPct(ASSEMBLY_ID, PROPERTY_ID)).thenReturn(new BigDecimal("47.50"));

        service.retirarAsistencia(ASSEMBLY_ID, UNIT_ID);

        InOrder order = inOrder(repository);
        order.verify(repository).retirarAsistencia(ASSEMBLY_ID, UNIT_ID);
        order.verify(repository).updateQuorumAlcanzado(ASSEMBLY_ID, new BigDecimal("47.50"));
    }

    @Test
    void withdrawalOfUnitWithoutActiveAttendanceIsNotFound() {
        loginAs("ADMIN_PROPIEDAD");
        when(repository.retirarAsistencia(ASSEMBLY_ID, UNIT_ID)).thenReturn(0);

        assertThrows(NoSuchElementException.class, () -> service.retirarAsistencia(ASSEMBLY_ID, UNIT_ID));

        verify(repository, never()).updateQuorumAlcanzado(any(), any());
    }

    private void loginAs(String role) {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(USER_ID)
                .organizationId(ORG_ID)
                .propertyId(PROPERTY_ID)
                .roleCode(role)
                .build());
    }

    private void givenCallerIsLegalOwner() {
        when(repository.esPropietarioLegalActivo(UNIT_ID, PERSON_ID)).thenReturn(true);
    }

    private void givenAssemblyInState(String state) {
        AsambleaDTO assembly = new AsambleaDTO();
        assembly.setIdAsamblea(ASSEMBLY_ID);
        assembly.setIdPropiedad(PROPERTY_ID);
        assembly.setIdOrganizacion(ORG_ID);
        assembly.setEstado(state);
        when(repository.findById(ASSEMBLY_ID)).thenReturn(Optional.of(assembly));
    }

    private void givenVotingInState(String state) {
        VotacionDTO voting = new VotacionDTO();
        voting.setIdVotacion(VOTING_ID);
        voting.setIdAsamblea(ASSEMBLY_ID);
        voting.setEstado(state);
        when(repository.findVotacionById(VOTING_ID)).thenReturn(Optional.of(voting));
    }

    private VotoRequestDTO vote(Long idPersonaVotante) {
        return VotoRequestDTO.builder()
                .idUnidad(UNIT_ID)
                .idPersonaVotante(idPersonaVotante)
                .opcionVoto("SI")
                .build();
    }

    private AsistenciaRequestDTO ownerAttendance(BigDecimal clientWeight) {
        AsistenciaRequestDTO request = new AsistenciaRequestDTO();
        request.setIdUnidad(UNIT_ID);
        request.setIdPersonaAsistente(PERSON_ID);
        request.setEsPropietarioDirecto("S");
        request.setCoeficientePonderado(clientWeight);
        return request;
    }

    private AsistenciaRequestDTO proxyAttendance(Long idPoder) {
        AsistenciaRequestDTO request = new AsistenciaRequestDTO();
        request.setIdUnidad(UNIT_ID);
        request.setIdPersonaAsistente(PROXY_ID);
        request.setEsPropietarioDirecto("N");
        request.setIdPoder(idPoder);
        return request;
    }

    private VotoRequestDTO capturedVote() {
        ArgumentCaptor<VotoRequestDTO> vote = ArgumentCaptor.forClass(VotoRequestDTO.class);
        verify(repository).registrarVoto(eq(VOTING_ID), vote.capture(), any(BigDecimal.class));
        return vote.getValue();
    }
}
