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

    private final RowMapper<PersonaDTO> rowMapper = (rs, rowNum) -> {
        Long idApto = null;
        String numApto = null;
        String tipoRelacion = null;
        try {
            long val = rs.getLong("ID_APARTAMENTO");
            if (!rs.wasNull()) idApto = val;
        } catch (Exception ignored) {}
        try {
            numApto = rs.getString("NUMERO_APARTAMENTO");
        } catch (Exception ignored) {}
        try {
            tipoRelacion = rs.getString("TIPO_RELACION");
        } catch (Exception ignored) {}

        return new PersonaDTO(
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
                rs.getString("ESTADO"),
                idApto,
                numApto,
                tipoRelacion
        );
    };

    @Override
    public List<PersonaDTO> findAll(int limit, int offset) {
        String sql = """
            SELECT p.*,
                   COALESCE(
                       (SELECT ru.ID_UNIDAD FROM RESIDENTES_UNIDAD ru WHERE ru.ID_PERSONA = p.ID_PERSONA AND ru.ESTADO = 'ACTIVO' AND ROWNUM = 1),
                       (SELECT pu.ID_UNIDAD FROM PROPIETARIOS_UNIDAD pu WHERE pu.ID_PERSONA = p.ID_PERSONA AND pu.ESTADO = 'ACTIVO' AND ROWNUM = 1),
                       (SELECT ru.ID_UNIDAD FROM RESIDENTES_UNIDAD ru WHERE ru.ID_PERSONA = p.ID_PERSONA AND ROWNUM = 1)
                   ) AS ID_APARTAMENTO,
                   COALESCE(
                       (SELECT u.IDENTIFICADOR FROM UNIDADES u JOIN RESIDENTES_UNIDAD ru ON u.ID_UNIDAD = ru.ID_UNIDAD WHERE ru.ID_PERSONA = p.ID_PERSONA AND ru.ESTADO = 'ACTIVO' AND ROWNUM = 1),
                       (SELECT u.IDENTIFICADOR FROM UNIDADES u JOIN PROPIETARIOS_UNIDAD pu ON u.ID_UNIDAD = pu.ID_UNIDAD WHERE pu.ID_PERSONA = p.ID_PERSONA AND pu.ESTADO = 'ACTIVO' AND ROWNUM = 1),
                       (SELECT u.IDENTIFICADOR FROM UNIDADES u JOIN RESIDENTES_UNIDAD ru ON u.ID_UNIDAD = ru.ID_UNIDAD WHERE ru.ID_PERSONA = p.ID_PERSONA AND ROWNUM = 1)
                   ) AS NUMERO_APARTAMENTO,
                   CASE
                       WHEN EXISTS (SELECT 1 FROM PROPIETARIOS_UNIDAD pu WHERE pu.ID_PERSONA = p.ID_PERSONA AND pu.ESTADO = 'ACTIVO')
                            AND EXISTS (SELECT 1 FROM RESIDENTES_UNIDAD ru WHERE ru.ID_PERSONA = p.ID_PERSONA AND ru.ESTADO = 'ACTIVO')
                         THEN 'PROPIETARIO_RESIDENTE'
                       WHEN EXISTS (SELECT 1 FROM PROPIETARIOS_UNIDAD pu WHERE pu.ID_PERSONA = p.ID_PERSONA AND pu.ESTADO = 'ACTIVO')
                         THEN 'PROPIETARIO_NO_RESIDENTE'
                       WHEN EXISTS (SELECT 1 FROM RESIDENTES_UNIDAD ru WHERE ru.ID_PERSONA = p.ID_PERSONA AND ru.ESTADO = 'ACTIVO' AND ru.TIPO_RESIDENTE = 'ARRENDATARIO')
                         THEN 'ARRENDATARIO'
                       WHEN EXISTS (SELECT 1 FROM RESIDENTES_UNIDAD ru WHERE ru.ID_PERSONA = p.ID_PERSONA AND ru.ESTADO = 'ACTIVO' AND ru.TIPO_RESIDENTE IN ('FAMILIAR', 'CONVIVIENTE', 'OTRO'))
                         THEN 'CONVIVIENTE'
                       ELSE COALESCE((SELECT ru.TIPO_RESIDENTE FROM RESIDENTES_UNIDAD ru WHERE ru.ID_PERSONA = p.ID_PERSONA AND ROWNUM = 1), 'RESIDENTE')
                   END AS TIPO_RELACION
            FROM PERSONAS p
            ORDER BY p.ID_PERSONA DESC
            OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", limit)
                .addValue("offset", offset);
        return jdbcTemplate.query(sql, params, rowMapper);
    }

    @Override
    public Optional<PersonaDTO> findById(Long id) {
        String sql = """
            SELECT p.*,
                   COALESCE(
                       (SELECT ru.ID_UNIDAD FROM RESIDENTES_UNIDAD ru WHERE ru.ID_PERSONA = p.ID_PERSONA AND ru.ESTADO = 'ACTIVO' AND ROWNUM = 1),
                       (SELECT pu.ID_UNIDAD FROM PROPIETARIOS_UNIDAD pu WHERE pu.ID_PERSONA = p.ID_PERSONA AND pu.ESTADO = 'ACTIVO' AND ROWNUM = 1),
                       (SELECT ru.ID_UNIDAD FROM RESIDENTES_UNIDAD ru WHERE ru.ID_PERSONA = p.ID_PERSONA AND ROWNUM = 1)
                   ) AS ID_APARTAMENTO,
                   COALESCE(
                       (SELECT u.IDENTIFICADOR FROM UNIDADES u JOIN RESIDENTES_UNIDAD ru ON u.ID_UNIDAD = ru.ID_UNIDAD WHERE ru.ID_PERSONA = p.ID_PERSONA AND ru.ESTADO = 'ACTIVO' AND ROWNUM = 1),
                       (SELECT u.IDENTIFICADOR FROM UNIDADES u JOIN PROPIETARIOS_UNIDAD pu ON u.ID_UNIDAD = pu.ID_UNIDAD WHERE pu.ID_PERSONA = p.ID_PERSONA AND pu.ESTADO = 'ACTIVO' AND ROWNUM = 1),
                       (SELECT u.IDENTIFICADOR FROM UNIDADES u JOIN RESIDENTES_UNIDAD ru ON u.ID_UNIDAD = ru.ID_UNIDAD WHERE ru.ID_PERSONA = p.ID_PERSONA AND ROWNUM = 1)
                   ) AS NUMERO_APARTAMENTO,
                   CASE
                       WHEN EXISTS (SELECT 1 FROM PROPIETARIOS_UNIDAD pu WHERE pu.ID_PERSONA = p.ID_PERSONA AND pu.ESTADO = 'ACTIVO')
                            AND EXISTS (SELECT 1 FROM RESIDENTES_UNIDAD ru WHERE ru.ID_PERSONA = p.ID_PERSONA AND ru.ESTADO = 'ACTIVO')
                         THEN 'PROPIETARIO_RESIDENTE'
                       WHEN EXISTS (SELECT 1 FROM PROPIETARIOS_UNIDAD pu WHERE pu.ID_PERSONA = p.ID_PERSONA AND pu.ESTADO = 'ACTIVO')
                         THEN 'PROPIETARIO_NO_RESIDENTE'
                       WHEN EXISTS (SELECT 1 FROM RESIDENTES_UNIDAD ru WHERE ru.ID_PERSONA = p.ID_PERSONA AND ru.ESTADO = 'ACTIVO' AND ru.TIPO_RESIDENTE = 'ARRENDATARIO')
                         THEN 'ARRENDATARIO'
                       WHEN EXISTS (SELECT 1 FROM RESIDENTES_UNIDAD ru WHERE ru.ID_PERSONA = p.ID_PERSONA AND ru.ESTADO = 'ACTIVO' AND ru.TIPO_RESIDENTE IN ('FAMILIAR', 'CONVIVIENTE', 'OTRO'))
                         THEN 'CONVIVIENTE'
                       ELSE COALESCE((SELECT ru.TIPO_RESIDENTE FROM RESIDENTES_UNIDAD ru WHERE ru.ID_PERSONA = p.ID_PERSONA AND ROWNUM = 1), 'RESIDENTE')
                   END AS TIPO_RELACION
            FROM PERSONAS p
            WHERE p.ID_PERSONA = :id
            """;
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
