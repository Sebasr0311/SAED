package com.saed.backend.asambleas.dto;

import java.math.BigDecimal;

/**
 * Outcome of a voting computed when it is closed (F10-12, DEC-11).
 *
 * @param opcionGanadora      option with the highest weight (SI, NO, BLANCO, ABSTENCION), EMPATE on a tie, null without votes
 * @param pesoSi              summed unit coefficient of the SI votes
 * @param pesoNo              summed unit coefficient of the NO votes
 * @param pesoBlanco          summed unit coefficient of the BLANCO votes
 * @param pesoAbstencion      summed unit coefficient of the ABSTENCION votes
 * @param pesoTotalEmitido    total weight cast
 * @param baseCoeficientes    summed coefficient of the property's active units when the voting was closed
 * @param porcentajeSi        SI weight as a percentage of the weight cast (4 decimals)
 * @param aprobada            whether the voting's required majority was reached
 */
public record VotacionResultado(String opcionGanadora, BigDecimal pesoSi, BigDecimal pesoNo, BigDecimal pesoBlanco,
                                BigDecimal pesoAbstencion, BigDecimal pesoTotalEmitido, BigDecimal baseCoeficientes,
                                BigDecimal porcentajeSi, boolean aprobada) {

    public String resultado() {
        return aprobada ? "APROBADA" : "RECHAZADA";
    }
}
