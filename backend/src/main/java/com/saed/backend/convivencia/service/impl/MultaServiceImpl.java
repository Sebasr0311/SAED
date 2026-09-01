package com.saed.backend.convivencia.service.impl;

import com.saed.backend.audit.Auditable;
import com.saed.backend.audit.AuditCategory;
import com.saed.backend.audit.AuditSeverity;
import com.saed.backend.convivencia.dto.MultaDTO;
import com.saed.backend.convivencia.repository.MultaRepository;
import com.saed.backend.convivencia.service.MultaService;
import com.saed.backend.common.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;

@Service
public class MultaServiceImpl implements MultaService {
    private static final Logger log = LoggerFactory.getLogger(MultaServiceImpl.class);

    private final MultaRepository repo;
    private final EmailService emailService;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public MultaServiceImpl(MultaRepository repo, EmailService emailService, NamedParameterJdbcTemplate jdbcTemplate) {
        this.repo = repo;
        this.emailService = emailService;
        this.jdbcTemplate = jdbcTemplate;
    }

    
    @Override
    public List<MultaDTO> findAll() {
        return repo.findAll();
    }
    
    @Override
    public MultaDTO findById(Long id) {
        return repo.findById(id);
    }
    
    @Override
    @Transactional
    public void pagar(Long id, String metodo) {
        // En una implementacion real se crearia un pago
        repo.updateEstado(id, "PAGADA");

        // Enviar notificación por email (non-blocking)
        try {
            // Obtener ID_UNIDAD de la multa para buscar el email del residente
            List<Map<String, Object>> multaInfo = jdbcTemplate.queryForList(
                "SELECT m.ID_UNIDAD FROM MULTAS m WHERE m.ID_MULTA = :id",
                new MapSqlParameterSource("id", id));
            if (!multaInfo.isEmpty()) {
                Long idUnidad = ((Number) multaInfo.get(0).get("ID_UNIDAD")).longValue();
                List<Map<String, Object>> residentes = jdbcTemplate.queryForList(
                    "SELECT P.EMAIL FROM PERSONAS P " +
                    "JOIN RESIDENTES_UNIDAD RU ON RU.ID_PERSONA = P.ID_PERSONA " +
                    "WHERE RU.ID_UNIDAD = :u AND P.EMAIL IS NOT NULL",
                    new MapSqlParameterSource("u", idUnidad));
                if (!residentes.isEmpty()) {
                    String destinatario = (String) residentes.get(0).get("EMAIL");
                    MultaDTO multa = repo.findById(id);
                    if (multa != null) {
                        String fecha = multa.getFechaCreacion() != null ? multa.getFechaCreacion().toString() : "";
                        emailService.enviarNotificacionMulta(destinatario, multa.getTipo(), multa.getMonto(), fecha);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error enviando notificación de multa", e);
        }
    }
    
    @Override
    @Transactional
    public void anular(Long id) {
        repo.updateEstado(id, "ANULADA");
    }
}
