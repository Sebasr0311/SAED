package com.saed.backend.person.repository.impl;

import com.saed.backend.person.dto.PersonaDTO;
import com.saed.backend.person.dto.PersonaRequestDTO;
import com.saed.backend.person.repository.PersonaRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class PersonaRepositoryImpl implements PersonaRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PersonaRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<PersonaDTO> rowMapper = (rs, rowNum) -> new PersonaDTO(
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
            rs.getString("ESTADO")
    );

    @Override
    public List<PersonaDTO> findAll(int limit, int offset) {
        String sql = "SELECT * FROM PERSONAS ORDER BY ID_PERSONA OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", limit)
                .addValue("offset", offset);
        return jdbcTemplate.query(sql, params, rowMapper);
    }

    @Override
    public Optional<PersonaDTO> findById(Long id) {
        String sql = "SELECT * FROM PERSONAS WHERE ID_PERSONA = :id";
        List<PersonaDTO> results = jdbcTemplate.query(sql, new MapSqlParameterSource("id", id), rowMapper);
        return results.stream().findFirst();
    }

    @Override
    public Long insert(PersonaRequestDTO request) {
        String sql = """
            INSERT INTO PERSONAS (
                ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA,
                PRIMER_NOMBRE, SEGUNDO_NOMBRE, PRIMER_APELLIDO, SEGUNDO_APELLIDO,
                EMAIL, TELEFONO
            ) VALUES (
                :tipoDocumentoId, :numeroDocumento, :tipoPersona,
                :primerNombre, :segundoNombre, :primerApellido, :segundoApellido,
                :email, :telefono
            )
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tipoDocumentoId", request.tipoDocumentoId())
                .addValue("numeroDocumento", request.numeroDocumento())
                .addValue("tipoPersona", request.tipoPersona())
                .addValue("primerNombre", request.primerNombre())
                .addValue("segundoNombre", request.segundoNombre())
                .addValue("primerApellido", request.primerApellido())
                .addValue("segundoApellido", request.segundoApellido())
                .addValue("email", request.email())
                .addValue("telefono", request.telefono());

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, params, keyHolder, new String[]{"ID_PERSONA"});
        
        return keyHolder.getKey().longValue();
    }

    @Override
    public void update(Long id, PersonaRequestDTO request) {
        String sql = """
            UPDATE PERSONAS SET
                ID_TIPO_DOCUMENTO = :tipoDocumentoId,
                NUMERO_DOCUMENTO = :numeroDocumento,
                TIPO_PERSONA = :tipoPersona,
                PRIMER_NOMBRE = :primerNombre,
                SEGUNDO_NOMBRE = :segundoNombre,
                PRIMER_APELLIDO = :primerApellido,
                SEGUNDO_APELLIDO = :segundoApellido,
                EMAIL = :email,
                TELEFONO = :telefono
            WHERE ID_PERSONA = :id
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("tipoDocumentoId", request.tipoDocumentoId())
                .addValue("numeroDocumento", request.numeroDocumento())
                .addValue("tipoPersona", request.tipoPersona())
                .addValue("primerNombre", request.primerNombre())
                .addValue("segundoNombre", request.segundoNombre())
                .addValue("primerApellido", request.primerApellido())
                .addValue("segundoApellido", request.segundoApellido())
                .addValue("email", request.email())
                .addValue("telefono", request.telefono());

        jdbcTemplate.update(sql, params);
    }

    @Override
    public void delete(Long id) {
        jdbcTemplate.update("DELETE FROM PERSONAS WHERE ID_PERSONA = :id", new MapSqlParameterSource("id", id));
    }
}
