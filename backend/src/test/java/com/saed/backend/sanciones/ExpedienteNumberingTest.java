package com.saed.backend.sanciones;

import com.saed.backend.sanciones.repository.impl.SancionRepositoryImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.time.Year;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** F10-16: case numbers come from the highest existing number under a property lock, not from COUNT(*)+1. */
class ExpedienteNumberingTest {

    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final SancionRepositoryImpl repository = new SancionRepositoryImpl(jdbc);

    @Test
    void thePropertyIsLockedBeforeTheNextNumberIsRead() {
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Integer.class))).thenReturn(8);

        String number = repository.generarSiguienteExpediente(7L);

        assertEquals("EXP-" + Year.now().getValue() + "-P7-0008", number);
        InOrder order = inOrder(jdbc);
        ArgumentCaptor<String> lock = ArgumentCaptor.forClass(String.class);
        order.verify(jdbc).queryForList(lock.capture(), any(SqlParameterSource.class));
        assertTrue(lock.getValue().contains("FROM PROPIEDADES") && lock.getValue().contains("FOR UPDATE"));
        ArgumentCaptor<String> next = ArgumentCaptor.forClass(String.class);
        order.verify(jdbc).queryForObject(next.capture(), any(SqlParameterSource.class), eq(Integer.class));
        assertTrue(next.getValue().contains("MAX("), next.getValue());
        assertTrue(!next.getValue().contains("COUNT("), next.getValue());
    }

    @Test
    void thePropertyLockQueryReturnsNothingOnAMock() {
        when(jdbc.queryForList(anyString(), any(SqlParameterSource.class))).thenReturn(List.of());
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Integer.class))).thenReturn(1);
        assertEquals("EXP-" + Year.now().getValue() + "-P3-0001", repository.generarSiguienteExpediente(3L));
    }
}
