package com.saed.backend.person.service.impl;

import com.saed.backend.authorization.service.PropertyConfigService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.person.dto.PersonaBatchItemDTO;
import com.saed.backend.person.exception.ConvivienteLimitExceededException;
import com.saed.backend.person.repository.PersonaRepository;
import com.saed.backend.person.repository.impl.PersonaRepositoryImpl;
import com.saed.backend.person.service.ConvivienteQuotaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F05-02 / F5-H09: the conviviente quota is checked after locking the unit row (parallel additions serialize on
 * that lock), fails closed without a tenant context or for SUPERADMIN, and cannot be bypassed by the bulk import.
 */
class ConvivienteQuotaLockingTest {

    private static final Long UNIT = 100L;
    private static final Long PROP = 10L;

    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final PropertyConfigService config = mock(PropertyConfigService.class);
    private final ConvivienteQuotaServiceImpl service = new ConvivienteQuotaServiceImpl(jdbc, config);

    @AfterEach
    void tearDown() {
        SaedContextHolder.clearContext();
    }

    private static void titular() {
        SaedContextHolder.setContext(SaedContext.builder().userId(7L).roleCode("RESIDENTE").roleScope("UNIDAD")
                .organizationId(1L).propertyId(PROP).unitId(UNIT).build());
    }

    private void stubLockAndCount(int active) {
        when(jdbc.queryForObject(argThat((String s) -> s != null && s.contains("FOR UPDATE")),
                any(SqlParameterSource.class), eq(Long.class))).thenReturn(PROP);
        when(jdbc.queryForObject(argThat((String s) -> s != null && s.contains("COUNT(1)") && s.contains("CONVIVIENTE")),
                any(SqlParameterSource.class), eq(Integer.class))).thenReturn(active);
        when(config.getIntValue(eq(PROP), anyString(), anyInt())).thenReturn(4);
    }

    @Test
    void unitRowIsLockedBeforeActiveConvivientesAreCounted() {
        titular();
        stubLockAndCount(3);

        service.validateAndLockQuota(UNIT);

        InOrder order = inOrder(jdbc);
        order.verify(jdbc).queryForObject(argThat((String s) -> s.contains("FOR UPDATE")),
                any(SqlParameterSource.class), eq(Long.class));
        order.verify(jdbc).queryForObject(argThat((String s) -> s.contains("COUNT(1)")),
                any(SqlParameterSource.class), eq(Integer.class));
    }

    @Test
    void quotaAtLimitIsRejected() {
        titular();
        stubLockAndCount(4);
        assertThrows(ConvivienteLimitExceededException.class, () -> service.validateAndLockQuota(UNIT));
    }

    @Test
    void missingContextAndSuperadminAreDenied() {
        assertThrows(AccessDeniedException.class, () -> service.validateAndLockQuota(UNIT));
        SaedContextHolder.setContext(SaedContext.builder().userId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());
        assertThrows(AccessDeniedException.class, () -> service.validateAndLockQuota(UNIT));
        assertThrows(AccessDeniedException.class, () -> service.getQuota(UNIT));
    }

    @Test
    void propertyAdminWithoutPropertyInContextIsDenied() {
        SaedContextHolder.setContext(SaedContext.builder().userId(3L).roleCode("ADMIN_PROPIEDAD").roleScope("PROPIEDAD")
                .organizationId(1L).build());
        when(jdbc.queryForObject(argThat((String s) -> s != null && s.contains("FOR UPDATE")),
                any(SqlParameterSource.class), eq(Long.class))).thenReturn(PROP);
        assertThrows(AccessDeniedException.class, () -> service.validateAndLockQuota(UNIT));
    }

    @Test
    void bulkImportOfAConvivienteGoesThroughTheQuota() {
        SaedContextHolder.setContext(SaedContext.builder().userId(3L).roleCode("ADMIN_PROPIEDAD").roleScope("PROPIEDAD")
                .organizationId(1L).propertyId(PROP).build());
        PersonaRepository repo = mock(PersonaRepository.class);
        ConvivienteQuotaService quota = mock(ConvivienteQuotaService.class);
        when(repo.findTipoDocumentoIdByCodigo(anyString())).thenReturn(Optional.of(1L));
        when(repo.findByNumeroDocumento("444")).thenReturn(Optional.empty());
        when(repo.insert(any())).thenReturn(44L);
        when(repo.findUnidadIdByNumero(PROP, "101")).thenReturn(Optional.of(UNIT));
        doThrow(new ConvivienteLimitExceededException(UNIT, 4, 4)).when(quota).validateAndLockQuota(UNIT);

        PersonaServiceImpl personas = new PersonaServiceImpl(repo, quota);
        var result = personas.importarBatch(List.of(
                new PersonaBatchItemDTO("CC", "444", "Ana", "Paz", null, null, "101", "CONVIVIENTE")));

        verify(repo, never()).asignarUnidad(anyLong(), anyLong(), anyString());
        assertEquals(1, result.fallidos());
    }

    @Test
    void bulkImportKeepsTheConvivienteTypeInsteadOfRelabelingItFamiliar() {
        NamedParameterJdbcTemplate named = mock(NamedParameterJdbcTemplate.class);
        when(named.queryForObject(anyString(), any(SqlParameterSource.class), eq(Integer.class))).thenReturn(0);
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        when(named.update(argThat((String s) -> s.startsWith("INSERT INTO RESIDENTES_UNIDAD")), params.capture()))
                .thenReturn(1);

        new PersonaRepositoryImpl(named).asignarUnidad(44L, UNIT, "CONVIVIENTE");

        assertEquals("CONVIVIENTE", params.getValue().getValue("tipoResidente"));
    }
}
