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
        String sql = "SELECT ID_NOTIFICACION, TITULO, MENSAJE, ENLACE_DESTINO, FECHA_ENVIO, FECHA_LEIDO " +
                     "FROM NOTIFICACIONES " +
                     "WHERE ID_USUARIO_DESTINATARIO = :idUsuario " +
                     "ORDER BY FECHA_ENVIO DESC";
        return jdbc.query(sql, new MapSqlParameterSource("idUsuario", idUsuario), (rs, rowNum) -> {
            NotificacionDTO dto = new NotificacionDTO();
            dto.setIdMensaje(rs.getLong("ID_NOTIFICACION"));
            dto.setTitulo(rs.getString("TITULO"));
            dto.setCuerpo(rs.getString("MENSAJE"));
            dto.setEnlaceDestino(rs.getString("ENLACE_DESTINO"));
            if (rs.getTimestamp("FECHA_ENVIO") != null) {
                dto.setFecha(rs.getTimestamp("FECHA_ENVIO").toLocalDateTime());
            }
            dto.setLeido(rs.getTimestamp("FECHA_LEIDO") != null);

            // Inferir tipo de notificación para la UI
            String tit = (dto.getTitulo() != null ? dto.getTitulo() : "").toUpperCase();
            String cuerpo = (dto.getCuerpo() != null ? dto.getCuerpo() : "").toUpperCase();
            if (tit.contains("PAQUETE") || cuerpo.contains("PAQUETE") || cuerpo.contains("PIN")) {
                dto.setTipo("PAQUETE");
                java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                        "(?:código(?:\\s+de)?\\s+retiro\\s+PIN|PIN(?:\\s+de\\s+retiro)?)\\s*[:#]?\\s*([0-9]{4})",
                        java.util.regex.Pattern.CASE_INSENSITIVE
                ).matcher(dto.getCuerpo() != null ? dto.getCuerpo() : "");
                if (m.find()) {
                    dto.setCodigoRetiroPin(m.group(1));
                }
            } else if (tit.contains("VISITA") || cuerpo.contains("VISITA")) {
                dto.setTipo("VISITA");
            } else if (tit.contains("SANCION") || tit.contains("MULTA")) {
                dto.setTipo("SANCION");
            } else if (tit.contains("AVISO") || tit.contains("COMUNICADO")) {
                dto.setTipo("COMUNICADO");
            } else {
                dto.setTipo("AVISO");
            }
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
    public void marcarTodasLeidas(Long idUsuario) {
        String sql = "UPDATE NOTIFICACIONES SET FECHA_LEIDO = CURRENT_TIMESTAMP " +
                     "WHERE ID_USUARIO_DESTINATARIO = :user AND FECHA_LEIDO IS NULL";
        jdbc.update(sql, new MapSqlParameterSource("user", idUsuario));
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
