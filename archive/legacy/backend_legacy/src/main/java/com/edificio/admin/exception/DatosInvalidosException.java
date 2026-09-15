package com.edificio.admin.exception;

/**
 * Se lanza cuando los datos de entrada no cumplen las reglas de negocio
 * o de validacion del servicio.
 */
public class DatosInvalidosException extends RuntimeException {

    public DatosInvalidosException(String mensaje) {
        super(mensaje);
    }

    public DatosInvalidosException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
