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

    private static VotacionResultado eval(String tipo, String si, String no, String blanco, String abst) {
        return VotacionResultadoCalculator.evaluar(tipo, w(si), w(no), w(blanco), w(abst), w("1.000000"));
    }

    @Test
    void simpleMajorityNeedsMoreYesThanNoAndMoreThanHalfOfTheCastWeight() {
        VotacionResultado ok = eval("SIMPLE_50_MAS_1", "0.55", "0.15", "0", "0");
        assertTrue(ok.aprobada());
        assertEquals("SI", ok.opcionGanadora());

        assertFalse(eval("SIMPLE_50_MAS_1", "0.25", "0.30", "0", "0").aprobada());
        // yes leads but blank and abstention keep it at or below half of the cast weight
        assertFalse(eval("SIMPLE_50_MAS_1", "0.25", "0.20", "0.30", "0.25").aprobada());
    }

    @Test
    void qualifiedMajorityNeedsSeventyPercentOfTheCastWeight() {
        assertFalse(eval("CALIFICADA_70_PCT", "0.25", "0.15", "0", "0").aprobada());
        assertTrue(eval("CALIFICADA_70_PCT", "0.55", "0.15", "0", "0").aprobada());
        assertTrue(eval("CALIFICADA_70_PCT", "0.70", "0.30", "0", "0").aprobada());
    }

    @Test
    void unanimityNeedsEveryCastVoteToBeYes() {
        assertTrue(eval("UNANIMIDAD_100_PCT", "0.40", "0", "0", "0").aprobada());
        assertFalse(eval("UNANIMIDAD_100_PCT", "0.40", "0", "0", "0.01").aprobada());
    }

    @Test
    void aVotingWithoutVotesIsRejectedWithoutAWinner() {
        VotacionResultado none = eval("SIMPLE_50_MAS_1", "0", "0", "0", "0");
        assertFalse(none.aprobada());
        assertNull(none.opcionGanadora());
        assertEquals(0, none.pesoTotalEmitido().signum());
        assertEquals(0, none.porcentajeSi().signum());
    }

    @Test
    void aTieForTheTopWeightHasNoWinner() {
        assertEquals("EMPATE", eval("SIMPLE_50_MAS_1", "0.30", "0.30", "0", "0").opcionGanadora());
    }

    @Test
    void totalsPercentageAndBaseAreReported() {
        VotacionResultado r = VotacionResultadoCalculator.evaluar("CALIFICADA_70_PCT",
                w("0.55"), w("0.15"), w("0.05"), w("0.05"), w("0.900000"));
        assertEquals(0, r.pesoTotalEmitido().compareTo(w("0.80")));
        assertEquals(0, r.baseCoeficientes().compareTo(w("0.900000")));
        assertEquals(0, r.porcentajeSi().compareTo(w("68.7500")));
        assertFalse(r.aprobada());
    }
}
