package com.saed.backend.reservas.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Excepción lanzada cuando una transición de estado en reservas viola la máquina de estados o reglas de cancelación (GAP-F8-07-04 / GAP-F8-07-05).
 */
public class ReservaTransicionInvalidaException extends ResponseStatusException {

    public ReservaTransicionInvalidaException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
