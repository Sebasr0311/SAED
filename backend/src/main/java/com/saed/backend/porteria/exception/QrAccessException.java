package com.saed.backend.porteria.exception;

import org.springframework.http.HttpStatus;

/**
 * Excepción de dominio para errores en la validación y consumo de credenciales QR.
 * Proporciona código de error estandarizado y código de estado HTTP (default 409 Conflict).
 */
public class QrAccessException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public QrAccessException(String message) {
        super(message);
        this.code = "CONFLICT";
        this.status = HttpStatus.CONFLICT;
    }

    public QrAccessException(String message, String code, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
