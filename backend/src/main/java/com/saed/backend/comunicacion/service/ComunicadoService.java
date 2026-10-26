package com.saed.backend.comunicacion.service;

import com.saed.backend.common.service.EmailService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * F08-03 official notices (COMUNICADOS): tenant-scoped publication and reading, recipient segmentation
 * (all / tower-block / owners / tenants / council), and per-user read and archive state
 * (COMUNICADOS_LECTURAS, V5.62).
 */
@Service
@Transactional
public class ComunicadoService {

    private static final Logger log = LoggerFactory.getLogger(ComunicadoService.class);
    private static final Set<String> SEGMENTS =
        Set.of("TODOS", "TORRE_BLOQUE", "SOLO_PROPIETARIOS", "SOLO_ARRENDATARIOS", "CONSEJO_ADMIN");

    /** Segments a resident can receive; CONSEJO_ADMIN has no membership model yet and stays staff-only. */
    private static final String RESIDENT_SEGMENTS = "("
        + "c.TIPO_SEGMENTACION = 'TODOS' "
        + "OR (c.TIPO_SEGMENTACION = 'TORRE_BLOQUE' AND c.ID_BLOQUE IN "
        + "    (SELECT un.ID_BLOQUE FROM UNIDADES un WHERE un.ID_UNIDAD IN (:units))) "
        + "OR (c.TIPO_SEGMENTACION = 'SOLO_PROPIETARIOS' AND EXISTS (SELECT 1 FROM PROPIETARIOS_UNIDAD pu "
        + "    JOIN USUARIOS us ON us.ID_PERSONA = pu.ID_PERSONA WHERE us.ID_USUARIO = :usuario "
        + "    AND pu.ID_UNIDAD IN (:units) AND NVL(pu.ESTADO, 'ACTIVO') IN ('ACTIVO', 'ACTIVA'))) "
        + "OR (c.TIPO_SEGMENTACION = 'SOLO_ARRENDATARIOS' AND EXISTS (SELECT 1 FROM RESIDENTES_UNIDAD ru "
        + "    JOIN USUARIOS us ON us.ID_PERSONA = ru.ID_PERSONA WHERE us.ID_USUARIO = :usuario "
        + "    AND ru.ID_UNIDAD IN (:units) AND ru.TIPO_RESIDENTE = 'ARRENDATARIO' AND NVL(ru.ESTADO, 'ACTIVO') = 'ACTIVO'))"
        + ")";

    private final NamedParameterJdbcTemplate jdbc;
    private final EmailService emailService;

    public ComunicadoService(NamedParameterJdbcTemplate jdbcTemplate, EmailService emailService) {
        this.jdbc = jdbcTemplate;
        this.emailService = emailService;
    }

    // ------------------------------------------------------------------ scope

    private static SaedContext requireContext() {
        SaedContext ctx = SaedContextHolder.getContext();
        if (ctx == null || ctx.getUserId() == null) {
            throw new AccessDeniedException("Missing tenant context");
        }
        if ("SUPERADMIN".equalsIgnoreCase(ctx.getRoleCode()) || "GLOBAL".equalsIgnoreCase(ctx.getRoleScope())) {
            throw new AccessDeniedException("Platform administrators do not read property notices");
        }
        return ctx;
    }

    private static boolean isResident(SaedContext ctx) {
        return "RESIDENTE".equalsIgnoreCase(ctx.getRoleCode()) || "RESIDENTE_CONVIVENCIA".equalsIgnoreCase(ctx.getRoleCode())
            || "UNIDAD".equalsIgnoreCase(ctx.getRoleScope());
    }

    private static boolean isOrgAdmin(SaedContext ctx) {
        return "ADMIN_ORGANIZACION".equalsIgnoreCase(ctx.getRoleCode()) || "ORGANIZACION".equalsIgnoreCase(ctx.getRoleScope());
    }

    /** Properties the caller may address, optionally narrowed to {@code requested} (which must be one of them). */
    private List<Long> scopedProperties(SaedContext ctx, Long requested) {
        if (isOrgAdmin(ctx) && ctx.getPropertyId() == null) {
            if (ctx.getOrganizationId() == null) {
                throw new AccessDeniedException("Missing organization context");
            }
            List<Long> props = jdbc.query(
                "SELECT ID_PROPIEDAD FROM PROPIEDADES WHERE ID_ORGANIZACION = :orgId AND NVL(ESTADO, 'ACTIVA') != 'INACTIVA'",
                Map.of("orgId", ctx.getOrganizationId()), (rs, r) -> rs.getLong("ID_PROPIEDAD"));
            if (requested == null) {
                if (props.isEmpty()) {
                    throw new AccessDeniedException("La organización no tiene propiedades activas");
                }
                return props;
            }
            if (!props.contains(requested)) {
                throw new AccessDeniedException("La propiedad no pertenece a la organización");
            }
            return List.of(requested);
        }
        if (ctx.getPropertyId() == null) {
            throw new AccessDeniedException("Missing property context");
        }
        if (requested != null && !requested.equals(ctx.getPropertyId())) {
            throw new AccessDeniedException("No tiene permisos sobre la propiedad indicada");
        }
        return List.of(ctx.getPropertyId());
    }

