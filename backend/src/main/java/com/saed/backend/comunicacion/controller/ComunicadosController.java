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
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD') or hasAuthority('SCOPE_RESIDENTE')")
    public List<Map<String, Object>> getAvisos() {
        return jdbcTemplate.queryForList("SELECT * FROM COMUNICADOS ORDER BY FECHA_PUBLICACION DESC", Map.of());
    }

    @PostMapping("/aviso")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<Void> postAviso(@RequestBody Map<String, Object> payload) {
        String titulo = (String) payload.getOrDefault("titulo", "Aviso");
        String contenido = (String) payload.getOrDefault("mensaje", "");
        String sql = "INSERT INTO COMUNICADOS (ID_PROPIEDAD, TITULO, CONTENIDO, TIPO_SEGMENTACION, PRIORIDAD, ESTADO) " +
                     "VALUES (SYS_CONTEXT('SAED_CTX', 'ID_PROPIEDAD'), :titulo, :contenido, 'TODOS', 'NORMAL', 'PUBLICADO')";
        Map<String, Object> params = new HashMap<>();
        params.put("titulo", titulo);
        params.put("contenido", contenido);
        jdbcTemplate.update(sql, params);

        // Envío de email masivo a residentes de la propiedad
        try {
            String emailSql = "SELECT u.EMAIL FROM USUARIOS u " +
                    "JOIN USUARIO_ASIGNACIONES ua ON u.ID_USUARIO = ua.ID_USUARIO " +
                    "WHERE ua.ESTADO = 'ACTIVA' AND u.EMAIL IS NOT NULL " +
                    "AND SYS_CONTEXT('SAED_CTX', 'ID_PROPIEDAD') IS NOT NULL";
            List<Map<String, Object>> emails = jdbcTemplate.queryForList(emailSql, Map.of());
            String asunto = "[SAED] " + titulo;
            String html = "<h2>" + titulo + "</h2><p>" + contenido + "</p><hr><p style='color:#888;font-size:12px'>Enviado desde SAED</p>";
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

        return ResponseEntity.ok().build();
    }

    @PostMapping("/aviso-ruido")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<Void> postAvisoRuido(@RequestBody Map<String, Object> payload) {
        String sql = "INSERT INTO NOTIFICACIONES (ID_USUARIO_DESTINATARIO, CANAL, TITULO, MENSAJE, ESTADO_ENVIO) " +
                     "VALUES (:idUsuario, 'ALERTA', 'Aviso por Ruido', 'Por favor modere el ruido en su apartamento.', 'ENVIADO')";
        Map<String, Object> params = new HashMap<>();
        params.put("idUsuario", payload.get("idResidente"));
        jdbcTemplate.update(sql, params);
        return ResponseEntity.ok().build();
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

