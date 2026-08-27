package com.saed.backend.convivencia.service.impl;
import com.saed.backend.convivencia.dto.QuejaDTO;
import com.saed.backend.convivencia.dto.QuejaRequestDTO;
import com.saed.backend.convivencia.repository.QuejaRepository;
import com.saed.backend.convivencia.service.QuejaService;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.common.service.EmailService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import java.util.Map;

@Service
public class QuejaServiceImpl implements QuejaService {
    private final QuejaRepository repo;
    private final EmailService emailService;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public QuejaServiceImpl(QuejaRepository repo, EmailService emailService, NamedParameterJdbcTemplate jdbcTemplate) {
        this.repo = repo;
        this.emailService = emailService;
        this.jdbcTemplate = jdbcTemplate;
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
        notificarPQRS(id, "RESPONDIDA", respuesta);
    }

    @Override
    @Transactional
    public void actualizarEstado(Long id, String estado) {
        repo.updateEstado(id, estado);
        notificarPQRS(id, estado, null);
    }

    @Override
    @Transactional
    public void actualizarPrioridad(Long id, String prioridad) {
        repo.updatePrioridad(id, prioridad);
    }
    
    private void notificarPQRS(Long idQueja, String estado, String respuesta) {
        try {
            List<Map<String, Object>> u = jdbcTemplate.queryForList(
                "SELECT P.EMAIL FROM QUEJAS_PQRS Q " +
                "JOIN USUARIOS U ON U.ID_USUARIO = Q.ID_REPORTANTE " +
                "JOIN PERSONAS P ON P.ID_PERSONA = U.ID_PERSONA " +
                "WHERE Q.ID_PQRS = :id AND P.EMAIL IS NOT NULL", 
                Map.of("id", idQueja)
            );
            if (!u.isEmpty()) {
                String destinatario = (String) u.get(0).get("EMAIL");
                emailService.enviarNotificacionPQRS(destinatario, "PQRS-" + idQueja, estado, respuesta);
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
}
