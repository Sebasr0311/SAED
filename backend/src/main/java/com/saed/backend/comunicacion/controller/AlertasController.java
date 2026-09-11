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

    private final RowMapper<AlertaDTO> rowMapper = (rs, rowNum) -> {
        String mensaje = null;
        try {
            mensaje = rs.getString("MENSAJE");
        } catch (Exception ignored) {}
        return new AlertaDTO(
            rs.getLong("ID_ALERTA"), rs.getLong("ID_PROPIEDAD"), rs.getString("TIPO_ALERTA"),
            rs.getString("NUMERO_APARTAMENTO"), rs.getString("NOMBRE_RESIDENTE"), rs.getString("ESTADO_CUOTA"),
            mensaje,
            rs.getString("LEIDA"), rs.getTimestamp("FECHA_CREACION") != null ? rs.getTimestamp("FECHA_CREACION").toInstant().atZone(ZoneId.systemDefault()) : null
        );
    };

    @GetMapping
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD')")
    public List<AlertaDTO> getAlertas(
            @RequestParam(required = false) String soloNoLeidas,
            @RequestParam(required = false) Long idPropiedad) {
        String sql = "SELECT * FROM ALERTAS_ADMIN WHERE 1=1";
        org.springframework.jdbc.core.namedparam.MapSqlParameterSource params = new org.springframework.jdbc.core.namedparam.MapSqlParameterSource();
        if ("true".equalsIgnoreCase(soloNoLeidas)) {
            sql += " AND LEIDA = 'N'";
        }
        if (idPropiedad != null) {
            sql += " AND ID_PROPIEDAD = :idPropiedad";
            params.addValue("idPropiedad", idPropiedad);
        }
        sql += " ORDER BY FECHA_CREACION DESC";
        return jdbcTemplate.query(sql, params, rowMapper);
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<AlertaDTO> crearAlerta(@RequestBody Map<String, Object> payload) {
        Long propId = payload.get("idPropiedad") != null
            ? Long.valueOf(payload.get("idPropiedad").toString())
            : (com.saed.backend.context.SaedContextHolder.getContext() != null
                ? com.saed.backend.context.SaedContextHolder.getContext().getPropertyId() : null);
        if (propId == null) {
            propId = 1L;
        }
        String tipoAlerta = (String) payload.getOrDefault("tipoAlerta", "OPERATIVA");
        String numeroApto = (String) payload.get("numeroApartamento");
        String nombreResidente = (String) payload.get("nombreResidente");
        String estadoCuota = (String) payload.getOrDefault("estadoCuota", "PENDIENTE");
        String mensaje = (String) payload.getOrDefault("mensaje", "Alerta operativa: " + tipoAlerta);
        if (mensaje == null || mensaje.isBlank()) {
            mensaje = "Alerta operativa de " + tipoAlerta + (numeroApto != null ? " en unidad " + numeroApto : "");
        }

        String sql = "INSERT INTO ALERTAS_ADMIN (ID_PROPIEDAD, TIPO_ALERTA, NUMERO_APARTAMENTO, NOMBRE_RESIDENTE, ESTADO_CUOTA, MENSAJE, LEIDA) " +
                     "VALUES (:propId, :tipo, :apto, :nombre, :estado, :mensaje, 'N')";
        org.springframework.jdbc.core.namedparam.MapSqlParameterSource params = new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
            .addValue("propId", propId)
            .addValue("tipo", tipoAlerta)
            .addValue("apto", numeroApto)
            .addValue("nombre", nombreResidente)
            .addValue("estado", estadoCuota)
            .addValue("mensaje", mensaje);
        jdbcTemplate.update(sql, params);

        AlertaDTO dto = new AlertaDTO(null, propId, tipoAlerta, numeroApto, nombreResidente, estadoCuota, mensaje, "N", java.time.ZonedDateTime.now());
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(dto);
    }

    @PutMapping("/{id}/leer")
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<Void> marcarLeida(@PathVariable Long id) {
        jdbcTemplate.update("UPDATE ALERTAS_ADMIN SET LEIDA = 'S' WHERE ID_ALERTA = :id", Map.of("id", id));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/atender")
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<Void> marcarAtendida(@PathVariable Long id) {
        jdbcTemplate.update("UPDATE ALERTAS_ADMIN SET LEIDA = 'S' WHERE ID_ALERTA = :id", Map.of("id", id));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/marcar-todas-leidas")
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<Void> marcarTodasLeidas(@RequestParam(required = false) Long idPropiedad) {
        String sql = "UPDATE ALERTAS_ADMIN SET LEIDA = 'S' WHERE LEIDA = 'N'";
        org.springframework.jdbc.core.namedparam.MapSqlParameterSource params = new org.springframework.jdbc.core.namedparam.MapSqlParameterSource();
        if (idPropiedad != null) {
            sql += " AND ID_PROPIEDAD = :idPropiedad";
            params.addValue("idPropiedad", idPropiedad);
        }
        jdbcTemplate.update(sql, params);
        return ResponseEntity.noContent().build();
    }
}

