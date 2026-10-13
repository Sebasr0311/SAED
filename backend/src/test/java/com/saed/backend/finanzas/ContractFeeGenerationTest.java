package com.saed.backend.finanzas;

import com.saed.backend.finanzas.repository.impl.FinanzasRepositoryImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * F06: the first rent fee of a new contract is generated without MERGE on a policy-protected table, without
 * swallowing database errors and without falling back to an arbitrary charge concept (which could belong to
 * another tenant). Mocked JDBC; no database.
 */
class ContractFeeGenerationTest {

    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final FinanzasRepositoryImpl repo = new FinanzasRepositoryImpl(jdbc);

    @Test
    void theRentConceptIsCreatedWithoutMergeAndTheFeeHasNoFallbackConcept() {
        when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(1);

        repo.generarCuotasIniciales(5L);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(2)).update(sql.capture(), any(SqlParameterSource.class));
        String conceptSql = sql.getAllValues().get(0);
        String feeSql = sql.getAllValues().get(1);
        assertTrue(conceptSql.contains("INSERT INTO CONCEPTOS_COBRO"), conceptSql);
        assertTrue(conceptSql.contains("NOT EXISTS"), conceptSql);
        assertFalse(conceptSql.contains("MERGE"), conceptSql);
        assertTrue(feeSql.contains("INSERT INTO CUOTAS"), feeSql);
        assertFalse(feeSql.contains("COALESCE"), "no fallback to an arbitrary concept: " + feeSql);
    }

    @Test
    void databaseFailuresPropagateInsteadOfLeavingAContractWithoutItsFirstFee() {
        when(jdbc.update(anyString(), any(SqlParameterSource.class)))
                .thenThrow(new DataAccessResourceFailureException("db down"));

        assertThrows(DataAccessResourceFailureException.class, () -> repo.generarCuotasIniciales(5L));
    }
}
