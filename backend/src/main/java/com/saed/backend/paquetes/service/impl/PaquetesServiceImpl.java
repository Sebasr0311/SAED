package com.saed.backend.paquetes.service.impl;

import com.saed.backend.audit.AuditService;
import com.saed.backend.common.service.FileStorageService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.paquetes.dto.PaqueteDTO;
import com.saed.backend.paquetes.dto.PaqueteEntregaDTO;
import com.saed.backend.paquetes.dto.PaqueteRequestDTO;
import com.saed.backend.paquetes.repository.PaquetesRepository;
import com.saed.backend.paquetes.service.PaquetesService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class PaquetesServiceImpl implements PaquetesService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PaquetesServiceImpl.class);
    private final PaquetesRepository paquetesRepository;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final FileStorageService fileStorageService;
    private final AuditService auditService;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_INTENTOS_PIN = 3;

    public PaquetesServiceImpl(PaquetesRepository paquetesRepository,
                               NamedParameterJdbcTemplate jdbcTemplate,
                               FileStorageService fileStorageService,
                               AuditService auditService) {
        this.paquetesRepository = paquetesRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.fileStorageService = fileStorageService;
        this.auditService = auditService;
    }

    private String generatePin() {
        return String.format("%04d", RANDOM.nextInt(10000));
    }

    @Override
    @Transactional
    public PaqueteDTO registrarPaquete(PaqueteRequestDTO request) {
        SaedContext ctx = requireStaffContext();
        Long propId = ctx.getPropertyId();
        Long userId = ctx.getUserId();
        Long orgId = ctx.getOrganizationId();

        // Anti-IDOR / Tenant isolation: La unidad debe pertenecer a la propiedad activa
        requireUnitInProperty(request.idUnidad(), propId);

        // Generar PIN criptográficamente seguro de 4 dígitos exclusivamente en backend
        String pin = generatePin();

        PaqueteDTO paquete = paquetesRepository.registrarPaquete(request, propId, pin, userId);

        notifyPickup(paquete, pin, request.empresaMensajeria());

        // Auditoría segura (sin exponer el PIN)
        auditService.recordSuccess(userId, orgId, propId, "INSERT", "PAQUETE", paquete.idPaquete(),
                null, null, null, "{\"evento\":\"PACKAGE_RECEIVED\",\"unidad\":" + request.idUnidad() + "}");

        return forCaller(paquete);
    }

    /** Staff re-sends the pickup notification (F08-02 recovery); the PIN goes only to unit residents. */
    @Override
    @Transactional
    public int reenviarNotificacion(Long id) {
        SaedContext ctx = requireStaffContext();
        PaqueteDTO pq = paquetesRepository.getPaqueteById(id)
                .orElseThrow(() -> new NoSuchElementException("Paquete no encontrado"));
        if (!ctx.getPropertyId().equals(pq.idPropiedad())) {
            throw new AccessDeniedException("El paquete no pertenece a la propiedad activa");
        }
        if (!"RECIBIDO".equalsIgnoreCase(pq.estado()) && !"PENDIENTE_ENTREGA".equalsIgnoreCase(pq.estado())) {
            throw new IllegalStateException("El paquete ya no está pendiente de entrega");
        }
        return notifyPickup(pq, pq.codigoRetiroPin(), pq.empresaMensajeria());
    }

    private int notifyPickup(PaqueteDTO paquete, String pin, String empresa) {
        String descNotif = (paquete.descripcion() != null && !paquete.descripcion().isBlank())
                ? paquete.descripcion() : "encomienda";
        String aptoTxt = (paquete.numeroApartamento() != null && !paquete.numeroApartamento().isBlank())
                ? (" para el apartamento " + paquete.numeroApartamento()) : "";
        String msg = "Se ha recibido un paquete (" + descNotif + ") de " +
                (empresa != null ? empresa : "mensajería") +
                aptoTxt + ". Código de retiro PIN: " + pin + ". Presenta este código en portería para retirar la encomienda.";
        return notifyUnitResidents(paquete, "📦 Paquete recibido en portería", msg, "/paquetes/" + paquete.idPaquete());
    }

    /**
     * Inserts one in-app notification per active resident of the package unit. A failure is logged with
     * the package id and failure type only (never the message text, which carries the PIN) and the
     * notification can be re-sent with {@link #reenviarNotificacion}; residents also see the PIN in their
     * package list.
     */
    private int notifyUnitResidents(PaqueteDTO pq, String titulo, String msg, String enlace) {
        try {
            Long idComunicado = jdbcTemplate.queryForObject(
                    "SELECT MIN(ID_COMUNICADO) FROM COMUNICADOS WHERE ID_PROPIEDAD = :propId",
                    new MapSqlParameterSource("propId", pq.idPropiedad()),
                    Long.class
            );
            List<Long> residentUserIds = jdbcTemplate.query(
                    "SELECT DISTINCT u.ID_USUARIO FROM USUARIOS u " +
                    "WHERE NVL(u.ESTADO, 'ACTIVO') = 'ACTIVO' AND (" +
                    "  u.ID_USUARIO IN (" +
                    "    SELECT ua.ID_USUARIO FROM USUARIO_ASIGNACIONES ua JOIN ROLES r ON ua.ID_ROL = r.ID_ROL " +
                    "    WHERE ua.ID_UNIDAD = :unitId AND ua.ESTADO = 'ACTIVA' " +
                    "    AND r.CODIGO IN ('RESIDENTE', 'RESIDENTE_CONVIVENCIA', 'PROPIETARIO_UNIDAD', 'ARRENDATARIO')" +
                    "  ) OR u.ID_PERSONA IN (" +
                    "    SELECT ru.ID_PERSONA FROM RESIDENTES_UNIDAD ru " +
                    "    WHERE ru.ID_UNIDAD = :unitId AND NVL(ru.ESTADO, 'ACTIVO') = 'ACTIVO'" +
                    "  ))",
                    new MapSqlParameterSource("unitId", pq.idUnidad()),
                    (rs, rowNum) -> rs.getLong("ID_USUARIO")
            );
            int sent = 0;
            for (Long resUserId : residentUserIds) {
                sent += jdbcTemplate.update(
                        "INSERT INTO NOTIFICACIONES (ID_COMUNICADO, ID_USUARIO_DESTINATARIO, CANAL, TITULO, MENSAJE, "
                                + "ENLACE_DESTINO, ESTADO_ENVIO) VALUES (:comunicadoId, :dest, 'IN_APP', :titulo, :msg, :enlace, 'ENVIADO')",
                        new MapSqlParameterSource()
                                .addValue("comunicadoId", idComunicado)
                                .addValue("dest", resUserId)
                                .addValue("titulo", titulo)
                                .addValue("msg", msg)
                                .addValue("enlace", enlace));
            }
            return sent;
        } catch (DataAccessException e) {
            log.warn("Package {} notification not stored ({}); it can be re-sent", pq.idPaquete(), e.getClass().getSimpleName());
            return 0;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaqueteDTO> getPaquetes() {
        SaedContext ctx = requireOperationalContext();
        if (isUnitScopedResident(ctx)) {
            Long unitId = ctx.getUnitId();
            if (unitId == null) {
                return List.of();
            }
            return forCaller(paquetesRepository.getPaquetesByUnidad(unitId));
        }
        Long propId = requireProperty(ctx);
        return forCaller(paquetesRepository.getPaquetesList().stream()
                .filter(p -> propId.equals(p.idPropiedad())).toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaqueteDTO> getPaquetesByUnidad(Long idUnidad) {
        SaedContext ctx = requireOperationalContext();
        if (isUnitScopedResident(ctx)) {
            if (ctx.getUnitId() == null || !ctx.getUnitId().equals(idUnidad)) {
                throw new AccessDeniedException("Acceso denegado a paquetes de otra unidad");
            }
        } else {
            requireUnitInProperty(idUnidad, requireProperty(ctx));
        }
        return forCaller(paquetesRepository.getPaquetesByUnidad(idUnidad));
    }

    @Override
    @Transactional(readOnly = true)
    public PaqueteDTO getPaqueteById(Long id) {
        SaedContext ctx = requireOperationalContext();
        PaqueteDTO pq = paquetesRepository.getPaqueteById(id)
                .orElseThrow(() -> new NoSuchElementException("Paquete no encontrado"));

        if (isUnitScopedResident(ctx)) {
            Long unitId = ctx.getUnitId();
            if (unitId == null || !unitId.equals(pq.idUnidad())) {
                throw new AccessDeniedException("Acceso denegado a paquetes de otra unidad");
            }
        } else if (!requireProperty(ctx).equals(pq.idPropiedad())) {
            throw new AccessDeniedException("Acceso denegado a paquetes de otra propiedad");
        }

        return forCaller(pq);
    }

    @Override
    @Transactional
    public PaqueteDTO actualizarPaquete(Long id, PaqueteRequestDTO request) {
        SaedContext ctx = requireStaffContext();
        PaqueteDTO existing = paquetesRepository.getPaqueteById(id)
                .orElseThrow(() -> new NoSuchElementException("Paquete no encontrado"));

        Long propId = ctx.getPropertyId();
        if (!propId.equals(existing.idPropiedad())) {
            throw new AccessDeniedException("El paquete no pertenece a la propiedad activa");
        }

        if ("ENTREGADO".equalsIgnoreCase(existing.estado())) {
            throw new IllegalStateException("No se puede modificar un paquete ya entregado");
        }

        if (request.idUnidad() != null) {
            Integer unitCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM UNIDADES WHERE ID_UNIDAD = :unitId AND ID_PROPIEDAD = :propId",
                    new MapSqlParameterSource("unitId", request.idUnidad()).addValue("propId", existing.idPropiedad()),
                    Integer.class
            );
            if (unitCount == null || unitCount == 0) {
                throw new AccessDeniedException("La unidad especificada no pertenece a la propiedad del paquete");
            }
        }

        return forCaller(paquetesRepository.actualizarPaquete(id, request));
    }

    @Override
    @Transactional
    public PaqueteDTO registrarEntrega(Long id, PaqueteEntregaDTO request) {
        SaedContext ctx = requireStaffContext();
        Long userId = ctx.getUserId();
        Long propId = ctx.getPropertyId();
        Long orgId = ctx.getOrganizationId();

        PaqueteDTO pq = paquetesRepository.getPaqueteById(id)
                .orElseThrow(() -> new NoSuchElementException("Paquete no encontrado"));

        // Aislamiento por propiedad
        if (!propId.equals(pq.idPropiedad())) {
            throw new AccessDeniedException("El paquete no pertenece a la propiedad activa del portero");
        }

        // Validación de estado: no permitir re-entrega
        if ("ENTREGADO".equalsIgnoreCase(pq.estado())) {
            throw new IllegalStateException("El paquete ya fue entregado previamente");
        }
        if (!"RECIBIDO".equalsIgnoreCase(pq.estado()) && !"PENDIENTE_ENTREGA".equalsIgnoreCase(pq.estado())) {
            throw new IllegalStateException("El paquete no se encuentra en estado pendiente de entrega");
        }

        // F08-02: every attempt is counted atomically (own transaction) before the PIN is compared, so
        // concurrent requests can never make more than MAX_INTENTOS_PIN comparisons.
        if (!paquetesRepository.reservarIntentoPin(id, MAX_INTENTOS_PIN)) {
            auditService.recordFailure(userId, orgId, propId, "ACCESO_DENEGADO", "PAQUETE", id,
                    null, null, null, "{\"evento\":\"PACKAGE_LOCKED\",\"motivo\":\"MAX_INTENTOS_EXCEDIDO\"}");
            throw new SecurityException("El paquete se encuentra bloqueado por superar el límite de intentos de PIN (3). " +
                    "Contacte a la administración.");
        }

        if (request.codigoRetiroPin() == null || !pq.codigoRetiroPin().equalsIgnoreCase(request.codigoRetiroPin().trim())) {
            auditService.recordFailure(userId, orgId, propId, "ACCESO_DENEGADO", "PAQUETE", id,
                    null, null, null, "{\"evento\":\"PACKAGE_INVALID_PIN\"}");
            throw new IllegalArgumentException("PIN de retiro incorrecto");
        }

        // Transición atómica en base de datos: solo una entrega concurrente puede ganar (resetea intentos)
        boolean updated = paquetesRepository.registrarEntrega(id, request, userId);
        if (!updated) {
            throw new IllegalStateException("El paquete ya fue entregado por otra solicitud concurrente");
        }

        String descNotif = (pq.descripcion() != null && !pq.descripcion().isBlank()) ? pq.descripcion() : "encomienda";
        notifyUnitResidents(pq, "📦 Paquete entregado",
                "El paquete (" + descNotif + ") ha sido retirado y entregado exitosamente en portería.", null);

        auditService.recordSuccess(userId, orgId, propId, "UPDATE", "PAQUETE", id,
                null, null, null, "{\"evento\":\"PACKAGE_DELIVERED\",\"unidad\":" + pq.idUnidad() + "}");

        return getPaqueteById(id);
    }

    /** Operational context; SUPERADMIN never reads package data (DEC-02). */
    private static SaedContext requireOperationalContext() {
        SaedContext ctx = SaedContextHolder.getContext();
        if (ctx == null || ctx.getUserId() == null) {
            throw new AccessDeniedException("Missing tenant context");
        }
        if ("SUPERADMIN".equalsIgnoreCase(ctx.getRoleCode()) || "GLOBAL".equalsIgnoreCase(ctx.getRoleScope())) {
            throw new AccessDeniedException("Platform administrators do not access package data");
        }
        return ctx;
    }

    private static Long requireProperty(SaedContext ctx) {
        if (ctx.getPropertyId() == null) {
            throw new AccessDeniedException("Missing property context");
        }
        return ctx.getPropertyId();
    }

    private static SaedContext requireStaffContext() {
        SaedContext ctx = requireOperationalContext();
        if (isUnitScopedResident(ctx)) {
            throw new AccessDeniedException("Only gate staff manage package custody");
        }
        requireProperty(ctx);
        return ctx;
    }

    private void requireUnitInProperty(Long unitId, Long propId) {
        Integer unitCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM UNIDADES WHERE ID_UNIDAD = :unitId AND ID_PROPIEDAD = :propId",
                new MapSqlParameterSource("unitId", unitId).addValue("propId", propId),
                Integer.class
        );
        if (unitCount == null || unitCount == 0) {
            throw new AccessDeniedException("La unidad especificada no pertenece a la propiedad activa");
        }
    }

    private static boolean isUnitScopedResident(SaedContext ctx) {
        return ctx != null && ("RESIDENTE".equalsIgnoreCase(ctx.getRoleCode())
                || "RESIDENTE_CONVIVENCIA".equalsIgnoreCase(ctx.getRoleCode())
                || "UNIDAD".equalsIgnoreCase(ctx.getRoleScope()));
    }

    /**
     * The pickup PIN is only returned to a resident of the recipient unit. Staff (portero,
     * admin) and any other caller get a redacted copy; the portero receives the PIN from the
     * resident at pickup and the server verifies it in {@link #registrarEntrega}.
     */
    private PaqueteDTO forCaller(PaqueteDTO pq) {
        if (pq == null) {
            return null;
        }
        SaedContext ctx = SaedContextHolder.getContext();
        boolean recipient = isUnitScopedResident(ctx)
                && ctx.getUnitId() != null
                && ctx.getUnitId().equals(pq.idUnidad());
        return recipient ? pq : pq.withoutPin();
    }

    private List<PaqueteDTO> forCaller(List<PaqueteDTO> paquetes) {
        return paquetes.stream().map(this::forCaller).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Resource getImagenPaquete(Long id) {
        PaqueteDTO pq = getPaqueteById(id); // Enforza aislamiento por unidad y propiedad
        String fotoUrl = pq.fotoPaqueteUrl();
        if (fotoUrl == null || fotoUrl.isBlank()) {
            throw new NoSuchElementException("El paquete no tiene imagen adjunta");
        }

        if (fotoUrl.startsWith("data:image/")) {
            int commaIdx = fotoUrl.indexOf(',');
            String base64Data = commaIdx >= 0 ? fotoUrl.substring(commaIdx + 1) : fotoUrl;
            byte[] decoded = Base64.getDecoder().decode(base64Data.trim());
            return new ByteArrayResource(decoded);
        }

        return fileStorageService.loadAsResource(fotoUrl);
    }

    @Override
    @Transactional(readOnly = true)
    public String getImagenMimeType(Long id) {
        PaqueteDTO pq = getPaqueteById(id);
        String fotoUrl = pq.fotoPaqueteUrl();
        if (fotoUrl != null && fotoUrl.startsWith("data:image/")) {
            int semicolonIdx = fotoUrl.indexOf(';');
            if (semicolonIdx > 5) {
                return fotoUrl.substring(5, semicolonIdx);
            }
            return "image/jpeg";
        }
        if (fotoUrl != null && fotoUrl.toLowerCase().endsWith(".png")) {
            return "image/png";
        }
        return "image/jpeg";
    }

    @Override
    @Transactional
    public void marcarEntregadoDirecto(Long id) {
        throw new UnsupportedOperationException(
                "La entrega directa sin PIN está deshabilitada por seguridad. Debe utilizar POST /api/v1/paquetes/{id}/entrega con PIN.");
    }
}
