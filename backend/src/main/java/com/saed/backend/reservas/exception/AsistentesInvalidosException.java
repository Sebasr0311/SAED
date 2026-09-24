package com.saed.backend.reservas.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Excepción lanzada cuando la cantidad de asistentes es menor o igual a cero (GAP-F8-07-02).
 */
public class AsistentesInvalidosException extends ResponseStatusException {

    public AsistentesInvalidosException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
