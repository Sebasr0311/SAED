package com.saed.backend.convivencia.service.impl;
import com.saed.backend.convivencia.dto.NotificacionDTO;
import com.saed.backend.convivencia.repository.NotificacionRepository;
import com.saed.backend.convivencia.service.NotificacionService;
import com.saed.backend.context.SaedContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class NotificacionServiceImpl implements NotificacionService {
    private final NotificacionRepository repo;

    public NotificacionServiceImpl(NotificacionRepository repo) {
        this.repo = repo;
    }


    @Override
    public List<NotificacionDTO> getMyNotificaciones() {
        Long idUsuario = SaedContextHolder.getContext().getUserId();
        return repo.findByUsuarioDestinatario(idUsuario);
    }

    @Override
    @Transactional
    public void marcarLeido(Long id) {
        Long idUsuario = SaedContextHolder.getContext().getUserId();
        repo.marcarLeido(id, idUsuario);
    }

    @Override
    @Transactional
    public void vaciarBuzon() {
        Long idUsuario = SaedContextHolder.getContext().getUserId();
        repo.vaciarBuzon(idUsuario);
    }

    @Override
    @Transactional
    public void eliminarMensajes(List<Long> ids) {
        Long idUsuario = SaedContextHolder.getContext().getUserId();
        repo.eliminarMensajes(ids, idUsuario);
    }
}
