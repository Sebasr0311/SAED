package com.saed.backend.convivencia.service.impl;

import com.saed.backend.audit.AuditService;
import com.saed.backend.common.service.EmailService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.convivencia.dto.MultaDTO;
import com.saed.backend.convivencia.repository.MultaRepository;
import com.saed.backend.convivencia.service.MultaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class MultaServiceImpl implements MultaService {
    private static final Logger log = LoggerFactory.getLogger(MultaServiceImpl.class);

    private final MultaRepository repo;
    private final EmailService emailService;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final AuditService auditService;

    public MultaServiceImpl(MultaRepository repo,
                            EmailService emailService,
                            NamedParameterJdbcTemplate jdbcTemplate,
                            AuditService auditService) {
        this.repo = repo;
        this.emailService = emailService;
        this.jdbcTemplate = jdbcTemplate;
        this.auditService = auditService;
    }

    @Override
    public List<MultaDTO> findAll() {
        return repo.findAll();
    }

    @Override
    public MultaDTO findById(Long id) {
        MultaDTO multa = repo.findById(id);
        if (multa == null) {
            throw new IllegalArgumentException("Multa no encontrada con ID: " + id);
        }
        return multa;
    }

    @Override
    public List<MultaDTO> findMisMultas() {
        SaedContext ctx = SaedContextHolder.getContext();
        Long userId = ctx != null ? ctx.getUserId() : null;
        if (userId == null) return Collections.emptyList();

        Long idPersona = null;
        String pSql = "SELECT ID_PERSONA FROM USUARIOS WHERE ID_USUARIO = :uId";
        List<Long> pList = jdbcTemplate.query(pSql, new MapSqlParameterSource("uId", userId), (rs, i) -> rs.getLong("ID_PERSONA"));
        if (!pList.isEmpty()) {
            idPersona = pList.get(0);
        }
        if (idPersona == null) return Collections.emptyList();

        String role = ctx.getRoleCode();
        boolean esSoloConviviente = "RESIDENTE_CONVIVENCIA".equalsIgnoreCase(role);

        List<Long> unidades = new ArrayList<>();
        if (!esSoloConviviente) {
            String uSql1 = "SELECT ID_UNIDAD FROM RESIDENTES_UNIDAD WHERE ID_PERSONA = :pId AND NVL(ESTADO, 'ACTIVO') = 'ACTIVO'";
            unidades.addAll(jdbcTemplate.query(uSql1, new MapSqlParameterSource("pId", idPersona), (rs, i) -> rs.getLong("ID_UNIDAD")));

            String uSql2 = "SELECT ID_UNIDAD FROM PROPIETARIOS_UNIDAD WHERE ID_PERSONA = :pId AND NVL(ESTADO, 'ACTIVO') = 'ACTIVO'";
            unidades.addAll(jdbcTemplate.query(uSql2, new MapSqlParameterSource("pId", idPersona), (rs, i) -> rs.getLong("ID_UNIDAD")));
        }

        return repo.findMisMultas(idPersona, unidades, esSoloConviviente);
    }

    @Override
    @Transactional
    public void pagar(Long id, String metodo) {
        MultaDTO multa = repo.findById(id);
        if (multa == null) {
            throw new IllegalArgumentException("Multa no encontrada con ID: " + id);
        }

        String estadoActual = multa.getEstado();
        if ("PAGADA".equalsIgnoreCase(estadoActual)) {
            return; // Idempotente
        }
        if ("ANULADA".equalsIgnoreCase(estadoActual)) {
            throw new IllegalStateException("No es posible registrar pago para una multa anulada.");
        }
        if (!Arrays.asList("IMPUESTA", "EN_DESCARGOS", "RATIFICADA", "PENDIENTE").contains(estadoActual)) {
            throw new IllegalStateException("La multa no se encuentra en un estado válido para recibir pago: " + estadoActual);
        }

        repo.updateEstado(id, "PAGADA");

        SaedContext ctx = SaedContextHolder.getContext();
        Long userId = ctx != null ? ctx.getUserId() : null;
        Long orgId = ctx != null ? ctx.getOrganizationId() : null;
        Long propId = ctx != null ? ctx.getPropertyId() : null;

        auditService.recordSuccess(
                userId, orgId, propId,
                "PAGAR_MULTA", "MULTAS", id,
                "127.0.0.1", "SAED-Core", estadoActual, "PAGADA"
        );

        // Enviar notificación por email (non-blocking)
        try {
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
                    String fecha = multa.getFechaCreacion() != null ? multa.getFechaCreacion().toString() : "";
                    emailService.enviarNotificacionMulta(destinatario, multa.getTipo(), multa.getMonto(), fecha);
                }
            }
        } catch (Exception e) {
            log.error("Error enviando notificación de multa", e);
        }
    }

    @Override
    @Transactional
    public void anular(Long id) {
        MultaDTO multa = repo.findById(id);
        if (multa == null) {
            throw new IllegalArgumentException("Multa no encontrada con ID: " + id);
        }

        String estadoActual = multa.getEstado();
        if ("ANULADA".equalsIgnoreCase(estadoActual)) {
            return; // Idempotente
        }
        if ("PAGADA".equalsIgnoreCase(estadoActual)) {
            throw new IllegalStateException("No es posible anular una multa que ya ha sido pagada.");
        }

        repo.updateEstado(id, "ANULADA");

        SaedContext ctx = SaedContextHolder.getContext();
        Long userId = ctx != null ? ctx.getUserId() : null;
        Long orgId = ctx != null ? ctx.getOrganizationId() : null;
        Long propId = ctx != null ? ctx.getPropertyId() : null;

        auditService.recordSuccess(
                userId, orgId, propId,
                "ANULAR_MULTA", "MULTAS", id,
                "127.0.0.1", "SAED-Core", estadoActual, "ANULADA"
        );
    }
}
