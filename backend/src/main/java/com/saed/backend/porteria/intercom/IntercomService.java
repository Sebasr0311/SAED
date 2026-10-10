package com.saed.backend.porteria.intercom;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.porteria.dto.QrAccesoDTO;
import com.saed.backend.porteria.dto.VisitaDTO;
import com.saed.backend.porteria.repository.PorteriaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * F07-02 intercom (citofonia): the gate opens a persisted request for a visit, the residents of the unit
 * approve or deny it exactly once, and the gate reads the persisted state. Requests expire after
 * {@link #TTL_MINUTES}; decisions on expired, cancelled or already-answered requests are conflicts.
 */
@Service
@Transactional
public class IntercomService {

    public static final int TTL_MINUTES = 5;
    private static final Set<String> OPEN_VISIT_STATES = Set.of("PROGRAMADA", "EN_CURSO");

    private final IntercomRepository repository;
    private final PorteriaRepository porteriaRepository;

    public IntercomService(IntercomRepository repository, PorteriaRepository porteriaRepository) {
        this.repository = repository;
        this.porteriaRepository = porteriaRepository;
    }

    private static SaedContext requireContext() {
        SaedContext ctx = SaedContextHolder.getContext();
        if (ctx == null || ctx.getUserId() == null) {
            throw new AccessDeniedException("Missing tenant context");
        }
        if ("SUPERADMIN".equals(ctx.getRoleCode()) || "GLOBAL".equals(ctx.getRoleScope())) {
            throw new AccessDeniedException("Platform administrators do not access porteria operational data");
        }
        return ctx;
    }

    private static Long requireStaffProperty(SaedContext ctx) {
        if (isResident(ctx) || ctx.getPropertyId() == null) {
            throw new AccessDeniedException("Gate staff of a property required");
        }
        return ctx.getPropertyId();
    }

    private static boolean isResident(SaedContext ctx) {
        return "RESIDENTE".equals(ctx.getRoleCode()) || "RESIDENTE_CONVIVENCIA".equals(ctx.getRoleCode())
            || "UNIDAD".equals(ctx.getRoleScope());
    }

    private List<Long> callerUnits(SaedContext ctx) {
        if (!isResident(ctx)) {
            throw new AccessDeniedException("Only residents answer intercom requests");
        }
        return ctx.getUnitId() != null ? List.of(ctx.getUnitId())
            : porteriaRepository.findActiveUnitIdsForUser(ctx.getUserId());
    }

    /** Opens (or reuses) the pending request for the visit of a QR scanned at the caller's gate. */
    public Map<String, Object> requestFromQr(String token) {
        SaedContext ctx = requireContext();
        Long propId = requireStaffProperty(ctx);
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token QR requerido para notificar");
        }
        QrAccesoDTO qr = porteriaRepository.getQrAccesoByToken(token)
            .orElseThrow(() -> new NoSuchElementException("Código QR no encontrado"));
        if (qr.visitaId() == null || !propId.equals(porteriaRepository.findVisitaPropertyId(qr.visitaId()).orElse(null))) {
            throw new NoSuchElementException("Código QR no encontrado");
        }
        VisitaDTO visita = porteriaRepository.getVisitaById(qr.visitaId())
            .orElseThrow(() -> new NoSuchElementException("Código QR no encontrado"));
        if (!OPEN_VISIT_STATES.contains(visita.estado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La visita fue cancelada o finalizada");
        }
        IntercomRequest open = repository.findOpenByVisita(visita.idVisita()).orElse(null);
        if (open == null) {
            String nombre = porteriaRepository.getVisitaDetalle(visita.idVisita())
                .map(d -> d.nombreVisitante() != null ? d.nombreVisitante().trim() : null).orElse(null);
            long id = repository.create(propId, visita.unidadId(), visita.idVisita(), ctx.getUserId(), nombre, TTL_MINUTES);
            open = repository.findById(id).orElseThrow(() -> new IllegalStateException("Solicitud no registrada"));
        }
        return toGateView(open);
    }

    /** Pending, non-expired requests of the caller's unit(s), in the shape the resident dashboard polls. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> pendingForCaller() {
        SaedContext ctx = requireContext();
        List<Long> units = callerUnits(ctx);
        if (units.isEmpty()) {
            return List.of();
        }
        return repository.findPendingByUnits(units).stream().map(r -> {
            Map<String, Object> m = new HashMap<>();
            m.put("idMensaje", r.idSolicitud());
            m.put("idSolicitud", r.idSolicitud());
            m.put("idVisita", r.idVisita());
            m.put("nombreVisitante", r.nombreVisitante());
            m.put("titulo", "Visitante en portería");
            m.put("mensaje", "El visitante " + (r.nombreVisitante() != null ? r.nombreVisitante() : "")
                + " solicita ingresar. ¿Autoriza el acceso?");
            m.put("fechaExpiracion", r.fechaExpiracion());
            return m;
        }).toList();
    }

    /** A resident of the unit approves or denies once; replays and expired requests are 409. */
    public void decide(Long id, boolean approve) {
        SaedContext ctx = requireContext();
        List<Long> units = callerUnits(ctx);
        IntercomRequest req = repository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Solicitud no encontrada"));
        if (!units.contains(req.idUnidad())) {
            throw new NoSuchElementException("Solicitud no encontrada");
        }
        if (repository.decide(id, approve ? "APROBADA" : "RECHAZADA", ctx.getUserId()) == 0) {
            repository.expireIfDue(id);
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "La solicitud ya fue respondida, cancelada o expiró");
        }
    }

    /** The gate reads the persisted state of the latest request of a visit of its property. */
    public Map<String, Object> resultForVisit(Long visitaId) {
        SaedContext ctx = requireContext();
        Long propId = requireStaffProperty(ctx);
        IntercomRequest req = repository.findLatestByVisita(visitaId).orElse(null);
        if (req == null || !propId.equals(req.idPropiedad())) {
            throw new NoSuchElementException("Solicitud no encontrada");
        }
        if ("PENDIENTE".equals(req.estado()) && repository.expireIfDue(req.idSolicitud()) > 0) {
            req = repository.findById(req.idSolicitud()).orElse(req);
        }
        return toGateView(req);
    }

    public void cancel(Long id) {
        SaedContext ctx = requireContext();
        Long propId = requireStaffProperty(ctx);
        IntercomRequest req = repository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Solicitud no encontrada"));
        if (!propId.equals(req.idPropiedad())) {
            throw new NoSuchElementException("Solicitud no encontrada");
        }
        if (repository.cancel(id) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La solicitud ya no está pendiente");
        }
    }

    private static Map<String, Object> toGateView(IntercomRequest r) {
        Map<String, Object> m = new HashMap<>();
        m.put("idSolicitud", r.idSolicitud());
        m.put("idMensaje", r.idSolicitud());
        m.put("idVisita", r.idVisita());
        m.put("estado", r.estado());
        // 1 only for a persisted approval, 0 only for a persisted denial; null while undecided.
        m.put("confirmado", "APROBADA".equals(r.estado()) ? Integer.valueOf(1)
            : ("RECHAZADA".equals(r.estado()) ? Integer.valueOf(0) : null));
        m.put("fechaExpiracion", r.fechaExpiracion());
        m.put("fechaRespuesta", r.fechaRespuesta());
        return m;
    }
}
