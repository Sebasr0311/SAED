package com.saed.backend.reservas.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Excepción de negocio lanzada cuando una unidad residencial con cuotas vencidas
 * o saldo en mora intenta realizar una reserva de zonas comunes (GAP-F8-02).
 */
public class UnidadEnMoraException extends ResponseStatusException {

    public UnidadEnMoraException(String reason) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, reason);
    }
}
