package com.saed.backend.comunicacion.controller;
import com.saed.backend.comunicacion.dto.AlertaDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import java.util.List;
import java.util.Map;
import java.time.ZoneId;

@Tag(name = "Alertas", description = "API para la gestion de Alertas")
@RestController
@RequestMapping("/api/v1/alertas")
public class AlertasController {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    public AlertasController(NamedParameterJdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    private final RowMapper<AlertaDTO> rowMapper = (rs, rowNum) -> new AlertaDTO(
        rs.getLong("ID_ALERTA"), rs.getLong("ID_PROPIEDAD"), rs.getString("TIPO_ALERTA"),
        rs.getString("NUMERO_APARTAMENTO"), rs.getString("NOMBRE_RESIDENTE"), rs.getString("ESTADO_CUOTA"),
        rs.getString("LEIDA"), rs.getTimestamp("FECHA_CREACION") != null ? rs.getTimestamp("FECHA_CREACION").toInstant().atZone(ZoneId.systemDefault()) : null
    );

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    public List<AlertaDTO> getAlertas(@RequestParam(required = false) String soloNoLeidas) {
        String sql = "SELECT * FROM ALERTAS_ADMIN";
        if ("true".equals(soloNoLeidas)) {
            sql += " WHERE LEIDA = 'N'";
        }
        sql += " ORDER BY FECHA_CREACION DESC";
        return jdbcTemplate.query(sql, rowMapper);
    }

    @PutMapping("/{id}/leer")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<Void> marcarLeida(@PathVariable Long id) {
        jdbcTemplate.update("UPDATE ALERTAS_ADMIN SET LEIDA = 'S' WHERE ID_ALERTA = :id", Map.of("id", id));
        return ResponseEntity.noContent().build();
    }
}

