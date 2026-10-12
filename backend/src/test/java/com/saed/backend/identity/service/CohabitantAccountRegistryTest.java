package com.saed.backend.identity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.CallableStatement;
import java.sql.SQLException;
import java.sql.Types;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.CallableStatementCallback;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

/** The registry only calls the validated security-schema procedure and maps its refusals; it never writes itself. */
class CohabitantAccountRegistryTest {

    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final JdbcOperations operations = mock(JdbcOperations.class);
    private final CallableStatement statement = mock(CallableStatement.class);
    private final CohabitantAccountRegistry registry = new CohabitantAccountRegistry(jdbc);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws SQLException {
        when(jdbc.getJdbcOperations()).thenReturn(operations);
        when(statement.getLong(7)).thenReturn(901L);
        when(operations.execute(anyString(), any(CallableStatementCallback.class))).thenAnswer(invocation ->
                ((CallableStatementCallback<?>) invocation.getArgument(1)).doInCallableStatement(statement));
    }

    @Test
    void callsTheSecuritySchemaProcedureAndReturnsTheNewUserId() throws SQLException {
        Long id = registry.register(10L, 55L, null, "synthetic-user", "synthetic@example.invalid", "synthetic-hash");

        assertEquals(901L, id);
        verify(operations).execute(eq("{call SAED_SEC_MASTER.SP_REGISTRAR_CONVIVIENTE(?, ?, ?, ?, ?, ?, ?)}"),
                any(CallableStatementCallback.class));
        verify(statement).setLong(1, 10L);
        verify(statement).setLong(2, 55L);
        verify(statement).setNull(3, Types.NUMERIC);
        verify(statement).setString(4, "synthetic-user");
        verify(statement).setString(5, "synthetic@example.invalid");
        verify(statement).setString(6, "synthetic-hash");
        verify(statement).registerOutParameter(7, Types.NUMERIC);
    }

    @Test
    void anExistingAccountIsPassedAsTheTargetAndNeedsNoCredentials() throws SQLException {
        registry.register(10L, 55L, 77L, "synthetic-user", "synthetic@example.invalid", null);

        verify(statement).setLong(3, 77L);
        verify(statement).setNull(6, Types.VARCHAR);
    }

    @Test
    void aRefusedAuthorizationBecomesAccessDeniedWithoutTheDatabaseMessage() {
        when(operations.execute(anyString(), any(CallableStatementCallback.class))).thenThrow(refusal(20472));

        AccessDeniedException denied = assertThrows(AccessDeniedException.class,
                () -> registry.register(10L, 55L, null, "u", "e", "h"));
        assertTrue(!denied.getMessage().contains("ORA-"), denied.getMessage());
    }

    @Test
    void theQuotaRefusalBecomesAConflict() {
        when(operations.execute(anyString(), any(CallableStatementCallback.class))).thenThrow(refusal(20476));

        ResponseStatusException conflict = assertThrows(ResponseStatusException.class,
                () -> registry.register(10L, 55L, null, "u", "e", "h"));
        assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
    }

    @Test
    void anUnrelatedDatabaseErrorIsNotMasked() {
        UncategorizedSQLException failure = refusal(942);
        when(operations.execute(anyString(), any(CallableStatementCallback.class))).thenThrow(failure);

        assertThrows(UncategorizedSQLException.class, () -> registry.register(10L, 55L, null, "u", "e", "h"));
    }

    private static UncategorizedSQLException refusal(int code) {
        return new UncategorizedSQLException("CallableStatementCallback", "call",
                new SQLException("ORA-" + code + ": synthetic", "72000", code));
    }
}
