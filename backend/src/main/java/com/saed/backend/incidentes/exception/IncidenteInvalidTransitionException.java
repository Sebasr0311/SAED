package com.saed.backend.incidentes.exception;

public class IncidenteInvalidTransitionException extends RuntimeException {
    private final String estadoOrigen;
    private final String estadoDestino;

    public IncidenteInvalidTransitionException(String estadoOrigen, String estadoDestino, String message) {
        super(message);
        this.estadoOrigen = estadoOrigen;
        this.estadoDestino = estadoDestino;
    }

    public IncidenteInvalidTransitionException(String estadoOrigen, String estadoDestino) {
        super("Transición de estado no permitida para el incidente: de '" + estadoOrigen + "' hacia '" + estadoDestino + "'.");
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
