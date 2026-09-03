package com.saed.backend.org.controller;

import com.saed.backend.common.dto.ApiResponse;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.org.dto.OrgDashboardDTO;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "Organization Dashboard", description = "KPIs ejecutivos y métricas consolidadas para la Organización cliente")
@RestController
@RequestMapping("/api/v1/org/dashboard")
@PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_ORGANIZACION', 'SCOPE_SUPERADMIN')")
public class OrgDashboardController {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public OrgDashboardController(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ApiResponse<OrgDashboardDTO> getDashboard() {
        SaedContext ctx = SaedContextHolder.getContext();
        Long orgId = ctx.getOrganizationId();
        if (orgId == null) {
            throw new AccessDeniedException("No se encontró contexto de organización activo");
        }

        MapSqlParameterSource params = new MapSqlParameterSource("orgId", orgId);

        // 1. Propiedades
        Map<String, Object> propStats = new HashMap<>();
        try {
            Number totalProp = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM PROPIEDADES WHERE id_organizacion = :orgId", params, Number.class);
            Number activasProp = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM PROPIEDADES WHERE id_organizacion = :orgId AND estado = 'ACTIVA'", params, Number.class);
            long total = totalProp != null ? totalProp.longValue() : 0L;
            long activas = activasProp != null ? activasProp.longValue() : 0L;
            propStats.put("total", total);
            propStats.put("activas", activas);
            propStats.put("inactivas", total - activas);
        } catch (Exception e) {
            propStats.put("total", 0);
            propStats.put("activas", 0);
            propStats.put("inactivas", 0);
        }

        // 2. Unidades
        Map<String, Object> unitStats = new HashMap<>();
        try {
            Number totalUnidades = jdbcTemplate.queryForObject(
                "SELECT COUNT(u.id_unidad) FROM UNIDADES u JOIN PROPIEDADES p ON u.id_propiedad = p.id_propiedad WHERE p.id_organizacion = :orgId",
                params, Number.class);
            unitStats.put("total", totalUnidades != null ? totalUnidades.longValue() : 0L);
        } catch (Exception e) {
            unitStats.put("total", 0);
        }

        // 3. Administradores de Propiedad
        Map<String, Object> adminStats = new HashMap<>();
        try {
            Number totalAdmins = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT ua.id_usuario)
                FROM USUARIO_ASIGNACIONES ua
                JOIN ROLES r ON ua.id_rol = r.id_rol
                WHERE ua.id_organizacion = :orgId
                  AND r.codigo IN ('ADMIN_PROPIEDAD', 'ADMIN_ORGANIZACION')
                  AND ua.estado = 'ACTIVA'
            """, params, Number.class);
            adminStats.put("activos", totalAdmins != null ? totalAdmins.longValue() : 0L);
        } catch (Exception e) {
            adminStats.put("activos", 0);
        }

        // 4. Usuarios de la Organización
        Map<String, Object> userStats = new HashMap<>();
        try {
            Number totalUsers = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT id_usuario) FROM USUARIO_ASIGNACIONES WHERE id_organizacion = :orgId AND estado = 'ACTIVA'",
                params, Number.class);
            userStats.put("total", totalUsers != null ? totalUsers.longValue() : 0L);
        } catch (Exception e) {
            userStats.put("total", 0);
        }

        // 5. Suscripción y Límites
        Map<String, Object> suscripcionStats = new HashMap<>();
        try {
            String planSql = """
                SELECT p.codigo AS plan_codigo, p.nombre AS plan_nombre, m.estado AS membresia_estado,
                       m.fecha_fin, p.limite_propiedades, p.limite_unidades, p.limite_usuarios
                FROM MEMBRESIAS m
                JOIN PLANES p ON m.id_plan = p.id_plan
                WHERE m.id_organizacion = :orgId
                ORDER BY m.fecha_inicio DESC
                FETCH FIRST 1 ROWS ONLY
            """;
            List<Map<String, Object>> planRows = jdbcTemplate.queryForList(planSql, params);
            if (!planRows.isEmpty()) {
                Map<String, Object> row = planRows.get(0);
                suscripcionStats.put("planNombre", row.get("PLAN_NOMBRE"));
                suscripcionStats.put("planCodigo", row.get("PLAN_CODIGO"));
                suscripcionStats.put("estado", row.get("MEMBRESIA_ESTADO"));
                suscripcionStats.put("fechaFin", row.get("FECHA_FIN"));

                long limProp = row.get("LIMITE_PROPIEDADES") != null ? ((Number) row.get("LIMITE_PROPIEDADES")).longValue() : 0L;
                long limUni = row.get("LIMITE_UNIDADES") != null ? ((Number) row.get("LIMITE_UNIDADES")).longValue() : 0L;
                long limUsr = row.get("LIMITE_USUARIOS") != null ? ((Number) row.get("LIMITE_USUARIOS")).longValue() : 0L;

                long usedProp = ((Number) propStats.getOrDefault("activas", 0)).longValue();
                long usedUni = ((Number) unitStats.getOrDefault("total", 0)).longValue();
                long usedUsr = ((Number) userStats.getOrDefault("total", 0)).longValue();

                suscripcionStats.put("limitePropiedades", limProp);
                suscripcionStats.put("consumoPropiedades", usedProp);
                suscripcionStats.put("porcentajePropiedades", limProp > 0 ? Math.min(100.0, Math.round((double) usedProp / limProp * 1000.0) / 10.0) : 0.0);

                suscripcionStats.put("limiteUnidades", limUni);
                suscripcionStats.put("consumoUnidades", usedUni);
                suscripcionStats.put("porcentajeUnidades", limUni > 0 ? Math.min(100.0, Math.round((double) usedUni / limUni * 1000.0) / 10.0) : 0.0);

                suscripcionStats.put("limiteUsuarios", limUsr);
                suscripcionStats.put("consumoUsuarios", usedUsr);
                suscripcionStats.put("porcentajeUsuarios", limUsr > 0 ? Math.min(100.0, Math.round((double) usedUsr / limUsr * 1000.0) / 10.0) : 0.0);
            }
        } catch (Exception e) {
            suscripcionStats.put("estado", "INDEFINIDO");
        }

        // 6. Finanzas Agregadas (BD-03)
        Map<String, Object> finanzasStats = new HashMap<>();
        try {
            Number totalPagos = jdbcTemplate.queryForObject("""
                SELECT NVL(SUM(pg.monto), 0)
                FROM PAGOS pg
                JOIN UNIDADES u ON pg.id_unidad = u.id_unidad
                JOIN PROPIEDADES p ON u.id_propiedad = p.id_propiedad
                WHERE p.id_organizacion = :orgId AND pg.estado = 'APROBADO'
            """, params, Number.class);

            Number totalCartera = jdbcTemplate.queryForObject("""
                SELECT NVL(SUM(c.saldo_pendiente), 0)
                FROM CARTERA c
                JOIN UNIDADES u ON c.id_unidad = u.id_unidad
                JOIN PROPIEDADES p ON u.id_propiedad = p.id_propiedad
                WHERE p.id_organizacion = :orgId AND c.estado IN ('PENDIENTE', 'VENCIDA', 'EN_MORA')
            """, params, Number.class);

            finanzasStats.put("totalRecaudado", totalPagos != null ? totalPagos.doubleValue() : 0.0);
            finanzasStats.put("carteraPendiente", totalCartera != null ? totalCartera.doubleValue() : 0.0);
        } catch (Exception e) {
            finanzasStats.put("totalRecaudado", 0.0);
            finanzasStats.put("carteraPendiente", 0.0);
        }

        // 7. Propiedades Recientes
        List<Map<String, Object>> recientes = List.of();
        try {
            String sqlRecientes = """
                SELECT p.id_propiedad, p.nombre, p.direccion, p.ciudad, p.estado,
                       p.tipo_ocupacion_predominante,
                       tp.nombre AS tipo_propiedad_nombre,
                       (SELECT COUNT(*) FROM UNIDADES u WHERE u.id_propiedad = p.id_propiedad) AS total_unidades,
                       (SELECT COUNT(DISTINCT ua.id_usuario) FROM USUARIO_ASIGNACIONES ua WHERE ua.id_propiedad = p.id_propiedad AND ua.estado = 'ACTIVA') AS total_admins
                FROM PROPIEDADES p
                LEFT JOIN TIPOS_PROPIEDAD tp ON p.id_tipo_propiedad = tp.id_tipo_propiedad
                WHERE p.id_organizacion = :orgId
                ORDER BY p.id_propiedad DESC
                FETCH FIRST 5 ROWS ONLY
            """;
            recientes = jdbcTemplate.queryForList(sqlRecientes, params);
        } catch (Exception ignored) {}

        return ApiResponse.success(new OrgDashboardDTO(
                propStats, unitStats, adminStats, userStats, suscripcionStats, finanzasStats, recientes
        ));
    }
}
