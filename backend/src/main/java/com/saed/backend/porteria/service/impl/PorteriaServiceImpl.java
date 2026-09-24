package com.saed.backend.porteria.service.impl;

import com.saed.backend.audit.Auditable;
import com.saed.backend.audit.AuditCategory;
import com.saed.backend.audit.AuditSeverity;

import com.saed.backend.common.service.EmailService;
import com.saed.backend.porteria.dto.*;
import com.saed.backend.porteria.exception.QrAccessException;
import com.saed.backend.porteria.repository.PorteriaRepository;
import com.saed.backend.porteria.service.PorteriaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import com.saed.backend.authorization.service.PropertyConfigService;
import com.saed.backend.authorization.service.PropertyConfigServiceImpl;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import java.time.Duration;

@Service
@Transactional
public class PorteriaServiceImpl implements PorteriaService {

    private static final Logger log = LoggerFactory.getLogger(PorteriaServiceImpl.class);

    private final PorteriaRepository porteriaRepository;
    private final EmailService emailService;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final PropertyConfigService propertyConfigService;

    public PorteriaServiceImpl(PorteriaRepository porteriaRepository,
                               EmailService emailService,
                               NamedParameterJdbcTemplate jdbcTemplate,
                               PropertyConfigService propertyConfigService) {
        this.porteriaRepository = porteriaRepository;
        this.emailService = emailService;
        this.jdbcTemplate = jdbcTemplate;
        this.propertyConfigService = propertyConfigService;
    }

