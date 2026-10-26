package com.saed.backend.finanzas.billing;

import java.math.BigDecimal;

/** Outcome of one period generation: new fees, fees that already existed (replay) and skipped units. */
public record GenerationResult(String periodo, int creadas, int existentes, int sinCoeficiente,
                               BigDecimal totalMensual) {
}
