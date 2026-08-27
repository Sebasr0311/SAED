package com.saed.backend.person.repository.impl;

import com.saed.backend.person.dto.PersonaDTO;
import com.saed.backend.person.dto.UnitOwnerDTO;
import com.saed.backend.person.dto.UnitOwnerRequestDTO;
import com.saed.backend.person.dto.UnitResidentDTO;
import com.saed.backend.person.dto.UnitResidentRequestDTO;
import com.saed.backend.person.repository.UnitInhabitantRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class UnitInhabitantRepositoryImpl implements UnitInhabitantRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public UnitInhabitantRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<PersonaDTO> personaRowMapper = (rs, rowNum) -> new PersonaDTO(
            rs.getLong("ID_PERSONA"),
            rs.getLong("ID_TIPO_DOCUMENTO"),
            rs.getString("NUMERO_DOCUMENTO"),
            rs.getString("TIPO_PERSONA"),
            rs.getString("PRIMER_NOMBRE"),
            rs.getString("SEGUNDO_NOMBRE"),
            rs.getString("PRIMER_APELLIDO"),
            rs.getString("SEGUNDO_APELLIDO"),
            rs.getString("EMAIL"),
            rs.getString("TELEFONO"),
            rs.getString("ESTADO_PERSONA")
    );

    private final RowMapper<UnitOwnerDTO> ownerRowMapper = (rs, rowNum) -> {
        PersonaDTO persona = personaRowMapper.mapRow(rs, rowNum);
        return new UnitOwnerDTO(
                rs.getLong("ID_PROPIETARIO_UNIDAD"),
                persona,
                rs.getBigDecimal("PORCENTAJE_PROPIEDAD"),
                rs.getString("ES_PRINCIPAL"),
                rs.getDate("FECHA_INICIO") != null ? rs.getDate("FECHA_INICIO").toLocalDate() : null,
                rs.getDate("FECHA_FIN") != null ? rs.getDate("FECHA_FIN").toLocalDate() : null,
                rs.getString("ESTADO_PROPIETARIO")
        );
    };

    private final RowMapper<UnitResidentDTO> residentRowMapper = (rs, rowNum) -> {
        PersonaDTO persona = personaRowMapper.mapRow(rs, rowNum);
        return new UnitResidentDTO(
                rs.getLong("ID_RESIDENTE_UNIDAD"),
                persona,
                rs.getString("TIPO_RESIDENTE"),
                rs.getDate("FECHA_INICIO") != null ? rs.getDate("FECHA_INICIO").toLocalDate() : null,
                rs.getDate("FECHA_FIN") != null ? rs.getDate("FECHA_FIN").toLocalDate() : null,
                rs.getString("ESTADO_RESIDENTE")
        );
    };

    @Override
    public List<UnitOwnerDTO> findOwnersByUnitId(Long unitId) {
        String sql = """
            SELECT pu.ID_PROPIETARIO_UNIDAD, pu.PORCENTAJE_PROPIEDAD, pu.ES_PRINCIPAL, pu.FECHA_INICIO, pu.FECHA_FIN, pu.ESTADO as ESTADO_PROPIETARIO,
                   p.ID_PERSONA, p.ID_TIPO_DOCUMENTO, p.NUMERO_DOCUMENTO, p.TIPO_PERSONA, p.PRIMER_NOMBRE, p.SEGUNDO_NOMBRE, 
                   p.PRIMER_APELLIDO, p.SEGUNDO_APELLIDO, p.EMAIL, p.TELEFONO, p.ESTADO as ESTADO_PERSONA
            FROM PROPIETARIOS_UNIDAD pu
            JOIN PERSONAS p ON pu.ID_PERSONA = p.ID_PERSONA
            WHERE pu.ID_UNIDAD = :unitId
            ORDER BY pu.ES_PRINCIPAL DESC, pu.PORCENTAJE_PROPIEDAD DESC
            """;
        return jdbcTemplate.query(sql, new MapSqlParameterSource("unitId", unitId), ownerRowMapper);
    }

    @Override
    public Long insertOwner(Long unitId, UnitOwnerRequestDTO request) {
        String sql = """
            INSERT INTO PROPIETARIOS_UNIDAD (
                ID_UNIDAD, ID_PERSONA, PORCENTAJE_PROPIEDAD, ES_PRINCIPAL
            ) VALUES (
                :unitId, :personaId, :porcentajePropiedad, :esPrincipal
            )
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("unitId", unitId)
                .addValue("personaId", request.personaId())
                .addValue("porcentajePropiedad", request.porcentajePropiedad())
                .addValue("esPrincipal", request.esPrincipal());

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, params, keyHolder, new String[]{"ID_PROPIETARIO_UNIDAD"});
        
        return keyHolder.getKey().longValue();
    }

    @Override
    public List<UnitResidentDTO> findResidentsByUnitId(Long unitId) {
        String sql = """
            SELECT ru.ID_RESIDENTE_UNIDAD, ru.TIPO_RESIDENTE, ru.FECHA_INICIO, ru.FECHA_FIN, ru.ESTADO as ESTADO_RESIDENTE,
                   p.ID_PERSONA, p.ID_TIPO_DOCUMENTO, p.NUMERO_DOCUMENTO, p.TIPO_PERSONA, p.PRIMER_NOMBRE, p.SEGUNDO_NOMBRE, 
                   p.PRIMER_APELLIDO, p.SEGUNDO_APELLIDO, p.EMAIL, p.TELEFONO, p.ESTADO as ESTADO_PERSONA
            FROM RESIDENTES_UNIDAD ru
            JOIN PERSONAS p ON ru.ID_PERSONA = p.ID_PERSONA
            WHERE ru.ID_UNIDAD = :unitId
            ORDER BY ru.FECHA_INICIO DESC
            """;
        return jdbcTemplate.query(sql, new MapSqlParameterSource("unitId", unitId), residentRowMapper);
    }

    @Override
    public Long insertResident(Long unitId, UnitResidentRequestDTO request) {
        String sql = """
            INSERT INTO RESIDENTES_UNIDAD (
                ID_UNIDAD, ID_PERSONA, TIPO_RESIDENTE
            ) VALUES (
                :unitId, :personaId, :tipoResidente
            )
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("unitId", unitId)
                .addValue("personaId", request.personaId())
                .addValue("tipoResidente", request.tipoResidente());

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, params, keyHolder, new String[]{"ID_RESIDENTE_UNIDAD"});
        
        return keyHolder.getKey().longValue();
    }
}
