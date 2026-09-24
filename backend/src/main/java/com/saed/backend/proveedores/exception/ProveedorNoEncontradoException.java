package com.saed.backend.proveedores.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Excepción lanzada cuando un proveedor no existe o no se encuentra dentro del tenant autorizado.
 * Mapeada a HTTP 404 Not Found.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ProveedorNoEncontradoException extends RuntimeException {
    public ProveedorNoEncontradoException(String message) {
        super(message);
    }

    public ProveedorNoEncontradoException(Long id) {
        super("Proveedor con id " + id + " no encontrado");
    }
}
