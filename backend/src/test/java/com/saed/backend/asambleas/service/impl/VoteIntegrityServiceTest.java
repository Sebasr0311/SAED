package com.saed.backend.asambleas.service.impl;

import com.saed.backend.asambleas.dto.AsambleaDTO;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests (no Spring context, no database) for vote integrity in
 * {@link AsambleaServiceImpl#emitirVoto}. Audit finding F10-01 / F10-02.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VoteIntegrityServiceTest {

    private static final Long VOTING_ID = 10L;
    private static final Long ASSEMBLY_ID = 20L;
    private static final Long PROPERTY_ID = 30L;
    private static final Long ORG_ID = 40L;
    private static final Long UNIT_ID = 50L;
    private static final Long USER_ID = 60L;
    private static final Long PERSON_ID = 70L;
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
        when(repository.existeVotoUnidad(VOTING_ID, UNIT_ID)).thenReturn(false);
        when(repository.registrarVoto(eq(VOTING_ID), any(VotoRequestDTO.class), any(BigDecimal.class))).thenReturn(99L);
    }

    @AfterEach
    void tearDown() {
        SaedContextHolder.clearContext();
    }

    @Test
    void forgedClientWeightIsIgnoredAndUnitCoefficientIsStored() {
        loginAs("RESIDENTE");
        givenCallerOwnsUnit();

        service.emitirVoto(VOTING_ID, voteWithWeight(new BigDecimal("99")));

        assertEquals(0, UNIT_COEFFICIENT.compareTo(capturedStoredWeight()),
                "Stored vote weight must be the server-derived unit coefficient");
    }

    @Test
    void forgedClientWeightIsIgnoredForAdminOperatedVote() {
        loginAs("ADMIN_PROPIEDAD");

        service.emitirVoto(VOTING_ID, voteWithWeight(new BigDecimal("99")));

        assertEquals(0, UNIT_COEFFICIENT.compareTo(capturedStoredWeight()),
                "Admin-operated votes must also use the server-derived unit coefficient");
    }

    @Test
    void voterWithoutEligibleUnitIsRejectedWithoutInsert() {
        loginAs("RESIDENTE");
        when(repository.usuarioPerteneceAUnidad(USER_ID, UNIT_ID, PERSON_ID)).thenReturn(false);
        when(repository.esApoderadoAprobado(ASSEMBLY_ID, UNIT_ID, PERSON_ID)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.emitirVoto(VOTING_ID, voteWithWeight(null)));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(repository, never()).registrarVoto(any(), any(), any());
    }

    @Test
    void unitFromAnotherPropertyIsRejectedWithoutInsert() {
        loginAs("RESIDENTE");
        givenCallerOwnsUnit();
        when(repository.findPropiedadUnidad(UNIT_ID)).thenReturn(PROPERTY_ID + 1);

        assertThrows(IllegalArgumentException.class,
                () -> service.emitirVoto(VOTING_ID, voteWithWeight(null)));

        verify(repository, never()).registrarVoto(any(), any(), any());
    }

    @Test
    void secondVoteForSameUnitIsRejectedWithConflict() {
        loginAs("RESIDENTE");
        givenCallerOwnsUnit();
        when(repository.existeVotoUnidad(VOTING_ID, UNIT_ID)).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.emitirVoto(VOTING_ID, voteWithWeight(null)));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(repository, never()).registrarVoto(any(), any(), any());
    }

    @Test
    void uniqueConstraintViolationOnInsertIsMappedToConflict() {
        loginAs("RESIDENTE");
        givenCallerOwnsUnit();
        when(repository.registrarVoto(eq(VOTING_ID), any(VotoRequestDTO.class), any(BigDecimal.class)))
                .thenThrow(new DuplicateKeyException("ORA-00001: unique constraint (UQ_VOTO_UNIDAD_VOTACION) violated"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.emitirVoto(VOTING_ID, voteWithWeight(null)));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"FINALIZADA", "CANCELADA", "BORRADOR", "EN_RECESO"})
    void voteIsRejectedWhenAssemblyIsNotOpen(String assemblyState) {
        loginAs("RESIDENTE");
        givenCallerOwnsUnit();
        givenAssemblyInState(assemblyState);

        assertThrows(IllegalStateException.class,
                () -> service.emitirVoto(VOTING_ID, voteWithWeight(null)));

        verify(repository, never()).registrarVoto(any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CERRADA", "ANULADA"})
    void voteIsRejectedWhenVotingIsNotOpen(String votingState) {
        loginAs("RESIDENTE");
        givenCallerOwnsUnit();
        givenVotingInState(votingState);

        assertThrows(IllegalStateException.class,
                () -> service.emitirVoto(VOTING_ID, voteWithWeight(null)));

        verify(repository, never()).registrarVoto(any(), any(), any());
    }

    private void loginAs(String role) {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(USER_ID)
                .organizationId(ORG_ID)
                .propertyId(PROPERTY_ID)
                .roleCode(role)
                .build());
    }

    private void givenCallerOwnsUnit() {
        when(repository.usuarioPerteneceAUnidad(USER_ID, UNIT_ID, PERSON_ID)).thenReturn(true);
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

    private VotoRequestDTO voteWithWeight(BigDecimal weight) {
        return VotoRequestDTO.builder()
                .idUnidad(UNIT_ID)
                .opcionVoto("SI")
                .coeficienteVoto(weight)
                .build();
    }

    private BigDecimal capturedStoredWeight() {
        ArgumentCaptor<BigDecimal> weight = ArgumentCaptor.forClass(BigDecimal.class);
        verify(repository).registrarVoto(eq(VOTING_ID), any(VotoRequestDTO.class), weight.capture());
        return weight.getValue();
    }
}
