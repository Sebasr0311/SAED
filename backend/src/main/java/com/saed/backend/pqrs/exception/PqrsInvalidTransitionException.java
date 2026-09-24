package com.saed.backend.pqrs.exception;

public class PqrsInvalidTransitionException extends RuntimeException {
    private final String estadoOrigen;
    private final String estadoDestino;

    public PqrsInvalidTransitionException(String estadoOrigen, String estadoDestino) {
        super(String.format("Transición de estado no permitida para el ticket: de '%s' a '%s'", estadoOrigen, estadoDestino));
        this.estadoOrigen = estadoOrigen;
        this.estadoDestino = estadoDestino;
    }

    public PqrsInvalidTransitionException(String estadoOrigen, String estadoDestino, String detalle) {
        super(String.format("Transición de estado no permitida para el ticket: de '%s' a '%s'. %s", estadoOrigen, estadoDestino, detalle));
        this.estadoOrigen = estadoOrigen;
        this.estadoDestino = estadoDestino;
    }

    public String getEstadoOrigen() {
        return estadoOrigen;
    }

    public String getEstadoDestino() {
        return estadoDestino;
    }
}
