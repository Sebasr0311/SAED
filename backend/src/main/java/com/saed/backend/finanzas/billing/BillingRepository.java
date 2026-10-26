package com.saed.backend.finanzas.billing;

import com.saed.backend.finanzas.domain.OutstandingFees;
import java.math.BigDecimal;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Persistence of periodic billing. Tenant isolation comes from the caller's database context (VPD) and
 * from the explicit property filter; there is no MERGE (Oracle rejects it on policy-protected tables)
 * and no client clock in SQL: dates arrive as parameters or use SYSDATE.
 */
@Repository
public class BillingRepository {
    private static final String ADMIN_CONCEPT_CODE = "ADMIN_MENSUAL";

    private final NamedParameterJdbcTemplate jdbc;

    public BillingRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<BillingSettings> findSettings(Long idPropiedad) {
        List<BillingSettings> rows = jdbc.query(
                "SELECT ID_PROPIEDAD, CUOTA_FIJA, PORC_DESCUENTO_PRONTO_PAGO, DIAS_PRONTO_PAGO, "
                        + "PORC_INTERES_MORA_MENSUAL, TOPE_INTERES_MORA_MENSUAL, DIAS_GRACIA, DIA_VENCIMIENTO "
                        + "FROM CONFIGURACION_COBRO_PROPIEDAD WHERE ID_PROPIEDAD = :propId",
                new MapSqlParameterSource("propId", idPropiedad),
                (rs, rowNum) -> new BillingSettings(rs.getLong("ID_PROPIEDAD"), rs.getBigDecimal("CUOTA_FIJA"),
                        rs.getBigDecimal("PORC_DESCUENTO_PRONTO_PAGO"), rs.getInt("DIAS_PRONTO_PAGO"),
                        rs.getBigDecimal("PORC_INTERES_MORA_MENSUAL"), rs.getBigDecimal("TOPE_INTERES_MORA_MENSUAL"),
                        rs.getInt("DIAS_GRACIA"), rs.getInt("DIA_VENCIMIENTO")));
        return rows.stream().findFirst();
    }

