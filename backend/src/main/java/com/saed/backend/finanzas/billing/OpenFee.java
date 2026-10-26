package com.saed.backend.finanzas.billing;

import java.math.BigDecimal;
import java.time.LocalDate;

/** An outstanding fee as read for the overdue / discount / interest transitions. */
public record OpenFee(Long idCuota, String estado, BigDecimal valorBase, BigDecimal valorDescuento,
                      BigDecimal valorInteresMora, BigDecimal saldoPendiente, LocalDate fechaVencimiento,
                      LocalDate fechaDescuentoProntoPago) {
}
