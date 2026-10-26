package com.saed.backend.asambleas.service.impl;

import com.saed.backend.asambleas.dto.AsambleaDTO;
import com.saed.backend.asambleas.dto.VotacionDTO;
import com.saed.backend.asambleas.repository.AsambleaRepository;
import com.saed.backend.audit.AuditService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F10-10 / F10-12: a power is decided once and only through the assembly that owns it; a closed voting cannot be
 * annulled. Pure Mockito, no database.
 */
class PoderDecisionAndAnnulmentTest {

    private static final Long ASSEMBLY = 20L;
    private static final Long PROPERTY = 30L;
    private static final Long POWER = 5L;

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
    }

    @AfterEach
    void tearDown() {
        SaedContextHolder.clearContext();
    }

    @Test
    void aPendingPowerOfTheAssemblyIsDecided() {
        when(repository.decidirPoder(ASSEMBLY, POWER, "APROBADO", 60L)).thenReturn(1);

        assertEquals("APROBADO", service.decidirPoder(ASSEMBLY, POWER, "APROBADO").getEstado());
    }

    @Test
    void aPowerOfAnotherAssemblyIsNotFound() {
        when(repository.decidirPoder(eq(ASSEMBLY), eq(POWER), anyString(), anyLong())).thenReturn(0);
        when(repository.existePoder(ASSEMBLY, POWER)).thenReturn(false);

        assertThrows(NoSuchElementException.class, () -> service.decidirPoder(ASSEMBLY, POWER, "APROBADO"));
        verify(audit, never()).recordSuccess(any(), any(), any(), anyString(), anyString(), any(), anyString(), anyString(),
                anyString(), anyString());
    }

    @Test
    void anAlreadyDecidedPowerCannotBeDecidedAgain() {
        when(repository.decidirPoder(eq(ASSEMBLY), eq(POWER), anyString(), anyLong())).thenReturn(0);
        when(repository.existePoder(ASSEMBLY, POWER)).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> service.decidirPoder(ASSEMBLY, POWER, "RECHAZADO"));
    }

    @Test
    void anAssemblyOutsideTheCallersScopeIsNotReachable() {
        AsambleaDTO foreign = new AsambleaDTO();
        foreign.setIdAsamblea(ASSEMBLY);
        foreign.setIdPropiedad(PROPERTY + 1);
        foreign.setIdOrganizacion(41L);
        foreign.setEstado("EN_CURSO");
        when(repository.findById(ASSEMBLY)).thenReturn(Optional.of(foreign));

        assertThrows(RuntimeException.class, () -> service.decidirPoder(ASSEMBLY, POWER, "APROBADO"));
        verify(repository, never()).decidirPoder(any(), any(), anyString(), any());
    }

    @Test
    void anInvalidDecisionIsRejectedBeforeTouchingTheRepository() {
        assertThrows(IllegalArgumentException.class, () -> service.decidirPoder(ASSEMBLY, POWER, "PENDIENTE_REVISION"));
        verify(repository, never()).decidirPoder(any(), any(), anyString(), any());
    }

    @Test
    void aClosedVotingCannotBeAnnulled() {
        VotacionDTO voting = new VotacionDTO();
        voting.setIdVotacion(10L);
        voting.setIdAsamblea(ASSEMBLY);
        voting.setEstado("CERRADA");
        when(repository.findVotacionById(10L)).thenReturn(Optional.of(voting));

        assertThrows(IllegalStateException.class, () -> service.anularVotacion(10L));
        verify(repository, never()).anularVotacion(anyLong());
    }

    @Test
    void anOpenVotingCanStillBeAnnulled() {
        VotacionDTO voting = new VotacionDTO();
        voting.setIdVotacion(10L);
        voting.setIdAsamblea(ASSEMBLY);
        voting.setEstado("ABIERTA");
        when(repository.findVotacionById(10L)).thenReturn(Optional.of(voting));

        service.anularVotacion(10L);

        verify(repository).anularVotacion(10L);
    }
}
