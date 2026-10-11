package com.saed.backend.finanzas;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.finanzas.dto.CoarrendatarioCreateDTO;
import com.saed.backend.finanzas.repository.CoarrendatarioRepository;
import com.saed.backend.finanzas.service.impl.ContratoParticipanteServiceImpl;
import com.saed.backend.person.service.ConvivienteQuotaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Contract participants: no SUPERADMIN elevation, missing and foreign contracts are indistinguishable (404), and
 * co-tenants must belong to the contract's organization. No database.
 */
class ContractParticipantScopeTest {
    private static final long ORG = 10L;
    private static final long PROP = 100L;
    private static final long CONTRACT = 900L;

    private NamedParameterJdbcTemplate jdbc;
    private JdbcOperations ops;
    private CoarrendatarioRepository repo;
    private ContratoParticipanteServiceImpl service;
    private String roleSeenByQuery;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        ops = mock(JdbcOperations.class);
        when(jdbc.getJdbcOperations()).thenReturn(ops);
        repo = mock(CoarrendatarioRepository.class);
        service = new ContratoParticipanteServiceImpl(repo, mock(ConvivienteQuotaService.class), jdbc);
        SaedContextHolder.setContext(SaedContext.builder().userId(3L).organizationId(ORG).propertyId(PROP)
                .roleCode("ADMIN_PROPIEDAD").roleScope("PROPIEDAD").build());
    }

    @AfterEach
    void tearDown() {
        SaedContextHolder.clearContext();
    }

    private void contractIn(long property, long org) {
        when(jdbc.queryForList(contains("FROM CONTRATOS c"), anyMap())).thenAnswer(inv -> {
            roleSeenByQuery = SaedContextHolder.getContext().getRoleCode();
            return List.<Map<String, Object>>of(Map.of("ID_CONTRATO", CONTRACT, "ID_UNIDAD", 1L, "ESTADO", "ACTIVO",
                    "ID_PROPIEDAD", property, "ID_ORGANIZACION", org));
        });
    }

    @Test
    void lookupRunsInTheCallersContextAndNeverElevates() {
        contractIn(PROP, ORG);
        when(repo.listarPorContrato(CONTRACT)).thenReturn(List.of());

        service.listarCoarrendatarios(CONTRACT);

        assertEquals("ADMIN_PROPIEDAD", roleSeenByQuery);
        verify(ops, never()).execute(anyString());
    }

    @Test
    void foreignAndMissingContractsGiveTheSameAnswer() {
        when(jdbc.queryForList(contains("FROM CONTRATOS c"), anyMap())).thenReturn(List.of());
        NoSuchElementException missing = assertThrows(NoSuchElementException.class,
                () -> service.listarCoarrendatarios(CONTRACT));

        contractIn(PROP + 1, ORG);
        NoSuchElementException foreign = assertThrows(NoSuchElementException.class,
                () -> service.listarCoarrendatarios(CONTRACT));

        assertEquals(missing.getMessage(), foreign.getMessage());
        verify(repo, never()).listarPorContrato(any());
    }

    @Test
    void superAdminIsDeniedBeforeAnyLookup() {
        SaedContextHolder.setContext(SaedContext.builder().userId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());
        assertThrows(AccessDeniedException.class, () -> service.listarCoarrendatarios(CONTRACT));
        verifyNoInteractions(jdbc);
    }

    @Test
    void anAdministratorWithoutScopeIdsFailsClosed() {
        SaedContextHolder.setContext(SaedContext.builder().userId(3L).roleCode("ADMIN_PROPIEDAD")
                .roleScope("PROPIEDAD").build());
        contractIn(PROP, ORG);
        assertThrows(NoSuchElementException.class, () -> service.listarCoarrendatarios(CONTRACT));
    }

    @Test
    void aCoTenantOfAnotherOrganizationIsNotFound() {
        contractIn(PROP, ORG);
        when(jdbc.queryForObject(contains("FROM PERSONAS"), anyMap(), org.mockito.ArgumentMatchers.<Class<Integer>>any()))
                .thenReturn(0);

        assertThrows(NoSuchElementException.class, () -> service.agregarCoarrendatario(
                new CoarrendatarioCreateDTO(CONTRACT, 777L, null, null, null)));
        verify(repo, never()).crear(any());
    }
}
