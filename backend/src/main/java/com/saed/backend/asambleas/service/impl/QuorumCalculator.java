package com.saed.backend.asambleas.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Quorum as a percentage of the property's total coefficient (DEC-04: coefficients are fractions,
 * so the property total is 1). There is a single scale; a sum above 1 is a data error and is capped.
 */
public final class QuorumCalculator {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private QuorumCalculator() {
    }

    public static BigDecimal toPercentage(BigDecimal presentCoefficientSum) {
        BigDecimal sum = presentCoefficientSum == null ? BigDecimal.ZERO : presentCoefficientSum;
        BigDecimal pct = sum.multiply(ONE_HUNDRED);
        if (pct.compareTo(ONE_HUNDRED) > 0) {
            pct = ONE_HUNDRED;
        } else if (pct.signum() < 0) {
            pct = BigDecimal.ZERO;
        }
        return pct.setScale(2, RoundingMode.HALF_UP);
    }
}
