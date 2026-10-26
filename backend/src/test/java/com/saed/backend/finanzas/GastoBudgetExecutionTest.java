package com.saed.backend.finanzas;

import com.saed.backend.common.dto.ApiResponse;
import com.saed.backend.common.service.FileStorageService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.finanzas.controller.GastosController;
import com.saed.backend.finanzas.domain.BudgetExecution;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * F06-05: the executed amount of a budget follows its expenses. Moving an expense between budgets
 * updates both atomically, cancelled expenses do not count, and the selected status persists (an
 * unknown status is refused instead of silently replaced). Mocked JDBC; no database.
 */
class GastoBudgetExecutionTest {
    private static final long PROP = 100L;

    private NamedParameterJdbcTemplate jdbc;
    private GastosController controller;
    private String currentState;
    private boolean targetBudgetInProperty;

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        controller = new GastosController(jdbc, mock(FileStorageService.class));
        currentState = "REGISTRADO";
        targetBudgetInProperty = true;
        when(jdbc.queryForList(anyString(), any(SqlParameterSource.class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("FROM GASTOS WHERE ID_GASTO")) {
                Map<String, Object> row = new HashMap<>();
                row.put("ID_PRESUPUESTO", 1L);
                row.put("MONTO", bd("500.00"));
                row.put("ESTADO", currentState);
                row.put("FACTURA_SOPORTE_URL", null);
                return List.of(row);
            }
            return List.of();
        });
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Integer.class)))
                .thenAnswer(invocation -> targetBudgetInProperty ? 1 : 0);
        when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(1);
        SaedContextHolder.setContext(SaedContext.builder().userId(5L).organizationId(10L).propertyId(PROP)
                .roleCode("ADMIN_PROPIEDAD").roleScope("PROPIEDAD").build());
    }

    @AfterEach
    void tearDown() {
        SaedContextHolder.clearContext();
    }

    private Map<Long, BigDecimal> budgetDeltas() {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc, atLeast(0)).update(sql.capture(), params.capture());
        Map<Long, BigDecimal> deltas = new HashMap<>();
        for (int i = 0; i < sql.getAllValues().size(); i++) {
            if (sql.getAllValues().get(i).contains("UPDATE PRESUPUESTOS")) {
                SqlParameterSource p = params.getAllValues().get(i);
                deltas.merge(((Number) p.getValue("pId")).longValue(), (BigDecimal) p.getValue("diff"), BigDecimal::add);
            }
        }
        return deltas;
    }

    // ---- pure accounting

    @Test
    void movingAnExpenseMovesItsAmountBetweenBudgets() {
        List<BudgetExecution.Movement> moves = BudgetExecution.between(1L, bd("500"), "REGISTRADO", 2L, bd("500"), "REGISTRADO");

        assertEquals(List.of(new BudgetExecution.Movement(1L, bd("-500")), new BudgetExecution.Movement(2L, bd("500"))), moves);
    }

    @Test
    void changingTheAmountInTheSameBudgetOnlyMovesTheDifference() {
        assertEquals(List.of(new BudgetExecution.Movement(1L, bd("200"))),
                BudgetExecution.between(1L, bd("500"), "PAGADO", 1L, bd("700"), "PAGADO"));
    }

    @Test
    void cancelledExpensesDoNotCountTowardsExecution() {
        assertEquals(List.of(new BudgetExecution.Movement(1L, bd("-500"))),
                BudgetExecution.between(1L, bd("500"), "REGISTRADO", 1L, bd("500"), "ANULADO"));
        assertEquals(List.of(new BudgetExecution.Movement(1L, bd("500"))),
                BudgetExecution.between(1L, bd("500"), "ANULADO", 1L, bd("500"), "PAGADO"));
        assertTrue(BudgetExecution.between(1L, bd("500"), "ANULADO", 2L, bd("500"), "ANULADO").isEmpty());
    }

    @Test
    void anUnchangedExpenseMovesNothing() {
        assertTrue(BudgetExecution.between(1L, bd("500"), "REGISTRADO", 1L, bd("500"), "PAGADO").isEmpty());
    }

    @Test
    void anExpenseWithoutABudgetNeverMovesAnything() {
        assertTrue(BudgetExecution.between(null, bd("500"), "REGISTRADO", null, bd("900"), "REGISTRADO").isEmpty());
        assertEquals(List.of(new BudgetExecution.Movement(3L, bd("900"))),
                BudgetExecution.between(null, bd("500"), "REGISTRADO", 3L, bd("900"), "REGISTRADO"));
    }

    // ---- controller behaviour

    @Test
    void movingAnExpenseUpdatesBothBudgets() {
        Map<String, Object> body = new HashMap<>();
        body.put("idPresupuesto", 2);

        controller.actualizar(77L, body);

        Map<Long, BigDecimal> deltas = budgetDeltas();
        assertEquals(0, bd("-500.00").compareTo(deltas.get(1L)));
        assertEquals(0, bd("500.00").compareTo(deltas.get(2L)));
    }

    @Test
    void movingToABudgetOfAnotherPropertyChangesNothing() {
        targetBudgetInProperty = false;
        Map<String, Object> body = new HashMap<>();
        body.put("idPresupuesto", 9);

        ResponseEntity<ApiResponse<String>> response = controller.actualizar(77L, body);

        assertTrue(response.getStatusCode().is4xxClientError());
        verify(jdbc, never()).update(contains("UPDATE GASTOS"), any(SqlParameterSource.class));
        assertTrue(budgetDeltas().isEmpty());
    }

    @Test
    void cancellingAnExpenseReleasesItsExecutedAmount() {
        Map<String, Object> body = new HashMap<>();
        body.put("estado", "ANULADO");

        controller.actualizar(77L, body);

        assertEquals(0, bd("-500.00").compareTo(budgetDeltas().get(1L)));
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).update(contains("UPDATE GASTOS"), params.capture());
        assertEquals("ANULADO", params.getValue().getValue("estado"));
    }

    @Test
    void anUnknownStatusIsRefusedInsteadOfBeingReplaced() {
        Map<String, Object> body = new HashMap<>();
        body.put("categoria", "Aseo");
        body.put("beneficiario", "ACME");
        body.put("monto", 100);
        body.put("estado", "PENDIENTE_X");

        ResponseEntity<ApiResponse<Map<String, Object>>> response = controller.crearJson(body);

        assertEquals(400, response.getStatusCode().value());
        verify(jdbc, never()).update(contains("INSERT INTO GASTOS"), any(SqlParameterSource.class),
                any(org.springframework.jdbc.support.KeyHolder.class), any(String[].class));
    }

    @Test
    void aCancelledExpenseIsCreatedWithoutTouchingTheBudget() {
        doAnswer(invocation -> {
            ((org.springframework.jdbc.support.GeneratedKeyHolder) invocation.getArgument(2))
                    .getKeyList().add(Map.of("ID_GASTO", 77L));
            return 1;
        }).when(jdbc).update(contains("INSERT INTO GASTOS"), any(SqlParameterSource.class),
                any(org.springframework.jdbc.support.KeyHolder.class), any(String[].class));
        Map<String, Object> body = new HashMap<>();
        body.put("categoria", "Aseo");
        body.put("beneficiario", "ACME");
        body.put("monto", 100);
        body.put("estado", "ANULADO");
        body.put("idPresupuesto", 1);

        ResponseEntity<ApiResponse<Map<String, Object>>> response = controller.crearJson(body);

        assertEquals(201, response.getStatusCode().value());
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).update(contains("INSERT INTO GASTOS"), params.capture(),
                any(org.springframework.jdbc.support.KeyHolder.class), any(String[].class));
        assertEquals("ANULADO", params.getValue().getValue("estado"));
        assertTrue(budgetDeltas().isEmpty());
    }

    @Test
    void deletingACancelledExpenseDoesNotReleaseAnythingTwice() {
        currentState = "ANULADO";

        controller.eliminar(77L);

        assertTrue(budgetDeltas().isEmpty());
    }

    @Test
    void writesRunInOneTransaction() throws Exception {
        for (String name : List.of("actualizar", "eliminar", "crearJson")) {
            var method = java.util.Arrays.stream(GastosController.class.getDeclaredMethods())
                    .filter(m -> m.getName().equals(name)).findFirst().orElseThrow();
            assertNotNull(method.getAnnotation(Transactional.class), name + " must be @Transactional");
        }
    }
}
