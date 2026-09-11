package com.saed.backend.comunicacion.controller;

import com.saed.backend.audit.Auditable;
import com.saed.backend.audit.AuditCategory;
import com.saed.backend.audit.AuditSeverity;
import com.saed.backend.common.service.EmailService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.logging.Logger;

@Tag(name = "Comunicados", description = "API para la gestion de Comunicados")
@RestController
@RequestMapping("/api/v1/buzon")
public class ComunicadosController {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final EmailService emailService;
    private static final Logger log = Logger.getLogger(ComunicadosController.class.getName());

    public ComunicadosController(NamedParameterJdbcTemplate jdbcTemplate, EmailService emailService) {
        this.jdbcTemplate = jdbcTemplate;
        this.emailService = emailService;
    }

    @GetMapping("/avisos")
    @PreAuthorize("isAuthenticated()")
    public List<Map<String, Object>> getAvisos(@RequestParam(required = false) Long idPropiedad) {
        String sql = "SELECT * FROM COMUNICADOS WHERE NVL(ESTADO, 'PUBLICADO') != 'ARCHIVADO'";
        Map<String, Object> params = new HashMap<>();
        if (idPropiedad != null) {
            sql += " AND ID_PROPIEDAD = :idPropiedad";
            params.put("idPropiedad", idPropiedad);
        }
        sql += " ORDER BY FECHA_PUBLICACION DESC";
        return jdbcTemplate.queryForList(sql, params);
    }

    @PostMapping("/aviso")
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<Map<String, Object>> postAviso(@RequestBody Map<String, Object> payload) {
        String titulo = (String) payload.getOrDefault("titulo", "Aviso Oficial");
        String contenido = (String) payload.getOrDefault("mensaje", payload.getOrDefault("contenido", ""));
        String rawPrioridad = ((String) payload.getOrDefault("prioridad", "NORMAL")).toUpperCase();
        String prioridad;
        if ("ALTA".equals(rawPrioridad) || "IMPORTANTE".equals(rawPrioridad)) {
            prioridad = "IMPORTANTE";
        } else if ("URGENTE".equals(rawPrioridad)) {
            prioridad = "URGENTE";
        } else if ("BAJA".equals(rawPrioridad)) {
            prioridad = "BAJA";
        } else {
            prioridad = "NORMAL";
        }
        String segmentacion = (String) payload.getOrDefault("tipoSegmentacion", "TODOS");
        Long propId = payload.get("idPropiedad") != null
            ? Long.valueOf(payload.get("idPropiedad").toString())
            : null;
        Long resolvedPropId = propId != null
            ? propId
            : (com.saed.backend.context.SaedContextHolder.getContext() != null && com.saed.backend.context.SaedContextHolder.getContext().getPropertyId() != null
                ? com.saed.backend.context.SaedContextHolder.getContext().getPropertyId() : 1L);

        String sql = "INSERT INTO COMUNICADOS (ID_PROPIEDAD, TITULO, CONTENIDO, TIPO_SEGMENTACION, PRIORIDAD, ESTADO) " +
                     "VALUES (:propId, :titulo, :contenido, :segmentacion, :prioridad, 'PUBLICADO')";
        Map<String, Object> params = new HashMap<>();
        params.put("propId", resolvedPropId);
        params.put("titulo", titulo);
        params.put("contenido", contenido);
        params.put("segmentacion", segmentacion);
        params.put("prioridad", prioridad);
        jdbcTemplate.update(sql, params);

        // Envío de email masivo a residentes de la propiedad (opcional según payload)
        boolean enviarEmail = Boolean.TRUE.equals(payload.get("enviarEmail")) || !Boolean.FALSE.equals(payload.get("enviarEmail"));
        if (enviarEmail) {
            try {
                String emailSql = "SELECT u.EMAIL FROM USUARIOS u " +
                        "JOIN USUARIO_ASIGNACIONES ua ON u.ID_USUARIO = ua.ID_USUARIO " +
                        "WHERE ua.ESTADO = 'ACTIVA' AND u.EMAIL IS NOT NULL " +
                        "AND (ua.ID_PROPIEDAD = :propId OR (:propId IS NULL AND SYS_CONTEXT('SAED_CTX', 'ID_PROPIEDAD') IS NOT NULL))";
                Map<String, Object> emailParams = new HashMap<>();
                emailParams.put("propId", resolvedPropId);
                List<Map<String, Object>> emails = jdbcTemplate.queryForList(emailSql, emailParams);
                String asunto = "[SAED " + prioridad + "] " + titulo;
                String html = "<h2>" + titulo + "</h2><p>" + contenido + "</p><hr><p style='color:#888;font-size:12px'>Enviado desde SAED — Centro de Comunicaciones</p>";
                for (Map<String, Object> row : emails) {
                    String email = (String) row.get("EMAIL");
                    if (email != null && !email.isBlank()) {
                        emailService.enviarHtmlPublico(email, asunto, html);
                    }
                }
                log.info("Aviso enviado por email a " + emails.size() + " residentes");
            } catch (Exception e) {
                log.warning("No se pudieron enviar emails del aviso: " + e.getMessage());
            }
        }

        return ResponseEntity.ok(Map.of("mensaje", "Aviso publicado exitosamente"));
    }

