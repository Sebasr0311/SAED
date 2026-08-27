package com.saed.backend.comunicacion.controller;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Tag(name = "Comunicados", description = "API para la gestion de Comunicados")
@RestController
@RequestMapping("/api/v1/buzon")
public class ComunicadosController {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    public ComunicadosController(NamedParameterJdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    @GetMapping("/avisos")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD') or hasAuthority('SCOPE_RESIDENTE')")
    public List<Map<String, Object>> getAvisos() {
        return jdbcTemplate.queryForList("SELECT * FROM COMUNICADOS ORDER BY FECHA_PUBLICACION DESC", Map.of());
    }

    @PostMapping("/aviso")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<Void> postAviso(@RequestBody Map<String, Object> payload) {
        String sql = "INSERT INTO COMUNICADOS (ID_PROPIEDAD, TITULO, CONTENIDO, TIPO_SEGMENTACION, PRIORIDAD, ESTADO) " +
                     "VALUES (SYS_CONTEXT('SAED_CTX', 'ID_PROPIEDAD'), :titulo, :contenido, 'TODOS', 'NORMAL', 'PUBLICADO')";
        Map<String, Object> params = new HashMap<>();
        params.put("titulo", payload.get("titulo"));
        params.put("contenido", payload.get("mensaje"));
        jdbcTemplate.update(sql, params);
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
}

