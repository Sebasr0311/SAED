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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
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

    public static final int MAX_QR_USES = 10;
    public static final int MIN_QR_VALIDITY_MINUTES = 5;
    public static final int MAX_QR_VALIDITY_MINUTES = 7 * 24 * 60;
    /** Per-property overtime tariff for visitor parking, per started hour. Default 0 (no charge). */
    public static final String KEY_TARIFA_PARQUEADERO_HORA = "TARIFA_PARQUEADERO_VISITANTE_HORA";
    /** Free visitor parking minutes before the tariff applies. Defaults to the maximum visit time. */
    public static final String KEY_MINUTOS_GRATIS_PARQUEADERO = "MINUTOS_GRATIS_PARQUEADERO_VISITANTE";

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final List<String> OPEN_VISIT_STATES = List.of("PROGRAMADA", "EN_CURSO");
    private static final Set<String> TERMINAL_VISIT_STATES = Set.of("CANCELADA", "RECHAZADA", "FINALIZADA");
    private static final Set<String> RESIDENT_VISIT_STATES = Set.of("PROGRAMADA", "CANCELADA");
    private static final Set<String> STAFF_VISIT_STATES =
        Set.of("PROGRAMADA", "EN_CURSO", "FINALIZADA", "CANCELADA", "RECHAZADA");
    private static final Set<String> METODOS_INGRESO =
        Set.of("CODIGO_QR", "LLAMADA_PORTERIA", "PREAUTORIZADA", "EMERGENCIA");

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

    // --- SCOPE ---

    /** Tenant context for operational data. SUPERADMIN never reads porteria data (DEC-02). */
    private static SaedContext requireOperationalContext() {
        SaedContext ctx = SaedContextHolder.getContext();
        if (ctx == null || ctx.getUserId() == null) {
            throw new AccessDeniedException("Missing tenant context");
        }
        if ("SUPERADMIN".equals(ctx.getRoleCode()) || "GLOBAL".equals(ctx.getRoleScope())) {
            throw new AccessDeniedException("Platform administrators do not access porteria operational data");
        }
        return ctx;
    }

    private static Long requirePropertyId(SaedContext ctx) {
        if (ctx.getPropertyId() == null) {
            throw new AccessDeniedException("Missing property context");
        }
        return ctx.getPropertyId();
    }

    private static boolean isResident(SaedContext ctx) {
        return "RESIDENTE".equals(ctx.getRoleCode()) || "RESIDENTE_CONVIVENCIA".equals(ctx.getRoleCode())
            || "UNIDAD".equals(ctx.getRoleScope());
    }

    /**
     * Loads a visit only when it is in the caller's scope: residents see visits of their unit, staff see
     * visits of their property. Out-of-scope and nonexistent visits are indistinguishable (404).
     */
    private VisitaDTO requireVisitInScope(Long visitaId) {
        SaedContext ctx = requireOperationalContext();
        VisitaDTO visita = porteriaRepository.getVisitaById(visitaId)
            .orElseThrow(() -> new NoSuchElementException("Visita no encontrada"));
        boolean inScope;
        if (isResident(ctx)) {
            inScope = ctx.getUnitId() != null
                ? ctx.getUnitId().equals(visita.unidadId())
                : porteriaRepository.findActiveUnitIdsForUser(ctx.getUserId()).contains(visita.unidadId());
        } else {
            Long propId = ctx.getPropertyId();
            inScope = propId != null && propId.equals(porteriaRepository.findVisitaPropertyId(visitaId).orElse(null));
        }
        if (!inScope) {
            throw new NoSuchElementException("Visita no encontrada");
        }
        return visita;
    }

    // --- ADMIN CRUD PORTERÍAS ---
    @Override
    @Transactional(readOnly = true)
    public List<PorteriaDTO> listarPorterias() {
        Long propId = requirePropertyId(requireOperationalContext());
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
        Long propId = requirePropertyId(requireOperationalContext());
        List<PorteriaDTO> found = jdbcTemplate.query(
            "SELECT ID_PORTERIA, NOMBRE, UBICACION, TELEFONO_CONTACTO, ESTADO FROM PORTERIAS "
                + "WHERE ID_PORTERIA = :id AND ID_PROPIEDAD = :propId",
            Map.of("id", id, "propId", propId),
            (rs, rowNum) -> new PorteriaDTO(
                rs.getLong("ID_PORTERIA"), rs.getString("NOMBRE"),
                rs.getString("UBICACION"), rs.getString("TELEFONO_CONTACTO"),
                rs.getString("ESTADO")
            )
        );
        return found.stream().findFirst().orElseThrow(() -> new NoSuchElementException("Portería no encontrada"));
    }

    @Override
    public PorteriaDTO crearPorteria(PorteriaCreateDTO request) {
        Long propId = requirePropertyId(requireOperationalContext());
        var keyHolder = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbcTemplate.update(
            "INSERT INTO PORTERIAS (ID_PROPIEDAD, NOMBRE, UBICACION, TELEFONO_CONTACTO) VALUES (:propId, :nombre, :ubicacion, :tel)",
            new MapSqlParameterSource()
                .addValue("propId", propId)
                .addValue("nombre", request.nombre())
                .addValue("ubicacion", request.ubicacion())
                .addValue("tel", request.telefonoContacto()),
            keyHolder,
            new String[]{"ID_PORTERIA"}
        );
        Number id = keyHolder.getKey();
        return getPorteriaById(id.longValue());
    }

    @Override
    public PorteriaDTO actualizarPorteria(Long id, PorteriaCreateDTO request) {
        Long propId = requirePropertyId(requireOperationalContext());
        int updated = jdbcTemplate.update(
            "UPDATE PORTERIAS SET NOMBRE = :nombre, UBICACION = :ubicacion, TELEFONO_CONTACTO = :tel "
                + "WHERE ID_PORTERIA = :id AND ID_PROPIEDAD = :propId",
            new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("propId", propId)
                .addValue("nombre", request.nombre())
                .addValue("ubicacion", request.ubicacion())
                .addValue("tel", request.telefonoContacto())
        );
        if (updated == 0) {
            throw new NoSuchElementException("Portería no encontrada");
        }
        return getPorteriaById(id);
    }

    @Override
    public void eliminarPorteria(Long id) {
        Long propId = requirePropertyId(requireOperationalContext());
        int deleted = jdbcTemplate.update("DELETE FROM PORTERIAS WHERE ID_PORTERIA = :id AND ID_PROPIEDAD = :propId",
            Map.of("id", id, "propId", propId));
        if (deleted == 0) {
            throw new NoSuchElementException("Portería no encontrada");
        }
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
        return requireVisitInScope(id);
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

    private int maxVisitMinutes(Long propId) {
        return propertyConfigService != null
            ? propertyConfigService.getIntValue(propId, PropertyConfigServiceImpl.KEY_TIEMPO_MAXIMO_VISITA, 240)
            : 240;
    }

    @Override
    @Transactional(readOnly = true)
    public List<VisitaListDTO> getVisitasResumen() {
        Long propId = requirePropertyId(requireOperationalContext());
        List<VisitaListDTO> visitas = porteriaRepository.getVisitasResumen(propId);
        final int limit = maxVisitMinutes(propId);
        ZonedDateTime now = ZonedDateTime.now(BOGOTA);

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

    /**
     * F07-03: one transactional exit. Finalizes the visit, closes every vehicle still inside with a
     * server-side fee, frees its parking spot, revokes remaining QRs and records the SALIDA access.
     * A repeated or concurrent exit is a no-op.
     */
    @Override
    @Auditable(action = "CHECKOUT_VISITA", resource = "ACCESO_PORTERIA", category = AuditCategory.SECURITY, severity = AuditSeverity.INFO)
    public void registrarSalidaVisita(Long id) {
        SaedContext ctx = requireOperationalContext();
        VisitaDTO visita = requireVisitInScope(id);
        if (!OPEN_VISIT_STATES.contains(visita.estado())) {
            return;
        }
        if (porteriaRepository.transitionVisitaEstado(id, "FINALIZADA", OPEN_VISIT_STATES) == 0) {
            return;
        }
        Long propId = requirePropertyId(ctx);
        for (VehiculoVisitaDTO vehiculo : porteriaRepository.findVehiculosDentro(id)) {
            closeVehicle(vehiculo, propId);
        }
        porteriaRepository.revokeActiveQrs(id, ctx.getUserId(), "SALIDA_REGISTRADA");
        Long porteriaId = porteriaRepository.findFirstPorteriaId(propId)
            .orElseThrow(() -> new IllegalStateException("La propiedad no tiene porterías configuradas"));
        porteriaRepository.insertSalidaVisita(id, porteriaId, ctx.getUserId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<VisitaHistorialDTO> getVisitasHistorial(String fechaInicio, String fechaFin) {
        Long propId = requirePropertyId(requireOperationalContext());
        return porteriaRepository.getVisitasHistorial(propId, fechaInicio, fechaFin);
    }

    @Override
    @Transactional(readOnly = true)
    public VisitaDetalleDTO getVisitaDetalle(Long id) {
        requireVisitInScope(id);
        VisitaDetalleDTO v = porteriaRepository.getVisitaDetalle(id)
                .orElseThrow(() -> new NoSuchElementException("Visita no encontrada"));
        Long propId = porteriaRepository.findVisitaPropertyId(id).orElse(null);
        int maxMinutos = propId != null ? maxVisitMinutes(propId) : 240;

        ZonedDateTime now = ZonedDateTime.now(BOGOTA);
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

    /**
     * F7-07: unit and visitor are immutable after creation; residents may only keep or cancel their visit;
     * a closed visit is never reopened; closing a visit revokes its QR codes.
     */
    @Override
    public VisitaDTO actualizarVisita(Long id, VisitaRequestDTO request) {
        SaedContext ctx = requireOperationalContext();
        VisitaDTO existing = requireVisitInScope(id);
        String estado = request.estado() != null ? request.estado().trim().toUpperCase() : existing.estado();
        Set<String> allowed = isResident(ctx) ? RESIDENT_VISIT_STATES : STAFF_VISIT_STATES;
        if (!allowed.contains(estado)) {
            throw new IllegalArgumentException("Estado de visita no permitido: " + estado);
        }
        if (TERMINAL_VISIT_STATES.contains(existing.estado()) && !estado.equals(existing.estado())) {
            throw new IllegalStateException("La visita ya fue cerrada y no puede modificarse");
        }
        String metodo = request.metodoIngreso() != null && METODOS_INGRESO.contains(request.metodoIngreso().trim().toUpperCase())
            ? request.metodoIngreso().trim().toUpperCase() : existing.metodoIngreso();
        // SEC-02: Retain original autorizadoPor to prevent tampering on update
        VisitaRequestDTO safeRequest = new VisitaRequestDTO(
            existing.unidadId(),
            existing.visitanteId(),
            metodo,
            request.motivo(),
            existing.autorizadoPor(),
            request.fechaProgramada() != null ? request.fechaProgramada() : existing.fechaProgramada(),
            estado
        );
        VisitaDTO updated = porteriaRepository.updateVisita(id, safeRequest);
        if (TERMINAL_VISIT_STATES.contains(estado)) {
            porteriaRepository.revokeActiveQrs(id, ctx.getUserId(), "VISITA_" + estado);
        }
        return updated;
    }

    @Override
    @Auditable(action = "CANCELAR_VISITA", resource = "ACCESO_PORTERIA", category = AuditCategory.SECURITY, severity = AuditSeverity.INFO)
    public void cancelarVisita(Long id) {
        SaedContext ctx = requireOperationalContext();
        VisitaDTO visita = requireVisitInScope(id);
        if ("CANCELADA".equals(visita.estado())) {
            porteriaRepository.revokeActiveQrs(id, ctx.getUserId(), "VISITA_CANCELADA");
            return;
        }
        if (porteriaRepository.transitionVisitaEstado(id, "CANCELADA", List.of("PROGRAMADA")) == 0) {
            throw new IllegalStateException("Solo se pueden cancelar visitas programadas");
        }
        porteriaRepository.revokeActiveQrs(id, ctx.getUserId(), "VISITA_CANCELADA");
    }

    @Override
    @Auditable(action = "REVOCAR_QR", resource = "ACCESO_PORTERIA", category = AuditCategory.SECURITY, severity = AuditSeverity.INFO)
    public void revocarQr(Long idQr, String motivo) {
        SaedContext ctx = requireOperationalContext();
        QrAccesoDTO qr = porteriaRepository.getQrAccesoById(idQr)
            .orElseThrow(() -> new NoSuchElementException("QR Acceso no encontrado"));
        if (qr.visitaId() == null) {
            throw new NoSuchElementException("QR Acceso no encontrado");
        }
        requireVisitInScope(qr.visitaId());
        String reason = motivo == null || motivo.isBlank() ? "REVOCADO_POR_USUARIO"
            : (motivo.length() > 200 ? motivo.substring(0, 200) : motivo);
        if (porteriaRepository.revokeQr(idQr, ctx.getUserId(), reason) == 0 && !"REVOCADO".equals(qr.estado())) {
            throw new IllegalStateException("El código QR ya no se encuentra activo");
        }
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
        Long propId = requirePropertyId(requireOperationalContext());
        if (!propId.equals(propiedadId)) {
            throw new AccessDeniedException("No tiene permisos sobre la propiedad indicada");
        }
        return porteriaRepository.getRegistrosByPropiedad(propiedadId);
    }

    /**
     * F07-01: the QR token, uses, state and generator are server-controlled; the requested expiry is
     * clamped to [MIN_QR_VALIDITY_MINUTES, MAX_QR_VALIDITY_MINUTES]; the visit must be open and in scope.
     */
    @Override
    public QrAccesoDTO generarQrAcceso(QrAccesoRequestDTO request) {
        SaedContext ctx = requireOperationalContext();
        VisitaDTO visita = requireVisitInScope(request.visitaId());
        if (!OPEN_VISIT_STATES.contains(visita.estado())) {
            throw new IllegalStateException("No se puede generar un código QR para una visita cerrada");
        }
        ZonedDateTime now = ZonedDateTime.now(BOGOTA);
        ZonedDateTime min = now.plusMinutes(MIN_QR_VALIDITY_MINUTES);
        ZonedDateTime max = now.plusMinutes(MAX_QR_VALIDITY_MINUTES);
        ZonedDateTime requested = request.fechaExpiracion();
        ZonedDateTime expiry = requested == null || requested.isAfter(max) ? max
            : (requested.isBefore(min) ? min : requested);
        int usos = request.usosPermitidos() == null ? 1
            : Math.max(1, Math.min(MAX_QR_USES, request.usosPermitidos()));

        QrAccesoRequestDTO safeRequest = new QrAccesoRequestDTO(
            visita.idVisita(),
            UUID.randomUUID().toString(),
            expiry,
            usos,
            "ACTIVO",
            ctx.getUserId()
        );
        QrAccesoDTO qr = porteriaRepository.createQrAcceso(safeRequest);
        try {
            List<Map<String, Object>> residentes = jdbcTemplate.queryForList(
                "SELECT P.EMAIL FROM PERSONAS P " +
                "JOIN RESIDENTES_UNIDAD UH ON UH.ID_PERSONA = P.ID_PERSONA " +
                "WHERE UH.ID_UNIDAD = :u AND P.EMAIL IS NOT NULL",
                Map.of("u", visita.unidadId())
            );
            if (!residentes.isEmpty()) {
                String destinatario = (String) residentes.get(0).get("EMAIL");
                emailService.enviarCorreoQRAsync(destinatario, qr.tokenQr(), qr.fechaExpiracion().toString(), "Visitante");
            }
        } catch (RuntimeException e) {
            // The QR is valid without the courtesy e-mail; record only the failure type.
            log.warn("QR e-mail for visit {} not sent ({})", visita.idVisita(), e.getClass().getSimpleName());
        }
        return qr;
    }

    @Override
    @Transactional(readOnly = true)
    public QrAccesoDTO getQrAccesoById(Long id) {
        QrAccesoDTO qr = porteriaRepository.getQrAccesoById(id)
                .orElseThrow(() -> new NoSuchElementException("QR Acceso no encontrado"));
        if (qr.visitaId() == null) {
            throw new NoSuchElementException("QR Acceso no encontrado");
        }
        requireVisitInScope(qr.visitaId());
        return qr;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> validarQrDetalle(String token) {
        SaedContext ctx = requireOperationalContext();
        if (token == null || token.isBlank()) {
            return Map.of("valido", false, "mensaje", "Token no proporcionado");
        }
        QrAccesoDTO qr = porteriaRepository.getQrAccesoByToken(token).orElse(null);
        Long qrPropId = qr != null && qr.visitaId() != null
            ? porteriaRepository.findVisitaPropertyId(qr.visitaId()).orElse(null) : null;
        if (qr == null || ctx.getPropertyId() == null || !ctx.getPropertyId().equals(qrPropId)) {
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
        VisitaDTO visita = porteriaRepository.getVisitaById(qr.visitaId()).orElse(null);
        if (visita == null || !OPEN_VISIT_STATES.contains(visita.estado())) {
            return Map.of("valido", false, "mensaje", "La visita asociada al código QR fue cancelada o finalizada");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("valido", true);
        result.put("mensaje", "Código QR válido");
        result.put("codigoQr", qr.tokenQr());
        result.put("idVisita", qr.visitaId());
        result.put("fechaExpiracion", qr.fechaExpiracion() != null ? qr.fechaExpiracion().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) : null);
        result.put("fechaGeneracion", qr.fechaGeneracion() != null ? qr.fechaGeneracion().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) : null);
        if (qr.fechaExpiracion() != null) {
            long minRestantes = java.time.Duration.between(ZonedDateTime.now(BOGOTA), qr.fechaExpiracion()).toMinutes();
            result.put("minutosRestantes", minRestantes);
        }

        VisitaDetalleDTO detalle = porteriaRepository.getVisitaDetalle(qr.visitaId()).orElse(null);
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

    /**
     * F07-01 / F7-08: entry by QR. Requires the caller's property context (no default property or
     * portero), refuses QRs of closed visits, and consumes the QR only through the atomic
     * SP_VALIDAR_CONSUMIR_QR. Parking is claimed with a conditional update (F7-04).
     */
    @Override
    @Auditable(action = "CHECKIN_QR", resource = "ACCESO_PORTERIA", category = AuditCategory.SECURITY, severity = AuditSeverity.INFO)
    public Map<String, Object> registrarEntradaQr(String token, String medioTransporte, String placa, String descripcion) {
        SaedContext ctx = requireOperationalContext();
        Long propId = requirePropertyId(ctx);
        Long porteroId = ctx.getUserId();
        if (token == null || token.isBlank()) {
            throw new QrAccessException("Token QR requerido", "BAD_REQUEST", HttpStatus.BAD_REQUEST);
        }

        // A QR of a cancelled/rejected/finished visit never admits entry, even if it is still ACTIVO.
        QrAccesoDTO visible = porteriaRepository.getQrAccesoByToken(token).orElse(null);
        if (visible != null && visible.visitaId() != null) {
            VisitaDTO visita = porteriaRepository.getVisitaById(visible.visitaId()).orElse(null);
            if (visita != null && !OPEN_VISIT_STATES.contains(visita.estado())) {
                throw new QrAccessException("La visita asociada al código QR fue cancelada o finalizada",
                    "QR_REVOKED", HttpStatus.CONFLICT);
            }
        }

        Long porteriaId = porteriaRepository.findFirstPorteriaId(propId)
            .orElseThrow(() -> new IllegalStateException("La propiedad no tiene porterías configuradas"));

        QrConsumoResultadoDTO resultadoSp = porteriaRepository.validarYConsumirQrSp(token, porteriaId, porteroId);

        if (!resultadoSp.valido()) {
            String msg = resultadoSp.mensaje() != null ? resultadoSp.mensaje() : "Acceso denegado con código QR";
            if (msg.startsWith("Error interno")) {
                log.warn("SP_VALIDAR_CONSUMIR_QR reported an internal error for property {}", propId);
                throw new QrAccessException("No fue posible validar el código QR", "QR_VALIDATION_ERROR",
                    HttpStatus.INTERNAL_SERVER_ERROR);
            }
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

        if (visitaId != null) {
            porteriaRepository.updateVisitaEstado(visitaId, "EN_CURSO");
            String obs = (medioTransporte != null ? medioTransporte : "A_PIE") +
                    (descripcion != null && !descripcion.isBlank() ? " - " + descripcion : "");
            porteriaRepository.complementEntrada(visitaId,
                placa != null && !placa.isBlank() ? placa.trim().toUpperCase() : null,
                obs.length() > 300 ? obs.substring(0, 300) : obs);
        }

        String parqAsignado = null;
        boolean carro = "CARRO".equalsIgnoreCase(medioTransporte);
        boolean moto = "MOTO".equalsIgnoreCase(medioTransporte);
        if (visitaId != null && (carro || moto)) {
            ParkingSpotClaim claim = porteriaRepository.claimAvailableSpot(propId, moto ? "MOTOS" : "VISITANTES")
                .orElse(null);
            if (claim != null) {
                parqAsignado = claim.numeroParqueadero();
            }
            porteriaRepository.createVehiculoVisita(new VehiculoVisitaRequestDTO(
                visitaId,
                claim != null ? claim.idParqueadero() : null,
                placa != null ? placa.trim().toUpperCase() : "",
                carro ? "CARRO" : "MOTO",
                "DENTRO"
            ));
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
    public void registrarSalidaVehiculo(Long vehiculoVisitaId) {
        SaedContext ctx = requireOperationalContext();
        VehiculoVisitaDTO vehiculo = porteriaRepository.getVehiculoVisitaById(vehiculoVisitaId)
            .orElseThrow(() -> new NoSuchElementException("Vehículo no encontrado"));
        requireVisitInScope(vehiculo.visitaId());
        if (!"DENTRO".equals(vehiculo.estado())) {
            return;
        }
        closeVehicle(vehiculo, requirePropertyId(ctx));
    }

    private void closeVehicle(VehiculoVisitaDTO vehiculo, Long propId) {
        BigDecimal fee = computeParkingFee(propId, vehiculo.fechaIngreso(), ZonedDateTime.now(BOGOTA));
        boolean closed = porteriaRepository.closeVehiculoVisita(vehiculo.idVehiculoVisita(), fee);
        if (closed && vehiculo.parqueaderoId() != null) {
            porteriaRepository.releaseParqueadero(vehiculo.parqueaderoId());
        }
    }

    /**
     * Overtime fee: the per-property hourly tariff applied to every started hour beyond the free minutes.
     * With no tariff configured (default) the fee is zero.
     */
    BigDecimal computeParkingFee(Long propId, ZonedDateTime ingreso, ZonedDateTime salida) {
        int tarifa = propertyConfigService.getIntValue(propId, KEY_TARIFA_PARQUEADERO_HORA, 0);
        if (tarifa <= 0 || ingreso == null || salida == null) {
            return BigDecimal.ZERO;
        }
        int gratis = propertyConfigService.getIntValue(propId, KEY_MINUTOS_GRATIS_PARQUEADERO, maxVisitMinutes(propId));
        long minutos = Math.max(0, Duration.between(ingreso, salida).toMinutes());
        long exceso = minutos - Math.max(0, gratis);
        if (exceso <= 0) {
            return BigDecimal.ZERO;
        }
        long horas = (exceso + 59) / 60;
        return BigDecimal.valueOf(horas * tarifa);
    }
}
