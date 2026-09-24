package com.saed.backend.reservas.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Excepción lanzada cuando se intenta crear o renombrar una zona común con un nombre
 * que ya existe en la misma copropiedad (violación de UIX_ZONA_NOMBRE) (GAP-F8-05).
 */
public class ZonaNombreDuplicadoException extends ResponseStatusException {

    public ZonaNombreDuplicadoException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
