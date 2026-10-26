package com.saed.backend.identity.repository;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.identity.dto.AuthUserDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

class AuthRepositoryProfileSafetyTest {

    private JdbcTemplate jdbcTemplate;
    private SimpleJdbcCall profileCall;
    private AuthRepositoryImpl repository;
    private Map<String, Object> authoritativeProfile;
    private boolean failOptionalLookup;

    @BeforeEach
    void setUp() {
        SaedContextHolder.clearContext();
        authoritativeProfile = new HashMap<>();
        authoritativeProfile.put("p_nombre_usuario", "synthetic-user");
        authoritativeProfile.put("p_email", "synthetic@example.invalid");
        authoritativeProfile.put("p_rol_codigo", "ADMIN_ORGANIZACION");
        authoritativeProfile.put("p_alcance", "ORGANIZACION");
        authoritativeProfile.put("p_org_id", 10L);
        authoritativeProfile.put("p_prop_id", 11L);
        authoritativeProfile.put("p_unidad_id", 12L);
        jdbcTemplate = mock(JdbcTemplate.class, invocation -> {
            String method = invocation.getMethod().getName();
            Object[] arguments = invocation.getArguments();
            if (arguments.length > 0 && arguments[0] instanceof String sql) {
                if (method.startsWith("query") && failOptionalLookup) {
                    throw new IllegalStateException("Synthetic optional lookup failure");
                }
                if ("queryForList".equals(method) && sql.contains("FROM USUARIOS")) {
                    return List.of(Map.of("ID_PERSONA", 200L, "TELEFONO", "synthetic-phone",
                            "NOMBRE_COMPLETO", "Synthetic Person"));
                }
                if ("query".equals(method) && sql.startsWith("SELECT ID_UNIDAD")) {
                    return List.of(99L);
                }
                if ("query".equals(method) && sql.startsWith("SELECT TIPO_RESIDENTE")) {
                    return List.of("CONVIVIENTE");
                }
                if ("queryForList".equals(method) && sql.contains("FROM UNIDADES")) {
                    return List.of(Map.of("ID_PROPIEDAD", 98L, "ID_ORGANIZACION", 97L,
                            "IDENTIFICADOR", "SYNTHETIC-UNIT"));
                }
                if ("queryForObject".equals(method) && sql.contains("IDENTIFICADOR")) {
                    return "SYNTHETIC-UNIT";
                }
            }
            return Answers.RETURNS_DEFAULTS.answer(invocation);
        });
        repository = new AuthRepositoryImpl(jdbcTemplate);
        profileCall = mock(SimpleJdbcCall.class);
        when(profileCall.execute(anyMap())).thenReturn(authoritativeProfile);
        ReflectionTestUtils.setField(repository, "getUserProfileCall", profileCall);
    }

    @AfterEach
    void cleanUp() {
        SaedContextHolder.clearContext();
    }