    private List<Long> residentUnits(SaedContext ctx) {
        if (ctx.getUnitId() != null) {
            return List.of(ctx.getUnitId());
        }
        return jdbc.query(
            "SELECT ID_UNIDAD FROM USUARIO_ASIGNACIONES WHERE ID_USUARIO = :u AND ESTADO IN ('ACTIVA', 'ACTIVO') "
                + "AND ID_UNIDAD IS NOT NULL",
            Map.of("u", ctx.getUserId()), (rs, r) -> rs.getLong("ID_UNIDAD"));
    }

    /** WHERE clause and parameters of the notices visible to the caller. */
    private String visibility(SaedContext ctx, Long requested, Map<String, Object> params) {
        params.put("props", scopedProperties(ctx, requested));
        params.put("usuario", ctx.getUserId());
        StringBuilder where = new StringBuilder(
            "c.ID_PROPIEDAD IN (:props) AND NVL(c.ESTADO, 'PUBLICADO') = 'PUBLICADO' ");
        if (isResident(ctx)) {
            List<Long> units = residentUnits(ctx);
            params.put("units", units.isEmpty() ? List.of(-1L) : units);
            where.append("AND (c.FECHA_EXPIRACION IS NULL OR c.FECHA_EXPIRACION >= TRUNC(SYSDATE)) AND ")
                .append(RESIDENT_SEGMENTS).append(' ');
        }
        return where.toString();
    }

    // ---------------------------------------------------------------- reading

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listVisible(Long idPropiedad) {
        SaedContext ctx = requireContext();
        Map<String, Object> params = new HashMap<>();
        String where = visibility(ctx, idPropiedad, params);
        return jdbc.queryForList(
            "SELECT c.ID_COMUNICADO, c.ID_PROPIEDAD, c.ID_BLOQUE, c.TITULO, TO_CHAR(c.CONTENIDO) AS CONTENIDO, "
                + "c.TIPO_SEGMENTACION, c.PRIORIDAD, c.ESTADO, c.FECHA_PUBLICACION, c.FECHA_EXPIRACION AS FECHA_VENCIMIENTO, "
                + "p.NOMBRE AS PROPIEDAD_NOMBRE, CASE WHEN l.LEIDO_EN IS NULL THEN 'N' ELSE 'S' END AS LEIDO "
                + "FROM COMUNICADOS c LEFT JOIN PROPIEDADES p ON p.ID_PROPIEDAD = c.ID_PROPIEDAD "
                + "LEFT JOIN COMUNICADOS_LECTURAS l ON l.ID_COMUNICADO = c.ID_COMUNICADO AND l.ID_USUARIO = :usuario "
                + "WHERE " + where
                + "AND NOT EXISTS (SELECT 1 FROM COMUNICADOS_LECTURAS a WHERE a.ID_COMUNICADO = c.ID_COMUNICADO "
                + "AND a.ID_USUARIO = :usuario AND a.ARCHIVADO_EN IS NOT NULL) "
                + "ORDER BY c.FECHA_PUBLICACION DESC",
            params);
    }

    /** Notices the caller archived for themselves (still visible to them, but out of the main list). */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listArchivedForUser() {
        SaedContext ctx = requireContext();
        Map<String, Object> params = new HashMap<>();
        String where = visibility(ctx, null, params);
        return jdbc.queryForList(
            "SELECT c.ID_COMUNICADO, c.ID_PROPIEDAD, c.TITULO, TO_CHAR(c.CONTENIDO) AS CONTENIDO, c.TIPO_SEGMENTACION, "
                + "c.PRIORIDAD, c.FECHA_PUBLICACION, a.ARCHIVADO_EN "
                + "FROM COMUNICADOS c JOIN COMUNICADOS_LECTURAS a ON a.ID_COMUNICADO = c.ID_COMUNICADO "
                + "AND a.ID_USUARIO = :usuario AND a.ARCHIVADO_EN IS NOT NULL "
                + "WHERE " + where + "ORDER BY a.ARCHIVADO_EN DESC",
            params);
    }

