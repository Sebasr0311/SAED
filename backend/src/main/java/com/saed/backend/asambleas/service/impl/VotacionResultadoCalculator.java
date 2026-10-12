package com.saed.backend.asambleas.service.impl;

import com.saed.backend.asambleas.dto.VotacionResultado;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Weighted tally and majority evaluation applied when a voting is closed (F10-12, DEC-11).
 * Weights are the unit coefficients recorded with each vote (DEC-03); the majority rules are DEC-11.
 */
public final class VotacionResultadoCalculator {

    private static final BigDecimal HALF = new BigDecimal("0.50");
    private static final BigDecimal SEVENTY_PERCENT = new BigDecimal("0.70");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private VotacionResultadoCalculator() {
    }

    public static VotacionResultado evaluar(String tipoMayoria, BigDecimal si, BigDecimal no, BigDecimal blanco,
                                            BigDecimal abstencion, BigDecimal baseCoeficientes,
                                            BigDecimal pesoRepresentado) {
        BigDecimal pesoSi = nz(si);
        BigDecimal pesoNo = nz(no);
        BigDecimal pesoBlanco = nz(blanco);
        BigDecimal pesoAbstencion = nz(abstencion);
        BigDecimal total = pesoSi.add(pesoNo).add(pesoBlanco).add(pesoAbstencion);

        BigDecimal represented = nz(pesoRepresentado);
        BigDecimal base = nz(baseCoeficientes);

        // Ley 675 de 2001: art. 45 (ordinary decisions) counts against the coefficients represented in the session,
        // art. 46 (qualified decisions) against the total coefficients of the property.
        boolean qualified = "CALIFICADA_70_PCT".equals(tipoMayoria);
        BigDecimal denominator = qualified ? base : represented;

        BigDecimal porcentajeSi = BigDecimal.ZERO.setScale(4);
        boolean aprobada = false;
        if (total.signum() > 0 && denominator.signum() > 0) {
            porcentajeSi = pesoSi.multiply(HUNDRED).divide(denominator, 4, RoundingMode.HALF_UP);
            if (qualified) {
                aprobada = pesoSi.compareTo(denominator.multiply(SEVENTY_PERCENT)) >= 0;
            } else if ("UNANIMIDAD_100_PCT".equals(tipoMayoria)) {
                // Defined over the represented coefficients: every represented coefficient voted SI.
                aprobada = pesoSi.compareTo(denominator) == 0;
            } else {
                // SIMPLE_50_MAS_1 and any unknown value: the most common rule, more than half of the represented.
                aprobada = pesoSi.compareTo(denominator.multiply(HALF)) > 0;
            }
        }
        return new VotacionResultado(ganadora(pesoSi, pesoNo, pesoBlanco, pesoAbstencion, total), pesoSi, pesoNo,
                pesoBlanco, pesoAbstencion, total, baseCoeficientes, pesoRepresentado, porcentajeSi, aprobada);
    }

    private static String ganadora(BigDecimal si, BigDecimal no, BigDecimal blanco, BigDecimal abstencion,
                                   BigDecimal total) {
        if (total.signum() == 0) {
            return null;
        }
        BigDecimal max = si.max(no).max(blanco).max(abstencion);
        int leaders = 0;
        String leader = null;
        String[] names = {"SI", "NO", "BLANCO", "ABSTENCION"};
        BigDecimal[] weights = {si, no, blanco, abstencion};
        for (int i = 0; i < names.length; i++) {
            if (weights[i].compareTo(max) == 0) {
                leaders++;
                leader = names[i];
            }
        }
        return leaders == 1 ? leader : "EMPATE";
    }

    private static BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
