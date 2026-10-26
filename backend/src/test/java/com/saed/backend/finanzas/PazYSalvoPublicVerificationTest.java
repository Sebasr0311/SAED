package com.saed.backend.finanzas;

import com.saed.backend.common.service.FileStorageService;
import com.saed.backend.common.service.PdfService;
import com.saed.backend.finanzas.service.impl.PazYSalvoServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import com.saed.backend.context.SaedContextHolder;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The public verification endpoint: unguessable codes (at least 128 random bits), a minimal authenticity answer
 * (no balance, reason or internal ids), and no silent failure while switching the database context. No database.
 */
class PazYSalvoPublicVerificationTest {

    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final JdbcOperations ops = mock(JdbcOperations.class);
    private final PazYSalvoServiceImpl service =
            new PazYSalvoServiceImpl(jdbc, mock(FileStorageService.class), mock(PdfService.class));

    @AfterEach
    void tearDown() {
        SaedContextHolder.clearContext();
    }

    @Test
    void verificationCodesCarryAtLeast128BitsAndDoNotRepeat() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String code = PazYSalvoServiceImpl.nuevoCodigoVerificacion();
            assertTrue(code.length() >= 32 && code.length() <= 64, code);
            assertTrue(code.matches("[A-Za-z0-9_-]+"), code);
            assertTrue(seen.add(code));
        }
    }

    @Test
    void theAnswerOnlyAuthenticatesTheCertificate() {
        when(jdbc.getJdbcOperations()).thenReturn(ops);
        Map<String, Object> row = new HashMap<>();
        row.put("NUMERO_UNIDAD", "101");
        row.put("NOMBRE_PROPIEDAD", "Torres");
        row.put("CODIGO_VERIFICACION", "abc");
        row.put("FECHA_EMISION", LocalDate.now().minusDays(1));
        row.put("FECHA_VENCIMIENTO", LocalDate.now().plusDays(20));
        row.put("DOCUMENTO_HASH", "hash");
        row.put("ESTADO", "VALIDO");
        when(jdbc.queryForList(anyString(), any(SqlParameterSource.class))).thenReturn(List.of(row));

        Map<String, Object> answer = service.verificarPorCodigo("abc");

        assertEquals(Boolean.TRUE, answer.get("esValido"));
        for (String forbidden : List.of("SALDO_A_LA_FECHA", "MOTIVO", "ID_UNIDAD", "ID_PAZ_SALVO")) {
            assertFalse(answer.containsKey(forbidden), forbidden);
        }
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture(), any(SqlParameterSource.class));
        for (String column : List.of("SALDO_A_LA_FECHA", "MOTIVO", "p.ID_UNIDAD", "p.ID_PAZ_SALVO")) {
            assertFalse(sql.getValue().substring(0, sql.getValue().indexOf(" FROM ")).contains(column), column);
        }
    }

    @Test
    void unknownCodesAnswerNothing() {
        when(jdbc.getJdbcOperations()).thenReturn(ops);
        when(jdbc.queryForList(anyString(), any(SqlParameterSource.class))).thenReturn(List.of());
        assertNull(service.verificarPorCodigo("nope"));
    }

    @Test
    void aFailureToSwitchTheDatabaseContextIsNotSwallowed() {
        when(jdbc.getJdbcOperations()).thenReturn(ops);
        org.mockito.Mockito.doThrow(new org.springframework.dao.DataAccessResourceFailureException("down"))
                .when(ops).execute(anyString());
        assertThrows(RuntimeException.class, () -> service.verificarPorCodigo("abc"));
    }
}
