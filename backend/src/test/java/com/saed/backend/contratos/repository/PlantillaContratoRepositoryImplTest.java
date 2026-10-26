package com.saed.backend.contratos.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * F01-01: PLANTILLAS_CONTRATOS is owned by versioned migrations. Database failures must propagate to the
 * global handler (bounded diagnostic + 500) instead of running DDL at request time or being reported as
 * "no templates" / "version 0".
 */
@ExtendWith(MockitoExtension.class)
class PlantillaContratoRepositoryImplTest {

    @Mock
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private PlantillaContratoRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        lenient().when(namedParameterJdbcTemplate.getJdbcTemplate()).thenReturn(jdbcTemplate);
        repository = new PlantillaContratoRepositoryImpl(namedParameterJdbcTemplate, new ObjectMapper());
    }

    private static BadSqlGrammarException missingTable() {
        return new BadSqlGrammarException("select", "SELECT * FROM PLANTILLAS_CONTRATOS",
                new SQLException("ORA-00942: table or view does not exist", "42000", 942));
    }

    @Test
    @SuppressWarnings("unchecked")
    void findByOrganizacionId_databaseFailurePropagatesWithoutRequestTimeDdl() {
        when(namedParameterJdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenThrow(missingTable());

        assertThrows(DataAccessException.class, () -> repository.findByOrganizacionId(1L, null));
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void findActivasByOrganizacionId_databaseFailurePropagatesWithoutRequestTimeDdl() {
        when(namedParameterJdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenThrow(missingTable());

        assertThrows(DataAccessException.class, () -> repository.findActivasByOrganizacionId(1L));
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void getMaxVersion_databaseFailurePropagatesInsteadOfRestartingVersioningAtZero() {
        when(namedParameterJdbcTemplate.queryForObject(anyString(), any(SqlParameterSource.class), eq(Integer.class)))
                .thenThrow(missingTable());

        assertThrows(DataAccessException.class, () -> repository.getMaxVersion(1L, "CONTRATO_TEST"));
    }
}