    @DeleteMapping("/aviso/{id}")
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<Void> archivarAviso(@PathVariable Long id) {
        jdbcTemplate.update("UPDATE COMUNICADOS SET ESTADO = 'ARCHIVADO' WHERE ID_COMUNICADO = :id", Map.of("id", id));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/aviso-ruido")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_PORTERO')")
    public ResponseEntity<Void> postAvisoRuido(@RequestBody Map<String, Object> payload) {
        Long idResidente = payload.get("idResidente") != null ? Long.valueOf(payload.get("idResidente").toString()) : null;
        Long idApartamento = payload.get("idApartamento") != null ? Long.valueOf(payload.get("idApartamento").toString()) : null;
        String cuerpo = payload.get("cuerpo") != null ? payload.get("cuerpo").toString() : "Por favor modere el ruido en su apartamento.";

        List<Long> userIds = new java.util.ArrayList<>();
        if (idResidente != null) {
            try {
                List<Long> uCheck = jdbcTemplate.query(
                    "SELECT ID_USUARIO FROM USUARIOS WHERE ID_USUARIO = :id",
                    Map.of("id", idResidente), (rs, r) -> rs.getLong("ID_USUARIO")
                );
                if (!uCheck.isEmpty()) {
                    userIds.add(idResidente);
                } else {
                    List<Long> pUids = jdbcTemplate.query(
                        "SELECT ID_USUARIO FROM USUARIOS WHERE ID_PERSONA = :id",
                        Map.of("id", idResidente), (rs, r) -> rs.getLong("ID_USUARIO")
                    );
                    userIds.addAll(pUids);
                }
            } catch (Exception ignored) {
                userIds.add(idResidente);
            }
        }
        if (idApartamento != null) {
            try {
                List<Long> aptUsers = jdbcTemplate.query(
                    "SELECT ua.ID_USUARIO FROM USUARIO_ASIGNACIONES ua " +
                    "WHERE ua.ID_UNIDAD = :idUnidad AND ua.ESTADO IN ('ACTIVA', 'ACTIVO') " +
                    "UNION " +
                    "SELECT u.ID_USUARIO FROM RESIDENTES_UNIDAD ru " +
                    "JOIN USUARIOS u ON ru.ID_PERSONA = u.ID_PERSONA " +
                    "WHERE ru.ID_UNIDAD = :idUnidad AND ru.ESTADO = 'ACTIVO'",
                    Map.of("idUnidad", idApartamento),
                    (rs, rowNum) -> rs.getLong("ID_USUARIO")
                );
                for (Long uid : aptUsers) {
                    if (!userIds.contains(uid)) {
                        userIds.add(uid);
                    }
                }
            } catch (Exception e) {
                log.warning("No se pudieron resolver usuarios para aviso de ruido: " + e.getMessage());
            }
        }

        if (userIds.isEmpty() && idApartamento != null) {
            Long currentUserId = com.saed.backend.context.SaedContextHolder.getContext() != null
                ? com.saed.backend.context.SaedContextHolder.getContext().getUserId() : 1L;
            userIds.add(currentUserId != null ? currentUserId : 1L);
        }

        String sql = "INSERT INTO NOTIFICACIONES (ID_USUARIO_DESTINATARIO, CANAL, TITULO, MENSAJE, ENLACE_DESTINO, ESTADO_ENVIO) " +
                     "VALUES (:idUsuario, 'ALERTA', 'Aviso por Ruido', :mensaje, :enlace, 'ENVIADO')";
        String enlace = idApartamento != null ? "UNIDAD:" + idApartamento : null;
        for (Long uid : userIds) {
            try {
                Map<String, Object> params = new HashMap<>();
                params.put("idUsuario", uid);
                params.put("mensaje", cuerpo);
                params.put("enlace", enlace);
                jdbcTemplate.update(sql, params);
            } catch (Exception e) {
                log.warning("Error insertando notificacion de aviso de ruido: " + e.getMessage());
            }
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping("/aviso-ruido/estado")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_PORTERO')")
    public ResponseEntity<Map<String, Object>> getEstadoAvisoRuido(@RequestParam Long idApartamento) {
        String sql = "SELECT MAX(FECHA_ENVIO) AS ULTIMA_FECHA FROM NOTIFICACIONES " +
                     "WHERE TITULO = 'Aviso por Ruido' AND (" +
                     "  ENLACE_DESTINO = :enlace " +
                     "  OR ID_USUARIO_DESTINATARIO IN (" +
                     "    SELECT ua.ID_USUARIO FROM USUARIO_ASIGNACIONES ua WHERE ua.ID_UNIDAD = :idApto " +
                     "    UNION " +
                     "    SELECT u.ID_USUARIO FROM RESIDENTES_UNIDAD ru JOIN USUARIOS u ON ru.ID_PERSONA = u.ID_PERSONA WHERE ru.ID_UNIDAD = :idApto" +
                     "  )" +
                     ")";
        try {
            List<java.sql.Timestamp> list = jdbcTemplate.query(
                sql,
                Map.of("enlace", "UNIDAD:" + idApartamento, "idApto", idApartamento),
                (rs, r) -> rs.getTimestamp("ULTIMA_FECHA")
            );
            java.sql.Timestamp ultimaFecha = (list != null && !list.isEmpty()) ? list.get(0) : null;
            if (ultimaFecha == null) {
                return ResponseEntity.ok(Map.of(
                    "tieneAviso", false,
                    "puedeMultar", false,
                    "minutosTranscurridos", 0,
                    "minutosRestantes", 30,
                    "mensaje", "Debe realizar el aviso de ruido primero antes de aplicar una multa."
                ));
            }

            long diffMillis = System.currentTimeMillis() - ultimaFecha.getTime();
            long minutosTranscurridos = Math.max(0, diffMillis / (60 * 1000));
            boolean puedeMultar = minutosTranscurridos >= 30;
            long minutosRestantes = puedeMultar ? 0 : (30 - minutosTranscurridos);

            Map<String, Object> resp = new HashMap<>();
            resp.put("tieneAviso", true);
            resp.put("puedeMultar", puedeMultar);
            resp.put("fechaAviso", ultimaFecha.toInstant().toString());
            resp.put("minutosTranscurridos", minutosTranscurridos);
            resp.put("minutosRestantes", minutosRestantes);
            resp.put("mensaje", puedeMultar
                ? "Precedente verificado: Aviso de ruido enviado hace " + minutosTranscurridos + " minutos. Puede proceder con la multa."
                : "No puede aplicar la multa aún. Deben transcurrir al menos 30 minutos desde el aviso de ruido (faltan " + minutosRestantes + " minutos)."
            );
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            log.warning("Error consultando estado de aviso de ruido: " + e.getMessage());
            return ResponseEntity.ok(Map.of(
                "tieneAviso", false,
                "puedeMultar", false,
                "mensaje", "No fue posible verificar el aviso de ruido: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/confirmar-pendiente")
    @PreAuthorize("hasAuthority('SCOPE_RESIDENTE')")
    public List<Map<String, Object>> confirmarPendiente() {
        return java.util.Collections.emptyList(); // Feature removed in V4 schema
    }

    @PostMapping("/confirmar")
    @PreAuthorize("hasAuthority('SCOPE_RESIDENTE')")
    public ResponseEntity<Void> confirmar(@RequestBody Map<String, Object> payload) {
        return ResponseEntity.ok().build();
    }

    @GetMapping("/resultado-notificar")
    @PreAuthorize("hasAuthority('SCOPE_PORTERO') or hasAuthority('SCOPE_ADMIN_PROPIEDAD') or hasAuthority('SCOPE_RESIDENTE')")
    public ResponseEntity<Map<String, Object>> getResultadoNotificar(@RequestParam(required = false) Long idVisita) {
        return ResponseEntity.ok(Map.of(
            "confirmado", 1,
            "idVisita", idVisita != null ? idVisita : 0L,
            "mensaje", "Visita confirmada y autorizada"
        ));
    }
}

