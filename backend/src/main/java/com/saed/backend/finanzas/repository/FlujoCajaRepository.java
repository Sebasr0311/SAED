package com.saed.backend.finanzas.repository;

import com.saed.backend.finanzas.domain.OutstandingFees;
import com.saed.backend.finanzas.dto.*;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cash-flow reads. Database failures propagate (F06): a failed query must never be reported to the
 * administrator as a balance of zero or an empty list.
 */
@Repository
public class FlujoCajaRepository {

    private static final String TENANT_FILTER_UNIT = """
                WHERE ((:propId IS NOT NULL AND u.ID_PROPIEDAD = :propId)
                   OR (:propId IS NULL AND :orgId IS NOT NULL AND prop.ID_ORGANIZACION = :orgId))
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public FlujoCajaRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private Map<String, Object> tenantParams() {
        SaedContext ctx = SaedContextHolder.getContext();
        Long orgId = ctx != null ? ctx.getOrganizationId() : null;
        Long propId = ctx != null ? ctx.getPropertyId() : null;
        if (propId == null && orgId == null) {
            throw new org.springframework.security.access.AccessDeniedException(
                "No se ha establecido un contexto de propiedad u organizacion valido para el flujo de caja"
            );
        }
        Map<String, Object> p = new HashMap<>();
        p.put("orgId", orgId);
        p.put("propId", propId);
        return p;
    }

    public BigDecimal getSaldoActual() {
        BigDecimal ingresos = getTotalIngresos();
        BigDecimal egresos = getTotalEgresos();
        return (ingresos != null ? ingresos : BigDecimal.ZERO)
                .subtract(egresos != null ? egresos : BigDecimal.ZERO);
    }

    public BigDecimal getTotalIngresos() {
        String sql = """
            SELECT NVL(SUM(p.MONTO_TOTAL), 0)
            FROM PAGOS p
            JOIN UNIDADES u ON p.ID_UNIDAD = u.ID_UNIDAD
            JOIN PROPIEDADES prop ON u.ID_PROPIEDAD = prop.ID_PROPIEDAD
            """ + TENANT_FILTER_UNIT + """
              AND p.ESTADO = 'APROBADO'
            """;
        return scalar(sql);
    }

    public BigDecimal getTotalEgresos() {
        String sql = """
            SELECT NVL(SUM(g.MONTO), 0)
            FROM GASTOS g
            JOIN PROPIEDADES prop ON g.ID_PROPIEDAD = prop.ID_PROPIEDAD
            WHERE ((:propId IS NOT NULL AND g.ID_PROPIEDAD = :propId)
               OR (:propId IS NULL AND :orgId IS NOT NULL AND prop.ID_ORGANIZACION = :orgId))
              AND g.ESTADO = 'PAGADO'
            """;
        return scalar(sql);
    }

    public BigDecimal getIngresosEsperados() {
        String sql = """
            SELECT NVL(SUM(c.SALDO_PENDIENTE), 0)
            FROM CUOTAS c
            JOIN UNIDADES u ON c.ID_UNIDAD = u.ID_UNIDAD
            JOIN PROPIEDADES prop ON u.ID_PROPIEDAD = prop.ID_PROPIEDAD
            """ + TENANT_FILTER_UNIT + "  AND " + OutstandingFees.predicate("c") + "\n";
        return scalar(sql);
    }

    public BigDecimal getGastosProgramados() {
        String sql = """
            SELECT NVL(SUM(pr.MONTO_PRESUPUESTADO - NVL(pr.MONTO_EJECUTADO, 0)), 0)
            FROM PRESUPUESTOS pr
            JOIN PROPIEDADES prop ON pr.ID_PROPIEDAD = prop.ID_PROPIEDAD
            WHERE ((:propId IS NOT NULL AND pr.ID_PROPIEDAD = :propId)
               OR (:propId IS NULL AND :orgId IS NOT NULL AND prop.ID_ORGANIZACION = :orgId))
              AND pr.TIPO = 'EGRESO'
              AND pr.ESTADO = 'APROBADO'
            """;
        return scalar(sql);
    }

    private BigDecimal scalar(String sql) {
        BigDecimal val = jdbc.queryForObject(sql, tenantParams(), BigDecimal.class);
        return val != null ? val : BigDecimal.ZERO;
    }

    public List<FlujoCajaMovimientoDTO> getMovimientosRecientes(int limite) {
        Map<String, Object> params = tenantParams();
        params.put("limite", limite);

        String sql = """
            SELECT * FROM (
                SELECT p.ID_PAGO AS id, 'INGRESO' AS tipo, 'Pago' AS categoria,
                       'Pago de cuota' AS descripcion, p.MONTO_TOTAL AS monto,
                       TRUNC(COALESCE(p.FECHA_PAGO, SYSDATE)) AS fecha,
                       p.ESTADO AS estado, u.IDENTIFICADOR AS unidad
                FROM PAGOS p
                JOIN UNIDADES u ON p.ID_UNIDAD = u.ID_UNIDAD
                JOIN PROPIEDADES prop ON u.ID_PROPIEDAD = prop.ID_PROPIEDAD
                WHERE ((:propId IS NOT NULL AND u.ID_PROPIEDAD = :propId)
                   OR (:propId IS NULL AND :orgId IS NOT NULL AND prop.ID_ORGANIZACION = :orgId))
                UNION ALL
                SELECT g.ID_GASTO AS id, 'EGRESO' AS tipo, g.CATEGORIA AS categoria,
                       g.BENEFICIARIO AS descripcion, g.MONTO AS monto,
                       TRUNC(COALESCE(g.FECHA_GASTO, SYSDATE)) AS fecha,
                       g.ESTADO AS estado, '-' AS unidad
                FROM GASTOS g
                JOIN PROPIEDADES prop ON g.ID_PROPIEDAD = prop.ID_PROPIEDAD
                WHERE ((:propId IS NOT NULL AND g.ID_PROPIEDAD = :propId)
                   OR (:propId IS NULL AND :orgId IS NOT NULL AND prop.ID_ORGANIZACION = :orgId))
                ORDER BY fecha DESC
            ) WHERE ROWNUM <= :limite
            """;

        return jdbc.query(sql, params, (rs, rowNum) -> mapMovimiento(rs));
    }

    public List<FlujoCajaMovimientoDTO> getProyeccionMensual() {
        Map<String, Object> params = tenantParams();

        String sql = """
            SELECT * FROM (
                SELECT c.ID_CUOTA AS id, 'INGRESO Esperado' AS tipo,
                       co.NOMBRE AS categoria,
                       u.IDENTIFICADOR || ' - ' || co.NOMBRE AS descripcion,
                       c.SALDO_PENDIENTE AS monto,
                       c.FECHA_VENCIMIENTO AS fecha,
                       c.ESTADO AS estado, u.IDENTIFICADOR AS unidad
                FROM CUOTAS c
                JOIN UNIDADES u ON c.ID_UNIDAD = u.ID_UNIDAD
                JOIN CONCEPTOS_COBRO co ON c.ID_CONCEPTO = co.ID_CONCEPTO
                JOIN PROPIEDADES prop ON u.ID_PROPIEDAD = prop.ID_PROPIEDAD
                WHERE ((:propId IS NOT NULL AND u.ID_PROPIEDAD = :propId)
                   OR (:propId IS NULL AND :orgId IS NOT NULL AND prop.ID_ORGANIZACION = :orgId))
                  AND """ + " " + OutstandingFees.predicate("c") + """

                ORDER BY fecha ASC
            ) WHERE ROWNUM <= 20
            """;

        return jdbc.query(sql, params, (rs, rowNum) -> mapMovimiento(rs));
    }

    private static FlujoCajaMovimientoDTO mapMovimiento(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new FlujoCajaMovimientoDTO(
            rs.getLong("id"),
            rs.getString("tipo"),
            rs.getString("categoria"),
            rs.getString("descripcion"),
            rs.getBigDecimal("monto"),
            rs.getDate("fecha") != null ? rs.getDate("fecha").toLocalDate() : null,
            rs.getString("estado"),
            rs.getString("unidad")
        );
    }
}
