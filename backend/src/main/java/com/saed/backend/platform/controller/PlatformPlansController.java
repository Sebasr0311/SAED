package com.saed.backend.platform.controller;

import com.saed.backend.audit.AuditCategory;
import com.saed.backend.audit.AuditSeverity;
import com.saed.backend.audit.Auditable;
import com.saed.backend.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Platform Plans", description = "Administración de Planes SaaS de SAED para SUPERADMIN (Persistencia Oracle)")
@RestController
@RequestMapping("/api/v1/platform/plans")
@PreAuthorize("hasAuthority('SCOPE_SUPERADMIN')")
public class PlatformPlansController {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PlatformPlansController(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> getPlans() {
        String sql = """
            SELECT ID_PLAN AS "id", CODIGO AS "codigo", NOMBRE AS "nombre",
                   DESCRIPCION AS "descripcion", PRECIO_MENSUAL AS "precioMensual",
                   LIMITE_PROPIEDADES AS "maxPropiedades", LIMITE_UNIDADES AS "maxUnidades",
                   LIMITE_USUARIOS AS "maxUsuarios", LIMITE_ALMACENAMIENTO_GB AS "maxAlmacenamientoGb",
                   ESTADO AS "estado"
            FROM PLANES
            ORDER BY PRECIO_MENSUAL ASC
            """;
        List<Map<String, Object>> plans = jdbcTemplate.queryForList(sql, new MapSqlParameterSource());
        return ApiResponse.success(plans);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPlanById(@PathVariable Long id) {
        String sql = """
            SELECT ID_PLAN AS "id", CODIGO AS "codigo", NOMBRE AS "nombre",
                   DESCRIPCION AS "descripcion", PRECIO_MENSUAL AS "precioMensual",
                   LIMITE_PROPIEDADES AS "maxPropiedades", LIMITE_UNIDADES AS "maxUnidades",
                   LIMITE_USUARIOS AS "maxUsuarios", LIMITE_ALMACENAMIENTO_GB AS "maxAlmacenamientoGb",
                   ESTADO AS "estado"
            FROM PLANES
            WHERE ID_PLAN = :id
            """;
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, new MapSqlParameterSource("id", id));
        if (rows.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ApiResponse.success(rows.get(0)));
    }

    @PostMapping
    @Auditable(action = "CREATE", resource = "PLAN_SAAS", category = AuditCategory.ADMINISTRATIVE, severity = AuditSeverity.HIGH)
    public ResponseEntity<ApiResponse<Map<String, Object>>> createPlan(@RequestBody Map<String, Object> payload) {
        String codigo = (String) payload.getOrDefault("codigo", "");
        String nombre = (String) payload.getOrDefault("nombre", "");
        String descripcion = (String) payload.getOrDefault("descripcion", "");
        Number precioMensual = (Number) payload.getOrDefault("precioMensual", 0);
        Number maxPropiedades = (Number) payload.getOrDefault("maxPropiedades", 1);
        Number maxUnidades = (Number) payload.getOrDefault("maxUnidades", 50);
        Number maxUsuarios = (Number) payload.getOrDefault("maxUsuarios", 100);
        Number maxAlmacenamientoGb = (Number) payload.getOrDefault("maxAlmacenamientoGb", 5);

        if (codigo.isBlank()) {
            codigo = nombre.toUpperCase().replaceAll("[^A-Z0-9]", "_");
        }

        String sql = """
            INSERT INTO PLANES (CODIGO, NOMBRE, DESCRIPCION, PRECIO_MENSUAL, LIMITE_PROPIEDADES, LIMITE_UNIDADES, LIMITE_USUARIOS, LIMITE_ALMACENAMIENTO_GB, ESTADO)
            VALUES (:codigo, :nombre, :descripcion, :precioMensual, :maxPropiedades, :maxUnidades, :maxUsuarios, :maxAlmacenamientoGb, 'ACTIVO')
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("codigo", codigo)
                .addValue("nombre", nombre)
                .addValue("descripcion", descripcion)
                .addValue("precioMensual", precioMensual)
                .addValue("maxPropiedades", maxPropiedades)
                .addValue("maxUnidades", maxUnidades)
                .addValue("maxUsuarios", maxUsuarios)
                .addValue("maxAlmacenamientoGb", maxAlmacenamientoGb);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, params, keyHolder, new String[]{"ID_PLAN"});
        Number newId = keyHolder.getKey();

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(Map.of(
                "id", newId != null ? newId.longValue() : 0,
                "codigo", codigo,
                "nombre", nombre,
                "estado", "ACTIVO"
        )));
    }

    @PutMapping("/{id}")
    @Auditable(action = "UPDATE", resource = "PLAN_SAAS", category = AuditCategory.ADMINISTRATIVE, severity = AuditSeverity.HIGH)
    public ResponseEntity<ApiResponse<Map<String, Object>>> updatePlan(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        String nombre = (String) payload.get("nombre");
        String descripcion = (String) payload.get("descripcion");
        Number precioMensual = (Number) payload.get("precioMensual");
        Number maxPropiedades = (Number) payload.get("maxPropiedades");
        Number maxUnidades = (Number) payload.get("maxUnidades");
        Number maxUsuarios = (Number) payload.get("maxUsuarios");
        Number maxAlmacenamientoGb = (Number) payload.get("maxAlmacenamientoGb");
        String estado = (String) payload.get("estado");

        String sql = """
            UPDATE PLANES
            SET NOMBRE = NVL(:nombre, NOMBRE),
                DESCRIPCION = NVL(:descripcion, DESCRIPCION),
                PRECIO_MENSUAL = NVL(:precioMensual, PRECIO_MENSUAL),
                LIMITE_PROPIEDADES = NVL(:maxPropiedades, LIMITE_PROPIEDADES),
                LIMITE_UNIDADES = NVL(:maxUnidades, LIMITE_UNIDADES),
                LIMITE_USUARIOS = NVL(:maxUsuarios, LIMITE_USUARIOS),
                LIMITE_ALMACENAMIENTO_GB = NVL(:maxAlmacenamientoGb, LIMITE_ALMACENAMIENTO_GB),
                ESTADO = NVL(:estado, ESTADO)
            WHERE ID_PLAN = :id
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("nombre", nombre)
                .addValue("descripcion", descripcion)
                .addValue("precioMensual", precioMensual)
                .addValue("maxPropiedades", maxPropiedades)
                .addValue("maxUnidades", maxUnidades)
                .addValue("maxUsuarios", maxUsuarios)
                .addValue("maxAlmacenamientoGb", maxAlmacenamientoGb)
                .addValue("estado", estado);

        int rows = jdbcTemplate.update(sql, params);
        if (rows == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ApiResponse.success(Map.of("id", id, "message", "Plan actualizado exitosamente")));
    }
}
