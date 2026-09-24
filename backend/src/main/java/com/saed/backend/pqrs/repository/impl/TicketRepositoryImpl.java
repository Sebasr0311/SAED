package com.saed.backend.pqrs.repository.impl;

import com.saed.backend.pqrs.dto.PqrsSlaConfigDTO;
import com.saed.backend.pqrs.dto.TicketRequestDTO;
import com.saed.backend.pqrs.dto.TicketResponseDTO;
import com.saed.backend.pqrs.dto.TicketTrazabilidadDTO;
import com.saed.backend.pqrs.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class TicketRepositoryImpl implements TicketRepository {

    private static final Logger log = LoggerFactory.getLogger(TicketRepositoryImpl.class);
    private static final ZoneId BOGOTA_ZONE = ZoneId.of("America/Bogota");

    private final JdbcTemplate jdbcTemplate;

    public TicketRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<TicketResponseDTO> ticketRowMapper = (rs, rowNum) -> {
        TicketResponseDTO dto = new TicketResponseDTO();
        dto.setIdTicket(rs.getLong("ID_TICKET"));
        dto.setIdPropiedad(rs.getLong("ID_PROPIEDAD"));
        
        long idUnidad = rs.getLong("ID_UNIDAD");
        if (!rs.wasNull()) dto.setIdUnidad(idUnidad);

        long personaRadica = rs.getLong("ID_PERSONA_RADICA");
        if (!rs.wasNull()) dto.setIdPersonaRadica(personaRadica);

        dto.setNumeroRadicado(rs.getString("NUMERO_RADICADO"));
        dto.setTipo(rs.getString("TIPO"));
        dto.setCategoria(rs.getString("CATEGORIA"));
        dto.setPrioridad(rs.getString("PRIORIDAD"));
        dto.setAsunto(rs.getString("ASUNTO"));
        dto.setDescripcion(rs.getString("DESCRIPCION"));
        dto.setAdjuntosUrl(rs.getString("ADJUNTOS_URL"));
        dto.setEstado(rs.getString("ESTADO"));

        Timestamp radicado = rs.getTimestamp("FECHA_RADICACION");
        if (radicado != null) dto.setFechaRadicacion(radicado.toInstant().atZone(BOGOTA_ZONE));

        Timestamp limite = rs.getTimestamp("FECHA_LIMITE_SLA");
        if (limite != null) dto.setFechaLimiteSla(limite.toInstant().atZone(BOGOTA_ZONE));

        long responsable = rs.getLong("RESPONSABLE_ASIGNADO");
        if (!rs.wasNull()) dto.setResponsableAsignado(responsable);

        int calificacion = rs.getInt("CALIFICACION_SERVICIO");
        if (!rs.wasNull()) dto.setCalificacionServicio(calificacion);

        dto.setObservacionCierre(rs.getString("OBSERVACION_CIERRE"));

        Timestamp cierre = rs.getTimestamp("FECHA_CIERRE");
        if (cierre != null) dto.setFechaCierre(cierre.toInstant().atZone(BOGOTA_ZONE));

        // Enriched metadata from joins if present
        safeSetString(rs, "IDENTIFICADOR_UNIDAD", dto::setIdentificadorUnidad);
        safeSetString(rs, "NOMBRE_RADICADOR", dto::setNombreRadicador);
        safeSetString(rs, "NOMBRE_RESPONSABLE", dto::setNombreResponsable);
        safeSetString(rs, "ULTIMA_RESPUESTA", dto::setUltimaRespuesta);

        dto.calcularSlaMetrics();
        return dto;
    };

    private void safeSetString(ResultSet rs, String column, java.util.function.Consumer<String> setter) {
        try {
            String val = rs.getString(column);
            if (val != null && !val.isBlank()) {
                setter.accept(val.trim());
            }
        } catch (SQLException ignored) {}
    }

    private final RowMapper<TicketTrazabilidadDTO> trazabilidadRowMapper = (rs, rowNum) -> {
        TicketTrazabilidadDTO dto = new TicketTrazabilidadDTO();
        dto.setIdTrazabilidad(rs.getLong("ID_TRAZABILIDAD"));
        dto.setIdTicket(rs.getLong("ID_TICKET"));

        long usr = rs.getLong("ID_USUARIO");
        if (!rs.wasNull()) dto.setIdUsuario(usr);

        safeSetString(rs, "NOMBRE_USUARIO", dto::setNombreUsuario);
        dto.setTipoIntervencion(rs.getString("TIPO_INTERVENCION"));
        dto.setComentario(rs.getString("COMENTARIO"));
        dto.setAdjuntoUrl(rs.getString("ADJUNTO_URL"));
        dto.setEstadoAnterior(rs.getString("ESTADO_ANTERIOR"));
        dto.setEstadoNuevo(rs.getString("ESTADO_NUEVO"));

        Timestamp ts = rs.getTimestamp("FECHA_HORA");
        if (ts != null) dto.setFechaHora(ts.toInstant().atZone(BOGOTA_ZONE));

        return dto;
    };

    @Override
    public List<TicketResponseDTO> findAll(Long idPropiedad) {
        String sql = """
            SELECT q.*,
                   u.IDENTIFICADOR AS IDENTIFICADOR_UNIDAD,
                   p.PRIMER_NOMBRE || ' ' || p.PRIMER_APELLIDO AS NOMBRE_RADICADOR,
                   (SELECT p2.PRIMER_NOMBRE || ' ' || p2.PRIMER_APELLIDO FROM USUARIOS u2 JOIN PERSONAS p2 ON u2.ID_PERSONA = p2.ID_PERSONA WHERE u2.ID_USUARIO = q.RESPONSABLE_ASIGNADO) AS NOMBRE_RESPONSABLE,
                   (SELECT t.COMENTARIO FROM PQRS_TRAZABILIDAD t WHERE t.ID_TICKET = q.ID_TICKET ORDER BY t.FECHA_HORA DESC, t.ID_TRAZABILIDAD DESC FETCH FIRST 1 ROWS ONLY) AS ULTIMA_RESPUESTA
            FROM PQRS_TICKETS q
            LEFT JOIN UNIDADES u ON q.ID_UNIDAD = u.ID_UNIDAD
            LEFT JOIN PERSONAS p ON q.ID_PERSONA_RADICA = p.ID_PERSONA
            WHERE (:propId IS NULL OR q.ID_PROPIEDAD = :propId)
            ORDER BY q.FECHA_RADICACION DESC
        """.replace(":propId", String.valueOf(idPropiedad));
        return jdbcTemplate.query(sql, ticketRowMapper);
    }

    @Override
    public List<TicketResponseDTO> findByPersona(Long idPropiedad, Long idUsuario) {
        Long idPersona = getIdPersonaFromUsuario(idUsuario);
        String sql = """
            SELECT q.*,
                   u.IDENTIFICADOR AS IDENTIFICADOR_UNIDAD,
                   p.PRIMER_NOMBRE || ' ' || p.PRIMER_APELLIDO AS NOMBRE_RADICADOR,
                   (SELECT p2.PRIMER_NOMBRE || ' ' || p2.PRIMER_APELLIDO FROM USUARIOS u2 JOIN PERSONAS p2 ON u2.ID_PERSONA = p2.ID_PERSONA WHERE u2.ID_USUARIO = q.RESPONSABLE_ASIGNADO) AS NOMBRE_RESPONSABLE,
                   (SELECT t.COMENTARIO FROM PQRS_TRAZABILIDAD t WHERE t.ID_TICKET = q.ID_TICKET ORDER BY t.FECHA_HORA DESC, t.ID_TRAZABILIDAD DESC FETCH FIRST 1 ROWS ONLY) AS ULTIMA_RESPUESTA
            FROM PQRS_TICKETS q
            LEFT JOIN UNIDADES u ON q.ID_UNIDAD = u.ID_UNIDAD
            LEFT JOIN PERSONAS p ON q.ID_PERSONA_RADICA = p.ID_PERSONA
            WHERE (:propId IS NULL OR q.ID_PROPIEDAD = :propId)
              AND q.ID_PERSONA_RADICA = ?
            ORDER BY q.FECHA_RADICACION DESC
        """.replace(":propId", String.valueOf(idPropiedad));
        return jdbcTemplate.query(sql, ticketRowMapper, idPersona);
    }

    @Override
    public Optional<TicketResponseDTO> findById(Long idTicket, Long idPropiedad) {
        String sql = """
            SELECT q.*,
                   u.IDENTIFICADOR AS IDENTIFICADOR_UNIDAD,
                   p.PRIMER_NOMBRE || ' ' || p.PRIMER_APELLIDO AS NOMBRE_RADICADOR,
                   (SELECT p2.PRIMER_NOMBRE || ' ' || p2.PRIMER_APELLIDO FROM USUARIOS u2 JOIN PERSONAS p2 ON u2.ID_PERSONA = p2.ID_PERSONA WHERE u2.ID_USUARIO = q.RESPONSABLE_ASIGNADO) AS NOMBRE_RESPONSABLE,
                   (SELECT t.COMENTARIO FROM PQRS_TRAZABILIDAD t WHERE t.ID_TICKET = q.ID_TICKET ORDER BY t.FECHA_HORA DESC, t.ID_TRAZABILIDAD DESC FETCH FIRST 1 ROWS ONLY) AS ULTIMA_RESPUESTA
            FROM PQRS_TICKETS q
            LEFT JOIN UNIDADES u ON q.ID_UNIDAD = u.ID_UNIDAD
            LEFT JOIN PERSONAS p ON q.ID_PERSONA_RADICA = p.ID_PERSONA
            WHERE q.ID_TICKET = ? AND (:propId IS NULL OR q.ID_PROPIEDAD = :propId)
        """.replace(":propId", String.valueOf(idPropiedad));
        List<TicketResponseDTO> results = jdbcTemplate.query(sql, ticketRowMapper, idTicket);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public Optional<TicketResponseDTO> findByIdGlobal(Long idTicket) {
        String sql = """
            SELECT q.*,
                   u.IDENTIFICADOR AS IDENTIFICADOR_UNIDAD,
                   p.PRIMER_NOMBRE || ' ' || p.PRIMER_APELLIDO AS NOMBRE_RADICADOR,
                   (SELECT p2.PRIMER_NOMBRE || ' ' || p2.PRIMER_APELLIDO FROM USUARIOS u2 JOIN PERSONAS p2 ON u2.ID_PERSONA = p2.ID_PERSONA WHERE u2.ID_USUARIO = q.RESPONSABLE_ASIGNADO) AS NOMBRE_RESPONSABLE,
                   (SELECT t.COMENTARIO FROM PQRS_TRAZABILIDAD t WHERE t.ID_TICKET = q.ID_TICKET ORDER BY t.FECHA_HORA DESC, t.ID_TRAZABILIDAD DESC FETCH FIRST 1 ROWS ONLY) AS ULTIMA_RESPUESTA
            FROM PQRS_TICKETS q
            LEFT JOIN UNIDADES u ON q.ID_UNIDAD = u.ID_UNIDAD
            LEFT JOIN PERSONAS p ON q.ID_PERSONA_RADICA = p.ID_PERSONA
            WHERE q.ID_TICKET = ?
        """;
        List<TicketResponseDTO> results = jdbcTemplate.query(sql, ticketRowMapper, idTicket);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public Long create(TicketRequestDTO request, Long idPropiedad, Long idUnidad, Long idUsuario, String numeroRadicado, ZonedDateTime fechaRadicacion, ZonedDateTime fechaLimiteSla) {
        Long idPersonaRadica = getIdPersonaFromUsuario(idUsuario);
        String sql = """
            INSERT INTO PQRS_TICKETS (
                ID_PROPIEDAD, ID_UNIDAD, ID_PERSONA_RADICA, NUMERO_RADICADO,
                TIPO, CATEGORIA, PRIORIDAD, ASUNTO, DESCRIPCION, ADJUNTOS_URL,
                FECHA_RADICACION, FECHA_LIMITE_SLA
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"ID_TICKET"});
            int idx = 1;
            ps.setLong(idx++, idPropiedad);
            if (idUnidad != null) {
                ps.setLong(idx++, idUnidad);
            } else {
                ps.setNull(idx++, Types.NUMERIC);
            }
            ps.setLong(idx++, idPersonaRadica);
            ps.setString(idx++, numeroRadicado);
            ps.setString(idx++, request.getTipo());
            ps.setString(idx++, request.getCategoria());
            ps.setString(idx++, request.getPrioridad());
            ps.setString(idx++, request.getAsunto());
            ps.setString(idx++, request.getDescripcion());
            if (request.getAdjuntosUrl() != null) {
                ps.setString(idx++, request.getAdjuntosUrl());
            } else {
                ps.setNull(idx++, Types.VARCHAR);
            }
            ps.setTimestamp(idx++, Timestamp.from(fechaRadicacion.toInstant()));
            ps.setTimestamp(idx++, Timestamp.from(fechaLimiteSla.toInstant()));
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : null;
    }

    @Override
    public int updateEstado(Long idTicket, Long idPropiedad, String estadoAnterior, String nuevoEstado, String observacionCierre) {
        String sql = """
            UPDATE PQRS_TICKETS
            SET ESTADO = ?,
                OBSERVACION_CIERRE = COALESCE(?, OBSERVACION_CIERRE),
                FECHA_CIERRE = CASE WHEN ? = 'CERRADO' THEN FROM_TZ(CAST(SYSTIMESTAMP AS TIMESTAMP), 'America/Bogota') ELSE FECHA_CIERRE END
            WHERE ID_TICKET = ? AND (:propClause) AND ESTADO = ?
        """.replace(":propClause", idPropiedad == null ? "1=1" : "ID_PROPIEDAD = ?");
        return jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql);
            int idx = 1;
            ps.setString(idx++, nuevoEstado);
            if (observacionCierre != null) {
                ps.setString(idx++, observacionCierre);
            } else {
                ps.setNull(idx++, Types.VARCHAR);
            }
            ps.setString(idx++, nuevoEstado);
            ps.setLong(idx++, idTicket);
            if (idPropiedad != null) {
                ps.setLong(idx++, idPropiedad);
            }
            ps.setString(idx++, estadoAnterior);
            return ps;
        });
    }

    @Override
    public int asignarResponsable(Long idTicket, Long idPropiedad, Long idResponsable) {
        String sql = """
            UPDATE PQRS_TICKETS
            SET RESPONSABLE_ASIGNADO = ?,
                ESTADO = CASE WHEN ESTADO = 'RADICADO' THEN 'ASIGNADO' ELSE ESTADO END
            WHERE ID_TICKET = ? AND (:propClause)
        """.replace(":propClause", idPropiedad == null ? "1=1" : "ID_PROPIEDAD = ?");
        return jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql);
            int idx = 1;
            if (idResponsable != null) {
                ps.setLong(idx++, idResponsable);
            } else {
                ps.setNull(idx++, Types.NUMERIC);
            }
            ps.setLong(idx++, idTicket);
            if (idPropiedad != null) {
                ps.setLong(idx++, idPropiedad);
            }
            return ps;
        });
    }

    @Override
    public int actualizarPrioridad(Long idTicket, Long idPropiedad, String prioridad, ZonedDateTime nuevaFechaSla) {
        String sql = """
            UPDATE PQRS_TICKETS
            SET PRIORIDAD = ?,
                FECHA_LIMITE_SLA = COALESCE(?, FECHA_LIMITE_SLA)
            WHERE ID_TICKET = ? AND (:propClause)
        """.replace(":propClause", idPropiedad == null ? "1=1" : "ID_PROPIEDAD = ?");
        return jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql);
            int idx = 1;
            ps.setString(idx++, prioridad);
            if (nuevaFechaSla != null) {
                ps.setTimestamp(idx++, Timestamp.from(nuevaFechaSla.toInstant()));
            } else {
                ps.setNull(idx++, Types.TIMESTAMP);
            }
            ps.setLong(idx++, idTicket);
            if (idPropiedad != null) {
                ps.setLong(idx++, idPropiedad);
            }
            return ps;
        });
    }

    @Override
    public void insertTrazabilidad(Long idTicket, Long idUsuario, String tipoIntervencion, String estadoAnterior, String estadoNuevo, String comentario, String adjuntoUrl) {
        String sql = """
            INSERT INTO PQRS_TRAZABILIDAD (
                ID_TICKET, ID_USUARIO, TIPO_INTERVENCION, ESTADO_ANTERIOR, ESTADO_NUEVO, COMENTARIO, ADJUNTO_URL
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
        """;
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql);
            ps.setLong(1, idTicket);
            if (idUsuario != null) {
                ps.setLong(2, idUsuario);
            } else {
                ps.setNull(2, Types.NUMERIC);
            }
            ps.setString(3, tipoIntervencion);
            if (estadoAnterior != null) {
                ps.setString(4, estadoAnterior);
            } else {
                ps.setNull(4, Types.VARCHAR);
            }
            if (estadoNuevo != null) {
                ps.setString(5, estadoNuevo);
            } else {
                ps.setNull(5, Types.VARCHAR);
            }
            if (comentario != null) {
                ps.setString(6, comentario);
            } else {
                ps.setNull(6, Types.VARCHAR);
            }
            if (adjuntoUrl != null) {
                ps.setString(7, adjuntoUrl);
            } else {
                ps.setNull(7, Types.VARCHAR);
            }
            return ps;
        });
    }

    @Override
    public List<TicketTrazabilidadDTO> findTrazabilidadByTicket(Long idTicket) {
        String sql = """
            SELECT t.ID_TRAZABILIDAD, t.ID_TICKET, t.ID_USUARIO, t.TIPO_INTERVENCION,
                   t.COMENTARIO, t.ADJUNTO_URL, t.ESTADO_ANTERIOR, t.ESTADO_NUEVO, t.FECHA_HORA,
                   p.PRIMER_NOMBRE || ' ' || p.PRIMER_APELLIDO AS NOMBRE_USUARIO
            FROM PQRS_TRAZABILIDAD t
            LEFT JOIN USUARIOS u ON t.ID_USUARIO = u.ID_USUARIO
            LEFT JOIN PERSONAS p ON u.ID_PERSONA = p.ID_PERSONA
            WHERE t.ID_TICKET = ?
            ORDER BY t.FECHA_HORA ASC, t.ID_TRAZABILIDAD ASC
        """;
        return jdbcTemplate.query(sql, trazabilidadRowMapper, idTicket);
    }

    @Override
    public Optional<Integer> getSlaHoras(Long idPropiedad, String prioridad) {
        String sql = "SELECT TIEMPO_MAXIMO_HORAS FROM PQRS_SLA_CONFIGURACION WHERE ID_PROPIEDAD = ? AND PRIORIDAD = ?";
        try {
            Integer horas = jdbcTemplate.queryForObject(sql, Integer.class, idPropiedad, prioridad);
            return Optional.ofNullable(horas);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<PqrsSlaConfigDTO> getSlaConfigs(Long idPropiedad) {
        String sql = """
            SELECT ID_SLA_CONFIG, ID_PROPIEDAD, PRIORIDAD, TIEMPO_MAXIMO_HORAS, ALERTA_VENCIMIENTO_HORAS
            FROM PQRS_SLA_CONFIGURACION
            WHERE ID_PROPIEDAD = ?
            ORDER BY CASE PRIORIDAD
                WHEN 'EMERGENCIA' THEN 1
                WHEN 'ALTA' THEN 2
                WHEN 'MEDIA' THEN 3
                WHEN 'BAJA' THEN 4
                ELSE 5 END
        """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new PqrsSlaConfigDTO(
            rs.getLong("ID_SLA_CONFIG"),
            rs.getLong("ID_PROPIEDAD"),
            rs.getString("PRIORIDAD"),
            rs.getInt("TIEMPO_MAXIMO_HORAS"),
            rs.getInt("ALERTA_VENCIMIENTO_HORAS")
        ), idPropiedad);
    }

    @Override
    public void upsertSlaConfig(Long idPropiedad, String prioridad, int tiempoMaximoHoras, int alertaHoras) {
        String updateSql = """
            UPDATE PQRS_SLA_CONFIGURACION
            SET TIEMPO_MAXIMO_HORAS = ?, ALERTA_VENCIMIENTO_HORAS = ?
            WHERE ID_PROPIEDAD = ? AND PRIORIDAD = ?
        """;
        int updated = jdbcTemplate.update(updateSql, tiempoMaximoHoras, alertaHoras, idPropiedad, prioridad);
        if (updated == 0) {
            String insertSql = """
                INSERT INTO PQRS_SLA_CONFIGURACION (ID_PROPIEDAD, PRIORIDAD, TIEMPO_MAXIMO_HORAS, ALERTA_VENCIMIENTO_HORAS)
                VALUES (?, ?, ?, ?)
            """;
            jdbcTemplate.update(insertSql, idPropiedad, prioridad, tiempoMaximoHoras, alertaHoras);
        }
    }

    @Override
    public Long getIdPersonaFromUsuario(Long idUsuario) {
        try {
            String sql = "SELECT ID_PERSONA FROM USUARIOS WHERE ID_USUARIO = ?";
            return jdbcTemplate.queryForObject(sql, Long.class, idUsuario);
        } catch (EmptyResultDataAccessException e) {
            log.warn("Usuario ID {} no tiene persona vinculada", idUsuario);
            return null;
        }
    }

    @Override
    public Long getIdUnidadFromUsuario(Long idUsuario, Long idPropiedad) {
        try {
            String sql = """
                SELECT ID_UNIDAD FROM USUARIO_ASIGNACIONES
                WHERE ID_USUARIO = ? AND ID_PROPIEDAD = ? AND ESTADO = 'ACTIVA' AND ID_UNIDAD IS NOT NULL
                ORDER BY ID_ASIGNACION DESC FETCH FIRST 1 ROWS ONLY
            """;
            return jdbcTemplate.queryForObject(sql, Long.class, idUsuario, idPropiedad);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }
}
