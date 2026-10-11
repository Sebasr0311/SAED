package com.saed.backend.finanzas.billing;

import java.math.BigDecimal;

/**
 * Per-property billing settings (DEC-08). Early-payment discount and late interest are disabled
 * (0 %) until an administrator configures them; a fixed fee, when present, replaces the budget share.
 *
 * @param cuotaFija                fixed monthly fee per unit, or null to share the approved budget by coefficient
 * @param porcDescuentoProntoPago  discount percentage (0 = disabled)
 * @param diasProntoPago           days from the first day of the period during which the discount applies
 * @param porcInteresMoraMensual   simple monthly late-interest percentage (0 = disabled)
 * @param topeInteresMoraMensual   optional ceiling for the monthly interest percentage
 * @param diasGracia               days after the due date before interest starts
 * @param diaVencimiento           due day of the month (1-28)
 */
public record BillingSettings(Long idPropiedad, BigDecimal cuotaFija, BigDecimal porcDescuentoProntoPago,
                              int diasProntoPago, BigDecimal porcInteresMoraMensual,
                              BigDecimal topeInteresMoraMensual, int diasGracia, int diaVencimiento) {

    public static final int DEFAULT_DUE_DAY = 10;

    public static BillingSettings disabled(Long idPropiedad) {
        return new BillingSettings(idPropiedad, null, BigDecimal.ZERO, 0, BigDecimal.ZERO, null, 0, DEFAULT_DUE_DAY);
    }

    public boolean discountEnabled() {
        return porcDescuentoProntoPago != null && porcDescuentoProntoPago.signum() > 0 && diasProntoPago > 0;
    }

    public boolean interestEnabled() {
        return porcInteresMoraMensual != null && porcInteresMoraMensual.signum() > 0;
    }
}
