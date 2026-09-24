package com.saed.backend.reservas.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Excepción lanzada cuando una solicitud de reserva entra en colisión/solapamiento
 * horario con una reserva activa existente para la misma zona y fecha (GAP-F8-03).
 */
public class ReservaSolapadaException extends ResponseStatusException {

    public ReservaSolapadaException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
