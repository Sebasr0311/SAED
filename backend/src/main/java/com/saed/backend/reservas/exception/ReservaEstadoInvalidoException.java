package com.saed.backend.reservas.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Excepción lanzada cuando se intenta asignar un estado inexistente o inválido a una reserva (GAP-F8-07-05).
 */
public class ReservaEstadoInvalidoException extends ResponseStatusException {

    public ReservaEstadoInvalidoException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
