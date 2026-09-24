package com.saed.backend.porteria.dto;

/**
 * Resultado canónico de la invocación al procedimiento SP_VALIDAR_CONSUMIR_QR.
 */
public record QrConsumoResultadoDTO(
    boolean valido,
    String mensaje,
    Long visitaId
) {}
