package com.saed.backend.asambleas.service.impl;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Audit F10-11 / DEC-04: coefficients are fractions (0 &lt; c &lt;= 1), so quorum is always the
 * present-coefficient sum times 100, with no percentage-versus-fraction scale heuristic.
 */
class QuorumCalculatorTest {

    @Test
    void presentCoefficientFractionIsConvertedToPercentage() {
        assertEquals(new BigDecimal("52.50"), QuorumCalculator.toPercentage(new BigDecimal("0.525000")));
    }

    @Test
    void smallFractionIsNotMistakenForAPercentage() {
        assertEquals(new BigDecimal("0.80"), QuorumCalculator.toPercentage(new BigDecimal("0.008000")));
    }

    @Test
    void sumAboveOneIsCappedAtOneHundredInsteadOfReadAsAPercentage() {
        assertEquals(new BigDecimal("100.00"), QuorumCalculator.toPercentage(new BigDecimal("1.200000")));
    }

    @Test
    void absentSumIsZero() {
        assertEquals(new BigDecimal("0.00"), QuorumCalculator.toPercentage(null));
    }
}
