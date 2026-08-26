package com.edificio.admin.exception;

/**
 * Se lanza cuando se busca un registro por ID o criterio y no existe
 * en la base de datos.
 */
public class RegistroNoEncontradoException extends RuntimeException {

    public RegistroNoEncontradoException(String mensaje) {
        super(mensaje);
    }

    public RegistroNoEncontradoException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
