package com.saed.backend.asambleas.service.impl;

import com.saed.backend.asambleas.dto.VotacionResultado;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** F10-12 / DEC-11: weighted tally and majority evaluation applied when a voting is closed. */
class VotacionResultadoCalculatorTest {

    private static BigDecimal w(String v) {
        return new BigDecimal(v);
    }

    /** Evaluates with a property base of 1.0 and the given represented (present) coefficients. */
    private static VotacionResultado eval(String tipo, String rep, String si, String no, String blanco, String abst) {
        return VotacionResultadoCalculator.evaluar(tipo, w(si), w(no), w(blanco), w(abst), w("1.000000"), w(rep));
    }

    // Ley 675 art. 45: SIMPLE_50_MAS_1 = more than half of the coefficients represented in the session.
    @Test
    void simpleMajorityNeedsMoreThanHalfOfTheRepresentedCoefficients() {
        VotacionResultado ok = eval("SIMPLE_50_MAS_1", "0.70", "0.55", "0.15", "0", "0");
        assertTrue(ok.aprobada());
        assertEquals("SI", ok.opcionGanadora());

        assertFalse(eval("SIMPLE_50_MAS_1", "0.55", "0.25", "0.30", "0", "0").aprobada());
        assertFalse(eval("SIMPLE_50_MAS_1", "1.00", "0.25", "0.20", "0.30", "0.25").aprobada());
    }

    // Changed from the earlier cast-weight rule: 0.30 SI vs 0.05 NO was 85 % of the cast weight (approved) but is only
    // 42.9 % of the 0.70 represented, because represented units that did not vote count against the majority.
    @Test
    void representedUnitsThatDidNotVoteCountAgainstASimpleMajority() {
        assertFalse(eval("SIMPLE_50_MAS_1", "0.70", "0.30", "0.05", "0", "0").aprobada());
        assertFalse(eval("SIMPLE_50_MAS_1", "0.60", "0.30", "0", "0", "0").aprobada());
        assertTrue(eval("SIMPLE_50_MAS_1", "0.60", "0.31", "0", "0", "0").aprobada());
    }

    // Ley 675 art. 46: CALIFICADA_70_PCT = 70 % of the total coefficients of the property, not of the votes cast.
    // Changed from the earlier rule: 0.55 SI / 0.15 NO (78.6 % of the cast weight) no longer passes against a base of 1.0.
    @Test
    void qualifiedMajorityNeedsSeventyPercentOfTheTotalPropertyCoefficients() {
        assertFalse(eval("CALIFICADA_70_PCT", "0.70", "0.55", "0.15", "0", "0").aprobada());
        assertTrue(eval("CALIFICADA_70_PCT", "0.80", "0.70", "0.10", "0", "0").aprobada());
        VotacionResultado smallBase = VotacionResultadoCalculator.evaluar("CALIFICADA_70_PCT",
                w("0.65"), w("0.10"), w("0"), w("0"), w("0.900000"), w("0.75"));
        assertTrue(smallBase.aprobada());
    }

    // Unanimity is defined over the represented coefficients: every represented coefficient must have voted SI.
    @Test
    void unanimityNeedsEveryRepresentedCoefficientToVoteYes() {
        assertTrue(eval("UNANIMIDAD_100_PCT", "0.40", "0.40", "0", "0", "0").aprobada());
        assertFalse(eval("UNANIMIDAD_100_PCT", "0.41", "0.40", "0", "0", "0.01").aprobada());
        assertFalse(eval("UNANIMIDAD_100_PCT", "0.50", "0.40", "0", "0", "0").aprobada());
    }

    @Test
    void aVotingWithoutVotesIsRejectedWithoutAWinner() {
        VotacionResultado none = eval("SIMPLE_50_MAS_1", "0.50", "0", "0", "0", "0");
        assertFalse(none.aprobada());
        assertNull(none.opcionGanadora());
        assertEquals(0, none.pesoTotalEmitido().signum());
        assertEquals(0, none.porcentajeSi().signum());
    }

    @Test
    void aTieForTheTopWeightHasNoWinner() {
        assertEquals("EMPATE", eval("SIMPLE_50_MAS_1", "0.60", "0.30", "0.30", "0", "0").opcionGanadora());
    }

    @Test
    void totalsBasesAndThePercentageOfTheLegalDenominatorAreReported() {
        VotacionResultado r = VotacionResultadoCalculator.evaluar("CALIFICADA_70_PCT",
                w("0.55"), w("0.15"), w("0.05"), w("0.05"), w("0.900000"), w("0.80"));
        assertEquals(0, r.pesoTotalEmitido().compareTo(w("0.80")));
        assertEquals(0, r.baseCoeficientes().compareTo(w("0.900000")));
        assertEquals(0, r.pesoRepresentado().compareTo(w("0.80")));
        // CALIFICADA reports SI as a percentage of the property base: 0.55 / 0.90
        assertEquals(0, r.porcentajeSi().compareTo(w("61.1111")));
        assertFalse(r.aprobada());

        VotacionResultado simple = VotacionResultadoCalculator.evaluar("SIMPLE_50_MAS_1",
                w("0.55"), w("0.15"), w("0"), w("0"), w("0.900000"), w("0.80"));
        // SIMPLE reports SI as a percentage of the represented coefficients: 0.55 / 0.80
        assertEquals(0, simple.porcentajeSi().compareTo(w("68.7500")));
    }
}
