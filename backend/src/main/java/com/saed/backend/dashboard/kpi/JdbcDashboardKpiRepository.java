package com.saed.backend.dashboard.kpi;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/** Reads KPI source rows; every query is filtered by the server-derived scope (organization/property/unit). */
@Repository
public class JdbcDashboardKpiRepository implements DashboardKpiRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcDashboardKpiRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Scope predicate over aliases {@code u} (UNIDADES) and {@code p} (PROPIEDADES). */
    private static String unitScope(KpiScope scope, MapSqlParameterSource params) {
        params.addValue("orgId", scope.organizationId()).addValue("propId", scope.propertyId())
              .addValue("unitId", scope.unitId());
        if (scope.unitId() != null) {
            return " AND u.ID_UNIDAD = :unitId AND u.ID_PROPIEDAD = :propId AND p.ID_ORGANIZACION = :orgId ";
        }
        if (scope.propertyId() != null) {
            return " AND u.ID_PROPIEDAD = :propId AND p.ID_ORGANIZACION = :orgId ";
        }
        return " AND p.ID_ORGANIZACION = :orgId ";
    }

    @Override
    public List<FeeRow> fees(KpiScope scope) {
        MapSqlParameterSource params = new MapSqlParameterSource("desde", YearMonth.now().minusMonths(11).toString());
        String sql = "SELECT c.ID_UNIDAD, c.ESTADO, c.VALOR_BASE, c.SALDO_PENDIENTE, c.FECHA_VENCIMIENTO "
                + "FROM CUOTAS c JOIN UNIDADES u ON c.ID_UNIDAD = u.ID_UNIDAD "
                + "JOIN PROPIEDADES p ON u.ID_PROPIEDAD = p.ID_PROPIEDAD "
                + "WHERE c.ESTADO <> 'ANULADA' AND (c.PERIODO >= :desde OR c.SALDO_PENDIENTE > 0)"
                + unitScope(scope, params);
        return jdbc.query(sql, params, (rs, i) -> {
            Date due = rs.getDate("FECHA_VENCIMIENTO");
            return new FeeRow(rs.getLong("ID_UNIDAD"), rs.getString("ESTADO"), rs.getBigDecimal("VALOR_BASE"),
                    rs.getBigDecimal("SALDO_PENDIENTE"), due != null ? due.toLocalDate() : null);
        });
    }

    @Override
    public long countUnits(KpiScope scope) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String sql = "SELECT COUNT(*) FROM UNIDADES u JOIN PROPIEDADES p ON u.ID_PROPIEDAD = p.ID_PROPIEDAD "
                + "WHERE u.ESTADO <> 'ELIMINADA'" + unitScope(scope, params);
        Long count = jdbc.queryForObject(sql, params, Long.class);
        return count != null ? count : 0L;
    }

    @Override
    public List<TicketRow> openTickets(KpiScope scope) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("orgId", scope.organizationId()).addValue("propId", scope.propertyId())
                .addValue("unitId", scope.unitId());
        String predicate;
        if (scope.unitId() != null) {
            predicate = " AND t.ID_UNIDAD = :unitId AND t.ID_PROPIEDAD = :propId AND p.ID_ORGANIZACION = :orgId";
        } else if (scope.propertyId() != null) {
            predicate = " AND t.ID_PROPIEDAD = :propId AND p.ID_ORGANIZACION = :orgId";
        } else {
            predicate = " AND p.ID_ORGANIZACION = :orgId";
        }
        String sql = "SELECT t.PRIORIDAD, t.ESTADO, t.FECHA_RADICACION FROM PQRS_TICKETS t "
                + "JOIN PROPIEDADES p ON t.ID_PROPIEDAD = p.ID_PROPIEDAD "
                + "WHERE t.ESTADO IN ('RADICADO', 'ASIGNADO', 'EN_GESTION', 'ESCALADO')" + predicate;
        return jdbc.query(sql, params, (rs, i) -> {
            Timestamp radicado = rs.getTimestamp("FECHA_RADICACION");
            return new TicketRow(rs.getString("PRIORIDAD"), rs.getString("ESTADO"), radicado.toInstant());
        });
    }

    @Override
    public List<ReservationRow> reservations(KpiScope scope, LocalDate from) {
        MapSqlParameterSource params = new MapSqlParameterSource("desde", Date.valueOf(from));
        String sql = "SELECT r.ESTADO, r.FECHA_RESERVA FROM RESERVAS r "
                + "JOIN UNIDADES u ON r.ID_UNIDAD = u.ID_UNIDAD JOIN PROPIEDADES p ON u.ID_PROPIEDAD = p.ID_PROPIEDAD "
                + "WHERE (r.ESTADO = 'PENDIENTE' OR r.FECHA_RESERVA >= :desde)" + unitScope(scope, params);
        return jdbc.query(sql, params, (rs, i) -> {
            Date fecha = rs.getDate("FECHA_RESERVA");
            return new ReservationRow(rs.getString("ESTADO"), fecha != null ? fecha.toLocalDate() : null);
        });
    }

    @Override
    public List<MembershipRow> memberships() {
        return jdbc.query("SELECT ID_ORGANIZACION, ESTADO, FECHA_INICIO, FECHA_FIN FROM MEMBRESIAS",
                new MapSqlParameterSource(), (rs, i) -> {
                    Date inicio = rs.getDate("FECHA_INICIO");
                    Date fin = rs.getDate("FECHA_FIN");
                    return new MembershipRow(rs.getLong("ID_ORGANIZACION"), rs.getString("ESTADO"),
                            inicio != null ? inicio.toLocalDate() : null, fin != null ? fin.toLocalDate() : null);
                });
    }

    @Override
    public PlatformKpis platformCounts() {
        String sql = """
            SELECT
                (SELECT COUNT(*) FROM ORGANIZACIONES) AS ORG_TOTAL,
                (SELECT COUNT(*) FROM ORGANIZACIONES WHERE ESTADO = 'ACTIVA') AS ORG_ACTIVAS,
                (SELECT COUNT(*) FROM PROPIEDADES) AS PROP_TOTAL,
                (SELECT COUNT(*) FROM MEMBRESIAS WHERE ESTADO = 'ACTIVA') AS MEMB_ACTIVAS,
                (SELECT COUNT(*) FROM MEMBRESIAS WHERE ESTADO = 'ACTIVA' AND FECHA_FIN >= TRUNC(SYSDATE)
                    AND FECHA_FIN <= TRUNC(SYSDATE) + 30) AS MEMB_POR_VENCER,
                (SELECT COUNT(*) FROM PLANES WHERE ESTADO = 'ACTIVO') AS PLANES_ACTIVOS,
                (SELECT NVL(SUM(pl.PRECIO_MENSUAL), 0) FROM MEMBRESIAS m JOIN PLANES pl ON m.ID_PLAN = pl.ID_PLAN
                    WHERE m.ESTADO = 'ACTIVA') AS MRR
            FROM DUAL
            """;
        Map<String, Object> row = jdbc.queryForMap(sql, new MapSqlParameterSource());
        return new PlatformKpis(num(row, "ORG_TOTAL"), num(row, "ORG_ACTIVAS"), num(row, "PROP_TOTAL"),
                num(row, "MEMB_ACTIVAS"), num(row, "MEMB_POR_VENCER"), num(row, "PLANES_ACTIVOS"),
                row.get("MRR") instanceof BigDecimal mrr ? mrr : new BigDecimal(String.valueOf(row.get("MRR"))), null);
    }

    private static long num(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }
}
