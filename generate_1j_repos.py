import os
import io

base_dir = "backend/src/main/java/com/saed/backend/convivencia/repository/impl"

repos_impl = {
    "MultaRepositoryImpl.java": """package com.saed.backend.convivencia.repository.impl;

import com.saed.backend.convivencia.dto.MultaDTO;
import com.saed.backend.convivencia.repository.MultaRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import lombok.RequiredArgsConstructor;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class MultaRepositoryImpl implements MultaRepository {
    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public List<MultaDTO> findAll() {
        String sql = "SELECT m.ID_MULTA, u.IDENTIFICADOR as numeroApartamento, p.PRIMER_NOMBRE || ' ' || p.PRIMER_APELLIDO as nombreResidente, " +
                     "c.NOMBRE as tipo, m.MONTO, m.ESTADO, m.FECHA_CREACION " +
                     "FROM MULTAS m " +
                     "JOIN UNIDADES u ON m.ID_UNIDAD = u.ID_UNIDAD " +
                     "LEFT JOIN PERSONAS p ON m.ID_PERSONA_INFRACTORA = p.ID_PERSONA " +
                     "JOIN CONCEPTOS_COBRO c ON m.ID_CONCEPTO = c.ID_CONCEPTO " +
                     "ORDER BY m.FECHA_CREACION DESC";
        return jdbc.query(sql, (rs, rowNum) -> {
            MultaDTO dto = new MultaDTO();
            dto.setIdMulta(rs.getLong("ID_MULTA"));
            dto.setNumeroApartamento(rs.getString("numeroApartamento"));
            dto.setNombreResidente(rs.getString("nombreResidente"));
            dto.setTipo(rs.getString("tipo"));
            dto.setMonto(rs.getBigDecimal("MONTO"));
            
            // Map DB estado to Frontend estado
            String dbEstado = rs.getString("ESTADO");
            String mappedEstado = dbEstado;
            if ("IMPUESTA".equals(dbEstado) || "EN_DESCARGOS".equals(dbEstado)) mappedEstado = "PENDIENTE";
            if ("RATIFICADA".equals(dbEstado)) mappedEstado = "PENDIENTE";
            
            dto.setEstado(mappedEstado);
            if(rs.getTimestamp("FECHA_CREACION") != null) {
                dto.setFechaCreacion(rs.getTimestamp("FECHA_CREACION").toLocalDateTime());
            }
            return dto;
        });
    }

    @Override
    public MultaDTO findById(Long id) {
        String sql = "SELECT m.ID_MULTA, u.IDENTIFICADOR as numeroApartamento, p.PRIMER_NOMBRE || ' ' || p.PRIMER_APELLIDO as nombreResidente, " +
                     "c.NOMBRE as tipo, m.MONTO, m.ESTADO, m.FECHA_CREACION " +
                     "FROM MULTAS m " +
                     "JOIN UNIDADES u ON m.ID_UNIDAD = u.ID_UNIDAD " +
                     "LEFT JOIN PERSONAS p ON m.ID_PERSONA_INFRACTORA = p.ID_PERSONA " +
                     "JOIN CONCEPTOS_COBRO c ON m.ID_CONCEPTO = c.ID_CONCEPTO " +
                     "WHERE m.ID_MULTA = :id";
        return jdbc.queryForObject(sql, new MapSqlParameterSource("id", id), (rs, rowNum) -> {
            MultaDTO dto = new MultaDTO();
            dto.setIdMulta(rs.getLong("ID_MULTA"));
            dto.setNumeroApartamento(rs.getString("numeroApartamento"));
            dto.setNombreResidente(rs.getString("nombreResidente"));
            dto.setTipo(rs.getString("tipo"));
            dto.setMonto(rs.getBigDecimal("MONTO"));
            
            String dbEstado = rs.getString("ESTADO");
            String mappedEstado = dbEstado;
            if ("IMPUESTA".equals(dbEstado) || "EN_DESCARGOS".equals(dbEstado) || "RATIFICADA".equals(dbEstado)) mappedEstado = "PENDIENTE";
            dto.setEstado(mappedEstado);
            
            if(rs.getTimestamp("FECHA_CREACION") != null) {
                dto.setFechaCreacion(rs.getTimestamp("FECHA_CREACION").toLocalDateTime());
            }
            return dto;
        });
    }

    @Override
    public void updateEstado(Long id, String estado) {
        String sql = "UPDATE MULTAS SET ESTADO = :estado WHERE ID_MULTA = :id";
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("estado", estado)
                .addValue("id", id));
    }
}
""",
    "QuejaRepositoryImpl.java": """package com.saed.backend.convivencia.repository.impl;

import com.saed.backend.convivencia.dto.QuejaDTO;
import com.saed.backend.convivencia.dto.QuejaRequestDTO;
import com.saed.backend.convivencia.repository.QuejaRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import lombok.RequiredArgsConstructor;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class QuejaRepositoryImpl implements QuejaRepository {
    private final NamedParameterJdbcTemplate jdbc;

    private QuejaDTO mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        QuejaDTO dto = new QuejaDTO();
        dto.setIdQueja(rs.getLong("ID_TICKET"));
        dto.setRadicado(rs.getString("NUMERO_RADICADO"));
        dto.setTipo(rs.getString("TIPO"));
        dto.setCategoria(rs.getString("CATEGORIA"));
        dto.setPrioridad(rs.getString("PRIORIDAD"));
        dto.setTitulo(rs.getString("ASUNTO"));
        dto.setDescripcion(rs.getString("DESCRIPCION"));
        
        String dbEstado = rs.getString("ESTADO");
        String mappedEstado = dbEstado;
        if ("RADICADO".equals(dbEstado) || "ASIGNADO".equals(dbEstado)) mappedEstado = "PENDIENTE";
        if ("EN_GESTION".equals(dbEstado) || "ESCALADO".equals(dbEstado)) mappedEstado = "EN_REVISION";
        if ("RESUELTO".equals(dbEstado)) mappedEstado = "RESUELTA";
        if ("CERRADO".equals(dbEstado) || "RECHAZADO".equals(dbEstado)) mappedEstado = "CERRADA";
        dto.setEstado(mappedEstado);
        
        dto.setRespuesta(rs.getString("RESPUESTA"));
        dto.setAutor(rs.getString("autor"));
        dto.setApartamento(rs.getString("apartamento"));
        if(rs.getTimestamp("FECHA_RADICACION") != null) {
            dto.setFecha(rs.getTimestamp("FECHA_RADICACION").toLocalDateTime());
        }
        return dto;
    }

    @Override
    public List<QuejaDTO> findAll() {
        String sql = "SELECT q.ID_TICKET, q.NUMERO_RADICADO, q.TIPO, q.CATEGORIA, q.PRIORIDAD, q.ASUNTO, q.DESCRIPCION, q.ESTADO, q.FECHA_RADICACION, " +
                     "(SELECT t.CONTENIDO FROM PQRS_TRAZABILIDAD t WHERE t.ID_TICKET = q.ID_TICKET ORDER BY t.FECHA_ACCION DESC FETCH FIRST 1 ROWS ONLY) as RESPUESTA, " +
                     "p.PRIMER_NOMBRE || ' ' || p.PRIMER_APELLIDO as autor, u.IDENTIFICADOR as apartamento " +
                     "FROM PQRS_TICKETS q " +
                     "JOIN PERSONAS p ON q.ID_PERSONA_RADICA = p.ID_PERSONA " +
                     "LEFT JOIN UNIDADES u ON q.ID_UNIDAD = u.ID_UNIDAD " +
                     "ORDER BY q.FECHA_RADICACION DESC";
        return jdbc.query(sql, this::mapRow);
    }

    @Override
    public List<QuejaDTO> findByUserId(Long idUsuario) {
        String sql = "SELECT q.ID_TICKET, q.NUMERO_RADICADO, q.TIPO, q.CATEGORIA, q.PRIORIDAD, q.ASUNTO, q.DESCRIPCION, q.ESTADO, q.FECHA_RADICACION, " +
                     "(SELECT t.CONTENIDO FROM PQRS_TRAZABILIDAD t WHERE t.ID_TICKET = q.ID_TICKET ORDER BY t.FECHA_ACCION DESC FETCH FIRST 1 ROWS ONLY) as RESPUESTA, " +
                     "p.PRIMER_NOMBRE || ' ' || p.PRIMER_APELLIDO as autor, u.IDENTIFICADOR as apartamento " +
                     "FROM PQRS_TICKETS q " +
                     "JOIN PERSONAS p ON q.ID_PERSONA_RADICA = p.ID_PERSONA " +
                     "LEFT JOIN UNIDADES u ON q.ID_UNIDAD = u.ID_UNIDAD " +
                     "WHERE p.ID_USUARIO = :idUsuario " +
                     "ORDER BY q.FECHA_RADICACION DESC";
        return jdbc.query(sql, new MapSqlParameterSource("idUsuario", idUsuario), this::mapRow);
    }

    @Override
    public void create(QuejaRequestDTO dto, Long idUsuario, Long idPropiedad) {
        String sql = "INSERT INTO PQRS_TICKETS (ID_PROPIEDAD, ID_PERSONA_RADICA, NUMERO_RADICADO, TIPO, CATEGORIA, ASUNTO, DESCRIPCION, FECHA_LIMITE_SLA) " +
                     "VALUES (:propiedad, (SELECT ID_PERSONA FROM PERSONAS WHERE ID_USUARIO = :idUsuario), " +
                     "'PQR-' || TO_CHAR(SYSDATE, 'YYYYMMDDHH24MISS'), :tipo, :cat, :titulo, :desc, SYSDATE + 5)";
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("propiedad", idPropiedad)
                .addValue("idUsuario", idUsuario)
                .addValue("tipo", dto.getTipo())
                .addValue("cat", dto.getCategoria())
                .addValue("titulo", dto.getTitulo())
                .addValue("desc", dto.getDescripcion()));
    }

    @Override
    public void updateRespuesta(Long id, String respuesta) {
        String sql = "INSERT INTO PQRS_TRAZABILIDAD (ID_TICKET, ID_USUARIO_ACCION, TIPO_ACCION, ESTADO_ANTERIOR, ESTADO_NUEVO, CONTENIDO) " +
                     "VALUES (:id, SYS_CONTEXT('SAED_CTX','ID_USUARIO'), 'RESPUESTA', " +
                     "(SELECT ESTADO FROM PQRS_TICKETS WHERE ID_TICKET = :id), " +
                     "(SELECT ESTADO FROM PQRS_TICKETS WHERE ID_TICKET = :id), :respuesta)";
        jdbc.update(sql, new MapSqlParameterSource("id", id).addValue("respuesta", respuesta));
    }

    @Override
    public void updateEstado(Long id, String estado) {
        // Map back to DB values
        String dbEstado = estado;
        if ("PENDIENTE".equals(estado)) dbEstado = "RADICADO";
        if ("EN_REVISION".equals(estado)) dbEstado = "EN_GESTION";
        if ("RESUELTA".equals(estado)) dbEstado = "RESUELTO";
        if ("CERRADA".equals(estado)) dbEstado = "CERRADO";
        
        String sql = "UPDATE PQRS_TICKETS SET ESTADO = :estado WHERE ID_TICKET = :id";
        jdbc.update(sql, new MapSqlParameterSource("id", id).addValue("estado", dbEstado));
    }

    @Override
    public void updatePrioridad(Long id, String prioridad) {
        String sql = "UPDATE PQRS_TICKETS SET PRIORIDAD = :prioridad WHERE ID_TICKET = :id";
        jdbc.update(sql, new MapSqlParameterSource("id", id).addValue("prioridad", prioridad));
    }
}
""",
    "NotificacionRepositoryImpl.java": """package com.saed.backend.convivencia.repository.impl;

import com.saed.backend.convivencia.dto.NotificacionDTO;
import com.saed.backend.convivencia.repository.NotificacionRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import lombok.RequiredArgsConstructor;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class NotificacionRepositoryImpl implements NotificacionRepository {
    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public List<NotificacionDTO> findByUsuarioDestinatario(Long idUsuario) {
        String sql = "SELECT ID_NOTIFICACION, TITULO, MENSAJE, FECHA_ENVIO, FECHA_LEIDO " +
                     "FROM NOTIFICACIONES " +
                     "WHERE ID_USUARIO_DESTINATARIO = :idUsuario " +
                     "ORDER BY FECHA_ENVIO DESC";
        return jdbc.query(sql, new MapSqlParameterSource("idUsuario", idUsuario), (rs, rowNum) -> {
            NotificacionDTO dto = new NotificacionDTO();
            dto.setIdMensaje(rs.getLong("ID_NOTIFICACION"));
            dto.setTitulo(rs.getString("TITULO"));
            dto.setCuerpo(rs.getString("MENSAJE"));
            if(rs.getTimestamp("FECHA_ENVIO") != null) {
                dto.setFecha(rs.getTimestamp("FECHA_ENVIO").toLocalDateTime());
            }
            dto.setLeido(rs.getTimestamp("FECHA_LEIDO") != null);
            return dto;
        });
    }

    @Override
    public void marcarLeido(Long idNotificacion, Long idUsuario) {
        String sql = "UPDATE NOTIFICACIONES SET FECHA_LEIDO = CURRENT_TIMESTAMP " +
                     "WHERE ID_NOTIFICACION = :id AND ID_USUARIO_DESTINATARIO = :user";
        jdbc.update(sql, new MapSqlParameterSource("id", idNotificacion).addValue("user", idUsuario));
    }

    @Override
    public void vaciarBuzon(Long idUsuario) {
        String sql = "DELETE FROM NOTIFICACIONES WHERE ID_USUARIO_DESTINATARIO = :user";
        jdbc.update(sql, new MapSqlParameterSource("user", idUsuario));
    }

    @Override
    public void eliminarMensajes(List<Long> ids, Long idUsuario) {
        if (ids == null || ids.isEmpty()) return;
        String sql = "DELETE FROM NOTIFICACIONES WHERE ID_USUARIO_DESTINATARIO = :user AND ID_NOTIFICACION IN (:ids)";
        jdbc.update(sql, new MapSqlParameterSource("user", idUsuario).addValue("ids", ids));
    }
}
"""
}

for name, content in repos_impl.items():
    with open(os.path.join(base_dir, name), "w", encoding="utf-8") as f:
        f.write(content)

print("Phase 1J Repos Created")
