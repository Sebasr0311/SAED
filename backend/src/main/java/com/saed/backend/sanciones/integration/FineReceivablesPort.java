package com.saed.backend.sanciones.integration;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Integration point between sanctions and finance (audit F10-06): posts a fine that a sanction
 * resolution imposed as a receivable charge of the unit, so it appears in the unit's balance and
 * account statement.
 *
 * <p>The finance module owns the implementation. Implementations must be idempotent per
 * {@code idMulta} (a retried call returns the same receivable id and never creates a second charge),
 * must run inside the caller's transaction and tenant context, and must make sure the fine is not
 * collectable twice (once as a receivable and again through the separate MULTAS payment path).
 * When no implementation is registered the fine stays in MULTAS with {@code ID_CUOTA} null.
 */
public interface FineReceivablesPort {

    /**
     * @return the receivable (CUOTAS) id that represents the fine, or empty if it was not posted
     */
    Optional<Long> postFineReceivable(FineReceivable fine);

    record FineReceivable(Long idMulta, Long idSancion, Long idPropiedad, Long idUnidad,
                          Long idConcepto, BigDecimal monto, String descripcion) {
    }
}
