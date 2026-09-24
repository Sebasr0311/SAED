package com.saed.backend.dashboard.export;

/**
 * Excepción de regla de negocio lanzada cuando una exportación supera el límite máximo de registros permitido (10.000 filas).
 */
public class ExportVolumeLimitExceededException extends RuntimeException {
    public ExportVolumeLimitExceededException(String message) {
        super(message);
    }
}
