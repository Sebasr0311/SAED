package com.saed.backend.asambleas.service.impl;

import com.saed.backend.asambleas.dto.AsambleaDTO;
import com.saed.backend.asambleas.dto.VotacionDTO;
import com.saed.backend.asambleas.dto.VotacionResultado;
import com.saed.backend.asambleas.repository.AsambleaRepository;
import com.saed.backend.audit.AuditService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** F10-12: closing a voting locks it, computes the weighted result once and persists it with the closing. */
class CierreVotacionTest {

    private static final Long ASSEMBLY = 20L;
    private static final Long PROPERTY = 30L;
    private static final Long VOTING = 7L;

    private final AsambleaRepository repository = mock(AsambleaRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final AsambleaServiceImpl service = new AsambleaServiceImpl(repository, audit);

    @BeforeEach
    void setUp() {
        SaedContextHolder.setContext(SaedContext.builder().userId(60L).organizationId(40L).propertyId(PROPERTY)
                .roleCode("ADMIN_PROPIEDAD").roleScope("PROPIEDAD").build());
        AsambleaDTO assembly = new AsambleaDTO();
        assembly.setIdAsamblea(ASSEMBLY);
        assembly.setIdPropiedad(PROPERTY);
        assembly.setIdOrganizacion(40L);
        assembly.setEstado("EN_CURSO");
        when(repository.findById(ASSEMBLY)).thenReturn(Optional.of(assembly));
        when(repository.sumCoeficientesPropiedad(PROPERTY)).thenReturn(new BigDecimal("1.000000"));
    }

    @AfterEach
    void tearDown() {
        SaedContextHolder.clearContext();
    }

    private VotacionDTO voting(String estado, String tipo, String si, String no) {
        VotacionDTO v = new VotacionDTO();
        v.setIdVotacion(VOTING);
        v.setIdAsamblea(ASSEMBLY);
        v.setEstado(estado);
        v.setTipoMayoriaRequerida(tipo);
        v.setVotosSi(new BigDecimal(si));
        v.setVotosNo(new BigDecimal(no));
        v.setVotosBlanco(BigDecimal.ZERO);
        v.setVotosAbstencion(BigDecimal.ZERO);
        return v;
    }

    @Test
    void closingPersistsTheEvaluatedResultAfterLockingTheVoting() {
        when(repository.findVotacionById(VOTING))
                .thenReturn(Optional.of(voting("ABIERTA", "CALIFICADA_70_PCT", "0.25", "0.15")));
        when(repository.cerrarVotacion(eq(VOTING), any())).thenReturn(1);

        service.cerrarVotacion(VOTING);

        ArgumentCaptor<VotacionResultado> captor = ArgumentCaptor.forClass(VotacionResultado.class);
        var order = inOrder(repository);
        order.verify(repository).lockVotacionForUpdate(VOTING);
        order.verify(repository).cerrarVotacion(eq(VOTING), captor.capture());
        VotacionResultado stored = captor.getValue();
        assertFalse(stored.aprobada());
        assertEquals("SI", stored.opcionGanadora());
        assertEquals(0, stored.pesoTotalEmitido().compareTo(new BigDecimal("0.40")));
        assertEquals(0, stored.baseCoeficientes().compareTo(BigDecimal.ONE));
        verify(audit).recordSuccess(any(), any(), any(), eq("CERRAR_VOTACION"), eq("VOTACIONES"), eq(VOTING), any(),
                any(), eq("ABIERTA"), eq("CERRADA"));
    }

    @Test
    void anAlreadyClosedVotingIsNotClosedOrRecomputedAgain() {
        when(repository.findVotacionById(VOTING))
                .thenReturn(Optional.of(voting("CERRADA", "SIMPLE_50_MAS_1", "0.5", "0.1")));

        assertThrows(IllegalStateException.class, () -> service.cerrarVotacion(VOTING));
        verify(repository, never()).cerrarVotacion(anyLong(), any());
    }

    @Test
    void aConcurrentCloseThatLostTheRaceIsAConflict() {
        when(repository.findVotacionById(VOTING))
                .thenReturn(Optional.of(voting("ABIERTA", "SIMPLE_50_MAS_1", "0.5", "0.1")));
        when(repository.cerrarVotacion(eq(VOTING), any())).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> service.cerrarVotacion(VOTING));
        verify(audit, never()).recordSuccess(any(), any(), any(), eq("CERRAR_VOTACION"), any(), any(), any(), any(),
                any(), any());
    }

    @Test
    void theClosedVotingIsReturnedWithItsPersistedOutcome() {
        VotacionDTO open = voting("ABIERTA", "SIMPLE_50_MAS_1", "0.55", "0.15");
        VotacionDTO closed = voting("CERRADA", "SIMPLE_50_MAS_1", "0.55", "0.15");
        closed.setResultado("APROBADA");
        when(repository.findVotacionById(VOTING)).thenReturn(Optional.of(open), Optional.of(closed));
        when(repository.cerrarVotacion(eq(VOTING), any())).thenReturn(1);

        assertEquals("APROBADA", service.cerrarVotacion(VOTING).getResultado());
    }
}
