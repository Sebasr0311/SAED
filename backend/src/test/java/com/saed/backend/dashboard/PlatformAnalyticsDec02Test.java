package com.saed.backend.dashboard;

import com.saed.backend.dashboard.service.impl.AnalyticsServiceImpl;
import com.saed.backend.platform.dto.PlatformAnalyticsDTO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** DEC-02 / F11-02: platform analytics never reads tenant operational data and never hides SQL failures. */
class PlatformAnalyticsDec02Test {

    private static final List<String> OPERATIONAL_TABLES = List.of("RESIDENTES_UNIDAD", "TRABAJADORES", "VISITAS",
        "PAQUETES", "PQRS_TICKETS", "MANTENIMIENTOS", "RESERVAS", "COMUNICADOS", "TRANSACCIONES_PAGO", "PAGOS", "CUOTAS");

    private static Map<String, Object> zeros() {
        Map<String, Object> row = new HashMap<>();
        for (String k : List.of("TOTAL_ORG", "ACTIVAS_ORG", "TOTAL_PROP", "ACTIVAS_PROP", "TOTAL_UNIDADES",
                "TOTAL_USERS", "ACTIVE_USERS")) {
            row.put(k, 0);
        }
        return row;
    }

    @Test
    void platformAnalyticsQueriesNoOperationalTablesAndReportsNoFabricatedRetention() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForMap(anyString(), any(SqlParameterSource.class))).thenReturn(zeros());
        when(jdbc.queryForList(anyString(), any(SqlParameterSource.class))).thenReturn(new ArrayList<>());

        PlatformAnalyticsDTO dto = new AnalyticsServiceImpl(jdbc).getPlatformAnalytics("30d", null);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, atLeast(1)).queryForMap(sql.capture(), any(SqlParameterSource.class));
        verify(jdbc, atLeast(1)).queryForList(sql.capture(), any(SqlParameterSource.class));
        for (String statement : sql.getAllValues()) {
            for (String table : OPERATIONAL_TABLES) {
                assertFalse(statement.matches("(?s).*\\b" + table + "\\b.*"), "platform analytics reads " + table);
            }
        }
        assertNull(dto.tasaRetencionOrganizacionesPct(), "no organizations means no retention rate, not 100 %");
        assertTrue(dto.actividadOperativa().isEmpty());
        assertTrue(dto.transaccionesSandbox().isEmpty());
        assertFalse(dto.resumenGlobal().containsKey("residentes"));
    }

    @Test
    void sqlFailuresPropagateInsteadOfBecomingZeros() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForMap(anyString(), any(SqlParameterSource.class)))
            .thenThrow(new DataAccessResourceFailureException("down"));
        assertThrows(DataAccessResourceFailureException.class,
            () -> new AnalyticsServiceImpl(jdbc).getPlatformAnalytics("30d", null));
    }
}
