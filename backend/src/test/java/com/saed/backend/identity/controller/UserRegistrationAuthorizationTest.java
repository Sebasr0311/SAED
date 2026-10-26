package com.saed.backend.identity.controller;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.identity.service.TokenActivacionService;
import com.saed.backend.person.service.ConvivienteQuotaService;
import com.saed.backend.platform.service.PlanLimitService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserRegistrationAuthorizationTest {
    private NamedParameterJdbcTemplate jdbc;
    private JdbcOperations operations;
    private PasswordEncoder encoder;
    private ConvivienteQuotaService quota;
    private UsuarioController controller;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        operations = mock(JdbcOperations.class);
        encoder = mock(PasswordEncoder.class);
        quota = mock(ConvivienteQuotaService.class);
        when(jdbc.getJdbcOperations()).thenReturn(operations);
        controller = new UsuarioController(jdbc, encoder, tokenProvider(),
                quota, mock(PlanLimitService.class));
        SaedContextHolder.setContext(SaedContext.builder().userId(20L).organizationId(10L)
                .propertyId(100L).unitId(1000L).roleCode("ADMIN_PROPIEDAD").build());
    }

    @AfterEach
    void tearDown() { SaedContextHolder.clearContext(); }

    private Map<String, Object> payload() {
        return Map.of("username", "synthetic-user", "password", "synthetic-password",
                "email", "synthetic@example.invalid", "rol", "RESIDENTE", "idPersona", 55L,
                "idUnidad", 1000L);
    }

    private void existingAccount(String state, String role) {
        when(jdbc.queryForList(anyString(), anyMap())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("FROM USUARIOS WHERE ID_PERSONA")) {
                return List.of(Map.of("ID_USUARIO", 77L, "NOMBRE_USUARIO", "original-user",
                        "EMAIL", "original@example.invalid", "ESTADO", state));
            }
            if (sql.contains("JOIN ROLES")) {
                return List.of(Map.of("ID_ASIGNACION", 88L, "ID_UNIDAD", 1000L,
                        "ID_PROPIEDAD", 100L, "ID_ORGANIZACION", 10L, "ROL_CODIGO", role));
            }
            return List.of(Map.of("ID_PROPIEDAD", 100L, "ID_ORGANIZACION", 10L));
        });
        when(jdbc.queryForObject(anyString(), anyMap(), eq(Integer.class))).thenReturn(1);
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Integer.class))).thenReturn(1);
    }

    private void noWrites() {
        assertTrue(mockingDetails(jdbc).getInvocations().stream()
                .noneMatch(call -> call.getMethod().getName().equals("update")));
        verifyNoInteractions(encoder);
        verify(operations, never()).execute(anyString());
    }

    @Test
    void missingContextFailsClosedBeforeJdbc() {
        SaedContextHolder.clearContext();
        assertThrows(AccessDeniedException.class, () -> controller.crearUsuario(payload()));
        noWrites();
    }

    @Test
    void administratorCannotRegisterPrivilegedIdentity() {
        existingAccount("ACTIVO", "SUPERADMIN");
        assertThrows(AccessDeniedException.class, () -> controller.crearUsuario(payload()));
        noWrites();
    }

    @Test
    void registrationCannotReactivateInactiveIdentity() {
        existingAccount("INACTIVO", "RESIDENTE");
        assertThrows(AccessDeniedException.class, () -> controller.crearUsuario(payload()));
        noWrites();
    }

    @Test
    void foreignUnitCannotRealignCallerTenant() {
        when(jdbc.queryForList(anyString(), anyMap())).thenReturn(
                List.of(Map.of("ID_PROPIEDAD", 900L, "ID_ORGANIZACION", 90L)));
        assertThrows(AccessDeniedException.class, () -> controller.crearUsuario(payload()));
        noWrites();
    }

    @Test
    void existingPersonRequiresExplicitTenantRelationship() {
        when(jdbc.queryForList(anyString(), anyMap())).thenReturn(
                List.of(Map.of("ID_PROPIEDAD", 100L, "ID_ORGANIZACION", 10L)));
        when(jdbc.queryForObject(anyString(), anyMap(), eq(Integer.class))).thenReturn(0);
        assertThrows(AccessDeniedException.class, () -> controller.crearUsuario(payload()));
        noWrites();
    }

    @Test
    void httpRequestCannotUseMissingTenantContext() throws Exception {
        SaedContextHolder.clearContext();
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new DenialAdvice()).build();
        mvc.perform(post("/api/v1/usuarios").contentType("application/json")
                .content("{\"username\":\"synthetic\",\"rol\":\"RESIDENTE\"}"))
                .andExpect(status().isForbidden());
        noWrites();
    }

    @Test
    @SuppressWarnings("unchecked")
    void activeScopedIdentityKeepsCredentialsAndOtherAssignments() {
        existingAccount("ACTIVO", "RESIDENTE");
        when(jdbc.query(anyString(), anyMap(), any(RowMapper.class))).thenAnswer(invocation ->
                ((String) invocation.getArgument(0)).contains("FROM ROLES") ? List.of(5L) : List.of());
        SaedContext original = SaedContextHolder.getContext();
        var response = controller.crearUsuario(payload());
        assertEquals(201, response.getStatusCode().value());
        assertSame(original, SaedContextHolder.getContext());
        assertTrue(mockingDetails(jdbc).getInvocations().stream()
                .filter(call -> call.getMethod().getName().equals("update"))
                .noneMatch(call -> ((String) call.getArgument(0)).contains("UPDATE USUARIOS")
                        || ((String) call.getArgument(0)).contains("UPDATE PERSONAS")
                        || ((String) call.getArgument(0)).contains("UPDATE USUARIO_ASIGNACIONES")
                        || ((String) call.getArgument(0)).contains("UPDATE RESIDENTES_UNIDAD")));
        verifyNoInteractions(encoder);
        verify(operations, never()).execute(anyString());
        assertFalse(response.getBody().getData().containsKey("passwordGenerada"));
        assertEquals(false, response.getBody().getData().get("accountCreated"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void validScopedNewIdentityCanBeRegistered() throws Exception {
        when(jdbc.queryForList(anyString(), anyMap())).thenAnswer(invocation ->
                ((String) invocation.getArgument(0)).contains("FROM UNIDADES")
                        ? List.of(Map.of("ID_PROPIEDAD", 100L, "ID_ORGANIZACION", 10L)) : List.of());
        when(jdbc.query(anyString(), anyMap(), any(RowMapper.class))).thenAnswer(invocation ->
                ((String) invocation.getArgument(0)).contains("FROM ROLES") ? List.of(5L) : List.of());
        when(jdbc.update(anyString(), any(SqlParameterSource.class), any(KeyHolder.class), any(String[].class)))
                .thenAnswer(invocation -> {
                    KeyHolder keys = invocation.getArgument(2);
                    String[] columns = invocation.getArgument(3);
                    keys.getKeyList().add(Map.of(columns[0], columns[0].equals("ID_PERSONA") ? 55L : 77L));
                    return 1;
                });
        when(encoder.encode(anyString())).thenReturn("synthetic-encoded-value");
        when(jdbc.queryForObject(anyString(), anyMap(), eq(Integer.class))).thenReturn(0);
        accountProcedureReturnsUserId(77L);
        var fresh = new java.util.HashMap<>(payload());
        fresh.remove("idPersona");
        var response = controller.crearUsuario(fresh);
        assertEquals(201, response.getStatusCode().value());
        verify(encoder).encode(anyString());
        assertFalse(insertedUsuariosDirectly(), "the account row must come from the security procedure");
        verify(operations, never()).execute(anyString());
        assertEquals("ADMIN_PROPIEDAD", SaedContextHolder.getContext().getRoleCode());
        assertEquals(true, response.getBody().getData().get("accountCreated"));
    }

    /** V5.68: the account row of a new identity is created by the security-schema procedure (output index 5). */
    @SuppressWarnings("unchecked")
    private void accountProcedureReturnsUserId(long userId) throws Exception {
        java.sql.CallableStatement statement = mock(java.sql.CallableStatement.class);
        when(statement.getLong(5)).thenReturn(userId);
        when(operations.execute(anyString(), any(org.springframework.jdbc.core.CallableStatementCallback.class)))
                .thenAnswer(invocation -> ((org.springframework.jdbc.core.CallableStatementCallback<?>)
                        invocation.getArgument(1)).doInCallableStatement(statement));
    }

    private boolean insertedUsuariosDirectly() {
        return mockingDetails(jdbc).getInvocations().stream()
                .filter(call -> call.getMethod().getName().equals("update"))
                .anyMatch(call -> ((String) call.getArgument(0)).contains("INSERT INTO USUARIOS "));
    }

    @SuppressWarnings("unchecked")
    private void freshRegistration() throws Exception {
        when(jdbc.queryForList(anyString(), anyMap())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            return sql.contains("FROM UNIDADES") || sql.contains("FROM PROPIEDADES")
                    ? List.of(Map.of("ID_PROPIEDAD", 100L, "ID_ORGANIZACION", 10L)) : List.of();
        });
        when(jdbc.query(anyString(), anyMap(), any(RowMapper.class))).thenAnswer(invocation ->
                ((String) invocation.getArgument(0)).contains("FROM ROLES") ? List.of(5L) : List.of());
        when(jdbc.update(anyString(), any(SqlParameterSource.class), any(KeyHolder.class), any(String[].class)))
                .thenAnswer(invocation -> {
                    KeyHolder keys = invocation.getArgument(2);
                    String[] columns = invocation.getArgument(3);
                    keys.getKeyList().add(Map.of(columns[0], columns[0].equals("ID_PERSONA") ? 55L : 77L));
                    return 1;
                });
        when(encoder.encode(anyString())).thenReturn("synthetic-encoded-value");
        when(jdbc.queryForObject(anyString(), anyMap(), eq(Integer.class))).thenReturn(0);
        accountProcedureReturnsUserId(77L);
    }

    @Test
    void residentCanRegisterConvivienteWithOwnUnitQuotaAndPreservedContext() throws Exception {
        freshRegistration();
        // V5.66: the resident session creates the account only through the security-schema procedure.
        java.sql.CallableStatement statement = mock(java.sql.CallableStatement.class);
        when(statement.getLong(7)).thenReturn(77L);
        when(operations.execute(anyString(), any(org.springframework.jdbc.core.CallableStatementCallback.class)))
                .thenAnswer(invocation -> ((org.springframework.jdbc.core.CallableStatementCallback<?>)
                        invocation.getArgument(1)).doInCallableStatement(statement));
        SaedContext original = SaedContextHolder.getContext();
        original.setRoleCode("RESIDENTE");
        var request = new java.util.HashMap<>(payload());
        request.remove("idPersona");
        var response = controller.crearUsuario(request);
        assertEquals(201, response.getStatusCode().value());
        assertEquals("RESIDENTE_CONVIVENCIA", response.getBody().getData().get("rol"));
        verify(quota).validateAndLockQuota(1000L);
        assertSame(original, SaedContextHolder.getContext());
        assertEquals("RESIDENTE", original.getRoleCode());
        assertTrue(mockingDetails(jdbc).getInvocations().stream()
                .anyMatch(call -> call.getMethod().getName().equals("update")
                        && ((String) call.getArgument(0)).startsWith("INSERT INTO RESIDENTES_UNIDAD")
                        && "CONVIVIENTE".equals(((Map<?, ?>) call.getArgument(1)).get("tipo"))));
        verify(operations, never()).execute(anyString());
    }

    private String residenceTypeStoredFor(Map<String, Object> request) throws Exception {
        freshRegistration();
        var response = controller.crearUsuario(request);
        assertEquals(201, response.getStatusCode().value());
        return mockingDetails(jdbc).getInvocations().stream()
                .filter(call -> call.getMethod().getName().equals("update")
                        && ((String) call.getArgument(0)).startsWith("INSERT INTO RESIDENTES_UNIDAD"))
                .map(call -> (String) ((Map<?, ?>) call.getArgument(1)).get("tipo"))
                .findFirst().orElseThrow();
    }

    @Test
    void registeredResidentStoresADomainResidenceTypeNeverTitular() throws Exception {
        var request = new java.util.HashMap<>(payload());
        request.remove("idPersona");
        // Documented default (DEC-12): a RESIDENTE registered without a relation is the owner.
        assertEquals("PROPIETARIO", residenceTypeStoredFor(request));
    }

    @Test
    void registeredResidentTakesTheRelationFromTheRequest() throws Exception {
        var request = new java.util.HashMap<>(payload());
        request.remove("idPersona");
        request.put("tipoResidente", "arrendatario");
        assertEquals("ARRENDATARIO", residenceTypeStoredFor(request));
    }

    @Test
    void residentRelationOutsideTheDomainIsRejectedBeforeAnyWrite() throws Exception {
        freshRegistration();
        for (String invalid : List.of("TITULAR", "CONVIVIENTE", "FAMILIAR", "x")) {
            var request = new java.util.HashMap<>(payload());
            request.remove("idPersona");
            request.put("tipoResidente", invalid);
            assertEquals(400, controller.crearUsuario(request).getStatusCode().value(), invalid);
        }
        noWrites();
    }

    @Test
    void propertyAdminCanRegisterPorteroWithoutUnit() throws Exception {
        freshRegistration();
        var request = new java.util.HashMap<>(payload());
        request.remove("idPersona");
        request.remove("idUnidad");
        request.put("rol", "PORTERO");
        assertEquals(201, controller.crearUsuario(request).getStatusCode().value());
        assertFalse(insertedUsuariosDirectly(), "the account row must come from the security procedure");
        verify(operations).execute(contains("SP_REGISTRAR_CUENTA"),
                any(org.springframework.jdbc.core.CallableStatementCallback.class));
        verifyNoInteractions(quota);
        assertTrue(mockingDetails(jdbc).getInvocations().stream()
                .anyMatch(call -> call.getMethod().getName().equals("update")
                        && ((String) call.getArgument(0)).contains("INSERT INTO USUARIO_ASIGNACIONES")
                        && ((SqlParameterSource) call.getArgument(1)).getValue("idUnidad") == null
                        && Long.valueOf(100L).equals(((SqlParameterSource) call.getArgument(1)).getValue("propId"))
                        && Long.valueOf(10L).equals(((SqlParameterSource) call.getArgument(1)).getValue("orgId"))));
        verify(operations, never()).execute(anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void identicalActiveAssignmentIsReusedWithoutAnyWrite() {
        existingAccount("ACTIVO", "RESIDENTE");
        when(jdbc.query(anyString(), anyMap(), any(RowMapper.class))).thenAnswer(invocation ->
                ((String) invocation.getArgument(0)).contains("FROM ROLES") ? List.of(5L) : List.of());
        when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class))).thenAnswer(invocation ->
                ((String) invocation.getArgument(0)).contains("SELECT ID_ASIGNACION") ? List.of(88L) : List.of());
        assertEquals(201, controller.crearUsuario(payload()).getStatusCode().value());
        noWrites();
    }

    @Test
    void organizationAdminCannotUseAnotherOrganizationUnit() {
        SaedContextHolder.getContext().setRoleCode("ADMIN_ORGANIZACION");
        when(jdbc.queryForList(anyString(), anyMap())).thenReturn(
                List.of(Map.of("ID_PROPIEDAD", 900L, "ID_ORGANIZACION", 90L)));
        assertThrows(AccessDeniedException.class, () -> controller.crearUsuario(payload()));
        noWrites();
    }

    @Test
    void organizationAdminCannotResetPrivilegedAccount() {
        SaedContextHolder.getContext().setRoleCode("ADMIN_ORGANIZACION");
        existingAccount("ACTIVO", "ADMIN_PROPIEDAD");
        assertThrows(AccessDeniedException.class, () -> controller.crearUsuario(payload()));
        noWrites();
    }

    @Test
    void residentCannotSelectAnotherUnit() {
        SaedContextHolder.getContext().setRoleCode("RESIDENTE");
        var request = new java.util.HashMap<>(payload());
        request.put("idUnidad", 9999L);
        assertThrows(AccessDeniedException.class, () -> controller.crearUsuario(request));
        noWrites();
    }

    @Test
    void missingOrganizationFailsClosed() {
        SaedContextHolder.getContext().setOrganizationId(null);
        assertThrows(AccessDeniedException.class, () -> controller.crearUsuario(payload()));
        noWrites();
    }

    @Test
    void malformedUnitFailsClosed() {
        var request = new java.util.HashMap<>(payload());
        request.put("idUnidad", "invalid");
        assertThrows(AccessDeniedException.class, () -> controller.crearUsuario(request));
        noWrites();
    }

    @Test
    void httpForeignUnitIsDeniedWithoutDatabaseWrites() throws Exception {
        when(jdbc.queryForList(anyString(), anyMap())).thenReturn(
                List.of(Map.of("ID_PROPIEDAD", 900L, "ID_ORGANIZACION", 90L)));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new DenialAdvice()).build();
        mvc.perform(post("/api/v1/usuarios").contentType("application/json")
                .content("{\"username\":\"synthetic\",\"rol\":\"RESIDENTE\",\"idUnidad\":9000}"))
                .andExpect(status().isForbidden());
        noWrites();
    }

    @org.springframework.web.bind.annotation.RestControllerAdvice
    static class DenialAdvice {
        @org.springframework.web.bind.annotation.ExceptionHandler(AccessDeniedException.class)
        @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.FORBIDDEN)
        void denied() { }
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<com.saed.backend.identity.service.TokenActivacionService> tokenProvider() {
        ObjectProvider<com.saed.backend.identity.service.TokenActivacionService> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(mock(com.saed.backend.identity.service.TokenActivacionService.class));
        return provider;
    }
}