    /** UPDATE first, INSERT when the property had no settings row yet. */
    public void saveSettings(BillingSettings s) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("propId", s.idPropiedad())
                .addValue("cuotaFija", s.cuotaFija())
                .addValue("pctDescuento", s.porcDescuentoProntoPago())
                .addValue("diasPronto", s.diasProntoPago())
                .addValue("pctInteres", s.porcInteresMoraMensual())
                .addValue("tope", s.topeInteresMoraMensual())
                .addValue("gracia", s.diasGracia())
                .addValue("diaVenc", s.diaVencimiento());
        int updated = jdbc.update(
                "UPDATE CONFIGURACION_COBRO_PROPIEDAD SET CUOTA_FIJA = :cuotaFija, "
                        + "PORC_DESCUENTO_PRONTO_PAGO = :pctDescuento, DIAS_PRONTO_PAGO = :diasPronto, "
                        + "PORC_INTERES_MORA_MENSUAL = :pctInteres, TOPE_INTERES_MORA_MENSUAL = :tope, "
                        + "DIAS_GRACIA = :gracia, DIA_VENCIMIENTO = :diaVenc, FECHA_ACTUALIZACION = SYSDATE "
                        + "WHERE ID_PROPIEDAD = :propId",
                params);
        if (updated == 0) {
            jdbc.update(
                    "INSERT INTO CONFIGURACION_COBRO_PROPIEDAD (ID_PROPIEDAD, CUOTA_FIJA, PORC_DESCUENTO_PRONTO_PAGO, "
                            + "DIAS_PRONTO_PAGO, PORC_INTERES_MORA_MENSUAL, TOPE_INTERES_MORA_MENSUAL, DIAS_GRACIA, "
                            + "DIA_VENCIMIENTO, FECHA_ACTUALIZACION) "
                            + "VALUES (:propId, :cuotaFija, :pctDescuento, :diasPronto, :pctInteres, :tope, :gracia, "
                            + ":diaVenc, SYSDATE)",
                    params);
        }
    }

    /** The administration concept of the property; created on first use without MERGE. */
    public Long ensureAdministrationConcept(Long idPropiedad) {
        MapSqlParameterSource params = new MapSqlParameterSource("propId", idPropiedad)
                .addValue("codigo", ADMIN_CONCEPT_CODE);
        String lookup = "SELECT ID_CONCEPTO FROM CONCEPTOS_COBRO WHERE ID_PROPIEDAD = :propId "
                + "AND TIPO = 'ADMINISTRACION' AND ESTADO = 'ACTIVO' ORDER BY ID_CONCEPTO";
        List<Long> existing = jdbc.queryForList(lookup, params, Long.class);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        jdbc.update(
                "INSERT INTO CONCEPTOS_COBRO (ID_ORGANIZACION, ID_PROPIEDAD, CODIGO, NOMBRE, TIPO, APLICA_MORA, ESTADO) "
                        + "SELECT p.ID_ORGANIZACION, p.ID_PROPIEDAD, :codigo, 'Cuota de administracion', 'ADMINISTRACION', 'S', 'ACTIVO' "
                        + "FROM PROPIEDADES p WHERE p.ID_PROPIEDAD = :propId "
                        + "AND NOT EXISTS (SELECT 1 FROM CONCEPTOS_COBRO c WHERE c.ID_PROPIEDAD = p.ID_PROPIEDAD "
                        + "AND c.CODIGO = :codigo)",
                params);
        existing = jdbc.queryForList(lookup, params, Long.class);
        if (existing.isEmpty()) {
            throw new IllegalStateException("No se pudo resolver el concepto de cobro de administracion");
        }
        return existing.get(0);
    }

    public List<UnitShare> findActiveUnits(Long idPropiedad) {
        return jdbc.query(
                "SELECT ID_UNIDAD, COEFICIENTE_COPROPIEDAD FROM UNIDADES "
                        + "WHERE ID_PROPIEDAD = :propId AND ESTADO IN ('ACTIVA', 'ACTIVO') ORDER BY ID_UNIDAD",
                new MapSqlParameterSource("propId", idPropiedad),
                (rs, rowNum) -> new UnitShare(rs.getLong("ID_UNIDAD"), rs.getBigDecimal("COEFICIENTE_COPROPIEDAD")));
    }

    /** Sum of the approved expense lines of the property for a budget year (0 when none). */
    public BigDecimal approvedAnnualExpenseBudget(Long idPropiedad, int year) {
        BigDecimal total = jdbc.queryForObject(
                "SELECT NVL(SUM(MONTO_PRESUPUESTADO), 0) FROM PRESUPUESTOS "
                        + "WHERE ID_PROPIEDAD = :propId AND VIGENCIA_ANIO = :anio AND TIPO = 'EGRESO' AND ESTADO = 'APROBADO'",
                new MapSqlParameterSource("propId", idPropiedad).addValue("anio", year),
                BigDecimal.class);
        return total == null ? BigDecimal.ZERO : total;
    }

    /**
     * Inserts the fee unless the unit already has one for the concept and period (UQ_CUOTA_UNICA).
     * A concurrent insert that wins the race counts as "already existing".
     */
    public boolean insertFeeIfAbsent(NewFee fee) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idUnidad", fee.idUnidad())
                .addValue("idConcepto", fee.idConcepto())
                .addValue("periodo", fee.periodo())
                .addValue("valorBase", fee.valorBase())
                .addValue("valorDescuento", fee.valorDescuento())
                .addValue("saldo", fee.saldoPendiente())
                .addValue("vencimiento", java.sql.Date.valueOf(fee.fechaVencimiento()), Types.DATE)
                .addValue("fechaDescuento", fee.fechaDescuentoProntoPago() == null ? null
                        : java.sql.Date.valueOf(fee.fechaDescuentoProntoPago()), Types.DATE);
        try {
            return jdbc.update(
                    "INSERT INTO CUOTAS (ID_UNIDAD, ID_CONCEPTO, PERIODO, VALOR_BASE, VALOR_DESCUENTO, SALDO_PENDIENTE, "
                            + "FECHA_VENCIMIENTO, FECHA_DESCUENTO_PRONTO_PAGO, ESTADO) "
                            + "SELECT :idUnidad, :idConcepto, :periodo, :valorBase, :valorDescuento, :saldo, "
                            + ":vencimiento, :fechaDescuento, 'PENDIENTE' FROM DUAL "
                            + "WHERE NOT EXISTS (SELECT 1 FROM CUOTAS WHERE ID_UNIDAD = :idUnidad "
                            + "AND ID_CONCEPTO = :idConcepto AND PERIODO = :periodo)",
                    params) > 0;
        } catch (DuplicateKeyException race) {
            return false;
        }
    }

    /** PENDIENTE fees of the property past their due date become VENCIDA; returns how many moved. */
    public int markOverdue(Long idPropiedad, LocalDate today) {
        return jdbc.update(
                "UPDATE CUOTAS SET ESTADO = 'VENCIDA' WHERE ESTADO = 'PENDIENTE' AND SALDO_PENDIENTE > 0 "
                        + "AND FECHA_VENCIMIENTO < :hoy "
                        + "AND ID_UNIDAD IN (SELECT ID_UNIDAD FROM UNIDADES WHERE ID_PROPIEDAD = :propId)",
                new MapSqlParameterSource("propId", idPropiedad).addValue("hoy", java.sql.Date.valueOf(today), Types.DATE));
    }

    /** Outstanding fees of the property, locked so a concurrent payment cannot be overwritten. */
    public List<OpenFee> findOutstandingFees(Long idPropiedad) {
        return jdbc.query(
                "SELECT c.ID_CUOTA, c.ESTADO, c.VALOR_BASE, c.VALOR_DESCUENTO, c.VALOR_INTERES_MORA, c.SALDO_PENDIENTE, "
                        + "c.FECHA_VENCIMIENTO, c.FECHA_DESCUENTO_PRONTO_PAGO FROM CUOTAS c "
                        + "WHERE " + OutstandingFees.predicate("c") + " "
                        + "AND c.ID_UNIDAD IN (SELECT ID_UNIDAD FROM UNIDADES WHERE ID_PROPIEDAD = :propId) "
                        + "ORDER BY c.ID_CUOTA FOR UPDATE OF c.SALDO_PENDIENTE",
                new MapSqlParameterSource("propId", idPropiedad),
                (rs, rowNum) -> new OpenFee(rs.getLong("ID_CUOTA"), rs.getString("ESTADO"),
                        rs.getBigDecimal("VALOR_BASE"), rs.getBigDecimal("VALOR_DESCUENTO"),
                        rs.getBigDecimal("VALOR_INTERES_MORA"), rs.getBigDecimal("SALDO_PENDIENTE"),
                        rs.getDate("FECHA_VENCIMIENTO") == null ? null : rs.getDate("FECHA_VENCIMIENTO").toLocalDate(),
                        rs.getDate("FECHA_DESCUENTO_PRONTO_PAGO") == null ? null
                                : rs.getDate("FECHA_DESCUENTO_PRONTO_PAGO").toLocalDate()));
    }

    public void applyAdjustment(Long idCuota, BigDecimal newBalance, BigDecimal newDiscount, BigDecimal newInterest) {
        jdbc.update(
                "UPDATE CUOTAS SET SALDO_PENDIENTE = :saldo, VALOR_DESCUENTO = :descuento, VALOR_INTERES_MORA = :interes "
                        + "WHERE ID_CUOTA = :id AND ESTADO IN ('PENDIENTE', 'VENCIDA', 'PAGADA_PARCIAL')",
                new MapSqlParameterSource("id", idCuota).addValue("saldo", newBalance)
                        .addValue("descuento", newDiscount).addValue("interes", newInterest));
    }
}
