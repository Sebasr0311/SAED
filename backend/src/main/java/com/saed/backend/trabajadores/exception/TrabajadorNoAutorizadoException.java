package com.saed.backend.trabajadores.exception;

public class TrabajadorNoAutorizadoException extends RuntimeException {
    public TrabajadorNoAutorizadoException(String message) {
        super(message);
    }
}
