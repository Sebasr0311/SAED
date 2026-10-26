package com.saed.backend.finanzas.billing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** SQL shape of the billing repository: idempotent inserts, no MERGE, database clock only. */
class BillingRepositorySqlTest {

    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final BillingRepository repo = new BillingRepository(jdbc);

    private static NewFee sampleFee() {
        return new NewFee(1L, 77L, "2026-10", new BigDecimal("50000.00"), new BigDecimal("0.00"),
                new BigDecimal("50000.00"), LocalDate.of(2026, 10, 10), null);
    }

    @Test
    void feeInsertIsIdempotentPerUnitConceptAndPeriod() {
        when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(1, 0);

        assertTrue(repo.insertFeeIfAbsent(sampleFee()));
        assertFalse(repo.insertFeeIfAbsent(sampleFee()));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(2)).update(sql.capture(), any(SqlParameterSource.class));
        String text = sql.getValue();
        assertTrue(text.contains("INSERT INTO CUOTAS"), text);
        assertTrue(text.contains("WHERE NOT EXISTS"), text);
        assertFalse(text.contains("MERGE"), text);
        assertFalse(text.contains("CURRENT_DATE"), text);
        assertTrue(text.contains("ID_UNIDAD") && text.contains("ID_CONCEPTO") && text.contains("PERIODO"), text);
    }

    @Test
    void duplicateKeyRaceCountsAsAlreadyExisting() {
        when(jdbc.update(anyString(), any(SqlParameterSource.class)))
                .thenThrow(new org.springframework.dao.DuplicateKeyException("UQ_CUOTA_UNICA"));

        assertFalse(repo.insertFeeIfAbsent(sampleFee()));
    }

    @Test
    void overdueTransitionOnlyMovesUnpaidPendingFeesPastDueOfThatProperty() {
        when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(2);

        assertEquals(2, repo.markOverdue(100L, LocalDate.of(2026, 10, 11)));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).update(sql.capture(), params.capture());
        assertTrue(sql.getValue().contains("SET ESTADO = 'VENCIDA'"), sql.getValue());
        assertTrue(sql.getValue().contains("ESTADO = 'PENDIENTE'"), sql.getValue());
        assertTrue(sql.getValue().contains("SALDO_PENDIENTE > 0"), sql.getValue());
        assertTrue(sql.getValue().contains("ID_PROPIEDAD = :propId"), sql.getValue());
        assertEquals(100L, params.getValue().getValue("propId"));
    }

    @Test
    void budgetShareReadsOnlyApprovedExpenseLines() {
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(BigDecimal.class)))
                .thenReturn(new BigDecimal("1200000"));

        assertEquals(new BigDecimal("1200000"), repo.approvedAnnualExpenseBudget(100L, 2026));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForObject(sql.capture(), any(SqlParameterSource.class), eq(BigDecimal.class));
        assertTrue(sql.getValue().contains("TIPO = 'EGRESO'"), sql.getValue());
        assertTrue(sql.getValue().contains("ESTADO = 'APROBADO'"), sql.getValue());
    }

    @Test
    void conceptCreationAvoidsMerge() {
        when(jdbc.queryForList(anyString(), any(SqlParameterSource.class), eq(Long.class)))
                .thenReturn(List.of(), List.of(77L));
        when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(1);

        assertEquals(77L, repo.ensureAdministrationConcept(100L));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sql.capture(), any(SqlParameterSource.class));
        assertTrue(sql.getValue().contains("INSERT INTO CONCEPTOS_COBRO"), sql.getValue());
        assertTrue(sql.getValue().contains("NOT EXISTS"), sql.getValue());
        assertFalse(sql.getValue().contains("MERGE"), sql.getValue());
    }

    @Test
    void settingsAreUpsertedWithoutMerge() {
        when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(0, 1);

        repo.saveSettings(BillingSettings.disabled(100L));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(2)).update(sql.capture(), any(SqlParameterSource.class));
        assertTrue(sql.getAllValues().get(0).startsWith("UPDATE CONFIGURACION_COBRO_PROPIEDAD"));
        assertTrue(sql.getAllValues().get(1).startsWith("INSERT INTO CONFIGURACION_COBRO_PROPIEDAD"));
    }
}
