package com.saed.backend.authorization.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PropertyRepositoryImplDeletionTest {

    private static final List<String> PROTECTED_TABLES = List.of(
            "CUOTAS", "PAGOS", "PAGO_DETALLE", "TRANSACCIONES_PAGO", "MULTAS", "SANCIONES", "CONTRATOS",
            "CARTERA", "PAZ_Y_SALVOS", "ASAMBLEAS", "VOTOS", "ASISTENCIAS_ASAMBLEA",
            "PODERES_REPRESENTACION", "GASTOS", "CONCILIACIONES", "REGISTROS_ACCESO");

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @InjectMocks
    private PropertyRepositoryImpl repository;

    @Test
    void deleteCascade_whenAChildDeleteFails_propagatesAndNeverDeletesTheProperty() {
        when(jdbcTemplate.update(contains("DELETE FROM PAQUETES"), any(SqlParameterSource.class)))
                .thenThrow(new DataIntegrityViolationException("ORA-02292: integrity constraint violated"));

        assertThrows(DataIntegrityViolationException.class, () -> repository.deletePropertyCascade(10L, 5L));

        verify(jdbcTemplate, never()).update(contains("DELETE FROM PROPIEDADES"), any(SqlParameterSource.class));
    }

    @Test
    void deleteCascade_neverDeletesFinancialLegalOrAuditHistory() {
        when(jdbcTemplate.update(anyString(), any(SqlParameterSource.class))).thenReturn(1);

        assertTrue(repository.deletePropertyCascade(10L, 5L));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, atLeastOnce()).update(sql.capture(), any(SqlParameterSource.class));
        for (String statement : sql.getAllValues()) {
            String upper = statement.toUpperCase(Locale.ROOT);
            for (String table : PROTECTED_TABLES) {
                assertFalse(upper.startsWith("DELETE FROM " + table + " "),
                        "Cascade must not delete protected history table " + table + ": " + statement);
            }
        }
    }

    @Test
    void findProtectedHistory_reportsEveryTableThatHasRows() {
        when(jdbcTemplate.queryForObject(anyString(), any(SqlParameterSource.class), eq(Integer.class)))
                .thenReturn(0);
        when(jdbcTemplate.queryForObject(argThat((String s) -> s != null && s.contains("FROM CUOTAS ")),
                any(SqlParameterSource.class), eq(Integer.class))).thenReturn(1);
        when(jdbcTemplate.queryForObject(argThat((String s) -> s != null && s.contains("FROM CONTRATOS ")),
                any(SqlParameterSource.class), eq(Integer.class))).thenReturn(1);

        assertEquals(List.of("CUOTAS", "CONTRATOS"), repository.findProtectedHistory(10L));
    }

    @Test
    void findProtectedHistory_checksEveryProtectedTable() {
        when(jdbcTemplate.queryForObject(anyString(), any(SqlParameterSource.class), eq(Integer.class)))
                .thenReturn(1);

        List<String> found = repository.findProtectedHistory(10L);

        for (String table : List.of("CUOTAS", "PAGOS", "TRANSACCIONES_PAGO", "MULTAS", "SANCIONES",
                "CONTRATOS", "CARTERA", "PAZ_Y_SALVOS", "ASAMBLEAS", "VOTOS", "ASISTENCIAS_ASAMBLEA",
                "PODERES_REPRESENTACION", "GASTOS", "CONCILIACIONES", "REGISTROS_ACCESO")) {
            assertTrue(found.contains(table), "Missing history check for " + table);
        }
    }

    @Test
    void findProtectedHistory_whenAHistoryQueryFails_propagatesInsteadOfAllowingDeletion() {
        when(jdbcTemplate.queryForObject(anyString(), any(SqlParameterSource.class), eq(Integer.class)))
                .thenThrow(new DataIntegrityViolationException("ORA-00942: table or view does not exist"));

        assertThrows(DataIntegrityViolationException.class, () -> repository.findProtectedHistory(10L));
    }
}
