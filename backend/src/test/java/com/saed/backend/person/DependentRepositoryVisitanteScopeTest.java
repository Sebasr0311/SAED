package com.saed.backend.person;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.person.dto.VisitanteRequestDTO;
import com.saed.backend.person.repository.impl.DependentRepositoryImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.access.AccessDeniedException;

/**
 * A visitor pre-registered by a resident or an administrator has no visit yet, so the VISITANTES row-level policy
 * (V5.64) can only admit the insert when the row itself carries the context property and the registrant.
 */
class DependentRepositoryVisitanteScopeTest {

    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final DependentRepositoryImpl repository = new DependentRepositoryImpl(jdbc);

    @AfterEach
    void clear() {
        SaedContextHolder.clearContext();
    }

    private static VisitanteRequestDTO request() {
        return new VisitanteRequestDTO(55L, "N", "Empresa X", null, "Visita tecnica", "ACTIVO");
    }

    @Test
    void insertCarriesTheContextPropertyAndTheRegistrant() {
        SaedContextHolder.setContext(SaedContext.builder().userId(7L).organizationId(2L).propertyId(3L)
                .roleCode("RESIDENTE").build());

        repository.createVisitante(request());

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).update(sql.capture(), params.capture(), any(KeyHolder.class), any(String[].class));
        String statement = sql.getValue().toLowerCase();
        assertTrue(statement.contains("id_propiedad") && statement.contains("registrado_por"), statement);
        assertEquals(3L, params.getValue().getValue("propiedadId"));
        assertEquals(7L, params.getValue().getValue("registradoPor"));
    }

    @Test
    void withoutATenantContextNothingIsInserted() {
        assertThrows(AccessDeniedException.class, () -> repository.createVisitante(request()));
        SaedContextHolder.setContext(SaedContext.builder().userId(7L).roleCode("RESIDENTE").build());
        assertThrows(AccessDeniedException.class, () -> repository.createVisitante(request()));
        verify(jdbc, never()).update(anyString(), any(SqlParameterSource.class), any(KeyHolder.class), any(String[].class));
    }
}