    // --- ADMIN CRUD PORTERÍAS ---
    @Override
    @Transactional(readOnly = true)
    public List<PorteriaDTO> listarPorterias() {
        Long orgId = SaedContextHolder.getContext().getOrganizationId();
        Long propId = SaedContextHolder.getContext().getPropertyId();
        return jdbcTemplate.query(
            "SELECT ID_PORTERIA, NOMBRE, UBICACION, TELEFONO_CONTACTO, ESTADO FROM PORTERIAS " +
            "WHERE ID_PROPIEDAD = :propId ORDER BY NOMBRE",
            Map.of("propId", propId),
            (rs, rowNum) -> new PorteriaDTO(
                rs.getLong("ID_PORTERIA"), rs.getString("NOMBRE"),
                rs.getString("UBICACION"), rs.getString("TELEFONO_CONTACTO"),
                rs.getString("ESTADO")
            )
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PorteriaDTO getPorteriaById(Long id) {
        return jdbcTemplate.queryForObject(
            "SELECT ID_PORTERIA, NOMBRE, UBICACION, TELEFONO_CONTACTO, ESTADO FROM PORTERIAS WHERE ID_PORTERIA = :id",
            Map.of("id", id),
            (rs, rowNum) -> new PorteriaDTO(
                rs.getLong("ID_PORTERIA"), rs.getString("NOMBRE"),
                rs.getString("UBICACION"), rs.getString("TELEFONO_CONTACTO"),
                rs.getString("ESTADO")
            )
        );
    }

    @Override
    public PorteriaDTO crearPorteria(PorteriaCreateDTO request) {
        Long propId = SaedContextHolder.getContext().getPropertyId();
        var keyHolder = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbcTemplate.update(
            "INSERT INTO PORTERIAS (ID_PROPIEDAD, NOMBRE, UBICACION, TELEFONO_CONTACTO) VALUES (:propId, :nombre, :ubicacion, :tel)",
            new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                .addValue("propId", propId)
                .addValue("nombre", request.nombre())
                .addValue("ubicacion", request.ubicacion())
                .addValue("tel", request.telefonoContacto()),
            keyHolder
        );
        Number id = keyHolder.getKey();
        return getPorteriaById(id.longValue());
    }

    @Override
    public PorteriaDTO actualizarPorteria(Long id, PorteriaCreateDTO request) {
        jdbcTemplate.update(
            "UPDATE PORTERIAS SET NOMBRE = :nombre, UBICACION = :ubicacion, TELEFONO_CONTACTO = :tel WHERE ID_PORTERIA = :id",
            new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                .addValue("id", id)
                .addValue("nombre", request.nombre())
                .addValue("ubicacion", request.ubicacion())
                .addValue("tel", request.telefonoContacto())
        );
        return getPorteriaById(id);
    }

    @Override
    public void eliminarPorteria(Long id) {
        jdbcTemplate.update("DELETE FROM PORTERIAS WHERE ID_PORTERIA = :id", Map.of("id", id));
    }

    // --- OPERACIONES PORTERO ---
    @Override
    public VisitaDTO programarVisita(VisitaRequestDTO request) {
        com.saed.backend.context.SaedContext ctx = SaedContextHolder.getContext();
        if (ctx != null && ("RESIDENTE".equals(ctx.getRoleCode()) || "RESIDENTE_CONVIVENCIA".equals(ctx.getRoleCode()) || "UNIDAD".equals(ctx.getRoleScope()))) {
            if (ctx.getUnitId() != null && !ctx.getUnitId().equals(request.unidadId())) {
                throw new org.springframework.security.access.AccessDeniedException("No tiene permisos para programar visitas en otra unidad");
            }
        }
        if (ctx != null && "ADMIN_PROPIEDAD".equals(ctx.getRoleCode()) && ctx.getPropertyId() != null && request.unidadId() != null) {
            List<Long> match = jdbcTemplate.query(
                "SELECT ID_UNIDAD FROM UNIDADES WHERE ID_UNIDAD = :u AND ID_PROPIEDAD = :p",
                Map.of("u", request.unidadId(), "p", ctx.getPropertyId()), (rs, r) -> rs.getLong("ID_UNIDAD")
            );
            if (match.isEmpty()) {
                throw new org.springframework.security.access.AccessDeniedException("La unidad no pertenece a la propiedad asignada");
            }
        }
        // SEC-02: Server-controlled autorizador (prevencion de suplantacion)
        Long serverAutorizadoPor = ctx != null ? ctx.getUserId() : null;
        if (serverAutorizadoPor == null) {
            org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !(auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)) {
                try { serverAutorizadoPor = Long.parseLong(auth.getName()); } catch (Exception ignored) {}
            }
        }
        if (serverAutorizadoPor == null) {
            serverAutorizadoPor = request.autorizadoPor();
        }

        VisitaRequestDTO safeRequest = new VisitaRequestDTO(
            request.unidadId(),
            request.visitanteId(),
            request.metodoIngreso(),
            request.motivo(),
            serverAutorizadoPor,
            request.fechaProgramada(),
            request.estado()
        );
        return porteriaRepository.createVisita(safeRequest);
    }

    @Override
    @Transactional(readOnly = true)
    public VisitaDTO getVisitaById(Long id) {
        return porteriaRepository.getVisitaById(id)
                .orElseThrow(() -> new RuntimeException("Visita no encontrada"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<VisitaDTO> getVisitasByUnidad(Long unidadId) {
        com.saed.backend.context.SaedContext ctx = SaedContextHolder.getContext();
        if (ctx != null && ("RESIDENTE".equals(ctx.getRoleCode()) || "RESIDENTE_CONVIVENCIA".equals(ctx.getRoleCode()) || "UNIDAD".equals(ctx.getRoleScope()))) {
            if (ctx.getUnitId() != null && !ctx.getUnitId().equals(unidadId)) {
                throw new org.springframework.security.access.AccessDeniedException("No tiene permisos para consultar visitas de otra unidad");
            }
        }
        return porteriaRepository.getVisitasByUnidad(unidadId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VisitaListDTO> getVisitasResumen() {
        List<VisitaListDTO> visitas = porteriaRepository.getVisitasResumen();
        SaedContext ctx = SaedContextHolder.getContext();
        Long propId = (ctx != null && ctx.getPropertyId() != null) ? ctx.getPropertyId() : 1L;
        int maxMinutos = 240;
        if (propertyConfigService != null) {
            maxMinutos = propertyConfigService.getIntValue(propId, PropertyConfigServiceImpl.KEY_TIEMPO_MAXIMO_VISITA, 240);
        }

        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("America/Bogota"));
        final int limit = maxMinutos;

        return visitas.stream().map(v -> {
            Long minutos = null;
            Boolean excedido = false;
            if (v.fechaIngreso() != null) {
                if ("EN_CURSO".equalsIgnoreCase(v.estado())) {
                    minutos = Math.max(0, Duration.between(v.fechaIngreso(), now).toMinutes());
                    excedido = minutos > limit;
                } else if (v.fechaSalida() != null) {
                    minutos = Math.max(0, Duration.between(v.fechaIngreso(), v.fechaSalida()).toMinutes());
                    excedido = minutos > limit;
                }
            }
            return v.withPermanencia(minutos, limit, excedido);
        }).toList();
    }

    @Override
    public void registrarSalidaVisita(Long id) {
        porteriaRepository.getVisitaById(id).orElseThrow();
        porteriaRepository.updateVisitaEstado(id, "FINALIZADA");
    }

    @Override
    @Transactional(readOnly = true)
    public List<VisitaHistorialDTO> getVisitasHistorial(String fechaInicio, String fechaFin) {
        return porteriaRepository.getVisitasHistorial(fechaInicio, fechaFin);
    }

    @Override
    @Transactional(readOnly = true)
    public VisitaDetalleDTO getVisitaDetalle(Long id) {
        VisitaDetalleDTO v = porteriaRepository.getVisitaDetalle(id)
                .orElseThrow(() -> new RuntimeException("Visita no encontrada"));
        SaedContext ctx = SaedContextHolder.getContext();
        Long propId = (ctx != null && ctx.getPropertyId() != null) ? ctx.getPropertyId() : 1L;
        int maxMinutos = 240;
        if (propertyConfigService != null) {
            maxMinutos = propertyConfigService.getIntValue(propId, PropertyConfigServiceImpl.KEY_TIEMPO_MAXIMO_VISITA, 240);
        }

        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("America/Bogota"));
        Long minutos = null;
        Boolean excedido = false;
        if (v.fechaVisita() != null) {
            if ("EN_CURSO".equalsIgnoreCase(v.estado())) {
                minutos = Math.max(0, Duration.between(v.fechaVisita(), now).toMinutes());
                excedido = minutos > maxMinutos;
            } else if (v.fechaSalida() != null) {
                minutos = Math.max(0, Duration.between(v.fechaVisita(), v.fechaSalida()).toMinutes());
                excedido = minutos > maxMinutos;
            }
        }
        return v.withPermanencia(minutos, maxMinutos, excedido);
    }

    @Override
    public VisitaDTO actualizarVisita(Long id, VisitaRequestDTO request) {
        VisitaDTO existing = getVisitaById(id);
        // SEC-02: Retain original autorizadoPor to prevent tampering on update
        VisitaRequestDTO safeRequest = new VisitaRequestDTO(
            request.unidadId(),
            request.visitanteId(),
            request.metodoIngreso(),
            request.motivo(),
            existing.autorizadoPor(),
            request.fechaProgramada(),
            request.estado()
        );
        return porteriaRepository.updateVisita(id, safeRequest);
    }

    @Override
    @Auditable(action = "CHECKIN", resource = "ACCESO_PORTERIA", category = AuditCategory.SECURITY, severity = AuditSeverity.INFO)
    public RegistroAccesoDTO registrarEntrada(RegistroAccesoRequestDTO request) {
        if (!"ENTRADA".equals(request.tipoMovimiento())) {
            throw new IllegalArgumentException("El movimiento debe ser ENTRADA");
        }
        if (request.visitaId() != null) {
            porteriaRepository.updateVisitaEstado(request.visitaId(), "EN_CURSO");
        }
        return porteriaRepository.createRegistroAcceso(request);
    }

    @Override
    @Auditable(action = "CHECKOUT", resource = "ACCESO_PORTERIA", category = AuditCategory.SECURITY, severity = AuditSeverity.INFO)
    public RegistroAccesoDTO registrarSalida(RegistroAccesoRequestDTO request) {
        if (!"SALIDA".equals(request.tipoMovimiento())) {
            throw new IllegalArgumentException("El movimiento debe ser SALIDA");
        }
        if (request.visitaId() != null) {
            porteriaRepository.updateVisitaEstado(request.visitaId(), "FINALIZADA");
        }
        return porteriaRepository.createRegistroAcceso(request);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RegistroAccesoDTO> getRegistrosByPropiedad(Long propiedadId) {
        return porteriaRepository.getRegistrosByPropiedad(propiedadId);
    }

    @Override
    public QrAccesoDTO generarQrAcceso(QrAccesoRequestDTO request) {
        // SEC-02: Enforce server-controlled generadoPor
        Long serverGeneradoPor = SaedContextHolder.getContext() != null ? SaedContextHolder.getContext().getUserId() : null;
        if (serverGeneradoPor == null) {
            org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !(auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)) {
                try { serverGeneradoPor = Long.parseLong(auth.getName()); } catch (Exception ignored) {}
            }
        }
        if (serverGeneradoPor == null) {
            serverGeneradoPor = request.generadoPor();
        }
        QrAccesoRequestDTO safeRequest = new QrAccesoRequestDTO(
            request.visitaId(),
            request.tokenQr(),
            request.fechaExpiracion(),
            request.usosPermitidos(),
            request.estado(),
            serverGeneradoPor
        );
        QrAccesoDTO qr = porteriaRepository.createQrAcceso(safeRequest);
        try {
            Long unitId = SaedContextHolder.getContext().getUnitId();
            List<Map<String, Object>> residentes = jdbcTemplate.queryForList(
                "SELECT P.EMAIL FROM PERSONAS P " +
                "JOIN RESIDENTES_UNIDAD UH ON UH.ID_PERSONA = P.ID_PERSONA " +
                "WHERE UH.ID_UNIDAD = :u AND P.EMAIL IS NOT NULL", 
                Map.of("u", unitId)
            );
            if (!residentes.isEmpty()) {
                String destinatario = (String) residentes.get(0).get("EMAIL");
                emailService.enviarCorreoQRAsync(destinatario, qr.tokenQr(), qr.fechaExpiracion().toString(), "Visitante");
            }
        } catch(Exception e) { log.error("Error sending QR email", e); }
        return qr;
    }

    @Override
    @Transactional(readOnly = true)
    public QrAccesoDTO getQrAccesoById(Long id) {
        return porteriaRepository.getQrAccesoById(id)
                .orElseThrow(() -> new RuntimeException("QR Acceso no encontrado"));
    }

    @Override
    public boolean validarQr(String token) {
        QrAccesoDTO qr = porteriaRepository.getQrAccesoByToken(token).orElse(null);
        if (qr == null) return false;
        if (!"ACTIVO".equals(qr.estado())) return false;
        if (qr.fechaExpiracion().isBefore(ZonedDateTime.now())) return false;
        if (qr.usosConsumidos() >= qr.usosPermitidos()) return false;
        
        porteriaRepository.consumeQrUso(qr.idQr());
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> validarQrDetalle(String token) {
        if (token == null || token.isBlank()) {
            return Map.of("valido", false, "mensaje", "Token no proporcionado");
        }
        QrAccesoDTO qr = porteriaRepository.getQrAccesoByToken(token).orElse(null);
        if (qr == null) {
            return Map.of("valido", false, "mensaje", "Código QR no encontrado");
        }
        if (!"ACTIVO".equalsIgnoreCase(qr.estado())) {
            return Map.of("valido", false, "mensaje", "El código QR no se encuentra activo (Estado: " + qr.estado() + ")");
        }
        if (qr.fechaExpiracion() != null && qr.fechaExpiracion().isBefore(ZonedDateTime.now())) {
            return Map.of("valido", false, "mensaje", "El código QR ha expirado");
        }
        if (qr.usosPermitidos() != null && qr.usosConsumidos() != null && qr.usosConsumidos() >= qr.usosPermitidos()) {
            return Map.of("valido", false, "mensaje", "El código QR ya superó el límite de usos permitidos");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("valido", true);
        result.put("mensaje", "Código QR válido");
        result.put("codigoQr", qr.tokenQr());
        result.put("idVisita", qr.visitaId());
        result.put("fechaExpiracion", qr.fechaExpiracion() != null ? qr.fechaExpiracion().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) : null);
        result.put("fechaGeneracion", qr.fechaGeneracion() != null ? qr.fechaGeneracion().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) : null);
        if (qr.fechaExpiracion() != null) {
            long minRestantes = java.time.Duration.between(ZonedDateTime.now(ZoneId.of("America/Bogota")), qr.fechaExpiracion()).toMinutes();
            result.put("minutosRestantes", minRestantes);
        }

        VisitaDetalleDTO detalle = qr.visitaId() != null ? porteriaRepository.getVisitaDetalle(qr.visitaId()).orElse(null) : null;
        if (detalle != null) {
            String nombre = detalle.nombreVisitante() != null ? detalle.nombreVisitante() : "";
            if (detalle.apellidoVisitante() != null && !detalle.apellidoVisitante().isBlank()) {
                nombre = (nombre + " " + detalle.apellidoVisitante()).trim();
            }
            result.put("nombreVisitante", nombre);
            result.put("documentoVisitante", detalle.documentoVisitante() != null ? detalle.documentoVisitante() : "");
            result.put("nombreResidente", detalle.nombreResidente() != null ? detalle.nombreResidente() : "");
            result.put("numeroApartamento", detalle.numeroApartamento() != null ? detalle.numeroApartamento() : "");
            result.put("notas", detalle.notas() != null ? detalle.notas() : "");
        } else {
            result.put("nombreVisitante", "Visitante");
            result.put("documentoVisitante", "");
            result.put("nombreResidente", "");
            result.put("numeroApartamento", "");
            result.put("notas", "");
        }
        return result;
    }

    @Override
    @Auditable(action = "NOTIFICAR_VISITA", resource = "ACCESO_PORTERIA", category = AuditCategory.SECURITY, severity = AuditSeverity.INFO)
    public Map<String, Object> notificarVisitaQr(String token, String fotoCaptura) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token QR requerido para notificar");
        }
        QrAccesoDTO qr = porteriaRepository.getQrAccesoByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Código QR no encontrado"));

        Long destinatarioId = null;
        String nombreVisitante = "un visitante";
        if (qr.visitaId() != null) {
            try {
                VisitaDetalleDTO det = porteriaRepository.getVisitaDetalle(qr.visitaId()).orElse(null);
                if (det != null && det.nombreVisitante() != null) {
                    nombreVisitante = det.nombreVisitante();
                }
                List<Long> userIds = jdbcTemplate.queryForList(
                    "SELECT u.ID_USUARIO FROM USUARIOS u " +
                    "JOIN PERSONAS p ON u.ID_PERSONA = p.ID_PERSONA " +
                    "JOIN RESIDENTES_UNIDAD ru ON ru.ID_PERSONA = p.ID_PERSONA " +
                    "JOIN VISITAS v ON v.ID_UNIDAD = ru.ID_UNIDAD " +
                    "WHERE v.ID_VISITA = :visitaId " +
                    "ORDER BY CASE WHEN ru.TIPO_RESIDENTE = 'TITULAR' THEN 1 ELSE 2 END",
                    new MapSqlParameterSource("visitaId", qr.visitaId()),
                    Long.class
                );
                if (!userIds.isEmpty()) {
                    destinatarioId = userIds.get(0);
                }
            } catch (Exception e) {
                log.warn("No se pudo obtener destinatario para visita {}: {}", qr.visitaId(), e.getMessage());
            }
        }
        if (destinatarioId == null) {
            destinatarioId = SaedContextHolder.getContext().getUserId() != null ? SaedContextHolder.getContext().getUserId() : 1L;
        }

        Long notifId = System.currentTimeMillis();
        try {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("destId", destinatarioId)
                .addValue("titulo", "Visitante en portería")
                .addValue("mensaje", "El visitante " + nombreVisitante + " se encuentra en portería.")
                .addValue("estado", "PENDIENTE");
            jdbcTemplate.update(
                "INSERT INTO NOTIFICACIONES (ID_USUARIO_DESTINATARIO, CANAL, TITULO, MENSAJE, ESTADO_ENVIO) " +
                "VALUES (:destId, 'IN_APP', :titulo, :mensaje, :estado)",
                params, keyHolder, new String[]{"ID_NOTIFICACION"}
            );
            if (keyHolder.getKey() != null) {
                notifId = keyHolder.getKey().longValue();
            }
        } catch (Exception e) {
            log.warn("Error guardando registro en NOTIFICACIONES: {}", e.getMessage());
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("idMensaje", notifId);
        resp.put("idVisita", qr.visitaId());
        resp.put("status", "NOTIFICADO");
        resp.put("success", true);
        return resp;
    }

    @Override
    @Auditable(action = "CHECKIN_QR", resource = "ACCESO_PORTERIA", category = AuditCategory.SECURITY, severity = AuditSeverity.INFO)
    public Map<String, Object> registrarEntradaQr(String token, String medioTransporte, String placa, String descripcion) {
        if (token == null || token.isBlank()) {
            throw new QrAccessException("Token QR requerido", "BAD_REQUEST", HttpStatus.BAD_REQUEST);
        }

        // 1. Identificar portería y portero operador
        Long propId = SaedContextHolder.getContext() != null ? SaedContextHolder.getContext().getPropertyId() : null;
        Long porteroId = SaedContextHolder.getContext() != null ? SaedContextHolder.getContext().getUserId() : null;
        if (porteroId == null) porteroId = 1L;
        if (propId == null) propId = 1L;

        Long porteriaId = null;
        if (propId != null) {
            try {
                porteriaId = jdbcTemplate.queryForObject(
                    "SELECT MIN(ID_PORTERIA) FROM PORTERIAS WHERE ID_PROPIEDAD = :propId",
                    new MapSqlParameterSource("propId", propId),
                    Long.class
                );
            } catch (Exception ignored) {}
            if (porteriaId == null) {
                porteriaId = -1L * propId;
            }
        } else {
            try {
                porteriaId = jdbcTemplate.queryForObject("SELECT MIN(ID_PORTERIA) FROM PORTERIAS", new MapSqlParameterSource(), Long.class);
            } catch (Exception ignored) {}
            if (porteriaId == null) porteriaId = 1L;
        }

        // 2. Validación y Consumo Atómico mediante Stored Procedure Oracle (GAP-F7-02)
        QrConsumoResultadoDTO resultadoSp = porteriaRepository.validarYConsumirQrSp(token, porteriaId, porteroId);

        if (!resultadoSp.valido()) {
            String msg = resultadoSp.mensaje() != null ? resultadoSp.mensaje() : "Acceso denegado con código QR";
            String msgUpper = msg.toUpperCase();
            if (msgUpper.contains("NO ENCONTRADO") || msgUpper.contains("INVÁLIDO") || msgUpper.contains("INVALIDO")) {
                throw new QrAccessException(msg, "QR_NOT_FOUND", HttpStatus.NOT_FOUND);
            } else if (msgUpper.contains("EXPIRADO")) {
                throw new QrAccessException(msg, "QR_EXPIRED", HttpStatus.CONFLICT);
            } else if (msgUpper.contains("PROPIEDAD") || msgUpper.contains("PORTERÍA") || msgUpper.contains("PORTERIA")) {
                throw new QrAccessException(msg, "QR_PROPERTY_MISMATCH", HttpStatus.FORBIDDEN);
            } else if (msgUpper.contains("USADO") || msgUpper.contains("NO SE ENCUENTRA ACTIVO") || msgUpper.contains("LÍMITE") || msgUpper.contains("LIMITE") || msgUpper.contains("AGOTADO")) {
                throw new QrAccessException(msg, "QR_ALREADY_USED", HttpStatus.CONFLICT);
            } else {
                throw new QrAccessException(msg, "QR_ACCESS_DENIED", HttpStatus.CONFLICT);
            }
        }

        Long visitaId = resultadoSp.visitaId();

        // 3. Actualizar visita a EN_CURSO si aplica (F7-01 canónico)
        if (visitaId != null) {
            porteriaRepository.updateVisitaEstado(visitaId, "EN_CURSO");
        }

        // 4. Complementar metadata en REGISTROS_ACCESO (placa y observaciones) si aplica
        String obs = (medioTransporte != null ? medioTransporte : "A_PIE") +
                (descripcion != null && !descripcion.isBlank() ? " - " + descripcion : "");
        if (visitaId != null) {
            try {
                jdbcTemplate.update(
                    "UPDATE REGISTROS_ACCESO SET PLACA_VEHICULO = :placa, OBSERVACIONES = :obs " +
                    "WHERE ID_REGISTRO_ACCESO = (" +
                    "  SELECT MAX(ID_REGISTRO_ACCESO) FROM REGISTROS_ACCESO " +
                    "  WHERE ID_VISITA = :visitaId AND TIPO_MOVIMIENTO = 'ENTRADA'" +
                    ")",
                    new MapSqlParameterSource()
                        .addValue("placa", placa != null && !placa.isBlank() ? placa.trim().toUpperCase() : null)
                        .addValue("obs", obs)
                        .addValue("visitaId", visitaId)
                );
            } catch (Exception e) {
                log.warn("Aviso al complementar registro de acceso: {}", e.getMessage());
            }
        }

        // 5. Asignar parqueadero y registrar vehículo si aplica
        String parqAsignado = null;
        if (visitaId != null && ("CARRO".equalsIgnoreCase(medioTransporte) || "MOTO".equalsIgnoreCase(medioTransporte))) {
            try {
                List<Map<String, Object>> parqs = jdbcTemplate.queryForList(
                    "SELECT ID_PARQUEADERO, NUMERO_PARQUEADERO FROM PARQUEADEROS " +
                    "WHERE ID_PROPIEDAD = :propId AND ESTADO = 'DISPONIBLE' " +
                    "ORDER BY ID_PARQUEADERO",
                    new MapSqlParameterSource("propId", propId)
                );
                Long parqId = null;
                if (!parqs.isEmpty()) {
                    parqId = ((Number) parqs.get(0).get("ID_PARQUEADERO")).longValue();
                    parqAsignado = (String) parqs.get(0).get("NUMERO_PARQUEADERO");
                    jdbcTemplate.update("UPDATE PARQUEADEROS SET ESTADO = 'ASIGNADO' WHERE ID_PARQUEADERO = :id", new MapSqlParameterSource("id", parqId));
                }
                VehiculoVisitaRequestDTO vehRequest = new VehiculoVisitaRequestDTO(
                    visitaId,
                    parqId,
                    placa != null ? placa : "",
                    "CARRO".equalsIgnoreCase(medioTransporte) ? "CARRO" : "MOTO",
                    "DENTRO"
                );
                porteriaRepository.createVehiculoVisita(vehRequest);
            } catch (Exception e) {
                log.warn("No se pudo asignar parqueadero o registrar vehículo: {}", e.getMessage());
            }
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("mensaje", resultadoSp.mensaje() != null ? resultadoSp.mensaje() : "Entrada registrada exitosamente");
        resp.put("idVisita", visitaId);
        resp.put("parqueadero", parqAsignado);
        return resp;
    }

    @Override
    @Auditable(action = "CHECKIN_VEHICULO", resource = "ACCESO_VEHICULO", category = AuditCategory.SECURITY, severity = AuditSeverity.INFO)
    public VehiculoVisitaDTO registrarIngresoVehiculo(VehiculoVisitaRequestDTO request) {
        return porteriaRepository.createVehiculoVisita(request);
    }

    @Override
    @Auditable(action = "CHECKOUT_VEHICULO", resource = "ACCESO_VEHICULO", category = AuditCategory.SECURITY, severity = AuditSeverity.INFO)
    public void registrarSalidaVehiculo(Long vehiculoVisitaId, BigDecimal costoTotal) {
        porteriaRepository.registerSalidaVehiculo(vehiculoVisitaId, costoTotal);
    }

    private Map<String, Object> checkExistingEntry(QrAccesoDTO qr) {
        if (qr == null || qr.visitaId() == null) {
            return null;
        }
        try {
            List<Map<String, Object>> existing = jdbcTemplate.queryForList(
                "SELECT ra.ID_REGISTRO_ACCESO, p.NUMERO_PARQUEADERO " +
                "FROM REGISTROS_ACCESO ra " +
                "LEFT JOIN VEHICULOS_VISITA vv ON vv.ID_VISITA = ra.ID_VISITA AND vv.ESTADO = 'DENTRO' " +
                "LEFT JOIN PARQUEADEROS p ON p.ID_PARQUEADERO = vv.ID_PARQUEADERO " +
                "WHERE ra.ID_QR = :idQr AND ra.TIPO_ACCESO = 'ENTRADA' " +
                "ORDER BY ra.FECHA_ACCESO DESC",
                new MapSqlParameterSource("idQr", qr.idQr())
            );
            if (!existing.isEmpty()) {
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", true);
                resp.put("mensaje", "Entrada ya registrada previamente para este visitante");
                resp.put("idVisita", qr.visitaId());
                Object parqObj = existing.get(0).get("NUMERO_PARQUEADERO");
                String parq = parqObj != null ? parqObj.toString() : null;
                resp.put("parqueadero", parq);
                resp.put("yaRegistrado", true);
                return resp;
            }
        } catch (Exception e) {
            log.debug("Aviso al verificar entrada existente para QR {}: {}", qr.idQr(), e.getMessage());
        }
        return null;
    }
}






