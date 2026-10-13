package com.saed.backend.platform.entitlement;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.platform.exception.ModuleNotEntitledException;
import com.saed.backend.platform.service.impl.ModuleEntitlementServiceImpl;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F3-07 / F3-13: module entitlement is evaluated only for the organization of the server-side
 * context. Client headers/parameters and "first active organization" fallbacks are never used.
 */
class ModuleEntitlementResolutionTest {

    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final JdbcOperations ops = mock(JdbcOperations.class);
    private final ModuleEntitlementServiceImpl service = new ModuleEntitlementServiceImpl(jdbc);

    @AfterEach
    void clear() {
        SaedContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    private static void requestWith(String header, String value, String param) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (header != null) {
            request.addHeader(header, value);
        }
        if (param != null) {
            request.setParameter("idOrganizacion", param);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    void clientSuppliedOrganizationHeaderIsIgnored() {
        requestWith("X-Organization-Id", "77", null);

        assertNull(service.resolveCurrentOrganizationId());
    }

    @Test
    void clientSuppliedOrganizationParameterIsIgnored() {
        requestWith(null, null, "77");

        assertNull(service.resolveCurrentOrganizationId());
    }

    @Test
    void operationalAuthenticationWithoutContextDoesNotFallBackToAnotherOrganization() {
        when(jdbc.getJdbcOperations()).thenReturn(ops);
        when(ops.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class))).thenReturn(List.of(5L));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "someone", null, List.of(new SimpleGrantedAuthority("SCOPE_ADMIN_PROPIEDAD"))));

        assertNull(service.resolveCurrentOrganizationId());
    }

    @Test
    void contextOrganizationIsUsed() {
        SaedContextHolder.setContext(SaedContext.builder().userId(9L).organizationId(42L).roleCode("ADMIN_PROPIEDAD").build());

        assertEquals(42L, service.resolveCurrentOrganizationId());
    }

    @Test
    void moduleAccessWithoutServerContextIsDeniedEvenWithHeader() {
        requestWith("X-Organization-Id", "77", "77");

        assertThrows(ModuleNotEntitledException.class, () -> service.checkModuleAccess("FINANZAS"));
        verify(jdbc, never()).queryForList(anyString(), any(SqlParameterSource.class));
    }

    @Test
    void suspendedMembershipIsDenied() {
        SaedContextHolder.setContext(SaedContext.builder().userId(9L).organizationId(42L).roleCode("ADMIN_PROPIEDAD").build());
        when(jdbc.queryForList(contains("ESTADO IN ('ACTIVA', 'PRUEBA')"), any(SqlParameterSource.class)))
                .thenReturn(List.of());
        when(jdbc.queryForList(contains("ORDER BY m.ID_MEMBRESIA DESC"), any(SqlParameterSource.class)))
                .thenReturn(List.of())
                .thenReturn(List.of(Map.of("ESTADO", "SUSPENDIDA")));

        assertThrows(ModuleNotEntitledException.class, () -> service.checkModuleAccess("PAQUETES"));
    }

    @Test
    void entitledModuleIsGranted() {
        SaedContextHolder.setContext(SaedContext.builder().userId(9L).organizationId(42L).roleCode("ADMIN_PROPIEDAD").build());
        when(jdbc.queryForList(contains("ESTADO IN ('ACTIVA', 'PRUEBA')"), any(SqlParameterSource.class)))
                .thenReturn(List.of(Map.of("ID_MEMBRESIA", 1, "ID_PLAN", 2, "ESTADO", "ACTIVA")));
        when(jdbc.queryForList(contains("FROM PLAN_MODULOS pm"), any(SqlParameterSource.class)))
                .thenReturn(List.of(Map.of("HABILITADO", "S")));

        assertDoesNotThrow(() -> service.checkModuleAccess("PARQUEADEROS"));
    }
}
