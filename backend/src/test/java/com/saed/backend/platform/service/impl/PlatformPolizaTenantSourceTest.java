package com.saed.backend.platform.service.impl;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.seguros.service.PolizaTenantSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Tenant enumeration for the insurance expiry job: platform-scoped read of ids only, context always restored. */
class PlatformPolizaTenantSourceTest {

    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final PlatformPolizaTenantSource source = new PlatformPolizaTenantSource(jdbc);

    @AfterEach
    void clearContext() {
        SaedContextHolder.clearContext();
    }

    @Test
    void enumeratesWithThePlatformContextAndClearsItAfterwards() {
        AtomicReference<SaedContext> seen = new AtomicReference<>();
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any())).thenAnswer(invocation -> {
            seen.set(SaedContextHolder.getContext());
            return rows(invocation.getArgument(1), new long[][] {{40L, 30L, 60L}, {41L, 31L, 61L}});
        });

        List<PolizaTenantSource.TenantScope> tenants = source.tenants();

        assertEquals(List.of(new PolizaTenantSource.TenantScope(40L, 30L, 60L),
                new PolizaTenantSource.TenantScope(41L, 31L, 61L)), tenants);
        assertEquals("SUPERADMIN", seen.get().getRoleCode());
        assertNull(SaedContextHolder.getContext(), "the platform context must not outlive the enumeration");
    }

    @Test
    void restoresTheCallersContextInsteadOfLeavingThePlatformOne() {
        SaedContext caller = SaedContext.builder().userId(7L).organizationId(1L).propertyId(2L)
                .roleCode("ADMIN_PROPIEDAD").build();
        SaedContextHolder.setContext(caller);
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any())).thenReturn(List.of());

        source.tenants();

        assertSame(caller, SaedContextHolder.getContext());
    }

    @Test
    void clearsTheContextAlsoWhenTheQueryFails() {
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any()))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThrows(IllegalStateException.class, source::tenants);

        assertNull(SaedContextHolder.getContext());
    }

    @Test
    void readsOnlyActivePoliciesOfActivePropertiesWithAnActiveAdministrator() {
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any())).thenReturn(List.of());

        source.tenants();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any());
        String text = sql.getValue().toUpperCase();
        assertTrue(text.contains("FROM PROPIEDADES"), text);
        assertTrue(text.contains("POLIZAS_SEGURO"), text);
        assertTrue(text.contains("PS.ESTADO <> 'CANCELADA'"), text);
        assertTrue(text.contains("PR.ESTADO = 'ACTIVA'"), text);
        assertTrue(text.contains("'ADMIN_PROPIEDAD'"), text);
        assertTrue(text.contains("TRUNC(SYSDATE)"), text);
        assertFalse(text.contains("CURRENT_DATE") || text.contains("SYSTIMESTAMP"), text);
        // Ids only: no policy content, personal data or e-mail is read by the enumeration.
        assertFalse(text.contains("EMAIL") || text.contains("NUMERO_POLIZA"), text);
    }

    @Test
    void skipsAPropertyWithoutAnActiveAdministrator() {
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any())).thenAnswer(invocation ->
                rows(invocation.getArgument(1), new long[][] {{40L, 30L, 0L}, {41L, 31L, 61L}}));

        List<PolizaTenantSource.TenantScope> tenants = source.tenants();

        assertEquals(List.of(new PolizaTenantSource.TenantScope(41L, 31L, 61L)), tenants);
    }

    /** Applies the production row mapper to fake rows; an admin id of 0 stands for SQL NULL. */
    @SuppressWarnings("unchecked")
    private static List<Object> rows(RowMapper<?> mapper, long[][] data) throws SQLException {
        List<Object> mapped = new ArrayList<>();
        for (int i = 0; i < data.length; i++) {
            ResultSet rs = mock(ResultSet.class);
            long[] row = data[i];
            when(rs.getLong("ID_ORGANIZACION")).thenReturn(row[0]);
            when(rs.getLong("ID_PROPIEDAD")).thenReturn(row[1]);
            when(rs.getLong("ID_ADMIN")).thenReturn(row[2]);
            when(rs.wasNull()).thenReturn(row[2] == 0L);
            Object value = ((RowMapper<Object>) mapper).mapRow(rs, i);
            if (value != null) {
                mapped.add(value);
            }
        }
        return mapped;
    }
}
