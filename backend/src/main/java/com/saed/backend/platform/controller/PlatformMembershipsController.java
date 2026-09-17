package com.saed.backend.platform.controller;

import com.saed.backend.audit.AuditCategory;
import com.saed.backend.audit.AuditSeverity;
import com.saed.backend.audit.Auditable;
import com.saed.backend.common.dto.ApiResponse;
import com.saed.backend.context.SaedContextHolder;
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
    private final com.saed.backend.platform.service.MembershipHistoryService membershipHistoryService;

    public PlatformMembershipsController(NamedParameterJdbcTemplate jdbcTemplate,
                                         com.saed.backend.platform.service.MembershipHistoryService membershipHistoryService) {
        this.jdbcTemplate = jdbcTemplate;
        this.membershipHistoryService = membershipHistoryService;
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

        // Consultar membresía previa para registrar transición en historial
        String queryOldSql = """
            SELECT ID_MEMBRESIA, ID_PLAN
            FROM MEMBRESIAS
            WHERE ID_ORGANIZACION = :idOrg AND ESTADO IN ('ACTIVA', 'PRUEBA')
            ORDER BY ID_MEMBRESIA DESC
            """;
        List<Map<String, Object>> oldMems = jdbcTemplate.queryForList(queryOldSql, new MapSqlParameterSource("idOrg", idOrg));
        Long idPlanAnterior = null;
        String tipoCambio = "INICIO";
        if (!oldMems.isEmpty()) {
            Map<String, Object> prev = oldMems.get(0);
            idPlanAnterior = ((Number) prev.get("ID_PLAN")).longValue();
            if (!idPlanAnterior.equals(idPlan)) {
                tipoCambio = idPlan > idPlanAnterior ? "UPGRADE" : "DOWNGRADE";
            } else {
                tipoCambio = "RENOVACION";
            }
        }

        // Desactivar membresías previas activas de la organización
        String updateOldSql = """
            UPDATE MEMBRESIAS
            SET ESTADO = 'CANCELADA'
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
        Long idMembresiaCreada = newId != null ? newId.longValue() : 0L;

        // Registrar en MEMBRESIAS_HISTORIAL (atómico con @Transactional)
        Long callerUserId = (SaedContextHolder.getContext() != null) ? SaedContextHolder.getContext().getUserId() : null;
        membershipHistoryService.recordChange(
                idMembresiaCreada,
                idPlanAnterior,
                idPlan,
                tipoCambio,
                "Membresía asignada por SUPERADMIN con estado " + estado.toUpperCase(),
                callerUserId
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(Map.of(
                "id", idMembresiaCreada,
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
        String estadoNorm = nuevoEstado.toUpperCase();

        // Consultar membresía actual para validar existencia y obtener ID_PLAN
        String queryCurrent = "SELECT ID_PLAN, ESTADO FROM MEMBRESIAS WHERE ID_MEMBRESIA = :id";
        List<Map<String, Object>> rowsCurrent = jdbcTemplate.queryForList(queryCurrent, new MapSqlParameterSource("id", id));
        if (rowsCurrent.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Long idPlan = ((Number) rowsCurrent.get(0).get("ID_PLAN")).longValue();
        String estadoAnterior = (String) rowsCurrent.get(0).get("ESTADO");

        String sql = """
            UPDATE MEMBRESIAS
            SET ESTADO = :estado
            WHERE ID_MEMBRESIA = :id
            """;

        int rows = jdbcTemplate.update(sql, new MapSqlParameterSource("id", id).addValue("estado", estadoNorm));
        if (rows == 0) {
            return ResponseEntity.notFound().build();
        }

        // Determinar tipo de cambio según restricción CK_MEMBHIST_TIPO
        String tipoCambio;
        if ("SUSPENDIDA".equalsIgnoreCase(estadoNorm)) {
            tipoCambio = "SUSPENSION";
        } else if ("CANCELADA".equalsIgnoreCase(estadoNorm)) {
            tipoCambio = "CANCELACION";
        } else if ("ACTIVA".equalsIgnoreCase(estadoNorm) && "SUSPENDIDA".equalsIgnoreCase(estadoAnterior)) {
            tipoCambio = "REACTIVACION";
        } else {
            tipoCambio = "RENOVACION";
        }

        // Registrar en MEMBRESIAS_HISTORIAL (atómico con @Transactional)
        Long callerUserId = (SaedContextHolder.getContext() != null) ? SaedContextHolder.getContext().getUserId() : null;
        membershipHistoryService.recordChange(
                id,
                idPlan,
                idPlan,
                tipoCambio,
                "Cambio de estado de " + estadoAnterior + " a " + estadoNorm + " por SUPERADMIN",
                callerUserId
        );

        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "id", id,
                "estado", estadoNorm,
                "message", "Estado de membresía actualizado exitosamente"
        )));
    }

    @GetMapping("/{id}/historial")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getMembershipHistory(@PathVariable Long id) {
        List<Map<String, Object>> history = membershipHistoryService.getHistoryForMembership(id);
        return ResponseEntity.ok(ApiResponse.success(history));
    }
}
