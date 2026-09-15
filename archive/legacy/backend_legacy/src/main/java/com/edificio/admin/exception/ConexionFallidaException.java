package com.edificio.admin.exception;

/**
 * Se lanza cuando no se puede establecer o mantener la conexion con Oracle.
 */
public class ConexionFallidaException extends RuntimeException {

    public ConexionFallidaException(String mensaje) {
        super(mensaje);
    }

    public ConexionFallidaException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