    /** Returns the property of a notice visible to the caller; out-of-scope notices look like not found. */
    private Long requireVisible(SaedContext ctx, Long idComunicado) {
        Map<String, Object> params = new HashMap<>();
        String where = visibility(ctx, null, params);
        params.put("id", idComunicado);
        List<Long> found = jdbc.query(
            "SELECT c.ID_PROPIEDAD FROM COMUNICADOS c WHERE c.ID_COMUNICADO = :id AND " + where,
            params, (rs, r) -> rs.getLong("ID_PROPIEDAD"));
        if (found.isEmpty()) {
            throw new NoSuchElementException("Aviso no encontrado");
        }
        // Property-scoped callers can only see notices of their own property.
        return ctx.getPropertyId() != null ? ctx.getPropertyId() : found.get(0);
    }

    public void markRead(Long idComunicado) {
        upsertState(idComunicado, false);
    }

    public void archiveForUser(Long idComunicado) {
        upsertState(idComunicado, true);
    }

    private void upsertState(Long idComunicado, boolean archive) {
        SaedContext ctx = requireContext();
        Long propId = requireVisible(ctx, idComunicado);
        Map<String, Object> params = new HashMap<>();
        params.put("id", idComunicado);
        params.put("usuario", ctx.getUserId());
        params.put("propId", propId);
        String update = "UPDATE COMUNICADOS_LECTURAS SET LEIDO_EN = NVL(LEIDO_EN, SYSTIMESTAMP)"
            + (archive ? ", ARCHIVADO_EN = NVL(ARCHIVADO_EN, SYSTIMESTAMP)" : "")
            + " WHERE ID_COMUNICADO = :id AND ID_USUARIO = :usuario";
        if (jdbc.update(update, params) > 0) {
            return;
        }
        try {
            jdbc.update("INSERT INTO COMUNICADOS_LECTURAS (ID_COMUNICADO, ID_USUARIO, ID_PROPIEDAD, LEIDO_EN"
                + (archive ? ", ARCHIVADO_EN" : "") + ") VALUES (:id, :usuario, :propId, SYSTIMESTAMP"
                + (archive ? ", SYSTIMESTAMP" : "") + ")", params);
        } catch (DuplicateKeyException concurrent) {
            // Another session of the same user inserted the row first; apply the change to it.
            jdbc.update(update, params);
        }
    }

    // ------------------------------------------------------------- publishing

    private static String normalizeSegment(Object raw) {
        String value = raw == null ? "TODOS" : raw.toString().trim().toUpperCase();
        if ("PROPIETARIOS".equals(value)) {
            value = "SOLO_PROPIETARIOS";
        } else if ("ARRENDATARIOS".equals(value)) {
            value = "SOLO_ARRENDATARIOS";
        } else if ("CONSEJO".equals(value)) {
            value = "CONSEJO_ADMIN";
        }
        if (!SEGMENTS.contains(value)) {
            throw new IllegalArgumentException("Segmentación no válida: " + value);
        }
        return value;
    }

    private static String normalizePriority(Object raw) {
        String p = raw == null ? "NORMAL" : raw.toString().trim().toUpperCase();
        if ("ALTA".equals(p) || "IMPORTANTE".equals(p)) {
            return "IMPORTANTE";
        }
        if ("URGENTE".equals(p) || "BAJA".equals(p)) {
            return p;
        }
        return "NORMAL";
    }

