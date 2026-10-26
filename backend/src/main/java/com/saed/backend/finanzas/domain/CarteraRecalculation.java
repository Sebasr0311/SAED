package com.saed.backend.finanzas.domain;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Single portfolio (CARTERA) recalculation shared by the manual endpoint and the payment flow
 * (F6-07). It only writes the states CK_CARTERA_ESTADO accepts (AL_DIA, MORA_30, MORA_60,
 * MORA_CRITICA; COBRO_JURIDICO is a manual legal state this recalculation never overwrites) and
 * avoids MERGE, which Oracle rejects on policy-protected tables (ORA-28132).
 *
 * <p>CARTERA keeps one current snapshot per unit: the latest row is updated in place and a row is
 * inserted only for units that have none, so UQ_CARTERA_CORTE(ID_UNIDAD, FECHA_CORTE) is never hit.
 */
public final class CarteraRecalculation {
    private static final String SUMS = """
              NVL(SUM(CASE WHEN c.FECHA_VENCIMIENTO >= TRUNC(SYSDATE) THEN c.SALDO_PENDIENTE ELSE 0 END), 0),
              NVL(SUM(CASE WHEN c.FECHA_VENCIMIENTO < TRUNC(SYSDATE) AND TRUNC(SYSDATE) - c.FECHA_VENCIMIENTO <= 30 THEN c.SALDO_PENDIENTE ELSE 0 END), 0),
              NVL(SUM(CASE WHEN TRUNC(SYSDATE) - c.FECHA_VENCIMIENTO BETWEEN 31 AND 60 THEN c.SALDO_PENDIENTE ELSE 0 END), 0),
              NVL(SUM(CASE WHEN TRUNC(SYSDATE) - c.FECHA_VENCIMIENTO > 60 THEN c.SALDO_PENDIENTE ELSE 0 END), 0)
            """;

    private static final String ESTADO_EXPR = """
              CASE
                WHEN SALDO_MORA_90_MAS > 0 THEN 'MORA_CRITICA'
                WHEN SALDO_MORA_60 > 0 THEN 'MORA_60'
                WHEN SALDO_MORA_30 > 0 THEN 'MORA_30'
                ELSE 'AL_DIA'
              END
            """;

    private CarteraRecalculation() {
    }

    /** Recalculates every unit of a property; returns the number of rows written. */
    public static int recalcularPropiedad(NamedParameterJdbcTemplate jdbc, Long idPropiedad) {
        return run(jdbc, "dest.ID_UNIDAD IN (SELECT ID_UNIDAD FROM UNIDADES WHERE ID_PROPIEDAD = :propId)",
                "u.ID_PROPIEDAD = :propId", new MapSqlParameterSource("propId", idPropiedad));
    }

    /** Recalculates a single unit; returns the number of rows written. */
    public static int recalcularUnidad(NamedParameterJdbcTemplate jdbc, Long idUnidad) {
        return run(jdbc, "dest.ID_UNIDAD = :idUnidad", "u.ID_UNIDAD = :idUnidad",
                new MapSqlParameterSource("idUnidad", idUnidad));
    }

    private static int run(NamedParameterJdbcTemplate jdbc, String destScope, String unitScope,
                           MapSqlParameterSource params) {
        String latest = " AND dest.ID_CARTERA = (SELECT MAX(x.ID_CARTERA) FROM CARTERA x WHERE x.ID_UNIDAD = dest.ID_UNIDAD)";

        int updated = jdbc.update(
                "UPDATE CARTERA dest SET (SALDO_CORRIENTE, SALDO_MORA_30, SALDO_MORA_60, SALDO_MORA_90_MAS) = ("
                        + "SELECT " + SUMS
                        + " FROM CUOTAS c WHERE c.ID_UNIDAD = dest.ID_UNIDAD AND " + OutstandingFees.predicate("c") + ") "
                        + "WHERE " + destScope + latest,
                params);

        jdbc.update(
                "UPDATE CARTERA dest SET FECHA_CORTE = TRUNC(SYSDATE), ESTADO_CARTERA = CASE "
                        + "WHEN dest.ESTADO_CARTERA = 'COBRO_JURIDICO' THEN 'COBRO_JURIDICO' ELSE "
                        + ESTADO_EXPR.replace("SALDO_", "dest.SALDO_") + " END "
                        + "WHERE " + destScope + latest,
                params);

        int inserted = jdbc.update(
                "INSERT INTO CARTERA (ID_UNIDAD, SALDO_CORRIENTE, SALDO_MORA_30, SALDO_MORA_60, SALDO_MORA_90_MAS, FECHA_CORTE, ESTADO_CARTERA) "
                        + "SELECT t.ID_UNIDAD, t.SALDO_CORRIENTE, t.SALDO_MORA_30, t.SALDO_MORA_60, t.SALDO_MORA_90_MAS, TRUNC(SYSDATE), "
                        + ESTADO_EXPR.replace("SALDO_", "t.SALDO_")
                        + " FROM (SELECT u.ID_UNIDAD, "
                        + "NVL(SUM(CASE WHEN c.FECHA_VENCIMIENTO >= TRUNC(SYSDATE) THEN c.SALDO_PENDIENTE ELSE 0 END), 0) AS SALDO_CORRIENTE, "
                        + "NVL(SUM(CASE WHEN c.FECHA_VENCIMIENTO < TRUNC(SYSDATE) AND TRUNC(SYSDATE) - c.FECHA_VENCIMIENTO <= 30 THEN c.SALDO_PENDIENTE ELSE 0 END), 0) AS SALDO_MORA_30, "
                        + "NVL(SUM(CASE WHEN TRUNC(SYSDATE) - c.FECHA_VENCIMIENTO BETWEEN 31 AND 60 THEN c.SALDO_PENDIENTE ELSE 0 END), 0) AS SALDO_MORA_60, "
                        + "NVL(SUM(CASE WHEN TRUNC(SYSDATE) - c.FECHA_VENCIMIENTO > 60 THEN c.SALDO_PENDIENTE ELSE 0 END), 0) AS SALDO_MORA_90_MAS "
                        + "FROM UNIDADES u LEFT JOIN CUOTAS c ON c.ID_UNIDAD = u.ID_UNIDAD AND " + OutstandingFees.predicate("c")
                        + " WHERE " + unitScope
                        + " AND NOT EXISTS (SELECT 1 FROM CARTERA y WHERE y.ID_UNIDAD = u.ID_UNIDAD) "
                        + "GROUP BY u.ID_UNIDAD) t",
                params);
        return updated + inserted;
    }
}
