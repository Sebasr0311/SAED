package com.saed.backend.convivencia.service.impl;
import com.saed.backend.convivencia.dto.QuejaDTO;
import com.saed.backend.convivencia.dto.QuejaRequestDTO;
import com.saed.backend.convivencia.repository.QuejaRepository;
import com.saed.backend.convivencia.service.QuejaService;
import com.saed.backend.context.SaedContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class QuejaServiceImpl implements QuejaService {
    private final QuejaRepository repo;

    public QuejaServiceImpl(QuejaRepository repo) {
        this.repo = repo;
    }


    @Override
    public List<QuejaDTO> findAll() {
        return repo.findAll();
    }

    @Override
    public List<QuejaDTO> findMyQuejas() {
        Long idUsuario = SaedContextHolder.getContext().getUserId();
        return repo.findByUserId(idUsuario);
    }

    @Override
    @Transactional
    public void createQueja(QuejaRequestDTO dto) {
        Long idUsuario = SaedContextHolder.getContext().getUserId();
        Long idPropiedad = SaedContextHolder.getContext().getPropertyId();
        repo.create(dto, idUsuario, idPropiedad);
    }

    @Override
    @Transactional
    public void responder(Long id, String respuesta) {
        repo.updateRespuesta(id, respuesta);
    }

    @Override
    @Transactional
    public void actualizarEstado(Long id, String estado) {
        repo.updateEstado(id, estado);
    }

    @Override
    @Transactional
    public void actualizarPrioridad(Long id, String prioridad) {
        repo.updatePrioridad(id, prioridad);
    }
}
