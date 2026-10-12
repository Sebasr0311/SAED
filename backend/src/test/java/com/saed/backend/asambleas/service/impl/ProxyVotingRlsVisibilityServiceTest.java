package com.saed.backend.asambleas.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saed.backend.asambleas.dto.AsambleaDTO;
import com.saed.backend.asambleas.dto.VotacionDTO;
import com.saed.backend.asambleas.dto.VotoRequestDTO;
import com.saed.backend.asambleas.repository.AsambleaRepository;
import com.saed.backend.audit.AuditService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * DEC-03 proxy voting under RLS: a proxy holder's own tenant context cannot read the represented unit
 * (residents see only their assignment's unit), so eligibility comes from the APROBADO power and the weight from
 * the unit's registered attendance, without elevating the security context. Pure Mockito, no database.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProxyVotingRlsVisibilityServiceTest {

    private static final Long VOTING_ID = 10L;
    private static final Long ASSEMBLY_ID = 20L;
    private static final Long PROPERTY_ID = 30L;
    private static final Long ORG_ID = 40L;
    private static final Long UNIT_ID = 50L;
    private static final Long USER_ID = 60L;
    private static final Long PROXY_PERSON_ID = 80L;
    private static final BigDecimal ATTENDANCE_WEIGHT = new BigDecimal("0.031250");

    @Mock
    private AsambleaRepository repository;

    @Mock
    private AuditService auditService;

    private AsambleaServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AsambleaServiceImpl(repository, auditService);
        AsambleaDTO assembly = new AsambleaDTO();
        assembly.setIdAsamblea(ASSEMBLY_ID);
        assembly.setIdPropiedad(PROPERTY_ID);
        assembly.setIdOrganizacion(ORG_ID);
        assembly.setEstado("EN_CURSO");
        when(repository.findById(ASSEMBLY_ID)).thenReturn(Optional.of(assembly));
        VotacionDTO voting = new VotacionDTO();
        voting.setIdVotacion(VOTING_ID);
        voting.setIdAsamblea(ASSEMBLY_ID);
        voting.setEstado("ABIERTA");
        when(repository.findVotacionById(VOTING_ID)).thenReturn(Optional.of(voting));
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(USER_ID).organizationId(ORG_ID).propertyId(PROPERTY_ID).roleCode("RESIDENTE").build());
        when(repository.findPersonaUsuario(USER_ID)).thenReturn(PROXY_PERSON_ID);
        // The represented unit is hidden by RLS from the proxy holder.
        when(repository.findPropiedadUnidad(UNIT_ID)).thenReturn(null);
        when(repository.findCoeficienteUnidad(UNIT_ID)).thenReturn(null);
        when(repository.registrarVoto(eq(VOTING_ID), any(VotoRequestDTO.class), any(BigDecimal.class))).thenReturn(99L);
    }

    @AfterEach
    void tearDown() {
        SaedContextHolder.clearContext();
    }

    @Test
    void approvedProxyVotesForAUnitHiddenByRlsUsingTheAttendanceWeight() {
        when(repository.esApoderadoAprobado(ASSEMBLY_ID, UNIT_ID, PROXY_PERSON_ID)).thenReturn(true);
        when(repository.tieneAsistenciaVigente(ASSEMBLY_ID, UNIT_ID)).thenReturn(true);
        when(repository.findCoeficienteAsistencia(ASSEMBLY_ID, UNIT_ID)).thenReturn(ATTENDANCE_WEIGHT);

        service.emitirVoto(VOTING_ID, vote());

        ArgumentCaptor<BigDecimal> weight = ArgumentCaptor.forClass(BigDecimal.class);
        verify(repository).registrarVoto(eq(VOTING_ID), any(VotoRequestDTO.class), weight.capture());
        assertEquals(0, ATTENDANCE_WEIGHT.compareTo(weight.getValue()));
    }

    @Test
    void unauthorizedCallerGetsForbiddenNotBadRequestWhenTheUnitIsHidden() {
        when(repository.esApoderadoAprobado(ASSEMBLY_ID, UNIT_ID, PROXY_PERSON_ID)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.emitirVoto(VOTING_ID, vote()));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(repository, never()).registrarVoto(any(), any(), any());
    }

    @Test
    void approvedProxyWithoutRegisteredAttendanceCannotVote() {
        when(repository.esApoderadoAprobado(ASSEMBLY_ID, UNIT_ID, PROXY_PERSON_ID)).thenReturn(true);
        when(repository.tieneAsistenciaVigente(ASSEMBLY_ID, UNIT_ID)).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> service.emitirVoto(VOTING_ID, vote()));

        verify(repository, never()).registrarVoto(any(), any(), any());
    }

    @Test
    void approvedProxyWithoutAnyReadableCoefficientIsRejected() {
        when(repository.esApoderadoAprobado(ASSEMBLY_ID, UNIT_ID, PROXY_PERSON_ID)).thenReturn(true);
        when(repository.tieneAsistenciaVigente(ASSEMBLY_ID, UNIT_ID)).thenReturn(true);
        when(repository.findCoeficienteAsistencia(ASSEMBLY_ID, UNIT_ID)).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> service.emitirVoto(VOTING_ID, vote()));

        verify(repository, never()).registrarVoto(any(), any(), any());
    }

    private VotoRequestDTO vote() {
        return VotoRequestDTO.builder().idUnidad(UNIT_ID).opcionVoto("SI").build();
    }
}
