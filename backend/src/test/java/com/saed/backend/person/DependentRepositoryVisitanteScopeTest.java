package com.saed.backend.person;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.person.dto.VisitanteDTO;
import com.saed.backend.person.dto.VisitanteRequestDTO;
import com.saed.backend.person.repository.impl.DependentRepositoryImpl;
import java.sql.CallableStatement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.CallableStatementCallback;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.security.access.AccessDeniedException;

/**
 * A visitor pre-registered by a resident or an administrator is created (or found) by the security-schema resolver
 * V5.67, never by an application INSERT: the person may already be a visitor the caller cannot see (global unique key).
 */
class DependentRepositoryVisitanteScopeTest {

    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final JdbcOperations operations = mock(JdbcOperations.class);
    private final CallableStatement statement = mock(CallableStatement.class);
    private final DependentRepositoryImpl repository = new DependentRepositoryImpl(jdbc);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        when(jdbc.getJdbcOperations()).thenReturn(operations);
        when(statement.getLong(5)).thenReturn(130L);
        when(operations.execute(anyString(), any(CallableStatementCallback.class))).thenAnswer(invocation ->
                ((CallableStatementCallback<?>) invocation.getArgument(1)).doInCallableStatement(statement));
    }

    @AfterEach
    void clear() {
        SaedContextHolder.clearContext();
    }

    private static VisitanteRequestDTO request() {
        return new VisitanteRequestDTO(55L, "S", "Empresa X", null, "Visita tecnica", "ACTIVO");
    }

    @Test
    void theVisitorIsResolvedThroughTheProcedureAndThenCompleted() throws Exception {
        SaedContextHolder.setContext(SaedContext.builder().userId(7L).organizationId(2L).propertyId(3L)
                .roleCode("RESIDENTE").build());

        VisitanteDTO visitor = repository.createVisitante(request());

        assertEquals(130L, visitor.id());
        verify(statement).setLong(2, 55L);
        verify(statement).setString(3, "S");
        verify(statement).setString(4, "Empresa X");
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).update(sql.capture(), params.capture());
        assertTrue(sql.getValue().startsWith("UPDATE VISITANTES"), sql.getValue());
        assertEquals(130L, params.getValue().getValue("id"));
    }

    @Test
    void withoutATenantContextNothingIsResolved() throws Exception {
        assertThrows(AccessDeniedException.class, () -> repository.createVisitante(request()));
        SaedContextHolder.setContext(SaedContext.builder().userId(7L).roleCode("RESIDENTE").build());
        assertThrows(AccessDeniedException.class, () -> repository.createVisitante(request()));
        verify(operations, never()).execute(anyString(), any(CallableStatementCallback.class));
    }
}
