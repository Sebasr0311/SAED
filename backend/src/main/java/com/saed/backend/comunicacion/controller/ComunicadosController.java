package com.saed.backend.comunicacion.controller;

import com.saed.backend.audit.Auditable;
import com.saed.backend.audit.AuditCategory;
import com.saed.backend.audit.AuditSeverity;
import com.saed.backend.common.service.EmailService;
import com.saed.backend.comunicacion.service.ComunicadoService;
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
    private final ComunicadoService comunicadoService;
    private static final Logger log = Logger.getLogger(ComunicadosController.class.getName());

    public ComunicadosController(NamedParameterJdbcTemplate jdbcTemplate, EmailService emailService,
                                 ComunicadoService comunicadoService) {
        this.jdbcTemplate = jdbcTemplate;
        this.emailService = emailService;
        this.comunicadoService = comunicadoService;
    }

    /** F08-03: notices of the caller's property, filtered by segment, expiry and per-user archive. */
    @GetMapping("/avisos")
    @PreAuthorize("isAuthenticated()")
    public List<Map<String, Object>> getAvisos(@RequestParam(required = false) Long idPropiedad) {
        return comunicadoService.listVisible(idPropiedad);
    }

    @GetMapping("/avisos/archivados")
    @PreAuthorize("isAuthenticated()")
    public List<Map<String, Object>> getAvisosArchivados() {
        return comunicadoService.listArchivedForUser();
    }

    @PutMapping("/avisos/{id}/leido")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> marcarLeido(@PathVariable Long id) {
        comunicadoService.markRead(id);
        return ResponseEntity.noContent().build();
    }

    /** Archives the notice only for the caller (persisted per user, shared across sessions). */
    @PutMapping("/avisos/{id}/archivar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> archivarParaMi(@PathVariable Long id) {
        comunicadoService.archiveForUser(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/aviso")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD')")
    @Auditable(action = "PUBLICAR_AVISO", resource = "COMUNICADO", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.INFO)
    public ResponseEntity<Map<String, Object>> postAviso(@RequestBody Map<String, Object> payload) {
        return ResponseEntity.ok(comunicadoService.publish(payload));
    }

    /** Archives the notice for everyone (administration). */
    @DeleteMapping("/aviso/{id}")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD')")
    @Auditable(action = "ARCHIVAR_AVISO", resource = "COMUNICADO", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.INFO)
    public ResponseEntity<Void> archivarAviso(@PathVariable Long id) {
        comunicadoService.archiveOfficial(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/aviso-ruido")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_PORTERO')")
    public ResponseEntity<Void> postAvisoRuido(@RequestBody Map<String, Object> payload) {
        Long idResidente = payload.get("idResidente") != null ? Long.valueOf(payload.get("idResidente").toString()) : null;
        Long idApartamento = payload.get("idApartamento") != null ? Long.valueOf(payload.get("idApartamento").toString()) : null;
        String cuerpo = payload.get("cuerpo") != null ? payload.get("cuerpo").toString() : "Por favor modere el ruido en su apartamento.";

        // F08-03: the target unit must belong to the caller's property; residents are resolved from it.
        com.saed.backend.context.SaedContext ctx = com.saed.backend.context.SaedContextHolder.getContext();
        if (ctx == null || ctx.getPropertyId() == null) {
            throw new org.springframework.security.access.AccessDeniedException("Missing property context");
        }
        if (idApartamento == null) {
            throw new IllegalArgumentException("idApartamento es obligatorio");
        }
        List<Long> unidad = jdbcTemplate.query(
            "SELECT ID_UNIDAD FROM UNIDADES WHERE ID_UNIDAD = :u AND ID_PROPIEDAD = :p",
            Map.of("u", idApartamento, "p", ctx.getPropertyId()), (rs, r) -> rs.getLong("ID_UNIDAD"));
        if (unidad.isEmpty()) {
            throw new org.springframework.security.access.AccessDeniedException("La unidad no pertenece a la propiedad");
        }
        idResidente = null; // recipients come from the verified unit only

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

        if (userIds.isEmpty()) {
            throw new IllegalStateException("La unidad no tiene residentes con usuario para notificar");
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

    // Intercom confirmation endpoints (/confirmar-pendiente, /confirmar, /resultado-notificar) live in
    // porteria.intercom.IntercomController and are backed by persisted requests (F07-02).
}
