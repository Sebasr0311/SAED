package com.saed.backend.trabajadores.exception;

public class TrabajadorNoEncontradoException extends RuntimeException {
    public TrabajadorNoEncontradoException(String message) {
        super(message);
    }

    public TrabajadorNoEncontradoException(Long id) {
        super("Trabajador con id " + id + " no encontrado");
    }
}
