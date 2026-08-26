package com.edificio.admin.dao;

import java.sql.SQLException;
import java.util.List;

/**
 * Interfaz CRUD generica para todos los DAOs del sistema.
 * El tipo del ID es Integer para compatibilidad con los surrogates Oracle.
 */
public interface CrudDAO<T> {

    /** Devuelve todos los registros activos. */
    List<T> findAll() throws SQLException;

    /** Busca por clave primaria; devuelve null si no existe. */
    T findById(Integer id) throws SQLException;

    /**
     * Inserta un nuevo registro.
     * @return el ID generado por la secuencia Oracle
     */
    Integer insert(T entity) throws SQLException;

    /** Actualiza el registro existente. */
    void update(T entity) throws SQLException;

    /**
     * Soft-delete: marca el registro como inactivo / cancelado.
     * No ejecuta DELETE fisico.
     */
    void delete(Integer id) throws SQLException;
}
