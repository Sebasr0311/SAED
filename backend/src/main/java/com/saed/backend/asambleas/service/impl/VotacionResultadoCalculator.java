package com.saed.backend.asambleas.service.impl;

import com.saed.backend.asambleas.dto.VotacionResultado;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Weighted tally and majority evaluation applied when a voting is closed (F10-12, DEC-11).
 * Weights are the unit coefficients recorded with each vote (DEC-03).
 */
public final class VotacionResultadoCalculator {

    private static final BigDecimal HALF = new BigDecimal("0.50");
    private static final BigDecimal SEVENTY_PERCENT = new BigDecimal("0.70");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private VotacionResultadoCalculator() {
    }

    public static VotacionResultado evaluar(String tipoMayoria, BigDecimal si, BigDecimal no, BigDecimal blanco,
                                            BigDecimal abstencion, BigDecimal baseCoeficientes) {
        BigDecimal pesoSi = nz(si);
        BigDecimal pesoNo = nz(no);
        BigDecimal pesoBlanco = nz(blanco);
        BigDecimal pesoAbstencion = nz(abstencion);
        BigDecimal total = pesoSi.add(pesoNo).add(pesoBlanco).add(pesoAbstencion);

        BigDecimal porcentajeSi = BigDecimal.ZERO.setScale(4);
        boolean aprobada = false;
        if (total.signum() > 0) {
            porcentajeSi = pesoSi.multiply(HUNDRED).divide(total, 4, RoundingMode.HALF_UP);
            BigDecimal ratio = pesoSi.divide(total, 4, RoundingMode.HALF_UP);
            if ("CALIFICADA_70_PCT".equals(tipoMayoria)) {
                aprobada = ratio.compareTo(SEVENTY_PERCENT) >= 0;
            } else if ("UNANIMIDAD_100_PCT".equals(tipoMayoria)) {
                aprobada = pesoSi.compareTo(total) == 0;
            } else {
                // SIMPLE_50_MAS_1 and any unknown value fall back to the most conservative common rule
                aprobada = pesoSi.compareTo(pesoNo) > 0 && ratio.compareTo(HALF) > 0;
            }
        }
        return new VotacionResultado(ganadora(pesoSi, pesoNo, pesoBlanco, pesoAbstencion, total), pesoSi, pesoNo,
                pesoBlanco, pesoAbstencion, total, baseCoeficientes, porcentajeSi, aprobada);
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
