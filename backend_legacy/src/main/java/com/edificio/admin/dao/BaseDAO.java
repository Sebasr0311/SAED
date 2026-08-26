package com.edificio.admin.dao;

import java.sql.Connection;

/**
 * Clase base para todos los DAOs del sistema.
 * Centraliza la obtencion de conexion via ThreadLocal en ConexionBD,
 * eliminando la duplicacion del metodo conn() en cada implementacion.
 */
public abstract class BaseDAO {

    protected Connection conn() {
        return ConexionBD.getInstancia().getConexion();
    }
}
