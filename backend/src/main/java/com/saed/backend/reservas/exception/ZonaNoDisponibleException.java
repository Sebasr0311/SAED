package com.saed.backend.reservas.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Excepción de negocio lanzada cuando una zona común se encuentra en estado no operativo
 * (MANTENIMIENTO o INACTIVA) y no puede admitir nuevas reservas (GAP-F8-05).
 */
public class ZonaNoDisponibleException extends ResponseStatusException {

    public ZonaNoDisponibleException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
