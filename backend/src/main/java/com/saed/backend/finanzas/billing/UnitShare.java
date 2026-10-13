package com.saed.backend.finanzas.billing;

import java.math.BigDecimal;

/** A unit and its registered co-ownership coefficient (null when the census has no value yet). */
public record UnitShare(Long idUnidad, BigDecimal coeficiente) {
}
