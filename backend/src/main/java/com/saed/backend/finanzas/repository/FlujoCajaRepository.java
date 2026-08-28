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
        p.put("orgId", SaedContextHolder.getContext().getOrganizationId());
        p.put("propId", SaedContextHolder.getContext().getPropertyId());
        return p;
    }

    public BigDecimal getSaldoActual() {
        String sql = "SELECT NVL(SUM(saldo_total), 0) FROM cartera WHERE id_organizacion = :orgId AND id_propiedad = :propId";
        BigDecimal val = jdbc.queryForObject(sql, tenantParams(), BigDecimal.class);
        return val != null ? val : BigDecimal.ZERO;
    }

    public BigDecimal getTotalIngresos() {
        String sql = "SELECT NVL(SUM(monto), 0) FROM pagos p JOIN unidades u ON p.id_unidad = u.id_unidad " +
                     "WHERE u.id_organizacion = :orgId AND u.id_propiedad = :propId AND p.estado = 'CONFIRMADO'";
        BigDecimal val = jdbc.queryForObject(sql, tenantParams(), BigDecimal.class);
        return val != null ? val : BigDecimal.ZERO;
    }

    public BigDecimal getTotalEgresos() {
        String sql = "SELECT NVL(SUM(monto), 0) FROM gastos WHERE id_organizacion = :orgId AND id_propiedad = :propId AND estado != 'ANULADO'";
        BigDecimal val = jdbc.queryForObject(sql, tenantParams(), BigDecimal.class);
        return val != null ? val : BigDecimal.ZERO;
    }

    public BigDecimal getIngresosEsperados() {
        String sql = "SELECT NVL(SUM(c.monto_cuota - NVL(c.monto_pago, 0)), 0) FROM cuotas c " +
                     "JOIN unidades u ON c.id_unidad = u.id_unidad " +
                     "WHERE u.id_organizacion = :orgId AND u.id_propiedad = :propId AND c.estado IN ('PENDIENTE', 'VENCIDA')";
        BigDecimal val = jdbc.queryForObject(sql, tenantParams(), BigDecimal.class);
        return val != null ? val : BigDecimal.ZERO;
    }

    public BigDecimal getGastosProgramados() {
        String sql = "SELECT NVL(SUM(monto_presupuestado - NVL(monto_ejecutado, 0)), 0) FROM presupuestos " +
                     "WHERE id_organizacion = :orgId AND id_propiedad = :propId AND tipo = 'EGRESO'";
        BigDecimal val = jdbc.queryForObject(sql, tenantParams(), BigDecimal.class);
        return val != null ? val : BigDecimal.ZERO;
    }

    public List<FlujoCajaMovimientoDTO> getMovimientosRecientes(int limite) {
        String sql = "SELECT * FROM (" +
            "SELECT p.id_pago AS id, 'INGRESO' AS tipo, 'Pago' AS categoria, " +
            "       'Pago de cuota' AS descripcion, p.monto, TRUNC(p.fecha_pago) AS fecha, " +
            "       p.estado, u.identificador AS unidad " +
            "FROM pagos p JOIN unidades u ON p.id_unidad = u.id_unidad " +
            "WHERE u.id_organizacion = :orgId AND u.id_propiedad = :propId " +
            "UNION ALL " +
            "SELECT g.id_gasto AS id, 'EGRESO' AS tipo, g.categoria, " +
            "       g.beneficiario AS descripcion, g.monto, TRUNC(g.fecha_gasto) AS fecha, " +
            "       g.estado, NULL AS unidad " +
            "FROM gastos g " +
            "WHERE g.id_organizacion = :orgId AND g.id_propiedad = :propId AND g.estado != 'ANULADO' " +
            "ORDER BY fecha DESC" +
            ") WHERE ROWNUM <= :limite";

        Map<String, Object> params = tenantParams();
        params.put("limite", limite);

        return jdbc.query(sql, params, (rs, rowNum) -> new FlujoCajaMovimientoDTO(
            rs.getLong("ID"),
            rs.getString("TIPO"),
            rs.getString("CATEGORIA"),
            rs.getString("DESCRIPCION"),
            rs.getBigDecimal("MONTO"),
            rs.getDate("FECHA") != null ? rs.getDate("FECHA").toLocalDate() : null,
            rs.getString("ESTADO"),
            rs.getString("UNIDAD")
        ));
    }

    public List<FlujoCajaMovimientoDTO> getProyeccionMensual() {
        String sql =
            "SELECT * FROM (" +
            "SELECT c.id_cuota AS id, 'INGRESO Esperado' AS tipo, " +
            "       'Cuota' AS categoria, " +
            "       u.identificador || ' - ' || COALESCE(c.descripcion, 'Cuota mensual') AS descripcion, " +
            "       (c.monto_cuota - NVL(c.monto_pago, 0)) AS monto, " +
            "       c.fecha_vencimiento AS fecha, " +
            "       c.estado, u.identificador AS unidad " +
            "FROM cuotas c JOIN unidades u ON c.id_unidad = u.id_unidad " +
            "WHERE u.id_organizacion = :orgId AND u.id_propiedad = :propId " +
            "  AND c.estado IN ('PENDIENTE', 'VENCIDA') " +
            "UNION ALL " +
            "SELECT pr.id_presupuesto AS id, 'EGRESO Programado' AS tipo, " +
            "       pr.rubro AS categoria, " +
            "       pr.rubro || ' - ' || COALESCE(pr.observaciones, 'Presupuesto') AS descripcion, " +
            "       (pr.monto_presupuestado - NVL(pr.monto_ejecutado, 0)) AS monto, " +
            "       TRUNC(SYSDATE) AS fecha, " +
            "       'PENDIENTE' AS estado, NULL AS unidad " +
            "FROM presupuestos pr " +
            "WHERE pr.id_organizacion = :orgId AND pr.id_propiedad = :propId " +
            "  AND pr.tipo = 'EGRESO' " +
            "  AND NVL(pr.monto_ejecutado, 0) < pr.monto_presupuestado " +
            "ORDER BY fecha ASC" +
            ") WHERE ROWNUM <= 20";

        return jdbc.query(sql, tenantParams(), (rs, rowNum) -> new FlujoCajaMovimientoDTO(
            rs.getLong("ID"),
            rs.getString("TIPO"),
            rs.getString("CATEGORIA"),
            rs.getString("DESCRIPCION"),
            rs.getBigDecimal("MONTO"),
            rs.getDate("FECHA") != null ? rs.getDate("FECHA").toLocalDate() : null,
            rs.getString("ESTADO"),
            rs.getString("UNIDAD")
        ));
    }
}
