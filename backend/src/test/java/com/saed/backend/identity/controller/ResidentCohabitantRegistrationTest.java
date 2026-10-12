package com.saed.backend.identity.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saed.backend.common.service.EmailService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.person.service.ConvivienteQuotaService;
import com.saed.backend.platform.service.PlanLimitService;
import java.sql.CallableStatement;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.CallableStatementCallback;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * A resident cannot insert USUARIOS / USUARIO_ASIGNACIONES rows (their policies only admit the caller's own user id)
 * and USUARIOS visibility must stay narrow, so the cohabitant account is created by the validated security-schema
 * procedure, never by direct inserts from the resident session.
 */
class ResidentCohabitantRegistrationTest {

    private NamedParameterJdbcTemplate jdbc;
    private JdbcOperations operations;
    private CallableStatement statement;
    private UsuarioController controller;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        operations = mock(JdbcOperations.class);
        statement = mock(CallableStatement.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(jdbc.getJdbcOperations()).thenReturn(operations);
        when(statement.getLong(7)).thenReturn(901L);
        when(operations.execute(anyString(), any(CallableStatementCallback.class))).thenAnswer(invocation ->
                ((CallableStatementCallback<?>) invocation.getArgument(1)).doInCallableStatement(statement));
        when(encoder.encode(anyString())).thenReturn("synthetic-encoded-value");
        when(jdbc.queryForList(anyString(), anyMap())).thenAnswer(invocation ->
                ((String) invocation.getArgument(0)).contains("FROM UNIDADES")
                        ? List.of(Map.of("ID_PROPIEDAD", 100L, "ID_ORGANIZACION", 10L)) : List.of());
        when(jdbc.query(anyString(), anyMap(), any(RowMapper.class))).thenAnswer(invocation ->
                ((String) invocation.getArgument(0)).contains("FROM ROLES") ? List.of(5L) : List.of());
        when(jdbc.update(anyString(), any(SqlParameterSource.class), any(KeyHolder.class), any(String[].class)))
                .thenAnswer(invocation -> {
                    KeyHolder keys = invocation.getArgument(2);
                    keys.getKeyList().add(Map.of(((String[]) invocation.getArgument(3))[0], 55L));
                    return 1;
                });
        when(jdbc.queryForObject(anyString(), anyMap(), eq(Integer.class))).thenReturn(0);
        controller = new UsuarioController(jdbc, encoder, mock(ObjectProvider.class), mock(EmailService.class),
                mock(ConvivienteQuotaService.class), mock(PlanLimitService.class));
        SaedContextHolder.setContext(SaedContext.builder().userId(20L).organizationId(10L).propertyId(100L)
                .unitId(1000L).roleCode("RESIDENTE").roleScope("UNIDAD").build());
    }

    @AfterEach
    void tearDown() {
        SaedContextHolder.clearContext();
    }

    @Test
    void theResidentSessionCreatesTheCohabitantAccountOnlyThroughTheSecurityProcedure() {
        var response = controller.crearUsuario(Map.of("username", "synthetic-user", "password", "synthetic-password",
                "email", "synthetic@example.invalid", "rol", "RESIDENTE_CONVIVENCIA", "numeroDocumento", "CC123",
                "primerNombre", "Synthetic", "primerApellido", "Cohabitant", "idUnidad", 1000L));

        assertEquals(201, response.getStatusCode().value());
        assertEquals(901L, response.getBody().getData().get("idUsuario"));
        verify(operations).execute(contains("SAED_SEC_MASTER.SP_REGISTRAR_CONVIVIENTE"),
                any(CallableStatementCallback.class));
        assertTrue(mockingDetails(jdbc).getInvocations().stream()
                .filter(call -> call.getMethod().getName().equals("update"))
                .map(call -> ((String) call.getArgument(0)).toUpperCase())
                .noneMatch(sql -> sql.contains("INSERT INTO USUARIOS") || sql.contains("INSERT INTO USUARIO_ASIGNACIONES")));
    }
}
