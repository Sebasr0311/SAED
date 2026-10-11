package com.saed.backend.finanzas.service;

import com.saed.backend.sanciones.integration.FineReceivablesPort;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Finance side of the sanctions integration (F10-06): posts the fine of a resolved sanction to the
 * unit's receivables (CUOTAS) so it shows in the balance, the aging and the paz y salvo.
 *
 * <p>Idempotent per fine: the MULTAS row is locked and carries the link (ID_CUOTA); CUOTAS.ID_MULTA_ORIGEN
 * (unique per fine through UQ_CUOTA_UNICA_ORIGEN) guards the insert, so a retry, a replay or a crash
 * between the insert and the link finds the same receivable and never creates a second charge. Runs in the
 * caller's transaction and tenant context. Once posted, the fine is collected only through its fee: the
 * direct fine payment paths refuse a fine that already has one.
 */
@Component
public class FineReceivablesAdapter implements FineReceivablesPort {
    private static final Set<String> COLLECTABLE = Set.of("IMPUESTA", "EN_DESCARGOS", "RATIFICADA");
    private static final int DESCRIPTION_LIMIT = 300;

    private final NamedParameterJdbcTemplate jdbc;

    public FineReceivablesAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Long> postFineReceivable(FineReceivable fine) {
        if (fine == null || fine.idMulta() == null || fine.idUnidad() == null || fine.idConcepto() == null) {
            throw new IllegalArgumentException("La multa a cobrar no esta completa");
        }
        if (fine.monto() == null || fine.monto().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto de la multa debe ser mayor a cero");
        }

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT ID_UNIDAD, ID_CUOTA, ESTADO FROM MULTAS WHERE ID_MULTA = :id FOR UPDATE",
                new MapSqlParameterSource("id", fine.idMulta()));
        if (rows.isEmpty() || rows.get(0).get("ID_UNIDAD") == null
                || ((Number) rows.get(0).get("ID_UNIDAD")).longValue() != fine.idUnidad()) {
            throw new NoSuchElementException("Multa no encontrada: " + fine.idMulta());
        }
        Map<String, Object> row = rows.get(0);
        if (row.get("ID_CUOTA") != null) {
            return Optional.of(((Number) row.get("ID_CUOTA")).longValue());
        }
        if (!COLLECTABLE.contains(String.valueOf(row.get("ESTADO")))) {
            throw new IllegalStateException("La multa " + fine.idMulta() + " no es exigible en estado " + row.get("ESTADO"));
        }

        String description = fine.descripcion() == null ? "Multa" : fine.descripcion();
        if (description.length() > DESCRIPTION_LIMIT) {
            description = description.substring(0, DESCRIPTION_LIMIT);
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idUnidad", fine.idUnidad())
                .addValue("idConcepto", fine.idConcepto())
                .addValue("monto", fine.monto())
                .addValue("descripcion", description)
                .addValue("idMulta", fine.idMulta());
        jdbc.update(
                "INSERT INTO CUOTAS (ID_UNIDAD, ID_CONCEPTO, PERIODO, VALOR_BASE, SALDO_PENDIENTE, FECHA_VENCIMIENTO, "
                        + "ESTADO, DESCRIPCION, ID_MULTA_ORIGEN) "
                        + "SELECT :idUnidad, :idConcepto, TO_CHAR(SYSDATE, 'YYYY-MM'), :monto, :monto, TRUNC(SYSDATE) + 30, "
                        + "'PENDIENTE', :descripcion, :idMulta FROM DUAL "
                        + "WHERE NOT EXISTS (SELECT 1 FROM CUOTAS WHERE ID_MULTA_ORIGEN = :idMulta)",
                params);

        List<Map<String, Object>> fee = jdbc.queryForList(
                "SELECT ID_CUOTA FROM CUOTAS WHERE ID_MULTA_ORIGEN = :idMulta", new MapSqlParameterSource("idMulta", fine.idMulta()));
        if (fee.isEmpty()) {
            throw new IllegalStateException("No se pudo registrar la multa " + fine.idMulta() + " en la cartera");
        }
        Long idCuota = ((Number) fee.get(0).get("ID_CUOTA")).longValue();
        jdbc.update("UPDATE MULTAS SET ID_CUOTA = :idCuota WHERE ID_MULTA = :idMulta AND ID_CUOTA IS NULL",
                new MapSqlParameterSource("idCuota", idCuota).addValue("idMulta", fine.idMulta()));
        return Optional.of(idCuota);
    }
}
