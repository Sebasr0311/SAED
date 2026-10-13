package com.saed.backend.identity.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
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
 * DEC-01: a person created while registering an account belongs to the organization of the server-side registration
 * scope (administrator account creation, resident registering a cohabitant), never to NULL or to a request value.
 */
class UsuarioPersonaOrganizationTest {

    private static final long CONTEXT_ORG = 10L;

    private NamedParameterJdbcTemplate jdbc;
    private CallableStatement statement;
    private UsuarioController controller;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        JdbcOperations operations = mock(JdbcOperations.class);
        statement = mock(CallableStatement.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(jdbc.getJdbcOperations()).thenReturn(operations);
        when(statement.getLong(5)).thenReturn(902L);
        when(statement.getLong(7)).thenReturn(901L);
        when(operations.execute(anyString(), any(CallableStatementCallback.class))).thenAnswer(invocation ->
                ((CallableStatementCallback<?>) invocation.getArgument(1)).doInCallableStatement(statement));
        when(encoder.encode(anyString())).thenReturn("synthetic-encoded-value");
        when(jdbc.queryForList(anyString(), anyMap())).thenAnswer(invocation ->
                ((String) invocation.getArgument(0)).contains("FROM UNIDADES")
                        ? List.of(Map.of("ID_PROPIEDAD", 100L, "ID_ORGANIZACION", CONTEXT_ORG)) : List.of());
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
    }

    @AfterEach
    void tearDown() {
        SaedContextHolder.clearContext();
    }

    private void caller(String role, String scope) {
        SaedContextHolder.setContext(SaedContext.builder().userId(20L).organizationId(CONTEXT_ORG).propertyId(100L)
                .unitId(1000L).roleCode(role).roleScope(scope).build());
    }

    /** Parameter source of the single INSERT INTO PERSONAS issued during the call. */
    private SqlParameterSource personaInsert() {
        var inserts = mockingDetails(jdbc).getInvocations().stream()
                .filter(call -> call.getMethod().getName().equals("update"))
                .filter(call -> ((String) call.getArgument(0)).toUpperCase().contains("INSERT INTO PERSONAS"))
                .toList();
        assertEquals(1, inserts.size());
        String sql = inserts.get(0).getArgument(0);
        assertTrue(sql.toUpperCase().contains("ID_ORGANIZACION"));
        return inserts.get(0).getArgument(1);
    }

    @Test
    void residentRegisteringACohabitantCreatesThePersonInTheContextOrganization() {
        caller("RESIDENTE", "UNIDAD");

        var response = controller.crearUsuario(Map.of("username", "synthetic-user", "password", "synthetic-password",
                "email", "synthetic@example.invalid", "rol", "RESIDENTE_CONVIVENCIA", "numeroDocumento", "CC123",
                "primerNombre", "Synthetic", "primerApellido", "Cohabitant", "idUnidad", 1000L));

        assertEquals(201, response.getStatusCode().value());
        assertEquals(CONTEXT_ORG, personaInsert().getValue("org"));
    }

    @Test
    void administratorRegisteringAResidentCreatesThePersonInTheContextOrganization() {
        caller("ADMIN_PROPIEDAD", "PROPIEDAD");

        var response = controller.crearUsuario(Map.of("username", "synthetic-user", "password", "synthetic-password",
                "email", "synthetic@example.invalid", "rol", "RESIDENTE", "numeroDocumento", "CC124",
                "primerNombre", "Synthetic", "primerApellido", "Resident", "idUnidad", 1000L));

        assertEquals(201, response.getStatusCode().value());
        assertEquals(CONTEXT_ORG, personaInsert().getValue("org"));
    }
}
