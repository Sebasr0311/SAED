package com.saed.backend.finanzas;

import com.saed.backend.common.dto.ApiResponse;
import com.saed.backend.finanzas.controller.PresupuestoController;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * F06-05: the budget status the administrator selects is the one that is stored (the page used to send
 * ACTIVO/INACTIVO, which the table does not accept), invalid input is refused with a client error and a
 * budget that still has expenses cannot be deleted. Mocked JDBC; no database.
 */
class PresupuestoControllerTest {

    private NamedParameterJdbcTemplate jdbc;
    private PresupuestoController controller;
    private int expensesOfTheBudget;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        controller = new PresupuestoController(jdbc);
        expensesOfTheBudget = 0;
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Integer.class)))
                .thenAnswer(invocation -> expensesOfTheBudget);
        when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(1);
        doAnswer(invocation -> {
            ((GeneratedKeyHolder) invocation.getArgument(2)).getKeyList().add(Map.of("ID_PRESUPUESTO", 12L));
            return 1;
        }).when(jdbc).update(contains("INSERT INTO PRESUPUESTOS"), any(SqlParameterSource.class), any(KeyHolder.class),
                any(String[].class));
    }

    private static Map<String, Object> create(String state, Object year) {
        Map<String, Object> body = new HashMap<>();
        body.put("rubro", "Aseo");
        body.put("tipo", "egreso");
        body.put("montoPresupuestado", 1200000);
        body.put("vigenciaAnio", year);
        if (state != null) {
            body.put("estado", state);
        }
        return body;
    }

    @Test
    void theSelectedStatusIsStoredOnCreation() {
        ResponseEntity<ApiResponse<Map<String, Object>>> response = controller.crear(create("borrador", 2026));

        assertEquals(201, response.getStatusCode().value());
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).update(contains("INSERT INTO PRESUPUESTOS"), params.capture(), any(KeyHolder.class), any(String[].class));
        assertEquals("BORRADOR", params.getValue().getValue("estado"));
        assertEquals("EGRESO", params.getValue().getValue("tipo"));
    }

    @Test
    void withoutAStatusTheBudgetIsApproved() {
        controller.crear(create(null, 2026));

        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).update(contains("INSERT INTO PRESUPUESTOS"), params.capture(), any(KeyHolder.class), any(String[].class));
        assertEquals("APROBADO", params.getValue().getValue("estado"));
    }

    @Test
    void aStatusTheTableDoesNotAcceptIsRefusedWith400() {
        for (String bad : new String[]{"ACTIVO", "INACTIVO", "x"}) {
            ResponseEntity<ApiResponse<Map<String, Object>>> response = controller.crear(create(bad, 2026));
            assertEquals(400, response.getStatusCode().value(), bad);
        }
        verify(jdbc, never()).update(contains("INSERT INTO PRESUPUESTOS"), any(SqlParameterSource.class),
                any(KeyHolder.class), any(String[].class));
    }

    @Test
    void theValidityYearIsRequired() {
        assertEquals(400, controller.crear(create("APROBADO", 0)).getStatusCode().value());
        assertEquals(400, controller.crear(create("APROBADO", null)).getStatusCode().value());
    }

    @Test
    void updatingWithAnInvalidStatusIsRefusedAndAValidOneIsStored() {
        Map<String, Object> bad = new HashMap<>();
        bad.put("estado", "ACTIVO");
        assertEquals(400, controller.actualizar(12L, bad).getStatusCode().value());
        verify(jdbc, never()).update(contains("UPDATE PRESUPUESTOS"), any(SqlParameterSource.class));

        Map<String, Object> good = new HashMap<>();
        good.put("estado", "cerrado");
        assertEquals(200, controller.actualizar(12L, good).getStatusCode().value());
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).update(contains("UPDATE PRESUPUESTOS"), params.capture());
        assertEquals("CERRADO", params.getValue().getValue("estado"));
    }

    @Test
    void aMissingBudgetIsA404() {
        when(jdbc.update(contains("UPDATE PRESUPUESTOS"), any(SqlParameterSource.class))).thenReturn(0);
        when(jdbc.update(contains("DELETE FROM PRESUPUESTOS"), any(SqlParameterSource.class))).thenReturn(0);

        Map<String, Object> body = new HashMap<>();
        body.put("rubro", "Aseo");
        assertEquals(404, controller.actualizar(99L, body).getStatusCode().value());
        assertEquals(404, controller.eliminar(99L).getStatusCode().value());
    }

    @Test
    void aBudgetWithExpensesCannotBeDeleted() {
        expensesOfTheBudget = 3;

        ResponseEntity<ApiResponse<String>> response = controller.eliminar(12L);

        assertEquals(409, response.getStatusCode().value());
        verify(jdbc, never()).update(contains("DELETE FROM PRESUPUESTOS"), any(SqlParameterSource.class));
    }
}
