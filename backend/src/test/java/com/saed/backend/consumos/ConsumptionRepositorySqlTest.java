package com.saed.backend.consumos;

import com.saed.backend.consumos.repository.impl.MedicionConsumoRepositoryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** F11-03: SQL contract of the consumption repository (common-area filter, last reading, average, trend). */
class ConsumptionRepositorySqlTest {

    private NamedParameterJdbcTemplate jdbc;
    private MedicionConsumoRepositoryImpl repo;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        repo = new MedicionConsumoRepositoryImpl(jdbc);
        when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());
    }

    private String capturedQuerySql() {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(SqlParameterSource.class), any(RowMapper.class));
        return sql.getValue();
    }

    @Test
    void commonAreaListingSelectsOnlyReadingsWithoutUnit() {
        repo.findAllByPropiedad(10L, "2026-05", null, null, true);
        assertTrue(capturedQuerySql().contains("m.ID_UNIDAD IS NULL"));
    }

    @Test
    void listingWithoutFiltersDoesNotRestrictTheUnit() {
        repo.findAllByPropiedad(10L, "2026-05", null, null, false);
        assertFalse(capturedQuerySql().contains("ID_UNIDAD IS NULL"));
    }

    @Test
    void lastReadingOfACommonAreaMeterIgnoresUnitMetersAndLaterPeriods() {
        repo.findUltimaLectura(10L, null, "M-1", "AGUA", "2026-05");
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).query(sql.capture(), params.capture(), any(RowMapper.class));
        assertTrue(sql.getValue().contains("m.ID_UNIDAD IS NULL"));
        assertTrue(sql.getValue().contains("m.PERIODO < :periodo"));
        assertEquals("2026-05", ((MapSqlParameterSource) params.getValue()).getValue("periodo"));
    }

    @Test
    void historicalAverageExcludesTheEditedReadingAndScopesCommonAreas() {
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(BigDecimal.class)))
            .thenReturn(BigDecimal.TEN);
        repo.calcularPromedioHistorico(10L, null, "M-1", "AGUA", 55L);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).queryForObject(sql.capture(), params.capture(), eq(BigDecimal.class));
        assertTrue(sql.getValue().contains("ID_UNIDAD IS NULL"));
        assertTrue(sql.getValue().contains("ID_MEDICION <> :excluir"));
        assertEquals(55L, ((MapSqlParameterSource) params.getValue()).getValue("excluir"));
    }

    @Test
    void trendIsLimitedToTheRequestedNumberOfPeriods() {
        repo.getTendencias(10L, null, 6, null);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).query(sql.capture(), params.capture(), any(RowMapper.class));
        assertTrue(sql.getValue().contains("PERIODO >= :desde"));
        assertTrue(((MapSqlParameterSource) params.getValue()).getValue("desde").toString().matches("\\d{4}-\\d{2}"));
    }
}