    @Test
    void profileReadDoesNotWriteAssignmentsOrSelectLatestHabitation() {
        repository.getUserProfile(20L);

        assertFalse(mockingDetails(jdbcTemplate).getInvocations().stream().anyMatch(invocation -> {
            Object[] arguments = invocation.getArguments();
            return arguments.length > 0 && arguments[0] instanceof String sql
                    && sql.contains("USUARIO_ASIGNACIONES")
                    && ("update".equals(invocation.getMethod().getName())
                    || "execute".equals(invocation.getMethod().getName()));
        }), "Profile reads must never mutate authorization assignments");
        assertFalse(mockingDetails(jdbcTemplate).getInvocations().stream().anyMatch(invocation -> {
            Object[] arguments = invocation.getArguments();
            return arguments.length > 0 && arguments[0] instanceof String sql
                    && sql.startsWith("SELECT ID_UNIDAD FROM RESIDENTES_UNIDAD");
        }), "Latest habitation must not replace the selected assignment");
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUPERADMIN", "ADMIN_ORGANIZACION", "ADMIN_PROPIEDAD", "PORTERO",
            "RESIDENTE", "RESIDENTE_CONVIVENCIA"})
    void sixStoredRolesAndTenantScopeRemainAuthoritative(String role) {
        authoritativeProfile.put("p_rol_codigo", role);
        authoritativeProfile.put("p_alcance", "UNIDAD");

        AuthUserDTO profile = repository.getUserProfile(20L);

        assertEquals(role, profile.getRol());
        assertEquals("UNIDAD", profile.getAlcance());
        assertEquals(10L, profile.getIdOrganizacion());
        assertEquals(11L, profile.getIdPropiedad());
        assertEquals(12L, profile.getIdUnidad());
    }

    @Test
    void descriptiveHabitationLookupUsesOnlyTheSelectedUnit() {
        authoritativeProfile.put("p_rol_codigo", "RESIDENTE");

        AuthUserDTO profile = repository.getUserProfile(20L);

        assertEquals("RESIDENTE", profile.getRol());
        assertEquals("CONVIVIENTE", profile.getTipoResidente());
        assertTrue(mockingDetails(jdbcTemplate).getInvocations().stream().anyMatch(invocation -> {
            Object[] arguments = invocation.getArguments();
            return arguments.length >= 4 && arguments[0] instanceof String sql
                    && sql.startsWith("SELECT TIPO_RESIDENTE") && Long.valueOf(12L).equals(arguments[3]);
        }), "Habitation metadata must be constrained to the package-selected unit");
    }

    @Test
    void unitlessAdministratorRemainsUnitlessDespiteOtherHabitation() {
        authoritativeProfile.remove("p_unidad_id");

        AuthUserDTO profile = repository.getUserProfile(20L);

        assertEquals("ADMIN_ORGANIZACION", profile.getRol());
        assertNull(profile.getIdUnidad());
        assertEquals(10L, profile.getIdOrganizacion());
    }

    @Test
    void profileWithoutAnAssignmentRetainsIdentityOnlyScope() {
        authoritativeProfile.remove("p_rol_codigo");
        authoritativeProfile.remove("p_alcance");
        authoritativeProfile.remove("p_org_id");
        authoritativeProfile.remove("p_prop_id");
        authoritativeProfile.remove("p_unidad_id");

        AuthUserDTO profile = repository.getUserProfile(20L);

        assertEquals(20L, profile.getIdUsuario());
        assertNull(profile.getRol());
        assertNull(profile.getIdOrganizacion());
        assertNull(profile.getIdPropiedad());
        assertNull(profile.getIdUnidad());
    }

    @Test
    void previousContextIsRestoredAfterSuccessfulOptionalLookup() {
        SaedContext previous = SaedContext.builder().userId(30L).organizationId(31L).build();
        SaedContextHolder.setContext(previous);

        repository.getUserProfile(20L);

        assertSame(previous, SaedContextHolder.getContext());
    }

    @Test
    void previousContextIsRestoredWhenOptionalLookupFails() {
        SaedContext previous = SaedContext.builder().userId(30L).organizationId(31L).build();
        SaedContextHolder.setContext(previous);
        failOptionalLookup = true;

        AuthUserDTO profile = repository.getUserProfile(20L);

        assertSame(previous, SaedContextHolder.getContext());
        assertEquals("ADMIN_ORGANIZACION", profile.getRol());
        assertEquals(12L, profile.getIdUnidad());
    }

    @Test
    void absentContextIsClearedAfterOptionalLookupFailure() {
        failOptionalLookup = true;

        repository.getUserProfile(20L);

        assertNull(SaedContextHolder.getContext());
    }

    @Test
    void authoritativeCallFailureDoesNotReplacePreviousContext() {
        SaedContext previous = SaedContext.builder().userId(30L).build();
        SaedContextHolder.setContext(previous);
        when(profileCall.execute(anyMap())).thenThrow(new IllegalStateException("Synthetic profile failure"));

        assertThrows(IllegalStateException.class, () -> repository.getUserProfile(20L));

        assertSame(previous, SaedContextHolder.getContext());
    }

    @Test
    void bootstrapFailureSkipsAllDescriptiveQueriesAndRestoresContext() {
        SaedContext previous = SaedContext.builder().userId(30L).build();
        SaedContextHolder.setContext(previous);
        doThrow(new IllegalStateException("Synthetic bootstrap failure"))
                .when(jdbcTemplate).update(anyString(), anyLong());

        AuthUserDTO profile = repository.getUserProfile(20L);

        assertSame(previous, SaedContextHolder.getContext());
        assertEquals("ADMIN_ORGANIZACION", profile.getRol());
        assertFalse(mockingDetails(jdbcTemplate).getInvocations().stream()
                .anyMatch(invocation -> invocation.getMethod().getName().startsWith("query")));
    }
}
