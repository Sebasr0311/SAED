package com.saed.backend.finanzas;

import com.saed.backend.common.dto.ApiResponse;
import com.saed.backend.finanzas.controller.ConciliacionController;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
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
 * F06-05 / F6-15: the reconciliation API accepts the bank account under the name the page sends, persists
 * the status that was selected, refuses a CONCILIADO status while books and bank differ, and repeating a
 * reconciliation of the same account and period neither duplicates nor re-stamps it.
 */
class ConciliacionControllerTest {

    private NamedParameterJdbcTemplate jdbc;
    private ConciliacionController controller;
    private int existingForAccountAndPeriod;
    private String storedState;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        controller = new ConciliacionController(jdbc);
        existingForAccountAndPeriod = 0;
        storedState = "EN_PROCESO";
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Integer.class)))
                .thenAnswer(invocation -> existingForAccountAndPeriod);
        when(jdbc.queryForList(anyString(), any(SqlParameterSource.class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("FROM CONCILIACIONES WHERE ID_CONCILIACION")) {
                Map<String, Object> row = new HashMap<>();
                row.put("SALDO_BANCO", new BigDecimal("1000.00"));
                row.put("SALDO_LIBROS", new BigDecimal("900.00"));
                row.put("ESTADO", storedState);
                return List.of(row);
            }
            return List.of();
        });
        when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(1);
        doAnswer(invocation -> {
            ((GeneratedKeyHolder) invocation.getArgument(2)).getKeyList().add(Map.of("ID_CONCILIACION", 31L));
            return 1;
        }).when(jdbc).update(contains("INSERT INTO CONCILIACIONES"), any(SqlParameterSource.class), any(KeyHolder.class),
                any(String[].class));
    }

    private static Map<String, Object> body(String accountKey, String period, int bank, int books, String state) {
        Map<String, Object> body = new HashMap<>();
        body.put(accountKey, "Bancolombia 123");
        body.put("periodo", period);
        body.put("saldoBanco", bank);
        body.put("saldoLibros", books);
        if (state != null) {
            body.put("estado", state);
        }
        return body;
    }

    private SqlParameterSource insertedParams() {
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).update(contains("INSERT INTO CONCILIACIONES"), params.capture(), any(KeyHolder.class), any(String[].class));
        return params.getValue();
    }

    @Test
    void thePageFieldNameForTheBankAccountIsAccepted() {
        ResponseEntity<ApiResponse<Map<String, Object>>> response =
                controller.crear(body("cuentaBanco", "2026-10", 500, 500, "EN_PROCESO"));

        assertEquals(201, response.getStatusCode().value());
        assertEquals("Bancolombia 123", insertedParams().getValue("bancoCuenta"));
    }

    @Test
    void theApiFieldNameForTheBankAccountStillWorks() {
        controller.crear(body("bancoCuenta", "2026-10", 500, 500, null));

        assertEquals("Bancolombia 123", insertedParams().getValue("bancoCuenta"));
    }

    @Test
    void theSelectedStatusIsPersistedOnCreation() {
        controller.crear(body("bancoCuenta", "2026-10", 500, 400, "CON_DIFERENCIAS"));

        assertEquals("CON_DIFERENCIAS", insertedParams().getValue("estado"));
    }

    @Test
    void anUnknownStatusIsRefused() {
        ResponseEntity<ApiResponse<Map<String, Object>>> response =
                controller.crear(body("bancoCuenta", "2026-10", 500, 500, "LISTO"));

        assertEquals(400, response.getStatusCode().value());
        verify(jdbc, never()).update(contains("INSERT INTO CONCILIACIONES"), any(SqlParameterSource.class),
                any(KeyHolder.class), any(String[].class));
    }

    @Test
    void reconciledStatusIsRefusedWhileBankAndBooksDiffer() {
        ResponseEntity<ApiResponse<Map<String, Object>>> response =
                controller.crear(body("bancoCuenta", "2026-10", 500, 400, "CONCILIADO"));

        assertEquals(400, response.getStatusCode().value());
        verify(jdbc, never()).update(contains("INSERT INTO CONCILIACIONES"), any(SqlParameterSource.class),
                any(KeyHolder.class), any(String[].class));
    }

    @Test
    void theSameAccountAndPeriodIsNotReconciledTwice() {
        existingForAccountAndPeriod = 1;

        ResponseEntity<ApiResponse<Map<String, Object>>> response =
                controller.crear(body("bancoCuenta", "2026-10", 500, 500, null));

        assertEquals(409, response.getStatusCode().value());
        verify(jdbc, never()).update(contains("INSERT INTO CONCILIACIONES"), any(SqlParameterSource.class),
                any(KeyHolder.class), any(String[].class));
    }

    @Test
    void aMalformedPeriodIsRefusedBeforeTheDatabase() {
        ResponseEntity<ApiResponse<Map<String, Object>>> response =
                controller.crear(body("bancoCuenta", "octubre", 500, 500, null));

        assertEquals(400, response.getStatusCode().value());
        verifyNoInteractions(jdbc);
    }

    @Test
    void updatingWithThePageFieldNameChangesTheBankAccount() {
        Map<String, Object> update = new HashMap<>();
        update.put("cuentaBanco", "Davivienda 9");

        controller.actualizar(31L, update);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc, atLeastOnce()).update(sql.capture(), params.capture());
        int i = sql.getAllValues().indexOf(sql.getAllValues().stream().filter(s -> s.contains("BANCO_CUENTA")).findFirst().orElseThrow());
        assertEquals("Davivienda 9", params.getAllValues().get(i).getValue("bancoCuenta"));
    }

    @Test
    void patchingToReconciledIsRefusedWhileBankAndBooksDiffer() {
        ResponseEntity<ApiResponse<String>> response = controller.cambiarEstado(31L, Map.of("estado", "CONCILIADO"));

        assertEquals(400, response.getStatusCode().value());
        verify(jdbc, never()).update(contains("SET ESTADO"), any(SqlParameterSource.class));
    }

    @Test
    void repeatingTheSameStatusDoesNotRestampTheReconciliation() {
        storedState = "CON_DIFERENCIAS";

        ResponseEntity<ApiResponse<String>> response = controller.cambiarEstado(31L, Map.of("estado", "CON_DIFERENCIAS"));

        assertEquals(200, response.getStatusCode().value());
        verify(jdbc, never()).update(contains("SET ESTADO"), any(SqlParameterSource.class));
    }
}
