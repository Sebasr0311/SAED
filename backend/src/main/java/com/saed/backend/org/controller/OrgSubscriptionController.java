package com.saed.backend.org.controller;

import com.saed.backend.common.dto.ApiResponse;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.org.dto.OrgSubscriptionDTO;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

@Tag(name = "Organization Subscription", description = "Consulta del plan SaaS, membresía y límites contratados por la Organización")
@RestController
@RequestMapping("/api/v1/org/subscription")
@PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_ORGANIZACION', 'SCOPE_SUPERADMIN')")
public class OrgSubscriptionController {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public OrgSubscriptionController(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ApiResponse<OrgSubscriptionDTO> getSubscription() {
        SaedContext ctx = SaedContextHolder.getContext();
        Long orgId = ctx.getOrganizationId();
        if (orgId == null) {
            throw new AccessDeniedException("No se encontró contexto de organización activo");
        }

        String sql = """
            SELECT m.id_membresia, m.id_plan, m.estado AS membresia_estado,
                   m.fecha_inicio, m.fecha_fin, m.es_prueba,
                   p.codigo AS plan_codigo, p.nombre AS plan_nombre, p.descripcion AS plan_descripcion,
                   p.precio_mensual AS precio_mensual_cop,
                   p.limite_propiedades, p.limite_unidades, p.limite_usuarios,
                   p.limite_almacenamiento_gb,
                   (SELECT COUNT(*) FROM PROPIEDADES pr WHERE pr.id_organizacion = :orgId AND pr.estado = 'ACTIVA') AS propiedades_usadas,
                   (SELECT COUNT(*) FROM UNIDADES u JOIN PROPIEDADES pr ON u.id_propiedad = pr.id_propiedad WHERE pr.id_organizacion = :orgId) AS unidades_usadas,
                   (SELECT COUNT(DISTINCT ua.id_usuario) FROM USUARIO_ASIGNACIONES ua WHERE ua.id_organizacion = :orgId AND ua.estado = 'ACTIVA') AS usuarios_usados
            FROM MEMBRESIAS m
            JOIN PLANES p ON m.id_plan = p.id_plan
            WHERE m.id_organizacion = :orgId AND m.estado IN ('ACTIVA', 'PRUEBA')
            ORDER BY m.fecha_inicio DESC
            FETCH FIRST 1 ROWS ONLY
        """;

        List<OrgSubscriptionDTO> results = jdbcTemplate.query(sql, new MapSqlParameterSource("orgId", orgId), this::mapRow);
        if (results.isEmpty()) {
            // Fallback si la membresia esta suspendida o inactiva
            String fallbackSql = """
                SELECT m.id_membresia, m.id_plan, m.estado AS membresia_estado,
                       m.fecha_inicio, m.fecha_fin, m.es_prueba,
                       p.codigo AS plan_codigo, p.nombre AS plan_nombre, p.descripcion AS plan_descripcion,
                       p.precio_mensual AS precio_mensual_cop,
                       p.limite_propiedades, p.limite_unidades, p.limite_usuarios,
                       p.limite_almacenamiento_gb,
                       (SELECT COUNT(*) FROM PROPIEDADES pr WHERE pr.id_organizacion = :orgId AND pr.estado = 'ACTIVA') AS propiedades_usadas,
                       (SELECT COUNT(*) FROM UNIDADES u JOIN PROPIEDADES pr ON u.id_propiedad = pr.id_propiedad WHERE pr.id_organizacion = :orgId) AS unidades_usadas,
                       (SELECT COUNT(DISTINCT ua.id_usuario) FROM USUARIO_ASIGNACIONES ua WHERE ua.id_organizacion = :orgId AND ua.estado = 'ACTIVA') AS usuarios_usados
                FROM MEMBRESIAS m
                JOIN PLANES p ON m.id_plan = p.id_plan
                WHERE m.id_organizacion = :orgId
                ORDER BY m.fecha_inicio DESC
                FETCH FIRST 1 ROWS ONLY
            """;
            results = jdbcTemplate.query(fallbackSql, new MapSqlParameterSource("orgId", orgId), this::mapRow);
        }

        if (results.isEmpty()) {
            throw new java.util.NoSuchElementException("No se encontró suscripción activa para la organización");
        }

        return ApiResponse.success(results.get(0));
    }

    private OrgSubscriptionDTO mapRow(ResultSet rs, int rowNum) throws SQLException {
        OrgSubscriptionDTO dto = new OrgSubscriptionDTO();
        dto.setIdMembresia(rs.getLong("id_membresia"));
        dto.setIdPlan(rs.getLong("id_plan"));
        dto.setPlanCodigo(rs.getString("plan_codigo"));
        dto.setPlanNombre(rs.getString("plan_nombre"));
        dto.setPlanDescripcion(rs.getString("plan_descripcion"));
        dto.setPrecioMensualCop(rs.getDouble("precio_mensual_cop"));
        dto.setMembresiaEstado(rs.getString("membresia_estado"));

        Timestamp tsInicio = rs.getTimestamp("fecha_inicio");
        if (tsInicio != null) dto.setFechaInicio(tsInicio.toInstant().atZone(ZoneId.of("America/Bogota")));

        Timestamp tsFin = rs.getTimestamp("fecha_fin");
        if (tsFin != null) dto.setFechaFin(tsFin.toInstant().atZone(ZoneId.of("America/Bogota")));

        String esPrueba = rs.getString("es_prueba");
        dto.setTipoPeriodo("S".equalsIgnoreCase(esPrueba) ? "PRUEBA" : "MENSUAL");

        long limProp = rs.getLong("limite_propiedades");
        long usedProp = rs.getLong("propiedades_usadas");
        dto.setLimitePropiedades(limProp);
        dto.setPropiedadesUsadas(usedProp);
        dto.setPorcentajePropiedades(limProp > 0 ? Math.min(100.0, Math.round((double) usedProp / limProp * 1000.0) / 10.0) : 0.0);

        long limUni = rs.getLong("limite_unidades");
        long usedUni = rs.getLong("unidades_usadas");
        dto.setLimiteUnidades(limUni);
        dto.setUnidadesUsadas(usedUni);
        dto.setPorcentajeUnidades(limUni > 0 ? Math.min(100.0, Math.round((double) usedUni / limUni * 1000.0) / 10.0) : 0.0);

        long limUsr = rs.getLong("limite_usuarios");
        long usedUsr = rs.getLong("usuarios_usados");
        dto.setLimiteUsuarios(limUsr);
        dto.setUsuariosUsados(usedUsr);
        dto.setPorcentajeUsuarios(limUsr > 0 ? Math.min(100.0, Math.round((double) usedUsr / limUsr * 1000.0) / 10.0) : 0.0);

        dto.setLimiteAlmacenamientoGb(rs.getLong("limite_almacenamiento_gb"));

        return dto;
    }
}
