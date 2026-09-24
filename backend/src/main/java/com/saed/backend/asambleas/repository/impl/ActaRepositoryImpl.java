package com.saed.backend.asambleas.repository.impl;

import com.saed.backend.asambleas.dto.ActaCreateRequestDTO;
import com.saed.backend.asambleas.dto.ActaDTO;
import com.saed.backend.asambleas.dto.ActaUpdateRequestDTO;
import com.saed.backend.asambleas.repository.ActaRepository;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Repository
public class ActaRepositoryImpl implements ActaRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private static final ZoneId BOGOTA_ZONE = ZoneId.of("America/Bogota");

    public ActaRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<ActaDTO> actaRowMapper = (rs, rowNum) -> {
        ActaDTO dto = new ActaDTO();
        dto.setIdActa(rs.getLong("ID_ACTA"));
        dto.setIdAsamblea(rs.getLong("ID_ASAMBLEA"));
        dto.setNumeroActa(rs.getString("NUMERO_ACTA"));
        dto.setContenidoTexto(rs.getString("CONTENIDO_TEXTO"));
        dto.setDocumentoFirmadoUrl(rs.getString("DOCUMENTO_FIRMADO_URL"));
        
        long idDoc = rs.getLong("ID_DOCUMENTO");
        dto.setIdDocumento(rs.wasNull() ? null : idDoc);

        dto.setEstado(rs.getString("ESTADO"));

        long redPor = rs.getLong("REDACTADA_POR");
        dto.setRedactadaPor(rs.wasNull() ? null : redPor);

        dto.setRedactorNombre(rs.getString("REDACTOR_NOMBRE"));

        Timestamp tsCreacion = rs.getTimestamp("FECHA_CREACION");
        dto.setFechaCreacion(tsCreacion != null ? tsCreacion.toInstant().atZone(BOGOTA_ZONE).toOffsetDateTime() : null);

        // Asamblea join fields
        dto.setIdPropiedad(rs.getLong("ID_PROPIEDAD"));
        dto.setAsambleaTitulo(rs.getString("ASAMBLEA_TITULO"));
        dto.setAsambleaTipo(rs.getString("ASAMBLEA_TIPO"));
        dto.setAsambleaModalidad(rs.getString("ASAMBLEA_MODALIDAD"));
        dto.setAsambleaEstado(rs.getString("ASAMBLEA_ESTADO"));
        dto.setConvocatoriaNumero(rs.getInt("CONVOCATORIA_NUMERO"));

        Timestamp tsConv = rs.getTimestamp("FECHA_HORA_PRIMERA_CONV");
        dto.setFechaHoraPrimeraConv(tsConv != null ? tsConv.toInstant().atZone(BOGOTA_ZONE).toOffsetDateTime() : null);

        dto.setLugarOEnlace(rs.getString("LUGAR_O_ENLACE"));
        dto.setOrdenDelDia(rs.getString("ORDEN_DEL_DIA"));
        dto.setQuorumRequeridoPct(rs.getBigDecimal("QUORUM_REQUERIDO_PCT"));
        dto.setQuorumAlcanzadoPct(rs.getBigDecimal("QUORUM_ALCANZADO_PCT"));

        return dto;
    };

    private static final String BASE_SELECT = """
        SELECT act.ID_ACTA,
               act.ID_ASAMBLEA,
               act.NUMERO_ACTA,
               act.CONTENIDO_TEXTO,
               act.DOCUMENTO_FIRMADO_URL,
               act.ID_DOCUMENTO,
               act.ESTADO,
               act.REDACTADA_POR,
               (COALESCE(p.PRIMER_NOMBRE || ' ' || p.PRIMER_APELLIDO, u.NOMBRE_USUARIO, 'Sistema')) AS REDACTOR_NOMBRE,
               act.FECHA_CREACION,
               asm.ID_PROPIEDAD,
               asm.TITULO AS ASAMBLEA_TITULO,
               asm.TIPO AS ASAMBLEA_TIPO,
               asm.MODALIDAD AS ASAMBLEA_MODALIDAD,
               asm.ESTADO AS ASAMBLEA_ESTADO,
               asm.CONVOCATORIA_NUMERO,
               asm.FECHA_HORA_PRIMERA_CONV,
               asm.LUGAR_O_ENLACE,
               asm.ORDEN_DEL_DIA,
               asm.QUORUM_REQUERIDO_PCT,
               asm.QUORUM_ALCANZADO_PCT
        FROM ACTAS_ASAMBLEA act
        JOIN ASAMBLEAS asm ON act.ID_ASAMBLEA = asm.ID_ASAMBLEA
        LEFT JOIN USUARIOS u ON act.REDACTADA_POR = u.ID_USUARIO
        LEFT JOIN PERSONAS p ON u.ID_PERSONA = p.ID_PERSONA
        """;

    @Override
    public Optional<ActaDTO> findById(Long idActa) {
        String sql = BASE_SELECT + " WHERE act.ID_ACTA = :idActa";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, new MapSqlParameterSource("idActa", idActa), actaRowMapper));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<ActaDTO> findByAsambleaId(Long idAsamblea) {
        String sql = BASE_SELECT + " WHERE act.ID_ASAMBLEA = :idAsamblea";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, new MapSqlParameterSource("idAsamblea", idAsamblea), actaRowMapper));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<ActaDTO> findAllAdmin(Long idPropiedad, Long idOrganizacion, String estado) {
        StringBuilder sql = new StringBuilder(BASE_SELECT).append(" WHERE 1=1 ");
        MapSqlParameterSource params = new MapSqlParameterSource();

        if (idPropiedad != null) {
            sql.append(" AND asm.ID_PROPIEDAD = :idPropiedad ");
            params.addValue("idPropiedad", idPropiedad);
        }
        if (idOrganizacion != null) {
            sql.append(" AND asm.ID_ORGANIZACION = :idOrganizacion ");
            params.addValue("idOrganizacion", idOrganizacion);
        }
        if (estado != null && !estado.isBlank()) {
            sql.append(" AND act.ESTADO = :estado ");
            params.addValue("estado", estado);
        }

        sql.append(" ORDER BY act.ID_ACTA DESC ");
        return jdbcTemplate.query(sql.toString(), params, actaRowMapper);
    }

    @Override
    public List<ActaDTO> findAllResidente(Long idPropiedad) {
        String sql = BASE_SELECT + " WHERE act.ESTADO = 'PUBLICADA_OFICIAL' AND asm.ID_PROPIEDAD = :idPropiedad ORDER BY act.ID_ACTA DESC";
        return jdbcTemplate.query(sql, new MapSqlParameterSource("idPropiedad", idPropiedad), actaRowMapper);
    }

    @Override
    public Long createActa(ActaCreateRequestDTO request, Long idUsuarioRedactor) {
        String sql = """
            INSERT INTO ACTAS_ASAMBLEA (
                ID_ASAMBLEA, NUMERO_ACTA, CONTENIDO_TEXTO, DOCUMENTO_FIRMADO_URL,
                ID_DOCUMENTO, ESTADO, REDACTADA_POR, FECHA_CREACION
            ) VALUES (
                :idAsamblea, :numeroActa, :contenidoTexto, :documentoFirmadoUrl,
                :idDocumento, 'BORRADOR', :redactadaPor, SYSTIMESTAMP
            )
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idAsamblea", request.getIdAsamblea(), java.sql.Types.NUMERIC)
                .addValue("numeroActa", request.getNumeroActa(), java.sql.Types.VARCHAR)
                .addValue("contenidoTexto",
                        request.getContenidoTexto() != null
                                ? new org.springframework.jdbc.core.support.SqlLobValue(request.getContenidoTexto())
                                : null,
                        java.sql.Types.CLOB)
                .addValue("documentoFirmadoUrl", request.getDocumentoFirmadoUrl(), java.sql.Types.VARCHAR)
                .addValue("idDocumento", request.getIdDocumento(), java.sql.Types.NUMERIC)
                .addValue("redactadaPor", idUsuarioRedactor, java.sql.Types.NUMERIC);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, params, keyHolder, new String[]{"ID_ACTA"});
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : null;
    }


    @Override
    public void updateActa(Long idActa, ActaUpdateRequestDTO request) {
        // CLOB columns cannot use COALESCE in Oracle — build dynamic SET clauses instead
        StringBuilder sql = new StringBuilder("UPDATE ACTAS_ASAMBLEA SET NUMERO_ACTA = COALESCE(:numeroActa, NUMERO_ACTA), DOCUMENTO_FIRMADO_URL = COALESCE(:documentoFirmadoUrl, DOCUMENTO_FIRMADO_URL), ID_DOCUMENTO = COALESCE(:idDocumento, ID_DOCUMENTO)");

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idActa", idActa, java.sql.Types.NUMERIC)
                .addValue("numeroActa", request.getNumeroActa(), java.sql.Types.VARCHAR)
                .addValue("documentoFirmadoUrl", request.getDocumentoFirmadoUrl(), java.sql.Types.VARCHAR)
                .addValue("idDocumento", request.getIdDocumento(), java.sql.Types.NUMERIC);

        if (request.getContenidoTexto() != null) {
            sql.append(", CONTENIDO_TEXTO = :contenidoTexto");
            params.addValue("contenidoTexto",
                    new org.springframework.jdbc.core.support.SqlLobValue(request.getContenidoTexto()),
                    java.sql.Types.CLOB);
        }

        sql.append(" WHERE ID_ACTA = :idActa");
        jdbcTemplate.update(sql.toString(), params);
    }


    @Override
    public void updateEstado(Long idActa, String nuevoEstado) {
        String sql = "UPDATE ACTAS_ASAMBLEA SET ESTADO = :nuevoEstado WHERE ID_ACTA = :idActa";
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("idActa", idActa)
                .addValue("nuevoEstado", nuevoEstado));
    }

    @Override
    public void asociarDocumento(Long idActa, Long idDocumento, String documentoUrl) {
        String sql = """
            UPDATE ACTAS_ASAMBLEA
            SET ID_DOCUMENTO = :idDocumento,
                DOCUMENTO_FIRMADO_URL = COALESCE(:documentoUrl, DOCUMENTO_FIRMADO_URL)
            WHERE ID_ACTA = :idActa
            """;
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("idActa", idActa)
                .addValue("idDocumento", idDocumento)
                .addValue("documentoUrl", documentoUrl));
    }

    @Override
    public void lockActaForUpdate(Long idActa) {
        String sql = "SELECT ID_ACTA FROM ACTAS_ASAMBLEA WHERE ID_ACTA = :idActa FOR UPDATE NOWAIT";
        try {
            jdbcTemplate.queryForObject(sql, new MapSqlParameterSource("idActa", idActa), Long.class);
        } catch (EmptyResultDataAccessException e) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND,
                    "Acta no encontrada o sin acceso con ID: " + idActa);
        } catch (org.springframework.dao.DataAccessException e) {
            // ORA-00054 (NOWAIT lock busy → 409), ORA-28115 (VPD update_check → 404)
            String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (msg.contains("ora-00054") || msg.contains("54")) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.CONFLICT,
                        "Acta ID " + idActa + " está siendo modificada por otro proceso");
            }
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND,
                    "Acta no accesible con ID: " + idActa);
        }
    }

    @Override
    public boolean existsByAsambleaId(Long idAsamblea) {
        String sql = "SELECT COUNT(1) FROM ACTAS_ASAMBLEA WHERE ID_ASAMBLEA = :idAsamblea";
        Integer count = jdbcTemplate.queryForObject(sql, new MapSqlParameterSource("idAsamblea", idAsamblea), Integer.class);
        return count != null && count > 0;
    }
}
