package com.saed.backend.convivencia.repository.impl;

import com.saed.backend.convivencia.dto.NotificacionDTO;
import com.saed.backend.convivencia.repository.NotificacionRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public class NotificacionRepositoryImpl implements NotificacionRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public NotificacionRepositoryImpl(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }


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
