package com.saed.backend.incidentes.exception;

public class IncidenteInvalidStateException extends RuntimeException {
    private final String estado;

    public IncidenteInvalidStateException(String estado) {
        super("Estado de incidente no válido: '" + estado + "'. Estados permitidos: REPORTADO, EN_INVESTIGACION, ACCION_TOMADA, ESCALADO_A_SANCION, CERRADO.");
        this.estado = estado;
    }

    public String getEstado() {
        return estado;
    }
}
