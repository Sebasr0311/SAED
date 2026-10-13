package com.saed.backend.dashboard;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.dashboard.service.impl.ReportesServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** F11-01 / F11-04: the base report engine never broadens scope and counts partial payments as arrears. */
class ReportEngineScopeTest {

    private NamedParameterJdbcTemplate jdbc;
    private ReportesServiceImpl service;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        service = new ReportesServiceImpl(jdbc);
    }

    @AfterEach
    void clear() {
        SaedContextHolder.clearContext();
        SecurityContextHolder.clearContext();
    }

    private static void as(String role, Long org, Long prop) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            "u", "n/a", List.of(new SimpleGrantedAuthority("SCOPE_" + role))));
        SaedContextHolder.setContext(SaedContext.builder().userId(5L).organizationId(org).propertyId(prop)
            .roleCode(role).roleScope(role).build());
    }

    @Test
    void superadminCannotReadOperationalReports() {
        as("SUPERADMIN", 1L, null);
        assertThrows(AccessDeniedException.class, () -> service.getCarteraMorosa(null, null, null, 0, 50));
        verify(jdbc, never()).query(anyString(), any(SqlParameterSource.class), any(RowMapper.class));
    }

    @Test
    void propertyAdminWithoutPropertyContextCannotBroadenToRequestedOrAllProperties() {
        as("ADMIN_PROPIEDAD", 1L, null);
        assertThrows(AccessDeniedException.class, () -> service.getCarteraMorosa(null, null, null, 0, 50));
        assertThrows(AccessDeniedException.class, () -> service.getPagosRecientes(77L, null, null, 0, 50));
        verify(jdbc, never()).query(anyString(), any(SqlParameterSource.class), any(RowMapper.class));
    }

    @Test
    void organizationAdminWithoutOrganizationContextIsDenied() {
        as("ADMIN_ORGANIZACION", null, null);
        assertThrows(AccessDeniedException.class, () -> service.getCarteraMorosa(null, null, null, 0, 50));
        assertThrows(AccessDeniedException.class, () -> service.getEjecucionCuotas(77L, null, null, null, null));
        verify(jdbc, never()).query(anyString(), any(SqlParameterSource.class), any(RowMapper.class));
    }

    @Test
    void arrearsReportCountsPartiallyPaidFeesAndUsesOnlyExistingStates() {
        as("ADMIN_PROPIEDAD", 1L, 10L);
        when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());
        service.getCarteraMorosa(null, null, null, 0, 50);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).query(sql.capture(), params.capture(), any(RowMapper.class));
        assertTrue(sql.getValue().contains("'PAGADA_PARCIAL'"), "PAGADA_PARCIAL is outstanding debt");
        assertFalse(sql.getValue().contains("EN_MORA"), "EN_MORA is not a valid CUOTAS state");
        assertTrue(sql.getValue().contains("SALDO_PENDIENTE > 0"));
        assertEquals(10L, ((MapSqlParameterSource) params.getValue()).getValue("propId"));
    }

    @Test
    void feeExecutionCountsPartiallyPaidFeesAsPending() {
        as("ADMIN_PROPIEDAD", 1L, 10L);
        when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());
        service.getEjecucionCuotas(null, null, null, "2026-01", "2026-03");
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(SqlParameterSource.class), any(RowMapper.class));
        assertTrue(sql.getValue().contains("'PAGADA_PARCIAL'"));
        assertTrue(sql.getValue().contains("<> 'ANULADA'"), "voided fees are not billed");
    }

    @Test
    void organizationAdminForeignPropertyIsDenied() {
        as("ADMIN_ORGANIZACION", 1L, null);
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Integer.class))).thenReturn(0);
        assertThrows(AccessDeniedException.class, () -> service.getCarteraMorosa(99L, null, null, 0, 50));
        verify(jdbc, never()).query(anyString(), any(SqlParameterSource.class), any(RowMapper.class));
    }

    @Test
    void scopePropertyIsResolvedFromContextForExports() {
        as("ADMIN_PROPIEDAD", 1L, 10L);
        assertEquals(10L, service.resolveScopePropertyId(null));
        as("ADMIN_ORGANIZACION", 1L, null);
        assertEquals(null, service.resolveScopePropertyId(null));
    }
}
