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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Platform Memberships", description = "Administración de Membresías SaaS de Organizaciones para SUPERADMIN (Persistencia Oracle)")
@RestController
@RequestMapping("/api/v1/platform/memberships")
@PreAuthorize("hasAuthority('SCOPE_SUPERADMIN')")
public class PlatformMembershipsController {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PlatformMembershipsController(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> getMemberships() {
        String sql = """
            SELECT m.ID_MEMBRESIA AS "id",
                   m.ID_ORGANIZACION AS "idOrganizacion",
                   o.NOMBRE AS "organizacionNombre",
                   m.ID_PLAN AS "idPlan",
                   p.NOMBRE AS "planNombre",
                   p.CODIGO AS "planCodigo",
                   p.PRECIO_MENSUAL AS "precioMensual",
                   TO_CHAR(m.FECHA_INICIO, 'YYYY-MM-DD') AS "fechaInicio",
                   TO_CHAR(m.FECHA_FIN, 'YYYY-MM-DD') AS "fechaFin",
                   m.ESTADO AS "estado",
                   m.ES_PRUEBA AS "esPrueba"
            FROM MEMBRESIAS m
            JOIN ORGANIZACIONES o ON m.ID_ORGANIZACION = o.ID_ORGANIZACION
            JOIN PLANES p ON m.ID_PLAN = p.ID_PLAN
            ORDER BY m.ID_MEMBRESIA DESC
            """;
        List<Map<String, Object>> memberships = jdbcTemplate.queryForList(sql, new MapSqlParameterSource());
        return ApiResponse.success(memberships);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMembershipById(@PathVariable Long id) {
        String sql = """
            SELECT m.ID_MEMBRESIA AS "id",
                   m.ID_ORGANIZACION AS "idOrganizacion",
                   o.NOMBRE AS "organizacionNombre",
                   m.ID_PLAN AS "idPlan",
                   p.NOMBRE AS "planNombre",
                   p.CODIGO AS "planCodigo",
                   p.PRECIO_MENSUAL AS "precioMensual",
                   TO_CHAR(m.FECHA_INICIO, 'YYYY-MM-DD') AS "fechaInicio",
                   TO_CHAR(m.FECHA_FIN, 'YYYY-MM-DD') AS "fechaFin",
                   m.ESTADO AS "estado",
                   m.ES_PRUEBA AS "esPrueba"
            FROM MEMBRESIAS m
            JOIN ORGANIZACIONES o ON m.ID_ORGANIZACION = o.ID_ORGANIZACION
            JOIN PLANES p ON m.ID_PLAN = p.ID_PLAN
            WHERE m.ID_MEMBRESIA = :id
            """;
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, new MapSqlParameterSource("id", id));
        if (rows.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ApiResponse.success(rows.get(0)));
    }

    @PostMapping
    @Transactional
    @Auditable(action = "CREATE", resource = "MEMBRESIA_SAAS", category = AuditCategory.ADMINISTRATIVE, severity = AuditSeverity.HIGH)
    public ResponseEntity<ApiResponse<Map<String, Object>>> createMembership(@RequestBody Map<String, Object> payload) {
        Number orgIdNum = (Number) payload.get("idOrganizacion");
        Number planIdNum = (Number) payload.get("idPlan");
        String estado = (String) payload.getOrDefault("estado", "ACTIVA");
        String esPrueba = "PRUEBA".equalsIgnoreCase(estado) ? "S" : "N";

        if (orgIdNum == null || planIdNum == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("idOrganizacion e idPlan son requeridos"));
        }

        Long idOrg = orgIdNum.longValue();
        Long idPlan = planIdNum.longValue();

        // Desactivar membresías previas activas de la organización
        String updateOldSql = """
            UPDATE MEMBRESIAS
            SET ESTADO = 'INACTIVA'
            WHERE ID_ORGANIZACION = :idOrg AND ESTADO IN ('ACTIVA', 'PRUEBA')
            """;
        jdbcTemplate.update(updateOldSql, new MapSqlParameterSource("idOrg", idOrg));

        // Insertar nueva membresía respetando el esquema exacto de Oracle ATP
        String insertSql = """
            INSERT INTO MEMBRESIAS (ID_ORGANIZACION, ID_PLAN, FECHA_INICIO, FECHA_FIN, ESTADO, ES_PRUEBA)
            VALUES (:idOrg, :idPlan, TRUNC(SYSDATE), ADD_MONTHS(TRUNC(SYSDATE), 12), :estado, :esPrueba)
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idOrg", idOrg)
                .addValue("idPlan", idPlan)
                .addValue("estado", estado)
                .addValue("esPrueba", esPrueba);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(insertSql, params, keyHolder, new String[]{"ID_MEMBRESIA"});
        Number newId = keyHolder.getKey();

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(Map.of(
                "id", newId != null ? newId.longValue() : 0,
                "idOrganizacion", idOrg,
                "idPlan", idPlan,
                "estado", estado
        )));
    }

    @PutMapping("/{id}/estado")
    @Transactional
    @Auditable(action = "UPDATE", resource = "MEMBRESIA_SAAS", category = AuditCategory.ADMINISTRATIVE, severity = AuditSeverity.HIGH)
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateStatus(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        String nuevoEstado = (String) payload.get("estado");

        if (nuevoEstado == null || nuevoEstado.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("El estado es requerido"));
        }

        String sql = """
            UPDATE MEMBRESIAS
            SET ESTADO = :estado
            WHERE ID_MEMBRESIA = :id
            """;

        int rows = jdbcTemplate.update(sql, new MapSqlParameterSource("id", id).addValue("estado", nuevoEstado.toUpperCase()));
        if (rows == 0) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "id", id,
                "estado", nuevoEstado.toUpperCase(),
                "message", "Estado de membresía actualizado exitosamente"
        )));
    }
}