    private static Long toLong(Object raw) {
        if (raw == null || raw.toString().isBlank() || "TODAS".equalsIgnoreCase(raw.toString())) {
            return null;
        }
        try {
            return Long.valueOf(raw.toString().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Identificador no válido: " + raw);
        }
    }

    public Map<String, Object> publish(Map<String, Object> payload) {
        SaedContext ctx = requireContext();
        if (isResident(ctx)) {
            throw new AccessDeniedException("Solo la administración publica avisos");
        }
        String titulo = payload.get("titulo") != null ? payload.get("titulo").toString().trim() : "Aviso Oficial";
        Object rawContenido = payload.get("mensaje") != null ? payload.get("mensaje") : payload.get("contenido");
        String contenido = rawContenido != null ? rawContenido.toString() : "";
        String prioridad = normalizePriority(payload.get("prioridad"));
        String segmentacion = normalizeSegment(payload.get("tipoSegmentacion"));
        List<Long> targets = scopedProperties(ctx, toLong(payload.get("idPropiedad")));

        Long idBloque = toLong(payload.get("idBloque"));
        if ("TORRE_BLOQUE".equals(segmentacion)) {
            if (idBloque == null || targets.size() != 1) {
                throw new IllegalArgumentException("Un aviso por torre/bloque requiere un bloque de una sola propiedad");
            }
            List<Long> bloque = jdbc.query(
                "SELECT ID_BLOQUE FROM BLOQUES WHERE ID_BLOQUE = :b AND ID_PROPIEDAD = :p",
                Map.of("b", idBloque, "p", targets.get(0)), (rs, r) -> rs.getLong("ID_BLOQUE"));
            if (bloque.isEmpty()) {
                throw new AccessDeniedException("El bloque no pertenece a la propiedad");
            }
        } else {
            idBloque = null;
        }

        for (Long propId : targets) {
            Map<String, Object> params = new HashMap<>();
            params.put("propId", propId);
            params.put("bloque", idBloque);
            params.put("titulo", titulo);
            params.put("contenido", contenido);
            params.put("segmentacion", segmentacion);
            params.put("prioridad", prioridad);
            params.put("publicadoPor", ctx.getUserId());
            jdbc.update("INSERT INTO COMUNICADOS (ID_PROPIEDAD, ID_BLOQUE, TITULO, CONTENIDO, TIPO_SEGMENTACION, PRIORIDAD, "
                + "PUBLICADO_POR, ESTADO) VALUES (:propId, :bloque, :titulo, :contenido, :segmentacion, :prioridad, "
                + ":publicadoPor, 'PUBLICADO')", params);
        }

        int emails = 0;
        if (!Boolean.FALSE.equals(payload.get("enviarEmail"))) {
            String asunto = "[SAED " + prioridad + "] " + titulo;
            String html = "<h2>" + HtmlUtils.htmlEscape(titulo) + "</h2><p>" + HtmlUtils.htmlEscape(contenido)
                + "</p><hr><p style='color:#888;font-size:12px'>Enviado desde SAED - Centro de Comunicaciones</p>";
            for (Long propId : targets) {
                emails += sendToSegment(propId, segmentacion, idBloque, asunto, html);
            }
        }
        Map<String, Object> resp = new HashMap<>();
        resp.put("mensaje", targets.size() > 1
            ? "Aviso publicado exitosamente en " + targets.size() + " propiedades de la organización"
            : "Aviso publicado exitosamente");
        resp.put("propiedadesAfectadas", targets.size());
        resp.put("emailsEnviados", emails);
        return resp;
    }

    /** E-mail recipients follow the same segmentation as in-app reading. */
    private int sendToSegment(Long propId, String segmentacion, Long idBloque, String asunto, String html) {
        String filter = switch (segmentacion) {
            case "TORRE_BLOQUE" -> "AND ua.ID_UNIDAD IN (SELECT un.ID_UNIDAD FROM UNIDADES un WHERE un.ID_BLOQUE = :bloque) ";
            case "SOLO_PROPIETARIOS" -> "AND EXISTS (SELECT 1 FROM PROPIETARIOS_UNIDAD pu WHERE pu.ID_PERSONA = u.ID_PERSONA "
                + "AND pu.ID_UNIDAD = ua.ID_UNIDAD AND NVL(pu.ESTADO, 'ACTIVO') IN ('ACTIVO', 'ACTIVA')) ";
            case "SOLO_ARRENDATARIOS" -> "AND EXISTS (SELECT 1 FROM RESIDENTES_UNIDAD ru WHERE ru.ID_PERSONA = u.ID_PERSONA "
                + "AND ru.ID_UNIDAD = ua.ID_UNIDAD AND ru.TIPO_RESIDENTE = 'ARRENDATARIO' AND NVL(ru.ESTADO, 'ACTIVO') = 'ACTIVO') ";
            case "CONSEJO_ADMIN" -> "AND ua.ID_UNIDAD IS NULL ";
            default -> "";
        };
        Map<String, Object> params = new HashMap<>();
        params.put("propId", propId);
        params.put("bloque", idBloque);
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT DISTINCT u.EMAIL FROM USUARIOS u JOIN USUARIO_ASIGNACIONES ua ON u.ID_USUARIO = ua.ID_USUARIO "
                + "WHERE ua.ESTADO IN ('ACTIVA', 'ACTIVO') AND u.EMAIL IS NOT NULL AND ua.ID_PROPIEDAD = :propId " + filter,
            params);
        int sent = 0;
        for (Map<String, Object> row : rows) {
            Object address = row.get("EMAIL");
            if (address == null || address.toString().isBlank()) {
                continue;
            }
            try {
                emailService.enviarHtmlPublico(address.toString(), asunto, html);
                sent++;
            } catch (Exception e) {
                log.warn("Notice e-mail not delivered for property {} ({})", propId, e.getClass().getSimpleName());
            }
        }
        return sent;
    }

    /** Archives the notice for everyone (administration); out-of-scope ids look like not found. */
    public void archiveOfficial(Long idComunicado) {
        SaedContext ctx = requireContext();
        if (isResident(ctx)) {
            throw new AccessDeniedException("Solo la administración archiva avisos");
        }
        Map<String, Object> params = new HashMap<>();
        params.put("id", idComunicado);
        params.put("props", scopedProperties(ctx, null));
        if (jdbc.update("UPDATE COMUNICADOS SET ESTADO = 'ARCHIVADO' WHERE ID_COMUNICADO = :id AND ID_PROPIEDAD IN (:props)",
                params) == 0) {
            throw new NoSuchElementException("Aviso no encontrado");
        }
    }
}
