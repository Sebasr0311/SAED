package com.saed.backend.convivencia.service;
import com.saed.backend.convivencia.dto.NotificacionDTO;
import java.util.List;

public interface NotificacionService {
    List<NotificacionDTO> getMyNotificaciones();
    void marcarLeido(Long id);
    void marcarTodasLeidas();
    void vaciarBuzon();
    void eliminarMensajes(List<Long> ids);
}
