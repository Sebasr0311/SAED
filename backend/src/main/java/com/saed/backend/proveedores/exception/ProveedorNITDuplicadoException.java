package com.saed.backend.proveedores.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Excepción de negocio lanzada cuando se detecta un NIT duplicado dentro de la misma organización (UQ_PROVEEDOR_NIT).
 * Mapeada a HTTP 409 Conflict.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class ProveedorNITDuplicadoException extends RuntimeException {
    public ProveedorNITDuplicadoException(String message) {
        super(message);
    }
}
