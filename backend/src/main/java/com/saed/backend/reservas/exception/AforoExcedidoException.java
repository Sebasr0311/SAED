package com.saed.backend.reservas.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Excepción lanzada cuando la cantidad de asistentes supera el aforo máximo de la zona común (GAP-F8-07-01).
 */
public class AforoExcedidoException extends ResponseStatusException {

    public AforoExcedidoException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
