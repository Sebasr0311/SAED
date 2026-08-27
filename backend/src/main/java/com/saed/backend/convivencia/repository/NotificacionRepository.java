package com.saed.backend.convivencia.repository;
import com.saed.backend.convivencia.dto.NotificacionDTO;
import java.util.List;

public interface NotificacionRepository {
    List<NotificacionDTO> findByUsuarioDestinatario(Long idUsuario);
    void marcarLeido(Long idNotificacion, Long idUsuario);
    void vaciarBuzon(Long idUsuario);
    void eliminarMensajes(List<Long> ids, Long idUsuario);
}
