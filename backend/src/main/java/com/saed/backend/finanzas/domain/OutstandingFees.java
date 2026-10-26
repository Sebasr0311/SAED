package com.saed.backend.finanzas.domain;

/**
 * Single definition of an outstanding ("exigible") fee (F06-03 / F6-06). A fee is outstanding while it
 * has a balance and was not cancelled; PAGADA_PARCIAL therefore counts everywhere (paz y salvo,
 * portfolio, cash flow, reports). Callers embed these fragments instead of repeating state lists.
 */
public final class OutstandingFees {
    /** CUOTAS.ESTADO values that still carry a balance (CK_CUOTAS_ESTADO minus PAGADA and ANULADA). */
    public static final String STATES_SQL = "('PENDIENTE', 'VENCIDA', 'PAGADA_PARCIAL')";

    private OutstandingFees() {
    }

    /** Predicate for a CUOTAS alias: outstanding state and a positive balance. */
    public static String predicate(String alias) {
        return alias + ".ESTADO IN " + STATES_SQL + " AND " + alias + ".SALDO_PENDIENTE > 0";
    }
}
