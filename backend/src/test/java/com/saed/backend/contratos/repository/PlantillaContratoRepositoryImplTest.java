package com.saed.backend.contratos.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.contratos.dto.PlantillaContratoDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

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

    @Test
    void findByOrganizacionId_cuandoFallaBaseDeDatos_capturaYRetornaListaVaciaSinLanzar500() {
        when(namedParameterJdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.namedparam.SqlParameterSource.class), any(org.springframework.jdbc.core.RowMapper.class)))
                .thenThrow(new BadSqlGrammarException("select", "SELECT * FROM PLANTILLAS_CONTRATOS", new SQLException("ORA-00942: table or view does not exist")));

        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(0);
        doThrow(new RuntimeException("Simulated DDL error on readonly connection"))
                .when(jdbcTemplate).execute(anyString());

        List<PlantillaContratoDTO> result = repository.findByOrganizacionId(1L, null);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void findActivasByOrganizacionId_cuandoFallaBaseDeDatos_capturaYRetornaListaVacia() {
        when(namedParameterJdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.namedparam.SqlParameterSource.class), any(org.springframework.jdbc.core.RowMapper.class)))
                .thenThrow(new BadSqlGrammarException("select", "SELECT * FROM PLANTILLAS_CONTRATOS", new SQLException("ORA-00942: table or view does not exist")));

        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(0);
        doThrow(new RuntimeException("Simulated DDL error on readonly connection"))
                .when(jdbcTemplate).execute(anyString());

        List<PlantillaContratoDTO> result = repository.findActivasByOrganizacionId(1L);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getMaxVersion_cuandoTablaNoExiste_retornaCero() {
        when(namedParameterJdbcTemplate.queryForObject(anyString(), any(org.springframework.jdbc.core.namedparam.SqlParameterSource.class), eq(Integer.class)))
                .thenThrow(new BadSqlGrammarException("select", "SELECT MAX(version)...", new SQLException("ORA-00942")));

        Integer max = repository.getMaxVersion(1L, "CONTRATO_TEST");

        assertEquals(0, max);
    }
}
