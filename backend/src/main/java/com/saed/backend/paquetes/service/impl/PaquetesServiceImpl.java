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
        SaedContext ctx = SaedContextHolder.getContext();
        Long propId = ctx != null && ctx.getPropertyId() != null ? ctx.getPropertyId() : 1L;
        Long userId = ctx != null && ctx.getUserId() != null ? ctx.getUserId() : 1L;
        Long orgId = ctx != null ? ctx.getOrganizationId() : null;

        // Anti-IDOR / Tenant isolation: La unidad debe pertenecer a la propiedad activa
        Integer unitCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM UNIDADES WHERE ID_UNIDAD = :unitId AND ID_PROPIEDAD = :propId",
                new MapSqlParameterSource("unitId", request.idUnidad()).addValue("propId", propId),
                Integer.class
        );
        if (unitCount == null || unitCount == 0) {
            throw new AccessDeniedException("La unidad especificada no pertenece a la propiedad activa");
        }

        // Generar PIN criptográficamente seguro de 4 dígitos exclusivamente en backend
        String pin = generatePin();

        PaqueteDTO paquete = paquetesRepository.registrarPaquete(request, propId, pin, userId);

        // Notificar a los residentes activos de la unidad en NOTIFICACIONES
        try {
            Long idComunicado = null;
            try {
                idComunicado = jdbcTemplate.queryForObject(
                        "SELECT MIN(ID_COMUNICADO) FROM COMUNICADOS WHERE ID_PROPIEDAD = :propId",
                        new MapSqlParameterSource("propId", propId),
                        Long.class
                );
            } catch (Exception ignored) {
            }
            if (idComunicado == null) {
                try {
                    idComunicado = jdbcTemplate.queryForObject(
                            "SELECT MIN(ID_COMUNICADO) FROM COMUNICADOS",
                            new MapSqlParameterSource(),
                            Long.class
                    );
                } catch (Exception ignored) {
                }
            }

            String findResidentsSql = "SELECT DISTINCT u.ID_USUARIO FROM USUARIOS u " +
                    "WHERE NVL(u.ESTADO, 'ACTIVO') = 'ACTIVO' AND (" +
                    "  u.ID_USUARIO IN (" +
                    "    SELECT ua.ID_USUARIO FROM USUARIO_ASIGNACIONES ua JOIN ROLES r ON ua.ID_ROL = r.ID_ROL " +
                    "    WHERE ua.ID_UNIDAD = :unitId AND ua.ESTADO = 'ACTIVA' " +
                    "    AND r.CODIGO IN ('RESIDENTE', 'RESIDENTE_CONVIVENCIA', 'PROPIETARIO_UNIDAD', 'ARRENDATARIO')" +
                    "  ) OR u.ID_PERSONA IN (" +
                    "    SELECT ru.ID_PERSONA FROM RESIDENTES_UNIDAD ru " +
                    "    WHERE ru.ID_UNIDAD = :unitId AND NVL(ru.ESTADO, 'ACTIVO') = 'ACTIVO'" +
                    "  )" +
                    (request.idPersonaDestinatario() != null ? " OR u.ID_PERSONA = :destPersonaId" : "") +
                    ")";

            MapSqlParameterSource resParams = new MapSqlParameterSource("unitId", request.idUnidad());
            if (request.idPersonaDestinatario() != null) {
                resParams.addValue("destPersonaId", request.idPersonaDestinatario());
            }

            List<Long> residentUserIds = jdbcTemplate.query(
                    findResidentsSql,
                    resParams,
                    (rs, rowNum) -> rs.getLong("ID_USUARIO")
            );

            // Obtener identificador del apartamento / unidad
            String identificadorApto = "";
            try {
                identificadorApto = jdbcTemplate.queryForObject(
                        "SELECT IDENTIFICADOR FROM UNIDADES WHERE ID_UNIDAD = :unitId",
                        new MapSqlParameterSource("unitId", request.idUnidad()),
                        String.class
                );
            } catch (Exception ignored) {
            }
            String aptoTxt = (identificadorApto != null && !identificadorApto.isBlank())
                    ? (" para el apartamento " + identificadorApto) : "";

            String enlace = "/paquetes/" + paquete.idPaquete();
            for (Long resUserId : residentUserIds) {
                String notifSql = "INSERT INTO NOTIFICACIONES (ID_COMUNICADO, ID_USUARIO_DESTINATARIO, " +
                        "CANAL, TITULO, MENSAJE, ENLACE_DESTINO, ESTADO_ENVIO) " +
                        "VALUES (:comunicadoId, :dest, 'IN_APP', '📦 Paquete recibido en portería', :msg, :enlace, 'ENVIADO')";
                String descNotif = (request.descripcion() != null && !request.descripcion().isBlank())
                        ? request.descripcion() : "encomienda";
                String msg = "Se ha recibido un paquete (" + descNotif + ") de " +
                        (request.empresaMensajeria() != null ? request.empresaMensajeria() : "mensajería") +
                        aptoTxt + ". Código de retiro PIN: " + pin + ". Presenta este código en portería para retirar la encomienda.";
                jdbcTemplate.update(notifSql, new MapSqlParameterSource()
                        .addValue("comunicadoId", idComunicado)
                        .addValue("dest", resUserId)
                        .addValue("msg", msg)
                        .addValue("enlace", enlace));
            }
        } catch (Exception e) {
            log.warn("Aviso al registrar notificaciones de paquete: {}", e.getMessage());
        }

        // Auditoría segura (sin exponer el PIN)
        try {
            auditService.recordSuccess(userId, orgId, propId, "INSERT", "PAQUETE", paquete.idPaquete(),
                    null, null, null, "{\"evento\":\"PACKAGE_RECEIVED\",\"unidad\":" + request.idUnidad() + "}");
        } catch (Exception ignored) {
        }

        return paquete;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaqueteDTO> getPaquetes() {
        SaedContext ctx = SaedContextHolder.getContext();
        if (ctx != null && ("RESIDENTE".equalsIgnoreCase(ctx.getRoleCode())
                || "RESIDENTE_CONVIVENCIA".equalsIgnoreCase(ctx.getRoleCode())
                || "UNIDAD".equalsIgnoreCase(ctx.getRoleScope()))) {
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
        if (ctx != null && ("RESIDENTE".equalsIgnoreCase(ctx.getRoleCode())
                || "RESIDENTE_CONVIVENCIA".equalsIgnoreCase(ctx.getRoleCode())
                || "UNIDAD".equalsIgnoreCase(ctx.getRoleScope()))) {
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
        PaqueteDTO pq = paquetesRepository.getPaqueteById(id)
                .orElseThrow(() -> new NoSuchElementException("Paquete no encontrado"));

        if (ctx != null && ("RESIDENTE".equalsIgnoreCase(ctx.getRoleCode())
                || "RESIDENTE_CONVIVENCIA".equalsIgnoreCase(ctx.getRoleCode())
                || "UNIDAD".equalsIgnoreCase(ctx.getRoleScope()))) {
            Long unitId = ctx.getUnitId();
            if (unitId == null || !unitId.equals(pq.idUnidad())) {
                throw new AccessDeniedException("Acceso denegado a paquetes de otra unidad");
            }
        } else if (ctx != null && ("PORTERO".equalsIgnoreCase(ctx.getRoleCode())
                || "ADMIN_PROPIEDAD".equalsIgnoreCase(ctx.getRoleCode()))) {
            Long propId = ctx.getPropertyId();
            if (propId != null && !propId.equals(pq.idPropiedad())) {
                throw new AccessDeniedException("Acceso denegado a paquetes de otra propiedad");
            }
        }

        return pq;
    }

    @Override
    @Transactional
    public PaqueteDTO actualizarPaquete(Long id, PaqueteRequestDTO request) {
        SaedContext ctx = SaedContextHolder.getContext();
        PaqueteDTO existing = paquetesRepository.getPaqueteById(id)
                .orElseThrow(() -> new NoSuchElementException("Paquete no encontrado"));

        Long propId = ctx != null ? ctx.getPropertyId() : null;
        if (propId != null && !propId.equals(existing.idPropiedad())) {
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

        return paquetesRepository.actualizarPaquete(id, request);
    }

    @Override
    @Transactional
    public PaqueteDTO registrarEntrega(Long id, PaqueteEntregaDTO request) {
        SaedContext ctx = SaedContextHolder.getContext();
        Long userId = ctx != null && ctx.getUserId() != null ? ctx.getUserId() : 1L;
        Long propId = ctx != null ? ctx.getPropertyId() : null;
        Long orgId = ctx != null ? ctx.getOrganizationId() : null;

        // Auditoría de intento de entrega
        try {
            auditService.recordSuccess(userId, orgId, propId, "UPDATE", "PAQUETE", id,
                    null, null, null, "{\"evento\":\"PACKAGE_DELIVERY_ATTEMPT\"}");
        } catch (Exception ignored) {
        }

        PaqueteDTO pq = paquetesRepository.getPaqueteById(id)
                .orElseThrow(() -> new NoSuchElementException("Paquete no encontrado"));

        // Aislamiento por propiedad
        if (propId != null && !propId.equals(pq.idPropiedad())) {
            throw new AccessDeniedException("El paquete no pertenece a la propiedad activa del portero");
        }

        // Validación de estado: no permitir re-entrega
        if ("ENTREGADO".equalsIgnoreCase(pq.estado())) {
            throw new IllegalStateException("El paquete ya fue entregado previamente");
        }
        if (!"RECIBIDO".equalsIgnoreCase(pq.estado()) && !"PENDIENTE_ENTREGA".equalsIgnoreCase(pq.estado())) {
            throw new IllegalStateException("El paquete no se encuentra en estado pendiente de entrega");
        }

        // Protección anti-fuerza bruta: verificar contador de intentos fallidos
        Integer intentos = jdbcTemplate.queryForObject(
                "SELECT NVL(INTENTOS_FALLIDOS_PIN, 0) FROM PAQUETES WHERE ID_PAQUETE = :id",
                new MapSqlParameterSource("id", id),
                Integer.class
        );
        if (intentos != null && intentos >= MAX_INTENTOS_PIN) {
            try {
                auditService.recordFailure(userId, orgId, propId, "ACCESO_DENEGADO", "PAQUETE", id,
                        null, null, null, "{\"evento\":\"PACKAGE_LOCKED\",\"motivo\":\"MAX_INTENTOS_EXCEDIDO\"}");
            } catch (Exception ignored) {
            }
            throw new SecurityException("El paquete se encuentra bloqueado por superar el límite de intentos de PIN (3). " +
                    "Contacte a la administración.");
        }

        // Validación estricta del PIN de 4 dígitos
        if (request.codigoRetiroPin() == null || !pq.codigoRetiroPin().equalsIgnoreCase(request.codigoRetiroPin().trim())) {
            paquetesRepository.incrementarIntentosFallidos(id);
            int nuevosIntentos = (intentos != null ? intentos : 0) + 1;
            int restantes = Math.max(0, MAX_INTENTOS_PIN - nuevosIntentos);
            try {
                auditService.recordFailure(userId, orgId, propId, "ACCESO_DENEGADO", "PAQUETE", id,
                        null, null, null, "{\"evento\":\"PACKAGE_INVALID_PIN\",\"intentosRestantes\":" + restantes + "}");
            } catch (Exception ignored) {
            }
            throw new IllegalArgumentException("PIN de retiro incorrecto. Intentos restantes: " + restantes);
        }

        // Transición atómica en base de datos: solo una entrega concurrente puede ganar
        boolean updated = paquetesRepository.registrarEntrega(id, request, userId);
        if (!updated) {
            throw new IllegalStateException("El paquete ya fue entregado por otra solicitud concurrente");
        }

        // Notificación de entrega al residente (sin exponer el PIN)
        try {
            Long idComunicado = null;
            try {
                idComunicado = jdbcTemplate.queryForObject(
                        "SELECT MIN(ID_COMUNICADO) FROM COMUNICADOS WHERE ID_PROPIEDAD = :propId",
                        new MapSqlParameterSource("propId", pq.idPropiedad()),
                        Long.class
                );
            } catch (Exception ignored) {
            }

            String findResidentsSql = "SELECT DISTINCT u.ID_USUARIO FROM USUARIOS u " +
                    "WHERE NVL(u.ESTADO, 'ACTIVO') = 'ACTIVO' AND (" +
                    "  u.ID_USUARIO IN (" +
                    "    SELECT ua.ID_USUARIO FROM USUARIO_ASIGNACIONES ua JOIN ROLES r ON ua.ID_ROL = r.ID_ROL " +
                    "    WHERE ua.ID_UNIDAD = :unitId AND ua.ESTADO = 'ACTIVA' " +
                    "    AND r.CODIGO IN ('RESIDENTE', 'RESIDENTE_CONVIVENCIA', 'PROPIETARIO_UNIDAD', 'ARRENDATARIO')" +
                    "  ) OR u.ID_PERSONA IN (" +
                    "    SELECT ru.ID_PERSONA FROM RESIDENTES_UNIDAD ru " +
                    "    WHERE ru.ID_UNIDAD = :unitId AND NVL(ru.ESTADO, 'ACTIVO') = 'ACTIVO'" +
                    "  )" +
                    (pq.idPersonaDestinatario() != null ? " OR u.ID_PERSONA = :destPersonaId" : "") +
                    ")";

            MapSqlParameterSource resParams = new MapSqlParameterSource("unitId", pq.idUnidad());
            if (pq.idPersonaDestinatario() != null) {
                resParams.addValue("destPersonaId", pq.idPersonaDestinatario());
            }

            List<Long> residentUserIds = jdbcTemplate.query(
                    findResidentsSql,
                    resParams,
                    (rs, rowNum) -> rs.getLong("ID_USUARIO")
            );

            for (Long resUserId : residentUserIds) {
                String notifSql = "INSERT INTO NOTIFICACIONES (ID_COMUNICADO, ID_USUARIO_DESTINATARIO, " +
                        "CANAL, TITULO, MENSAJE, ESTADO_ENVIO) " +
                        "VALUES (:comunicadoId, :dest, 'IN_APP', '📦 Paquete entregado', :msg, 'ENVIADO')";
                String descNotif = (pq.descripcion() != null && !pq.descripcion().isBlank()) ? pq.descripcion() : "encomienda";
                String msg = "El paquete (" + descNotif + ") ha sido retirado y entregado exitosamente en portería.";
                jdbcTemplate.update(notifSql, new MapSqlParameterSource()
                        .addValue("comunicadoId", idComunicado)
                        .addValue("dest", resUserId)
                        .addValue("msg", msg));
            }
        } catch (Exception e) {
            log.warn("Aviso al registrar notificación de entrega de paquete: {}", e.getMessage());
        }

        // Auditoría de entrega exitosa
        try {
            auditService.recordSuccess(userId, orgId, propId, "UPDATE", "PAQUETE", id,
                    null, null, null, "{\"evento\":\"PACKAGE_DELIVERED\",\"unidad\":" + pq.idUnidad() + "}");
        } catch (Exception ignored) {
        }

        return getPaqueteById(id);
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
