package com.saed.backend.finanzas.repository;

import com.saed.backend.finanzas.dto.*;
import com.saed.backend.context.SaedContextHolder;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class FlujoCajaRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public FlujoCajaRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private Map<String, Object> tenantParams() {
        Map<String, Object> p = new HashMap<>();
        p.put("orgId", SaedContextHolder.getContext() != null ? SaedContextHolder.getContext().getOrganizationId() : null);
        p.put("propId", SaedContextHolder.getContext() != null ? SaedContextHolder.getContext().getPropertyId() : null);
        return p;
    }

    public BigDecimal getSaldoActual() {
        try {
            String sql = """
                SELECT NVL(SUM(c.SALDO_PENDIENTE), 0)
                FROM CUOTAS c
                JOIN UNIDADES u ON c.ID_UNIDAD = u.ID_UNIDAD
                WHERE (:propId IS NULL OR u.ID_PROPIEDAD = :propId)
                  AND c.ESTADO IN ('PENDIENTE', 'VENCIDA')
                """;
            BigDecimal val = jdbc.queryForObject(sql, tenantParams(), BigDecimal.class);
            return val != null ? val : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    public BigDecimal getTotalIngresos() {
        try {
            String sql = """
                SELECT NVL(SUM(p.MONTO_TOTAL), 0)
                FROM PAGOS p
                JOIN UNIDADES u ON p.ID_UNIDAD = u.ID_UNIDAD
                WHERE (:propId IS NULL OR u.ID_PROPIEDAD = :propId)
                  AND p.ESTADO IN ('APROBADO', 'CONFIRMADO')
                """;
            BigDecimal val = jdbc.queryForObject(sql, tenantParams(), BigDecimal.class);
            return val != null ? val : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    public BigDecimal getTotalEgresos() {
        try {
            String sql = "SELECT NVL(SUM(monto), 0) FROM gastos WHERE (:propId IS NULL OR id_propiedad = :propId) AND estado != 'ANULADO'";
            BigDecimal val = jdbc.queryForObject(sql, tenantParams(), BigDecimal.class);
            return val != null ? val : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    public BigDecimal getIngresosEsperados() {
        try {
            String sql = """
                SELECT NVL(SUM(c.SALDO_PENDIENTE), 0)
                FROM CUOTAS c
                JOIN UNIDADES u ON c.ID_UNIDAD = u.ID_UNIDAD
                WHERE (:propId IS NULL OR u.ID_PROPIEDAD = :propId)
                  AND c.ESTADO IN ('PENDIENTE', 'VENCIDA')
                """;
            BigDecimal val = jdbc.queryForObject(sql, tenantParams(), BigDecimal.class);
            return val != null ? val : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    public BigDecimal getGastosProgramados() {
        try {
            String sql = "SELECT NVL(SUM(monto_presupuestado - NVL(monto_ejecutado, 0)), 0) FROM presupuestos " +
                         "WHERE (:propId IS NULL OR id_propiedad = :propId) AND tipo = 'EGRESO'";
            BigDecimal val = jdbc.queryForObject(sql, tenantParams(), BigDecimal.class);
            return val != null ? val : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    public List<FlujoCajaMovimientoDTO> getMovimientosRecientes(int limite) {
        String sql = """
            SELECT * FROM (
                SELECT p.ID_PAGO AS id, 'INGRESO' AS tipo, 'Pago' AS categoria,
                       'Pago de cuota' AS descripcion, p.MONTO_TOTAL AS monto,
                       TRUNC(COALESCE(p.FECHA_CREACION, SYSDATE)) AS fecha,
                       p.ESTADO AS estado, u.IDENTIFICADOR AS unidad
                FROM PAGOS p
                JOIN UNIDADES u ON p.ID_UNIDAD = u.ID_UNIDAD
                WHERE (:propId IS NULL OR u.ID_PROPIEDAD = :propId)
                ORDER BY fecha DESC
            ) WHERE ROWNUM <= :limite
            """;

        Map<String, Object> params = tenantParams();
        params.put("limite", limite);

        try {
            return jdbc.query(sql, params, (rs, rowNum) -> new FlujoCajaMovimientoDTO(
                rs.getLong("id"),
                rs.getString("tipo"),
                rs.getString("categoria"),
                rs.getString("descripcion"),
                rs.getBigDecimal("monto"),
                rs.getDate("fecha") != null ? rs.getDate("fecha").toLocalDate() : null,
                rs.getString("estado"),
                rs.getString("unidad")
            ));
        } catch (Exception e) {
            return List.of();
        }
    }

    public List<FlujoCajaMovimientoDTO> getProyeccionMensual() {
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
                WHERE (:propId IS NULL OR u.ID_PROPIEDAD = :propId)
                  AND c.ESTADO IN ('PENDIENTE', 'VENCIDA')
                ORDER BY fecha ASC
            ) WHERE ROWNUM <= 20
            """;

        try {
            return jdbc.query(sql, tenantParams(), (rs, rowNum) -> new FlujoCajaMovimientoDTO(
                rs.getLong("id"),
                rs.getString("tipo"),
                rs.getString("categoria"),
                rs.getString("descripcion"),
                rs.getBigDecimal("monto"),
                rs.getDate("fecha") != null ? rs.getDate("fecha").toLocalDate() : null,
                rs.getString("estado"),
                rs.getString("unidad")
            ));
        } catch (Exception e) {
            return List.of();
        }
    }
}
