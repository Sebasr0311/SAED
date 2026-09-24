package com.saed.backend.pqrs.service.impl;

import com.saed.backend.audit.AuditCategory;
import com.saed.backend.audit.AuditSeverity;
import com.saed.backend.audit.Auditable;
import com.saed.backend.common.service.EmailService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.pqrs.dto.PqrsSlaConfigDTO;
import com.saed.backend.pqrs.dto.TicketRequestDTO;
import com.saed.backend.pqrs.dto.TicketResponseDTO;
import com.saed.backend.pqrs.dto.TicketTrazabilidadDTO;
import com.saed.backend.pqrs.exception.PqrsInvalidStateException;
import com.saed.backend.pqrs.exception.PqrsInvalidTransitionException;
import com.saed.backend.pqrs.repository.TicketRepository;
import com.saed.backend.pqrs.service.TicketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class TicketServiceImpl implements TicketService {

    private static final Logger log = LoggerFactory.getLogger(TicketServiceImpl.class);
    private static final ZoneId BOGOTA_ZONE = ZoneId.of("America/Bogota");

    private static final Set<String> VALID_TIPOS = Set.of(
            "PETICION", "QUEJA", "RECLAMO", "SUGERENCIA", "APELACION", "SOLICITUD"
    );

    private static final Set<String> VALID_CATEGORIAS = Set.of(
            "SEGURIDAD", "MANTENIMIENTO", "LIMPIEZA", "CONVIVENCIA", "ZONAS_COMUNES", "ADMINISTRACION", "FINANCIERA", "OTRA"
    );

    private static final Set<String> VALID_PRIORIDADES = Set.of(
            "EMERGENCIA", "ALTA", "MEDIA", "BAJA"
    );

    private static final Map<String, Set<String>> ALLOWED_TRANSITIONS = Map.of(
            "RADICADO", Set.of("ASIGNADO", "EN_GESTION", "RECHAZADO"),
            "ASIGNADO", Set.of("EN_GESTION", "RECHAZADO", "ASIGNADO"),
            "EN_GESTION", Set.of("ESCALADO", "RESUELTO", "RECHAZADO", "ASIGNADO"),
            "ESCALADO", Set.of("EN_GESTION", "RESUELTO", "RECHAZADO"),
            "RESUELTO", Set.of("CERRADO", "EN_GESTION"),
            "CERRADO", Set.of(),
            "RECHAZADO", Set.of()
    );

    private final TicketRepository ticketRepository;
    private final EmailService emailService;
    private final JdbcTemplate jdbcTemplate;

    public TicketServiceImpl(TicketRepository ticketRepository, EmailService emailService, JdbcTemplate jdbcTemplate) {
        this.ticketRepository = ticketRepository;
        this.emailService = emailService;
        this.jdbcTemplate = jdbcTemplate;
    }

    private Long getRequiredPropertyId() {
        SaedContext ctx = SaedContextHolder.getContext();
        Long propId = ctx != null ? ctx.getPropertyId() : null;
        if (propId == null) {
            throw new IllegalStateException("No hay una propiedad activa en el contexto de seguridad.");
        }
        return propId;
    }

    private Long getRequiredUserId() {
        SaedContext ctx = SaedContextHolder.getContext();
        Long userId = ctx != null ? ctx.getUserId() : null;
        if (userId == null) {
            throw new IllegalStateException("No hay un usuario autenticado en el contexto de seguridad.");
        }
        return userId;
    }

    @Override
    public List<TicketResponseDTO> getAllTickets() {
        SaedContext ctx = SaedContextHolder.getContext();
        Long propId = ctx != null ? ctx.getPropertyId() : null;
        return ticketRepository.findAll(propId);
    }

    @Override
    public List<TicketResponseDTO> getMyTickets() {
        Long propId = getRequiredPropertyId();
        Long userId = getRequiredUserId();
        return ticketRepository.findByPersona(propId, userId);
    }

    @Override
    public TicketResponseDTO getTicketById(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("ID de ticket inválido.");
        }
        Long propId = getRequiredPropertyId();
        TicketResponseDTO ticket = ticketRepository.findById(id, propId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket PQRS no encontrado para la propiedad activa."));

        SaedContext ctx = SaedContextHolder.getContext();
        String role = ctx != null ? ctx.getRoleCode() : "";
        if ("RESIDENTE".equals(role) || "RESIDENTE_CONVIVENCIA".equals(role)) {
            Long myPersonaId = ticketRepository.getIdPersonaFromUsuario(ctx.getUserId());
            if (ticket.getIdPersonaRadica() != null && !ticket.getIdPersonaRadica().equals(myPersonaId)) {
                throw new AccessDeniedException("No tiene permisos para consultar este ticket.");
            }
        }
        return ticket;
    }

    @Override
    @Transactional
    @Auditable(action = "CREATE", resource = "PQRS", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.INFO)
    public Long createTicket(TicketRequestDTO request) {
        if (request == null) {
            throw new IllegalArgumentException("El cuerpo de la solicitud no puede estar vacío.");
        }

        String asunto = request.getAsunto() != null ? request.getAsunto().trim() : "";
        if (asunto.length() < 5) {
            throw new IllegalArgumentException("El asunto debe tener al menos 5 caracteres.");
        }
        if (asunto.length() > 150) {
            asunto = asunto.substring(0, 150);
        }
        request.setAsunto(asunto);

        String desc = request.getDescripcion() != null ? request.getDescripcion().trim() : "";
        if (desc.length() < 10) {
            throw new IllegalArgumentException("La descripción debe tener al menos 10 caracteres.");
        }
        request.setDescripcion(desc);

        // Normalize tipo
        String tipo = request.getTipo() != null ? request.getTipo().trim().toUpperCase() : "PETICION";
        if (!VALID_TIPOS.contains(tipo)) {
            throw new IllegalArgumentException("Tipo de ticket inválido: " + tipo + ". Valores permitidos: " + VALID_TIPOS);
        }
        request.setTipo(tipo);

        // Normalize categoria
        String cat = request.getCategoria() != null ? request.getCategoria().trim().toUpperCase() : "ADMINISTRACION";
        if ("OTRO".equals(cat)) {
            cat = "OTRA";
        }
        if (!VALID_CATEGORIAS.contains(cat)) {
            throw new IllegalArgumentException("Categoría de ticket inválida: " + cat + ". Valores permitidos: " + VALID_CATEGORIAS);
        }
        request.setCategoria(cat);

        // Normalize prioridad
        String prioridad = request.getPrioridad() != null ? request.getPrioridad().trim().toUpperCase() : "MEDIA";
        if (!VALID_PRIORIDADES.contains(prioridad)) {
            throw new IllegalArgumentException("Prioridad de ticket inválida: " + prioridad + ". Valores permitidos: " + VALID_PRIORIDADES);
        }
        request.setPrioridad(prioridad);

        Long propId = getRequiredPropertyId();
        Long userId = getRequiredUserId();

        // Resolve unit ID if available
        Long unitId = request.getIdUnidad();
        if (unitId == null && SaedContextHolder.getContext() != null) {
            unitId = SaedContextHolder.getContext().getUnitId();
        }
        if (unitId == null) {
            unitId = ticketRepository.getIdUnidadFromUsuario(userId, propId);
        }

        // Dynamic SLA calculation from PQRS_SLA_CONFIGURACION
        int horasSla = ticketRepository.getSlaHoras(propId, prioridad)
                .orElseGet(() -> fallbackHorasSla(prioridad));

        ZonedDateTime now = ZonedDateTime.now(BOGOTA_ZONE);
        ZonedDateTime fechaLimiteSla = now.plusHours(horasSla);

        // Unique Radicado: PQRS-YYYYMMDD-XXXXXX
        String datePart = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String randomPart = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String numeroRadicado = "PQRS-" + datePart + "-" + randomPart;

        // Persist ticket
        Long idTicket = ticketRepository.create(request, propId, unitId, userId, numeroRadicado, now, fechaLimiteSla);

        // Atomic traceability: RADICACION
        ticketRepository.insertTrazabilidad(
                idTicket,
                userId,
                "RADICACION",
                null,
                "RADICADO",
                "Radicación formal del ticket: " + request.getAsunto(),
                request.getAdjuntosUrl()
        );

        log.info("[TicketService] Creado ticket PQRS ID {} (Radicado: {}) con SLA {} horas (Vence: {}) para propiedad {}",
                idTicket, numeroRadicado, horasSla, fechaLimiteSla, propId);

        return idTicket;
    }

    private int fallbackHorasSla(String prioridad) {
        switch (prioridad) {
            case "EMERGENCIA": return 12;
            case "ALTA": return 24;
            case "BAJA": return 120;
            case "MEDIA":
            default:
                return 72;
        }
    }

    public static String normalizeEstado(String estado) {
        if (estado == null || estado.isBlank()) {
            throw new PqrsInvalidStateException("El estado del ticket no puede estar vacío.");
        }
        String s = estado.trim().toUpperCase();
        switch (s) {
            case "EN_REVISION":
            case "REVISION":
            case "EN_PROCESO":
            case "EN_GESTION":
                return "EN_GESTION";
            case "PENDIENTE":
            case "RADICADO":
                return "RADICADO";
            case "ASIGNADO":
                return "ASIGNADO";
            case "ESCALADO":
                return "ESCALADO";
            case "RESUELTA":
            case "RESUELTO":
                return "RESUELTO";
            case "CERRADA":
            case "CERRADO":
                return "CERRADO";
            case "RECHAZADA":
            case "RECHAZADO":
                return "RECHAZADO";
            default:
                throw new PqrsInvalidStateException("Estado de ticket no reconocido: '" + estado + "'. Estados válidos: RADICADO, ASIGNADO, EN_GESTION, ESCALADO, RESUELTO, CERRADO, RECHAZADO.");
        }
    }

    @Override
    @Transactional
    @Auditable(action = "UPDATE_STATUS", resource = "PQRS", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.INFO)
    public void updateTicketStatus(Long id, String estado, String observacion) {
        Long propId = getRequiredPropertyId();
        Long userId = getRequiredUserId();

        String nuevoEstado = normalizeEstado(estado);
        TicketResponseDTO ticket = getTicketById(id);
        String estadoActual = ticket.getEstado();

        // If same state and no observation change, return cleanly
        if (estadoActual.equals(nuevoEstado) && (observacion == null || observacion.isBlank())) {
            return;
        }

        // State Machine validation
        if (!estadoActual.equals(nuevoEstado)) {
            Set<String> permitidos = ALLOWED_TRANSITIONS.getOrDefault(estadoActual, Set.of());
            if (!permitidos.contains(nuevoEstado)) {
                throw new PqrsInvalidTransitionException(estadoActual, nuevoEstado);
            }
        }

        // Concurrency-safe update (CAS)
        int updated = ticketRepository.updateEstado(id, propId, estadoActual, nuevoEstado, observacion);
        if (updated == 0) {
            TicketResponseDTO fresh = ticketRepository.findById(id, propId)
                    .orElseThrow(() -> new IllegalArgumentException("Ticket no encontrado."));
            throw new PqrsInvalidTransitionException(estadoActual, nuevoEstado,
                    "Conflicto de concurrencia: el ticket ya fue modificado por otra transacción. Estado actual: " + fresh.getEstado());
        }

        // Atomic traceability
        String tipoIntervencion;
        if ("CERRADO".equals(nuevoEstado)) {
            tipoIntervencion = "CIERRE";
        } else if ("ESCALADO".equals(nuevoEstado)) {
            tipoIntervencion = "ESCALAMIENTO";
        } else if ("ASIGNADO".equals(nuevoEstado)) {
            tipoIntervencion = "ASIGNACION";
        } else {
            tipoIntervencion = "CAMBIO_ESTADO";
        }

        String comment = (observacion != null && !observacion.isBlank())
                ? observacion
                : "Cambio de estado de " + estadoActual + " a " + nuevoEstado;

        ticketRepository.insertTrazabilidad(id, userId, tipoIntervencion, estadoActual, nuevoEstado, comment, null);

        // Notify resident on status update
        notificarCambioEstado(id, nuevoEstado, observacion);
    }

    @Override
    @Transactional
    @Auditable(action = "RESPOND", resource = "PQRS", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.INFO)
    public void responderTicket(Long id, String respuesta, String nuevoEstado) {
        if (respuesta == null || respuesta.trim().length() < 3) {
            throw new IllegalArgumentException("La respuesta debe tener al menos 3 caracteres.");
        }
        Long propId = getRequiredPropertyId();
        Long userId = getRequiredUserId();
        TicketResponseDTO ticket = getTicketById(id);

        SaedContext ctx = SaedContextHolder.getContext();
        String role = ctx != null ? ctx.getRoleCode() : "";
        boolean isResident = "RESIDENTE".equals(role) || "RESIDENTE_CONVIVENCIA".equals(role);

        String targetEstado = ticket.getEstado();
        if (nuevoEstado != null && !nuevoEstado.isBlank()) {
            String norm = normalizeEstado(nuevoEstado);
            if (!norm.equals(ticket.getEstado())) {
                Set<String> permitidos = ALLOWED_TRANSITIONS.getOrDefault(ticket.getEstado(), Set.of());
                if (!permitidos.contains(norm)) {
                    throw new PqrsInvalidTransitionException(ticket.getEstado(), norm);
                }
                ticketRepository.updateEstado(id, propId, ticket.getEstado(), norm, respuesta.trim());
                targetEstado = norm;
            }
        }

        // Traceability intervention type
        String tipoIntervencion = isResident ? "RESPUESTA_RESIDENTE" : "RESPUESTA_INTERNA";
        ticketRepository.insertTrazabilidad(
                id,
                userId,
                tipoIntervencion,
                ticket.getEstado(),
                targetEstado,
                respuesta.trim(),
                null
        );

        if (!isResident) {
            notificarCambioEstado(id, targetEstado, respuesta.trim());
        }
    }

    @Override
    @Transactional
    @Auditable(action = "ASSIGN", resource = "PQRS", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.INFO)
    public void asignarTicket(Long id, Long idResponsable, String observacion) {
        if (idResponsable == null || idResponsable <= 0) {
            throw new IllegalArgumentException("Debe especificar un responsable válido.");
        }
        Long propId = getRequiredPropertyId();
        Long userId = getRequiredUserId();
        TicketResponseDTO ticket = getTicketById(id);

        String estadoActual = ticket.getEstado();
        String nuevoEstado = "ASIGNADO";

        if (!estadoActual.equals("ASIGNADO")) {
            Set<String> permitidos = ALLOWED_TRANSITIONS.getOrDefault(estadoActual, Set.of());
            if (!permitidos.contains(nuevoEstado)) {
                throw new PqrsInvalidTransitionException(estadoActual, nuevoEstado);
            }
        }

        ticketRepository.asignarResponsable(id, propId, idResponsable);

        String comment = "Asignado a responsable ID: " + idResponsable +
                (observacion != null && !observacion.isBlank() ? " - " + observacion.trim() : "");

        ticketRepository.insertTrazabilidad(id, userId, "ASIGNACION", estadoActual, nuevoEstado, comment, null);
    }

    @Override
    @Transactional
    @Auditable(action = "UPDATE_PRIORITY", resource = "PQRS", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.INFO)
    public void actualizarPrioridad(Long id, String prioridad) {
        if (prioridad == null || prioridad.isBlank()) {
            throw new IllegalArgumentException("La prioridad no puede estar vacía.");
        }
        String p = prioridad.trim().toUpperCase();
        if (!VALID_PRIORIDADES.contains(p)) {
            throw new IllegalArgumentException("Prioridad inválida: " + p + ". Permitidas: " + VALID_PRIORIDADES);
        }

        Long propId = getRequiredPropertyId();
        Long userId = getRequiredUserId();
        TicketResponseDTO ticket = getTicketById(id);

        int horasSla = ticketRepository.getSlaHoras(propId, p).orElseGet(() -> fallbackHorasSla(p));
        ZonedDateTime fechaBase = ticket.getFechaRadicacion() != null ? ticket.getFechaRadicacion() : ZonedDateTime.now(BOGOTA_ZONE);
        ZonedDateTime nuevaFechaLimite = fechaBase.plusHours(horasSla);

        ticketRepository.actualizarPrioridad(id, propId, p, nuevaFechaLimite);

        String comment = "Prioridad actualizada a " + p + ". Nuevo SLA límite: " +
                nuevaFechaLimite.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        ticketRepository.insertTrazabilidad(id, userId, "CAMBIO_ESTADO", ticket.getEstado(), ticket.getEstado(), comment, null);
    }

    @Override
    public List<TicketTrazabilidadDTO> getTrazabilidad(Long id) {
        getTicketById(id); // IDOR check
        return ticketRepository.findTrazabilidadByTicket(id);
    }

    @Override
    public List<PqrsSlaConfigDTO> getSlaConfigs() {
        Long propId = getRequiredPropertyId();
        return ticketRepository.getSlaConfigs(propId);
    }

    @Override
    @Transactional
    public void upsertSlaConfig(PqrsSlaConfigDTO config) {
        if (config == null) throw new IllegalArgumentException("Configuración no puede ser nula.");
        Long propId = getRequiredPropertyId();
        String prioridad = config.getPrioridad() != null ? config.getPrioridad().trim().toUpperCase() : "";
        if (!VALID_PRIORIDADES.contains(prioridad)) {
            throw new IllegalArgumentException("Prioridad inválida: " + prioridad);
        }
        int horas = config.getTiempoMaximoHoras() != null && config.getTiempoMaximoHoras() > 0
                ? config.getTiempoMaximoHoras() : fallbackHorasSla(prioridad);
        int alerta = config.getAlertaVencimientoHoras() != null && config.getAlertaVencimientoHoras() > 0
                ? config.getAlertaVencimientoHoras() : 4;

        ticketRepository.upsertSlaConfig(propId, prioridad, horas, alerta);
        log.info("[TicketService] SLA actualizado para propiedad {} prioridad {}: {} horas (Alerta: {}h)",
                propId, prioridad, horas, alerta);
    }

    private void notificarCambioEstado(Long idTicket, String estado, String respuesta) {
        try {
            String sql = """
                SELECT p.EMAIL, q.NUMERO_RADICADO
                FROM PQRS_TICKETS q
                JOIN PERSONAS p ON q.ID_PERSONA_RADICA = p.ID_PERSONA
                WHERE q.ID_TICKET = ? AND p.EMAIL IS NOT NULL
            """;
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, idTicket);
            if (!rows.isEmpty()) {
                String email = (String) rows.get(0).get("EMAIL");
                String radicado = (String) rows.get(0).get("NUMERO_RADICADO");
                if (email != null && !email.isBlank()) {
                    emailService.enviarNotificacionPQRS(email, radicado, estado, respuesta);
                }
            }
        } catch (Exception e) {
            log.warn("[TicketService] No se pudo enviar notificación por correo para ticket {}: {}", idTicket, e.getMessage());
        }
    }
}
