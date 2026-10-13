package com.saed.backend.finanzas;

import com.saed.backend.audit.AuditService;
import com.saed.backend.common.service.EmailService;
import com.saed.backend.convivencia.repository.MultaRepository;
import com.saed.backend.convivencia.service.impl.MultaServiceImpl;
import com.saed.backend.finanzas.service.FineReceivablesAdapter;
import com.saed.backend.sanciones.integration.FineReceivablesPort;
import com.saed.backend.sanciones.integration.FineReceivablesPort.FineReceivable;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * F10-06 integration: a resolved sanction fine is posted to the unit's receivables exactly once.
 * The MULTAS row is the idempotency anchor; a retry (or a crash between the insert and the link)
 * returns the same receivable and never creates a second charge.
 */
class FineReceivablesAdapterTest {
    private static final long FINE = 41L;
    private static final long UNIT = 1000L;

    private NamedParameterJdbcTemplate jdbc;
    private FineReceivablesAdapter adapter;
    private Long linkedFee;
    private Long feeByOrigin;
    private long fineUnit;
    private int inserted;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        adapter = new FineReceivablesAdapter(jdbc);
        linkedFee = null;
        feeByOrigin = null;
        fineUnit = UNIT;
        inserted = 1;
        when(jdbc.queryForList(anyString(), any(SqlParameterSource.class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("FROM MULTAS")) {
                Map<String, Object> row = new HashMap<>();
                row.put("ID_UNIDAD", fineUnit);
                row.put("ID_CUOTA", linkedFee);
                row.put("ESTADO", "IMPUESTA");
                return List.of(row);
            }
            if (sql.contains("FROM CUOTAS")) {
                return feeByOrigin == null ? List.of() : List.of(Map.of("ID_CUOTA", feeByOrigin));
            }
            return List.of();
        });
        when(jdbc.update(contains("INSERT INTO CUOTAS"), any(SqlParameterSource.class))).thenAnswer(invocation -> {
            if (inserted > 0) {
                feeByOrigin = 700L;
            }
            return inserted;
        });
        when(jdbc.update(contains("UPDATE MULTAS"), any(SqlParameterSource.class))).thenReturn(1);
    }

    private static FineReceivable fine(long unit, String amount) {
        return new FineReceivable(FINE, 9L, 100L, unit, 5L, new BigDecimal(amount), "Multa expediente 2026-001");
    }

    @Test
    void postsOneReceivableForTheFineAndLinksIt() {
        Optional<Long> id = adapter.postFineReceivable(fine(UNIT, "250000.00"));

        assertEquals(Optional.of(700L), id);
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).update(contains("INSERT INTO CUOTAS"), params.capture());
        assertEquals(UNIT, params.getValue().getValue("idUnidad"));
        assertEquals(5L, params.getValue().getValue("idConcepto"));
        assertEquals(new BigDecimal("250000.00"), params.getValue().getValue("monto"));
        assertEquals(FINE, params.getValue().getValue("idMulta"));
        verify(jdbc).update(contains("UPDATE MULTAS"), any(SqlParameterSource.class));
    }

    @Test
    void aSecondCallReturnsTheSameReceivableWithoutAnotherCharge() {
        linkedFee = 700L;

        Optional<Long> id = adapter.postFineReceivable(fine(UNIT, "250000.00"));

        assertEquals(Optional.of(700L), id);
        verify(jdbc, never()).update(contains("INSERT INTO CUOTAS"), any(SqlParameterSource.class));
    }

    @Test
    void aRetryAfterACrashBetweenInsertAndLinkFindsTheExistingReceivable() {
        feeByOrigin = 700L;
        inserted = 0;   // NOT EXISTS (fee already posted for this fine) inserts nothing

        Optional<Long> id = adapter.postFineReceivable(fine(UNIT, "250000.00"));

        assertEquals(Optional.of(700L), id);
        verify(jdbc).update(contains("UPDATE MULTAS"), any(SqlParameterSource.class));
    }

    @Test
    void theInsertIsGuardedAgainstAnExistingReceivableOfTheSameFine() {
        adapter.postFineReceivable(fine(UNIT, "250000.00"));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, atLeastOnce()).update(sql.capture(), any(SqlParameterSource.class));
        String insert = sql.getAllValues().stream().filter(s -> s.contains("INSERT INTO CUOTAS")).findFirst().orElseThrow();
        assertTrue(insert.contains("NOT EXISTS"), insert);
        assertTrue(insert.contains("ID_MULTA_ORIGEN"), insert);
        assertFalse(insert.contains("MERGE"), insert);
        assertFalse(insert.contains("CURRENT_DATE"), insert);
        assertTrue(insert.contains("SYSDATE"), insert);
    }

    @Test
    void aFineThatDoesNotExistIsNotFound() {
        when(jdbc.queryForList(contains("FROM MULTAS"), any(SqlParameterSource.class))).thenReturn(List.of());

        assertThrows(NoSuchElementException.class, () -> adapter.postFineReceivable(fine(UNIT, "250000.00")));
        verify(jdbc, never()).update(contains("INSERT INTO CUOTAS"), any(SqlParameterSource.class));
    }

    @Test
    void aFineOfAnotherUnitIsNeverPostedToTheRequestedUnit() {
        fineUnit = 2000L;

        assertThrows(NoSuchElementException.class, () -> adapter.postFineReceivable(fine(UNIT, "250000.00")));
        verify(jdbc, never()).update(contains("INSERT INTO CUOTAS"), any(SqlParameterSource.class));
    }

    @Test
    void aNonPositiveAmountIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> adapter.postFineReceivable(fine(UNIT, "0")));
        verify(jdbc, never()).update(contains("INSERT INTO CUOTAS"), any(SqlParameterSource.class));
    }

    @Test
    void theAdapterImplementsThePort() {
        assertTrue(FineReceivablesPort.class.isAssignableFrom(FineReceivablesAdapter.class));
        assertNotNull(FineReceivablesAdapter.class.getAnnotation(org.springframework.stereotype.Component.class));
    }

    // ---- no second collection path

    @Test
    void aFineAlreadyInTheReceivablesCannotBePaidThroughTheFinePath() {
        NamedParameterJdbcTemplate multaJdbc = mock(NamedParameterJdbcTemplate.class);
        MultaRepository repo = mock(MultaRepository.class);
        com.saed.backend.convivencia.dto.MultaDTO dto = new com.saed.backend.convivencia.dto.MultaDTO();
        dto.setEstado("IMPUESTA");
        when(repo.findById(FINE)).thenReturn(dto);
        when(multaJdbc.queryForList(contains("ID_CUOTA"), any(SqlParameterSource.class)))
                .thenReturn(List.of(Map.of("ID_CUOTA", 700L)));
        MultaServiceImpl service = new MultaServiceImpl(repo, mock(EmailService.class), multaJdbc, mock(AuditService.class));

        assertThrows(IllegalStateException.class, () -> service.pagar(FINE, "EFECTIVO"));

        verify(repo, never()).updateEstado(anyLong(), anyString());
    }

    @Test
    void aMissingFineIsNotFoundWhenPaying() {
        MultaRepository repo = mock(MultaRepository.class);
        MultaServiceImpl service = new MultaServiceImpl(repo, mock(EmailService.class),
                mock(NamedParameterJdbcTemplate.class), mock(AuditService.class));

        assertThrows(NoSuchElementException.class, () -> service.pagar(FINE, "EFECTIVO"));
    }
}
