package com.saed.backend.person;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saed.backend.person.service.VisitorIdentityRegistry;
import java.sql.CallableStatement;
import java.sql.SQLException;
import java.sql.Types;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.CallableStatementCallback;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

/** Visitor registration is idempotent and tenant-correct only through the security-schema procedure (V5.67). */
class VisitorIdentityRegistryTest {

    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final JdbcOperations operations = mock(JdbcOperations.class);
    private final CallableStatement statement = mock(CallableStatement.class);
    private final VisitorIdentityRegistry registry = new VisitorIdentityRegistry(jdbc);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws SQLException {
        when(jdbc.getJdbcOperations()).thenReturn(operations);
        when(statement.getLong(5)).thenReturn(130L);
        when(operations.execute(anyString(), any(CallableStatementCallback.class))).thenAnswer(invocation ->
                ((CallableStatementCallback<?>) invocation.getArgument(1)).doInCallableStatement(statement));
    }

    @Test
    void resolvesAPersonThroughTheProcedureAndReturnsOnlyTheId() throws SQLException {
        Long id = registry.resolveByPerson(55L, true, "Acme");

        assertEquals(130L, id);
        verify(operations).execute(eq("{call SAED_SEC_MASTER.SP_RESOLVER_VISITANTE(?, ?, ?, ?, ?)}"),
                any(CallableStatementCallback.class));
        verify(statement).setNull(1, Types.NUMERIC);
        verify(statement).setLong(2, 55L);
        verify(statement).setString(3, "S");
        verify(statement).setString(4, "Acme");
        verify(statement).registerOutParameter(5, Types.NUMERIC);
    }

    @Test
    void claimingAnUnclaimedVisitorPassesTheVisitorIdOnly() throws SQLException {
        registry.claimUnclaimed(130L);

        verify(statement).setLong(1, 130L);
        verify(statement).setNull(2, Types.NUMERIC);
        verify(statement).setString(3, "N");
        verify(statement).setNull(4, Types.VARCHAR);
    }

    @Test
    void anUnresolvableVisitorIsNull() throws SQLException {
        when(statement.wasNull()).thenReturn(true);

        assertNull(registry.claimUnclaimed(130L));
    }

    @Test
    @SuppressWarnings("unchecked")
    void aRefusedContextBecomesAccessDeniedWithoutTheDatabaseMessage() {
        when(operations.execute(anyString(), any(CallableStatementCallback.class))).thenThrow(
                new UncategorizedSQLException("task", "call", new SQLException("ORA-20482: synthetic", "72000", 20482)));

        AccessDeniedException denied = assertThrows(AccessDeniedException.class,
                () -> registry.resolveByPerson(55L, false, null));
        assertEquals(false, denied.getMessage().contains("ORA-"));
    }
}
