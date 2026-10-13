package com.saed.backend.finanzas.billing;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A fee to be created for a unit and period. */
public record NewFee(Long idUnidad, Long idConcepto, String periodo, BigDecimal valorBase,
                     BigDecimal valorDescuento, BigDecimal saldoPendiente, LocalDate fechaVencimiento,
                     LocalDate fechaDescuentoProntoPago) {
}
