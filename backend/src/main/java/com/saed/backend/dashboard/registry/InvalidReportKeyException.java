package com.saed.backend.dashboard.registry;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Excepción lanzada cuando se intenta configurar o ejecutar un reporte con una clave no allowlistada.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidReportKeyException extends RuntimeException {

    private final String invalidKey;

    public InvalidReportKeyException(String invalidKey) {
        super(String.format("La clave de consulta origen '%s' no es válida o no está permitida en el registro seguro de reportes.", invalidKey));
        this.invalidKey = invalidKey;
    }

    public String getInvalidKey() {
        return invalidKey;
    }
}
