package com.saed.backend.sanciones.exception;

/**
 * Excepción de dominio para cuando una copropiedad intenta aplicar una sanción económica
 * (MULTA_ECONOMICA) sin tener configurado un concepto de cobro activo de tipo MULTA en CONCEPTOS_COBRO.
 */
public class ConceptoMultaNoConfiguradoException extends RuntimeException {
    public ConceptoMultaNoConfiguradoException(String message) {
        super(message);
    }
}
