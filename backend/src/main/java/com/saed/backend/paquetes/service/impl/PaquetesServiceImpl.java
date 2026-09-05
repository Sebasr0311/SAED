package com.saed.backend.paquetes.service.impl;

import com.saed.backend.audit.Auditable;
import com.saed.backend.audit.AuditCategory;
import com.saed.backend.audit.AuditSeverity;

import com.saed.backend.paquetes.dto.PaqueteDTO;
import com.saed.backend.paquetes.dto.PaqueteEntregaDTO;
import com.saed.backend.paquetes.dto.PaqueteRequestDTO;
import com.saed.backend.paquetes.repository.PaquetesRepository;
import com.saed.backend.paquetes.service.PaquetesService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class PaquetesServiceImpl implements PaquetesService {

    private final PaquetesRepository paquetesRepository;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    public PaquetesServiceImpl(PaquetesRepository paquetesRepository, NamedParameterJdbcTemplate jdbcTemplate) {
        this.paquetesRepository = paquetesRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    private String generatePin() {
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(CHARACTERS.charAt(RANDOM.nextInt(CHARACTERS.length())));
        }
        return sb.toString();
    }

    @Override
    @Transactional
    public PaqueteDTO registrarPaquete(PaqueteRequestDTO request) {
        SaedContext ctx = SaedContextHolder.getContext();
        String pin = generatePin();
        Long propId = ctx != null && ctx.getPropertyId() != null ? ctx.getPropertyId() : 1L;
        Long userId = ctx != null && ctx.getUserId() != null ? ctx.getUserId() : 1L;

        PaqueteDTO paquete = paquetesRepository.registrarPaquete(request, propId, pin, userId);

        // Notificar a los residentes activos de la unidad en NOTIFICACIONES
        try {
            Long idComunicado = null;
            try {
                idComunicado = jdbcTemplate.queryForObject(
                    "SELECT MIN(ID_COMUNICADO) FROM COMUNICADOS WHERE ID_PROPIEDAD = :propId",
                    new MapSqlParameterSource("propId", ctx != null && ctx.getPropertyId() != null ? ctx.getPropertyId() : 1L),
                    Long.class
                );
            } catch (Exception ignored) {}

            String findResidentsSql = "SELECT u.ID_USUARIO FROM USUARIO_ASIGNACIONES ua " +
                                      "JOIN USUARIOS u ON ua.ID_USUARIO = u.ID_USUARIO " +
                                      "WHERE ua.ID_UNIDAD = :unitId AND ua.ESTADO = 'ACTIVA'";
            List<Long> residentUserIds = jdbcTemplate.query(
                findResidentsSql,
                new MapSqlParameterSource("unitId", request.idUnidad()),
                (rs, rowNum) -> rs.getLong("ID_USUARIO")
            );
            for (Long resUserId : residentUserIds) {
                String notifSql = "INSERT INTO NOTIFICACIONES (ID_COMUNICADO, ID_USUARIO_DESTINATARIO, CANAL, TITULO, MENSAJE, ESTADO_ENVIO) " +
                                  "VALUES (:comunicadoId, :dest, 'IN_APP', 'Paquete recibido en portería', :msg, 'ENVIADO')";
                String msg = "Se ha recibido un paquete (" + request.descripcion() + ") de " +
                             (request.empresaMensajeria() != null ? request.empresaMensajeria() : "mensajería") +
                             ". Código de entrega PIN: " + pin + ".";
                jdbcTemplate.update(notifSql, new MapSqlParameterSource()
                    .addValue("comunicadoId", idComunicado)
                    .addValue("dest", resUserId)
                    .addValue("msg", msg));
            }
        } catch (Exception ignored) {
            // Notificación in-app secundaria; la persistencia del paquete no se interrumpe
        }

        return paquete;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaqueteDTO> getPaquetes() {
        SaedContext ctx = SaedContextHolder.getContext();
        if (ctx != null && "RESIDENTE".equalsIgnoreCase(ctx.getRoleCode())) {
            Long unitId = ctx.getUnitId();
            if (unitId == null) {
                return List.of();
            }
            return paquetesRepository.getPaquetesByUnidad(unitId);
        }
        return paquetesRepository.getPaquetesList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaqueteDTO> getPaquetesByUnidad(Long idUnidad) {
        SaedContext ctx = SaedContextHolder.getContext();
        if (ctx != null && "RESIDENTE".equalsIgnoreCase(ctx.getRoleCode())) {
            if (ctx.getUnitId() != null && !ctx.getUnitId().equals(idUnidad)) {
                throw new AccessDeniedException("Acceso denegado a paquetes de otra unidad");
            }
        }
        return paquetesRepository.getPaquetesByUnidad(idUnidad);
    }

    @Override
    @Transactional(readOnly = true)
    public PaqueteDTO getPaqueteById(Long id) {
        SaedContext ctx = SaedContextHolder.getContext();
        if (ctx != null && "RESIDENTE".equalsIgnoreCase(ctx.getRoleCode())) {
            Long unitId = ctx.getUnitId();
            List<Long> unitIds = jdbcTemplate.query(
                "SELECT ID_UNIDAD FROM PAQUETES WHERE ID_PAQUETE = :id",
                new MapSqlParameterSource("id", id),
                (rs, rowNum) -> rs.getLong("ID_UNIDAD")
            );
            if (!unitIds.isEmpty() && (unitId == null || !unitId.equals(unitIds.get(0)))) {
                throw new AccessDeniedException("Acceso denegado a paquetes de otra unidad");
            }
        }
        PaqueteDTO pq = paquetesRepository.getPaqueteById(id)
                .orElseThrow(() -> new NoSuchElementException("Paquete no encontrado"));
        return pq;
    }

    @Override
    @Transactional
    public PaqueteDTO actualizarPaquete(Long id, PaqueteRequestDTO request) {
        return paquetesRepository.actualizarPaquete(id, request);
    }

    @Override
    @Transactional
    public PaqueteDTO registrarEntrega(Long id, PaqueteEntregaDTO request) {
        SaedContext ctx = SaedContextHolder.getContext();
        PaqueteDTO pq = getPaqueteById(id);
        if (!pq.codigoRetiroPin().equals(request.codigoRetiroPin())) {
            throw new IllegalArgumentException("PIN incorrecto");
        }
        if (!"RECIBIDO".equals(pq.estado())) {
            throw new IllegalStateException("El paquete ya fue entregado o devuelto");
        }
        Long porteroId = ctx != null && ctx.getUserId() != null ? ctx.getUserId() : 1L;
        paquetesRepository.registrarEntrega(id, request, porteroId);
        return getPaqueteById(id);
    }

    @Override
    @Transactional
    public void marcarEntregadoDirecto(Long id) {
        SaedContext ctx = SaedContextHolder.getContext();
        PaqueteDTO pq = getPaqueteById(id);
        if ("ENTREGADO".equalsIgnoreCase(pq.estado())) {
            return;
        }
        Long porteroId = ctx != null && ctx.getUserId() != null ? ctx.getUserId() : 1L;
        paquetesRepository.marcarEntregadoDirecto(id, porteroId);
    }
}
